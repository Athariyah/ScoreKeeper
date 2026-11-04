package ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ───────────────────────── Бренд-палитра (из Figma) ─────────────────────────

object Brand {
    val Blue = Color(0xFF3B7CFF)
    val Green = Color(0xFF69DB7C)
    val Sky = Color(0xFF74C0FC)
    val Red = Color(0xFFFF8787)
    val Yellow = Color(0xFFFFD43B)
}

// ───────────────────────── Цвета темы ─────────────────────────

@Immutable
class ScoreKeeperColors(
    val canvas: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val buttonStart: Color,
    val buttonEnd: Color,
    val buttonGlow: Color,
    val onButton: Color,
    val iconButtonBg: Color,
    val iconButtonBgHover: Color,
    val iconButtonFg: Color,
    val titleTop: Color,
    val titleBottom: Color,
    val titleGlow: Color,
    val logoTop: Color,
    val logoBottom: Color,
    val logoDigits: Color,
    val chipBg: Color,
    val chipText: Color,
)

/** Светлая тема. Тёмную добавим отдельным объектом ScoreKeeperColors(...) по тёмным макетам. */
val LightColors = ScoreKeeperColors(
    canvas = Color.White,
    glassFill = Color(0x73FFF8D1),          // rgba(255,248,209,0.45) — стекло менее прозрачное
    glassBorder = Color(0x33AAA6A6),        // rgba(170,166,166,0.2)
    buttonStart = Color(0xFF1D6FF7),
    buttonEnd = Color(0xFF6B8EC7),
    buttonGlow = Color(0xFF2171F4),
    onButton = Color.White,
    iconButtonBg = Color(0xFFE3F2FD),
    iconButtonBgHover = Color(0xFFCFE5FB),
    iconButtonFg = Color(0xFF05306E),
    titleTop = Color(0xFF03020A),
    titleBottom = Color(0xFF0F0F7F),
    titleGlow = Color(0xFF2171F4),
    logoTop = Color(0xFF03020A),
    logoBottom = Color(0xFF0F0F7F),
    logoDigits = Color(0xFFDCB422),
    chipBg = Color.White,
    chipText = Color.Black,
)

// ───────────────────────── Шрифты и типографика ─────────────────────────
// Положите .ttf в frontend/src/main/resources/fonts/ с ТОЧНО такими именами.
// Если файла нет, приложение не падает, а берёт системный шрифт.

private fun resourceExists(path: String): Boolean =
    Thread.currentThread().contextClassLoader?.getResource(path) != null

private val clearSansLoaded = resourceExists("fonts/ClearSans-Bold.ttf")
private val aqumLoaded = resourceExists("fonts/AqumTwo-Classic.ttf")

private val ClearSans: FontFamily =
    if (clearSansLoaded) FontFamily(Font("fonts/ClearSans-Bold.ttf", FontWeight.Bold))
    else FontFamily.SansSerif

private val AqumTwo: FontFamily =
    if (aqumLoaded) FontFamily(Font("fonts/AqumTwo-Classic.ttf", FontWeight.Normal))
    else FontFamily.SansSerif

@Immutable
class ScoreKeeperTypography(
    val display: TextStyle,     // «ScoreKeeper»
    val logoDigits: TextStyle,  // «30:45» в логотипе
    val button: TextStyle,      // подписи кнопок
    val chip: TextStyle,        // мелкие плашки внизу
)

val DefaultTypography = ScoreKeeperTypography(
    display = TextStyle(fontFamily = ClearSans, fontWeight = FontWeight.Bold, fontSize = 100.sp),
    logoDigits = TextStyle(fontFamily = ClearSans, fontWeight = FontWeight.Bold, fontSize = 80.sp),
    button = TextStyle(
        fontFamily = AqumTwo,
        fontWeight = if (aqumLoaded) FontWeight.Normal else FontWeight.Bold,
        fontSize = 32.sp,
    ),
    // Подписи внизу: в Figma — Sans Serif Collection Regular 15.
    chip = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
)

// ───────────────────────── Доступ к теме ─────────────────────────

private val LocalColors = staticCompositionLocalOf { LightColors }
private val LocalTypography = staticCompositionLocalOf { DefaultTypography }

object ScoreKeeperTheme {
    val colors: ScoreKeeperColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
    val typography: ScoreKeeperTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current
}

@Composable
fun ScoreKeeperTheme(
    colors: ScoreKeeperColors = LightColors,
    typography: ScoreKeeperTypography = DefaultTypography,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides typography,
        content = content,
    )
}

// ───────────────────────── Масштабирование под размер окна ─────────────────────────
// Макет нарисован на 1920×1080. DesignStage подменяет Density так, что 1 dp = 1 px макета,
// а весь холст 1920×1080 вписывается в окно. Все размеры в коде можно писать как в Figma.
// Масштаб операционной системы (125%, 150%) при этом не влияет: берётся реальный размер окна в пикселях.

const val DESIGN_WIDTH = 1920f
const val DESIGN_HEIGHT = 1080f

@Immutable
class StageMetrics(val windowWidth: Dp, val windowHeight: Dp) {
    /** Насколько окно шире макета с каждой стороны (если пропорции не 16:9). */
    val originX: Dp get() = (windowWidth - DESIGN_WIDTH.dp) / 2f
    val originY: Dp get() = (windowHeight - DESIGN_HEIGHT.dp) / 2f
}

val LocalStage = staticCompositionLocalOf { StageMetrics(DESIGN_WIDTH.dp, DESIGN_HEIGHT.dp) }

/**
 * Занимает всё окно. Внутри 1 dp = 1 px макета 1920×1080.
 * Контент макета центрируйте через Modifier.align(Alignment.Center).requiredSize(1920.dp, 1080.dp),
 * а то, что должно липнуть к краям окна (иконки, плашки), выравнивайте по краям самого окна.
 */
@Composable
fun DesignStage(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = minOf(constraints.maxWidth / DESIGN_WIDTH, constraints.maxHeight / DESIGN_HEIGHT)
        if (scale <= 0f) return@BoxWithConstraints
        val stage = StageMetrics(
            windowWidth = (constraints.maxWidth / scale).dp,
            windowHeight = (constraints.maxHeight / scale).dp,
        )
        CompositionLocalProvider(
            LocalDensity provides Density(density = scale, fontScale = 1f),
            LocalStage provides stage,
        ) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}
