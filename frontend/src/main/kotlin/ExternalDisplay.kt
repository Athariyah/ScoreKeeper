import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
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

        ScoreboardViewport(Modifier.fillMaxSize()) {
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

/**
 * Внутренняя область табло БЕЗ рамки и скруглённых углов (в единицах пространства 1129×698).
 * Рамку и углы на ТВ не показываем: табло занимает весь экран.
 */
private const val VISIBLE_WIDTH = 1055f
private const val VISIBLE_HEIGHT = 654f
private const val VISIBLE_LEFT = 37f
private const val VISIBLE_TOP = 22f

/**
 * Общий «видоискатель» табло: и окно на ТВ, и предпросмотр в админке рисуют табло ТОЛЬКО через него.
 *
 *  • Содержимое (тексты, логотипы, плашки) масштабируется РАВНОМЕРНО — круги остаются кругами,
 *    цифры не растягиваются, на какой бы экран и в каком бы окне оно ни выводилось.
 *  • Фон (тёмная заливка и цветные диагонали) растягивается на всю область, поэтому на экране
 *    любых пропорций (16:9 на ТВ) нет пустых полос по бокам.
 */
@Composable
fun ScoreboardViewport(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / VISIBLE_WIDTH
            val sy = size.height / VISIBLE_HEIGHT
            withTransform({
                scale(sx, sy, Offset.Zero)
                translate(-VISIBLE_LEFT, -VISIBLE_TOP)
            }) {
                drawBoardBackdrop()
            }
        }

        val fit = minOf(maxWidth.value / VISIBLE_WIDTH, maxHeight.value / VISIBLE_HEIGHT)
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(baseDensity.density * fit, 1f),
        ) {
            // Центр видимой области табло совпадает с центром блока 1129×698 — выравниваем по центру.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredSize(1129.dp, 698.dp)
            ) {
                content()
            }
        }
    }
}

/** Фон табло в координатах 1129×698: тёмная заливка, синяя и красная диагонали. */
private fun DrawScope.drawBoardBackdrop() {
    drawRect(Color(0xFF070F2B), topLeft = Offset.Zero, size = Size(1129f, 698f))
    // тёмно-синяя диагональная полоса (в макете — с прозрачностью 0.6)
    val navy = Path().apply {
        moveTo(7f, 691f); lineTo(743.3f, 7f); lineTo(1122f, 7f); lineTo(1122f, 212.2f); close()
    }
    drawPath(
        navy,
        Brush.verticalGradient(listOf(Color(0xFF2A5298), Color(0xFF1E3C72)), startY = 7f, endY = 691f),
        alpha = 0.6f,
    )
    // красная диагональ (в макете — с прозрачностью 0.9)
    val red = Path().apply {
        moveTo(7f, 691f); lineTo(1122f, 100f); lineTo(1122f, 691f); close()
    }
    drawPath(
        red,
        Brush.horizontalGradient(listOf(Color(0xFFBA0C24), Color(0xFFF21B3F)), startX = 7f, endX = 1122f),
        alpha = 0.9f,
    )
}

/** Windows: переключает режим экранов на «Расширить» (как Win+P → Расширить), чтобы ТВ не дублировал ноутбук. */
fun forceExtendedDisplayOnWindows() {
    if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
    runCatching {
        ProcessBuilder("DisplaySwitch.exe", "/extend").start()
            .waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
    }
}