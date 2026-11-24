package update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Состояние автообновления для интерфейса.
 *
 *  • Проверка выполняется один раз за запуск и только при подключении к Wi-Fi.
 *  • «Закрыть» — окно больше не появится, пока приложение не запустят заново.
 *  • «Сейчас» — приложение закрывается, обновление скачивается и ставится, приложение запускается снова.
 *  • «Позже» — обновление скачается и установится само, когда пользователь закроет приложение.
 */
object UpdateManager {
    /** Найденное обновление (null — его нет или проверка не выполнялась). */
    var available: UpdateInfo? by mutableStateOf(null)
        private set

    /** Показывать ли окно (оно рисуется только на главном экране). */
    var popupVisible: Boolean by mutableStateOf(false)
        private set

    @Volatile
    private var scheduled: UpdateInfo? = null
    private val checked = AtomicBoolean(false)
    private val launched = AtomicBoolean(false)

    /** Проверяет релизы GitHub. Безопасно вызывать сколько угодно раз — работает один раз за запуск. */
    suspend fun checkOnce() {
        if (!checked.compareAndSet(false, true)) return
        withContext(Dispatchers.IO) {
            val repository = AppInfo.repository ?: return@withContext
            if (UpdateInstaller.installRoot() == null && !UpdateInstaller.forced()) return@withContext
            if (UpdateConfig.REQUIRE_WIFI && !NetworkStatus.isWifiConnected()) return@withContext
            val info = try {
                UpdateChecker.check(repository, AppInfo.version)
            } catch (e: Exception) {
                null   // нет интернета, лимит GitHub, неожиданный ответ — просто не показываем окно
            } ?: return@withContext
            available = info
            popupVisible = true
        }
    }

    /** «Сейчас». */
    fun installNow(language: String) {
        val info = available ?: return
        popupVisible = false
        if (UpdateInstaller.launch(info, UpdateInstaller.Mode.NOW, language)) {
            launched.set(true)
            exitProcess(0)
        } else {
            scheduled = info   // не удалось запустить сразу — попробуем при закрытии
        }
    }

    /** «Позже»: установить при закрытии приложения. */
    fun installLater() {
        scheduled = available
        popupVisible = false
    }

    /** Кнопка «закрыть»: окно скрыто до следующего запуска, установка не планируется. */
    fun dismiss() {
        popupVisible = false
    }

    /** Подключается к выходу из JVM: если выбрано «Позже» — запускает тихую установку. */
    fun installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread { onShutdown() })
    }

    private fun onShutdown() {
        val info = scheduled ?: return
        if (launched.compareAndSet(false, true)) {
            UpdateInstaller.launch(info, UpdateInstaller.Mode.LATER)
        }
    }
}
