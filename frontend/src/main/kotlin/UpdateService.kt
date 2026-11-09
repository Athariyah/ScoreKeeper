import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Metadata for a compatible installer from the latest public GitHub release. */
data class AppUpdate(
    val version: String,
    val releaseUrl: String,
    val downloadUrl: String,
    val assetName: String,
)

/**
 * GitHub Releases updater. The current public release currently carries the Windows setup EXE,
 * so unattended install/restart is intentionally enabled only for packaged Windows builds.
 */
object UpdateService {
    const val CURRENT_VERSION = "1.0.0" // Keep in sync with frontend/build.gradle.kts.
    private const val RELEASE_API = "https://api.github.com/repos/Athariyah/ScoreKeeper/releases/latest"
    private const val MAX_INSTALLER_BYTES = 1_000_000_000L

    private val json = Json { ignoreUnknownKeys = true }
    private val downloadExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "scorekeeper-update-download").apply { isDaemon = true }
    }
    private val shutdownHookRegistered = AtomicBoolean(false)
    @Volatile private var scheduledDownload: CompletableFuture<File>? = null
    @Volatile private var scheduledAppPath: String? = null
    @Volatile private var relaunchAfterInstall: Boolean = false

    fun isPackagedWindowsApp(): Boolean {
        if (!System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)) return false
        val command = ProcessHandle.current().info().command().orElse(null) ?: return false
        val launcher = File(command)
        return launcher.isFile && launcher.nameWithoutExtension.equals("ScoreKeeper", ignoreCase = true)
    }

    /** Returns null for offline, unsupported OS, no compatible installer, or an up-to-date build. */
    fun checkForUpdate(): AppUpdate? {
        if (!isPackagedWindowsApp()) return null
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "ScoreKeeper-Desktop-Updater")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val release = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                json.parseToJsonElement(reader.readText()).jsonObject
            }
            if (release["draft"]?.jsonPrimitive?.content == "true" ||
                release["prerelease"]?.jsonPrimitive?.content == "true"
            ) return null

            val version = release["tag_name"]?.jsonPrimitive?.content
                ?.takeIf { extractVersion(it) != null }
                ?: release["name"]?.jsonPrimitive?.content
                ?: return null
            val normalizedVersion = extractVersion(version) ?: return null
            if (compareVersions(normalizedVersion, CURRENT_VERSION) <= 0) return null

            val assets = release["assets"]?.jsonArray ?: return null
            val windowsAsset = assets.firstNotNullOfOrNull { element ->
                val asset = element.jsonObject
                val name = asset["name"]?.jsonPrimitive?.content ?: return@firstNotNullOfOrNull null
                // MSI is supported too, should a future GitHub release publish it instead.
                if (!name.equals("ScoreKeeper-Setup.exe", ignoreCase = true) &&
                    !name.endsWith(".msi", ignoreCase = true)
                ) return@firstNotNullOfOrNull null
                val url = asset["browser_download_url"]?.jsonPrimitive?.content
                    ?.takeIf { it.startsWith("https://github.com/") }
                    ?: return@firstNotNullOfOrNull null
                AppUpdate(
                    version = normalizedVersion,
                    releaseUrl = release["html_url"]?.jsonPrimitive?.content
                        ?: "https://github.com/Athariyah/ScoreKeeper/releases/latest",
                    downloadUrl = url,
                    assetName = name,
                )
            }
            windowsAsset
        } finally {
            connection.disconnect()
        }
    }

    /** Starts (or reuses) an asynchronous installer download and arranges install/relaunch on exit. */
    fun scheduleInstallAfterExit(update: AppUpdate, relaunchApp: Boolean): CompletableFuture<File> {
        check(isPackagedWindowsApp()) { "Automatic installation is available in the installed Windows app." }
        val appPath = ProcessHandle.current().info().command().orElseThrow {
            IllegalStateException("Could not locate the installed ScoreKeeper launcher.")
        }
        scheduledAppPath = appPath
        relaunchAfterInstall = relaunchApp
        val future = synchronized(this) {
            val previous = scheduledDownload
            if (previous != null && !previous.isCompletedExceptionally && !previous.isCancelled) {
                previous
            } else {
                CompletableFuture.supplyAsync({ download(update) }, downloadExecutor)
                    .also { scheduledDownload = it }
            }
        }
        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(Thread({
                runCatching {
                    val pending = scheduledDownload ?: return@runCatching
                    val installer = pending.get(10, TimeUnit.MINUTES)
                    val launcher = scheduledAppPath ?: return@runCatching
                    startWindowsInstallHelper(installer, launcher, relaunchAfterInstall)
                }
            }, "scorekeeper-update-on-exit"))
        }
        return future
    }

    private fun download(update: AppUpdate): File {
        val safeName = update.assetName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "ScoreKeeper-Setup.exe" }
        val updateDir = File(System.getProperty("java.io.tmpdir"), "ScoreKeeper/updates")
        if (!updateDir.exists() && !updateDir.mkdirs()) error("Could not create the update download folder.")
        val target = File(updateDir, safeName)
        val partial = File(updateDir, "$safeName.part")

        val connection = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ScoreKeeper-Desktop-Updater")
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val status = connection.responseCode
            check(status in 200..299) { "Installer download failed (HTTP $status)." }
            val contentLength = connection.contentLengthLong
            check(contentLength <= MAX_INSTALLER_BYTES) { "The installer file is unexpectedly large." }
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= MAX_INSTALLER_BYTES) { "The installer file is unexpectedly large." }
                        output.write(buffer, 0, read)
                    }
                }
            }
            check(partial.length() > 0L) { "The installer download was empty." }
            Files.move(
                partial.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
            return target
        } catch (error: Throwable) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    /** A detached batch helper waits until this process exits, runs the installer, then relaunches. */
    private fun startWindowsInstallHelper(installer: File, appPath: String, relaunchApp: Boolean) {
        val pid = ProcessHandle.current().pid()
        val helper = File.createTempFile("scorekeeper-update-", ".cmd")
        val installerPath = installer.absolutePath
        val escapedInstaller = installerPath.replace("%", "%%").replace("\"", "\"\"")
        val escapedApp = appPath.replace("%", "%%").replace("\"", "\"\"")
        val command = if (installer.extension.equals("msi", ignoreCase = true)) {
            "msiexec.exe /i \"$escapedInstaller\" /qn /norestart"
        } else {
            "start /wait \"\" \"$escapedInstaller\" /VERYSILENT /SUPPRESSMSGBOXES /NORESTART"
        }
        helper.writeText(
            """@echo off
:wait_for_app
""" +
                "tasklist /FI \"PID eq $pid\" | findstr /C:\"$pid\" >nul\r\n" +
                "if not errorlevel 1 (\r\n  timeout /t 1 /nobreak >nul\r\n  goto wait_for_app\r\n)\r\n" +
                "$command\r\n" +
                (if (relaunchApp) "if not errorlevel 1 start \"\" \"$escapedApp\"\r\n" else "") +
                "del \"%~f0\"\r\n",
            Charsets.UTF_8,
        )
        ProcessBuilder("cmd.exe", "/c", "start", "", helper.absolutePath)
            .redirectErrorStream(true)
            .start()
    }

    private fun extractVersion(text: String): String? {
        val match = Regex("(?<!\\d)(\\d+)\\.(\\d+)(?:\\.(\\d+))?").find(text) ?: return null
        return "${match.groupValues[1]}.${match.groupValues[2]}.${match.groupValues[3].ifBlank { "0" }}"
    }

    private fun compareVersions(first: String, second: String): Int {
        val a = first.split('.').map { it.toIntOrNull() ?: 0 }
        val b = second.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0..2) {
            val comparison = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }
}
