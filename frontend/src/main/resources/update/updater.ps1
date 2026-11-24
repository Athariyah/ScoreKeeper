<#
  Установка обновления ScoreKeeper.
  Запускается самим приложением (update/UpdateInstaller.kt) уже после того, как оно закрылось:
    1) ждёт завершения приложения и лаунчера;
    2) скачивает установщик из релиза GitHub (в режиме «now» — с окном прогресса);
    3) проверяет SHA-256, если сумма известна;
    4) запускает Inno Setup в тихом режиме — установка идёт поверх той же папки;
    5) в режиме «now» запускает приложение снова (и в случае ошибки — старую версию).
  Режим «later» (обновление «при закрытии») работает без окон и приложение не запускает.
  Журнал: %TEMP%\ScoreKeeper-update\update.log
#>
param(
    [Parameter(Mandatory = $true)][string]$Url,
    [Parameter(Mandatory = $true)][string]$FileName,
    [Parameter(Mandatory = $true)][string]$InstallDir,
    [ValidateSet('now', 'later')][string]$Mode = 'later',
    [ValidateSet('ru', 'en')][string]$Lang = 'ru',
    [string]$Sha256 = '',
    [string]$WaitPids = '',
    [string]$Version = ''
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$work = Join-Path $env:TEMP 'ScoreKeeper-update'
New-Item -ItemType Directory -Force -Path $work | Out-Null
$logFile = Join-Path $work 'update.log'

function Write-Log([string]$Message) {
    try {
        Add-Content -Path $logFile -Encoding UTF8 -Value ('{0:yyyy-MM-dd HH:mm:ss} {1}' -f (Get-Date), $Message)
    } catch { }
}

# Два обновления одновременно не запускаем.
$mutex = New-Object System.Threading.Mutex($false, 'Local\ScoreKeeperUpdater')
if (-not $mutex.WaitOne(0)) {
    Write-Log 'Другое обновление уже выполняется — выходим.'
    exit 0
}

if ($Lang -eq 'en') {
    $T = @{
        Closing    = 'Closing the application...'
        Downloading = 'Downloading the update... {0}%'
        Preparing  = 'Preparing the update...'
        Installing = 'Installing the update...'
        Failed     = "Could not install the update:`n{0}"
    }
} else {
    $T = @{
        Closing    = 'Закрываем приложение...'
        Downloading = 'Скачиваем обновление... {0}%'
        Preparing  = 'Подготовка обновления...'
        Installing = 'Устанавливаем обновление...'
        Failed     = "Не удалось установить обновление:`n{0}"
    }
}

# ── окно прогресса (только режим «now») ──
$form = $null
$label = $null
$bar = $null
if ($Mode -eq 'now') {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    [System.Windows.Forms.Application]::EnableVisualStyles()

    $form = New-Object System.Windows.Forms.Form
    $form.Text = 'ScoreKeeper'
    $form.StartPosition = 'CenterScreen'
    $form.FormBorderStyle = 'FixedDialog'
    $form.ControlBox = $false
    $form.MinimizeBox = $false
    $form.MaximizeBox = $false
    $form.TopMost = $true
    $form.ClientSize = New-Object System.Drawing.Size(420, 110)

    $label = New-Object System.Windows.Forms.Label
    $label.AutoSize = $false
    $label.Location = New-Object System.Drawing.Point(20, 20)
    $label.Size = New-Object System.Drawing.Size(380, 24)
    $label.Text = $T.Preparing

    $bar = New-Object System.Windows.Forms.ProgressBar
    $bar.Location = New-Object System.Drawing.Point(20, 58)
    $bar.Size = New-Object System.Drawing.Size(380, 22)
    $bar.Minimum = 0
    $bar.Maximum = 100
    $bar.Style = 'Marquee'

    $form.Controls.Add($label)
    $form.Controls.Add($bar)
    $form.Show()
    [System.Windows.Forms.Application]::DoEvents()
}

function Set-Status([string]$Text, [int]$Percent = -1) {
    if ($null -eq $form) { return }
    $label.Text = $Text
    if ($Percent -ge 0) {
        $bar.Style = 'Continuous'
        $bar.Value = [Math]::Min(100, $Percent)
    } else {
        $bar.Style = 'Marquee'
    }
    [System.Windows.Forms.Application]::DoEvents()
}

# Есть ли запущенные процессы из папки установки (приложение, лаунчер, бэкенд).
function Test-AppRunning {
    try {
        $running = Get-Process | Where-Object {
            $_.Path -and $_.Path.StartsWith($InstallDir, [System.StringComparison]::OrdinalIgnoreCase)
        }
        return [bool]$running
    } catch {
        return $false
    }
}

$installer = Join-Path $work $FileName
$mustRelaunch = ($Mode -eq 'now')

try {
    Write-Log "Старт: режим $Mode, версия $Version"

    # 1) ждём закрытия приложения и лаунчера
    Set-Status $T.Closing
    foreach ($id in ($WaitPids -split ',' | Where-Object { $_ -match '^\d+$' })) {
        $process = Get-Process -Id ([int]$id) -ErrorAction SilentlyContinue
        if ($process) { $null = $process.WaitForExit(60000) }
    }
    for ($i = 0; $i -lt 50 -and (Test-AppRunning); $i++) { Start-Sleep -Milliseconds 200 }

    # «Позже»: если пользователь успел открыть приложение снова — ничего не трогаем,
    # чтобы не закрыть ему идущий матч. Обновление предложится при следующем запуске.
    if ($Mode -eq 'later' -and (Test-AppRunning)) {
        Write-Log 'Приложение запущено снова — установка отложена.'
        exit 0
    }

    # 2) скачиваем установщик
    Set-Status ($T.Downloading -f 0) 0
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $request = [System.Net.HttpWebRequest]::Create($Url)
    $request.UserAgent = 'ScoreKeeper-Updater'
    $request.Timeout = 30000
    $request.ReadWriteTimeout = 30000
    $response = $request.GetResponse()
    try {
        $total = $response.ContentLength
        $stream = $response.GetResponseStream()
        $file = [System.IO.File]::Create($installer)
        try {
            $buffer = New-Object byte[] 262144
            $done = 0L
            $lastPercent = -1
            while (($read = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                $file.Write($buffer, 0, $read)
                $done += $read
                if ($total -gt 0) {
                    $percent = [int](100 * $done / $total)
                    if ($percent -ne $lastPercent) {
                        $lastPercent = $percent
                        Set-Status ($T.Downloading -f $percent) $percent
                    }
                }
            }
        } finally {
            $file.Dispose()
            $stream.Dispose()
        }
    } finally {
        $response.Close()
    }
    Write-Log "Скачано: $installer"

    # 3) контрольная сумма
    if ($Sha256) {
        $actual = (Get-FileHash -Path $installer -Algorithm SHA256).Hash
        if ($actual -ne $Sha256.Trim()) {
            throw "Контрольная сумма не совпала (ожидалась $Sha256, получена $actual)."
        }
        Write-Log 'SHA-256 совпала.'
    }

    # 4) установка поверх текущей (Inno Setup, AppId тот же, папка берётся из предыдущей установки)
    Set-Status $T.Installing
    if ($form) { $form.Hide() }
    $setupLog = Join-Path $work 'setup.log'
    $silence = if ($Mode -eq 'now') { '/SILENT' } else { '/VERYSILENT' }
    # «Сейчас»: приложение уже закрыто, при необходимости установщик закроет остатки сам.
    # «Позже»: чужие процессы не трогаем вообще.
    $closing = if ($Mode -eq 'now') { '/CLOSEAPPLICATIONS' } else { '/NOCLOSEAPPLICATIONS' }
    $setupArgs = '{0} /SUPPRESSMSGBOXES /NORESTART {1} /LOG="{2}"' -f $silence, $closing, $setupLog
    $setup = Start-Process -FilePath $installer -ArgumentList $setupArgs -PassThru -Wait
    if ($setup.ExitCode -ne 0) {
        throw "Установщик завершился с кодом $($setup.ExitCode) (см. $setupLog)."
    }
    Write-Log 'Обновление установлено.'
}
catch {
    Write-Log ('Ошибка: ' + $_.Exception.Message)
    if ($form) {
        $form.Hide()
        [void][System.Windows.Forms.MessageBox]::Show(
            ($T.Failed -f $_.Exception.Message), 'ScoreKeeper',
            [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Error)
    }
}
finally {
    Remove-Item -Path $installer -Force -ErrorAction SilentlyContinue
    if ($form) { $form.Dispose() }
    # 5) «Сейчас»: запускаем приложение — новое, а если обновить не вышло, то прежнее.
    if ($mustRelaunch) {
        $launcher = Join-Path $InstallDir 'ScoreKeeper.exe'
        if (Test-Path $launcher) {
            Start-Process -FilePath $launcher -WorkingDirectory $InstallDir
            Write-Log 'Приложение запущено.'
        }
    }
    $mutex.ReleaseMutex()
}
