package update

import java.io.File

/**
 * Запуск установки обновления. Само приложение не может заменить свои файлы, пока работает, поэтому
 * скачивание и установку выполняет отдельный скрипт `updater.ps1` (PowerShell есть в любой Windows 10/11):
 * он дожидается закрытия приложения, скачивает установщик из релиза, проверяет SHA-256 и запускает Inno Setup.
 */
object UpdateInstaller {

    enum class Mode(val arg: String) {
        /** «Сейчас»: окно с прогрессом, после установки приложение запускается снова. */
        NOW("now"),

        /** «Позже» (при закрытии): всё тихо, без окон, приложение не запускается. */
        LATER("later"),
    }

    fun forced(): Boolean = System.getProperty(UpdateConfig.FORCE_PROPERTY) == "true"

    /**
     * Папка установки (где лежит ScoreKeeper.exe-лаунчер) или null, если приложение запущено не из установщика.
     * Структура: <корень>\ScoreKeeper.exe, <корень>\BackendRelease, <корень>\FrontendRelease\ScoreKeeper\ScoreKeeper.exe.
     */
    fun installRoot(): File? {
        val command = ProcessHandle.current().info().command().orElse(null) ?: return null
        val appDir = File(command).parentFile ?: return null           // …\FrontendRelease\ScoreKeeper
        val frontendDir = appDir.parentFile ?: return null             // …\FrontendRelease
        val root = frontendDir.parentFile ?: return null               // корень установки
        val looksInstalled = frontendDir.name.equals("FrontendRelease", ignoreCase = true) &&
            File(root, "ScoreKeeper.exe").isFile &&
            File(root, "BackendRelease").isDirectory
        return root.takeIf { looksInstalled }
    }

    /** Запускает установку. true — скрипт запущен. [language]: «ru» или «en» (язык окна прогресса). */
    fun launch(info: UpdateInfo, mode: Mode, language: String = "ru"): Boolean {
        val root = installRoot() ?: return false
        val script = extractScript() ?: return false

        val command = mutableListOf(
            "powershell.exe", "-NoProfile", "-NonInteractive",
            "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
            "-File", script.absolutePath,
            "-Url", info.downloadUrl,
            "-FileName", info.assetName,
            "-InstallDir", root.absolutePath,
            "-Mode", mode.arg,
            "-Lang", language,
            "-Version", info.version,
            "-WaitPids", processesToWaitFor().joinToString(","),
        )
        info.sha256?.let { command += listOf("-Sha256", it) }

        return try {
            ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Приложение и его лаунчер: скрипт ждёт, пока они завершатся, и только потом ставит обновление. */
    private fun processesToWaitFor(): List<Long> {
        val self = ProcessHandle.current()
        val pids = mutableListOf(self.pid())
        self.parent().ifPresent { parent ->
            val name = parent.info().command().orElse("")
            if (name.endsWith("ScoreKeeper.exe", ignoreCase = true)) pids.add(parent.pid())
        }
        return pids
    }

    private fun extractScript(): File? {
        val bytes = UpdateInstaller::class.java.getResourceAsStream("/update/updater.ps1")?.use { it.readBytes() }
            ?: return null
        val dir = File(System.getProperty("java.io.tmpdir"), "ScoreKeeper-update").apply { mkdirs() }
        val file = File(dir, "updater.ps1")
        // Windows PowerShell 5.1 читает UTF-8 без BOM как ANSI — русский текст превратился бы в «кракозябры».
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val hasBom = bytes.size >= 3 && bytes[0] == bom[0] && bytes[1] == bom[1] && bytes[2] == bom[2]
        return try {
            file.writeBytes(if (hasBom) bytes else bom + bytes)
            file
        } catch (e: Exception) {
            null
        }
    }
}
