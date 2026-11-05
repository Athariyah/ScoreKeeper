import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ui.theme.AppStrings
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

/** Внешний (не основной) монитор / ТВ по HDMI. Границы — в координатах AWT. */
data class ExternalDisplay(val id: String, val bounds: Rectangle)

/** Одноразовый опрос: первый экран, который не является основным. */
private fun findExternalDisplay(): ExternalDisplay? {
    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val primary = ge.defaultScreenDevice
    val other = ge.screenDevices.firstOrNull { it.iDstring != primary.iDstring } ?: return null
    return ExternalDisplay(other.iDstring, Rectangle(other.defaultConfiguration.bounds))
}

/**
 * Следит за подключением внешнего экрана: раз в секунду опрашивает AWT.
 * null — внешнего экрана нет (отключили HDMI или режим «Дублирование»).
 */
@Composable
fun rememberExternalDisplay(pollMillis: Long = 1_000L): State<ExternalDisplay?> =
    produceState<ExternalDisplay?>(initialValue = null) {
        while (true) {
            value = withContext(Dispatchers.Default) { runCatching { findExternalDisplay() }.getOrNull() }
            delay(pollMillis)
        }
    }

/**
 * Полноэкранное табло на внешнем экране. Показывайте его, пока [display] != null.
 * Окно без рамки, на всю площадь монитора, не забирает фокус у окна оператора.
 */
@Composable
fun ApplicationScope.ExternalScoreboardWindow(
    display: ExternalDisplay,
    match: MatchState,
    logo1: ImageBitmap?,
    logo2: ImageBitmap?,
    tickerText: String,
    courtSide: String,
    strings: AppStrings,
) {
    val b = display.bounds
    // Состояние пересоздаём при смене экрана, чтобы окно сразу открывалось на нужном мониторе.
    val state = remember(display.id, b.x, b.y, b.width, b.height) {
        androidx.compose.ui.window.WindowState(
            position = WindowPosition(b.x.dp, b.y.dp),
            size = DpSize(b.width.dp, b.height.dp),
        )
    }
    Window(
        onCloseRequest = { /* закрывается само при отключении HDMI / выходе из игры */ },
        title = "ScoreKeeper - Табло",
        icon = painterResource("app_icon.png"),
        state = state,
        undecorated = true,
        resizable = false,
        alwaysOnTop = true,   // поверх панели задач на втором мониторе
        focusable = false,    // клики оператора не «перехватываются» окном табло
    ) {
        // Страховка: некоторые ОС игнорируют стартовую позицию — выставляем границы явно.
        LaunchedEffect(display) { window.bounds = Rectangle(b) }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF002296))
                .clipToBounds()
        ) {
            // Внутренняя область табло БЕЗ голубой рамки и скруглённых углов.
            // Если захотите чуть сильнее/слабее обрезать края — уменьшите/увеличьте эти 2 числа:
            val visibleWidth = 1055f
            val visibleHeight = 654f

            val scaleX = maxWidth.value / visibleWidth
            val scaleY = maxHeight.value / visibleHeight
            val baseDensity = LocalDensity.current

            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density * scaleY, 1f),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .requiredSize(1129.dp, 698.dp)
                        .graphicsLayer {
                            // Растягиваем по горизонтали до полного заполнения 16:9 экрана ТВ
                            this.scaleX = scaleX / scaleY
                        }
                ) {
                    AudienceScreen(
                        match = match,
                        logo1 = logo1,
                        logo2 = logo2,
                        tickerText = tickerText,
                        courtSide = courtSide,
                        strings = strings,
                    )
                }
            }
        }
    }
}

/** Windows: переключает режим экранов на «Расширить» (как Win+P → Расширить), чтобы ТВ не дублировал ноутбук. */
fun forceExtendedDisplayOnWindows() {
    if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
    runCatching {
        ProcessBuilder("DisplaySwitch.exe", "/extend").start()
            .waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
    }
}