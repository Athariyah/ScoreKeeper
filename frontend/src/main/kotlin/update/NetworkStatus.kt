package update

import java.util.concurrent.TimeUnit

/** Проверка подключения к Wi-Fi. */
object NetworkStatus {

    fun isWifiConnected(): Boolean {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        return if ("win" in os) windowsWifiConnected() else false
    }

    // `netsh wlan show interfaces` печатает строку «SSID : <имя сети>» только у подключённого адаптера
    // (у BSSID другое название строки). Ключи не локализуются, поэтому проверка работает и на русской Windows.
    private val ssidLine = Regex("""^\s*SSID\s*:\s*\S+""")

    private fun windowsWifiConnected(): Boolean = try {
        val process = ProcessBuilder("netsh", "wlan", "show", "interfaces")
            .redirectErrorStream(true)
            .start()
        // Вывод идёт в OEM-кодировке; нам нужны только ASCII-ключи, поэтому ошибок декодирования не будет.
        val text = process.inputStream.readBytes().toString(Charsets.ISO_8859_1)
        if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
        text.lineSequence().any { ssidLine.containsMatchIn(it) }
    } catch (e: Exception) {
        false
    }
}
