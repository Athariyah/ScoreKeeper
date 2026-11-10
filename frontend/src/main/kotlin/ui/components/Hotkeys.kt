package ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.theme.ScoreKeeperTheme

// ───────────────────────── Хоткеи: идентификаторы и группы ─────────────────────────
//
// Горячие клавиши — ровно те, что в макете «Хоткеи.pdf»:
//   матч:   1 / 2 — очко команде 1 / 2, «−», →| (корректировка), ⊠ (сброс), Enter (старт/финиш);
//   общие:  F11 и Esc — назад, ~ — настройки, F1 — закрыть, F2 — свернуть, Enter — готово.
// В окне подсказок они же работают как кнопки (см. HotkeysOverlay).
//
// Вся геометрия снята из присланных макетов (Frame 10.pdf — карточка 794×564,
// Group 7.pdf — верхняя полоса 616×53) и перенесена в dp один в один.

/** Одна горячая клавиша из списка подсказок. */
enum class HotkeyId {
    ADD_POINT_1,
    ADD_POINT_2,
    UNDO,
    CORRECTION,
    RESET,
    START_MATCH,
    BACK_F11,
    BACK_ESC,
    SETTINGS,
    CLOSE_APP,
    MINIMIZE,
    DONE,
}

/** Колонка подсказок: Match — «КНОПКИ МАТЧА», Main — «ОСНОВНЫЕ». */
enum class HotkeyGroup { Match, Main }

/** Что нарисовано внутри «колпашки» клавиши. */
enum class KeyGlyph {
    Digit1, Digit2, Minus, Correction, Reset, EnterWide,
    LetterH, Esc, Tilde, F1, F2, EnterSmall,
}

/**
 * Мигающая подсветка нажатой клавиши: окно подсказок получает [pulse] и подсвечивает
 * нужную строку. Живёт в отдельном классе (не в CompositionLocal), чтобы состояние
 * было доступно и обработчику клавиатуры, и оверлею.
 */
@Stable
class HotkeyPulse {
    /** Счётчик «вспышек»: растёт на каждое нажатие, даже если клавиша та же. */
    internal var seq by mutableLongStateOf(0L)

    /** id последней нажатой клавиши. */
    var lastId: HotkeyId? = null
        private set

    /** Номер «вспышки»: увеличивается при каждом нажатии, даже если клавиша та же. */
    val tick: Long get() = seq

    fun fire(id: HotkeyId) {
        lastId = id
        seq += 1
    }
}

// Метрики Aqum two Classic (замерены по TTF): ascent = 1.0·size, descent = 0.26·size,
// высота заглавной буквы = 0.702·size. Нужны, чтобы ставить текст точно по базовой линии
// из макета, а не «на глаз».
private const val AQUM_ASCENT = 1.0f
private const val AQUM_DESCENT = 0.26f

/** Верх текстового блока, при котором первая базовая линия попадает в [baseline]. */
internal fun blockTopForBaseline(baseline: Float, size: Float, lineHeight: Float): Float {
    val half = ((lineHeight - size * (AQUM_ASCENT + AQUM_DESCENT)) / 2f).coerceAtLeast(0f)
    return baseline - (half + size * AQUM_ASCENT)
}

// ───────────────────────── Геометрия карточки (Frame 10.pdf) ─────────────────────────

object HotkeyPanel {
    const val WIDTH = 794f
    const val HEIGHT = 564f
    const val RADIUS = 50f

    /** Кнопка «назад»: круг 61 с рамкой 3 dp и белым полем 55. */
    const val BACK_X = 711f
    const val BACK_Y = 22f
    const val BACK_SIZE = 61f

    const val DIVIDER_X = 396f
    const val DIVIDER_TOP = 123f
    const val DIVIDER_BOTTOM = 523f

    /** Заголовок «ХОТКЕИ»: обводка 296…494, 35.3…71.1 → верх заглавных ≈ 37.1. */
    const val TITLE_X = 296f
    const val TITLE_CAP_TOP = 37.1f
    const val TITLE_SIZE = 46f

    /** Заголовки колонок: «КНОПКИ МАТЧА» 96…298, «ОСНОВНЫЕ» 520…667; верх заглавных ≈ 107.2. */
    const val LEFT_HEADER_X = 96f
    const val RIGHT_HEADER_X = 520f
    const val HEADER_CAP_TOP = 107.2f
    const val HEADER_SIZE = 24f

    /** Клавиша: 48×52 по SVG (ширина у широких клавиш — 69 / 81 / 155). */
    const val KEY_H = 51.7f
    const val LABEL_SIZE = 13.9f
    const val LABEL_LINE = 18.2f
}

/**
 * Строка подсказки. Координаты — из макета, от левого верхнего угла карточки:
 * [keyX]/[keyY] — верхний левый угол «подошвы» клавиши, [keyW] — её ширина,
 * [textX]/[textY] — левый край и базовая линия первой строки подписи.
 */
class HotkeyRow(
    val id: HotkeyId,
    val glyph: KeyGlyph,
    val keyX: Float,
    val keyY: Float,
    val keyW: Float,
    val textX: Float,
    val textY: Float,
    val label: (ui.theme.AppStrings) -> String,
)

/** Левая колонка — «КНОПКИ МАТЧА». */
internal fun matchRows(): List<HotkeyRow> = listOf(
    HotkeyRow(HotkeyId.ADD_POINT_1, KeyGlyph.Digit1, 37f, 152f, 48f, 97f, 176.2f) { it.hotkeyAddPoint1 },
    HotkeyRow(HotkeyId.ADD_POINT_2, KeyGlyph.Digit2, 37f, 211.7f, 48f, 97f, 236.4f) { it.hotkeyAddPoint2 },
    HotkeyRow(HotkeyId.UNDO, KeyGlyph.Minus, 37f, 271f, 48f, 97f, 300.5f) { it.hotkeyUndo },
    HotkeyRow(HotkeyId.CORRECTION, KeyGlyph.Correction, 37f, 330f, 69f, 117.7f, 361.1f) { it.hotkeyCorrection },
    HotkeyRow(HotkeyId.RESET, KeyGlyph.Reset, 37f, 392f, 81f, 133f, 420.8f) { it.hotkeyReset },
    HotkeyRow(HotkeyId.START_MATCH, KeyGlyph.EnterWide, 37f, 455f, 155f, 207f, 472.2f) { it.hotkeyStartMatch },
)

/** Правая колонка — «ОСНОВНЫЕ». */
internal fun mainRows(): List<HotkeyRow> = listOf(
    HotkeyRow(HotkeyId.BACK_F11, KeyGlyph.LetterF11, 431.9f, 157f, 48f, 493.1f, 186.1f) { it.hotkeyBack },
    HotkeyRow(HotkeyId.BACK_ESC, KeyGlyph.Esc, 431.9f, 217f, 48f, 493.1f, 246.1f) { it.hotkeyBack },
    HotkeyRow(HotkeyId.SETTINGS, KeyGlyph.Tilde, 431.9f, 277f, 48f, 493.1f, 304.1f) { it.hotkeySettings },
    HotkeyRow(HotkeyId.CLOSE_APP, KeyGlyph.F1, 431.9f, 337f, 48f, 492.5f, 357f) { it.hotkeyCloseApp },
    HotkeyRow(HotkeyId.MINIMIZE, KeyGlyph.F2, 431.9f, 397f, 48f, 492.5f, 417f) { it.hotkeyMinimize },
    HotkeyRow(HotkeyId.DONE, KeyGlyph.EnterSmall, 431.9f, 459f, 48f, 536.9f, 483.5f) { it.hotkeyDone },
)

// ───────────────────────── Клавиши: точная геометрия из SVG макета ─────────────────────────
//
// Клавиши перенесены один в один из присланного SVG — те же координаты, толщины и радиусы.
// Слои рисуются в том же порядке, что и в SVG:
//   1) «подошва» #D7DFE2 — прямоугольник со скруглением, свисающий ниже корпуса;
//   2) «корпус» — второй прямоугольник той же заливки, чуть меньше;
//   3) «ножки» — два отрезка из-под колпашки к нижним углам (в SVG они под маской);
//   4) «колпашка» — белая с контуром 2 (в SVG stroke-width 4 при масштабе 1:1);
//   5) символ клавиши.
//
// Ширины клавиш в SVG: обычная 48, backspace 82, Tab 69, space 156, Enter 90.
// В карточке используются 48 / 69 / 81 / 155, поэтому ширина передаётся параметром,
// а геометрия углов и толщин остаётся как в SVG.

/** Высота клавиши в дизайн-единицах (SVG: 52). */
private const val KEY_SVG_H = 52f

/** Ширина «эталонной» клавиши, в координатах которой заданы символы (SVG: 48). */
private const val KEY_BASE_W = 48f

/** Скругление клавиши и скругление «колпашки» (SVG: 9.55 и 5.14). */
private const val KEY_RADIUS = 9.55f
private const val CAP_RADIUS = 5.14f

/** «Колпашка»: отступ от краёв клавиши (SVG: x 9.07, y 2 … 32.61). */
private const val CAP_LEFT = 9.07f
private const val CAP_TOP = 2f
private const val CAP_BOTTOM = 32.61f

/** «Корпус» под колпашкой (SVG: 2 … 47.31). */
private const val BODY_LEFT = 2f
private const val BODY_TOP = 2f
private const val BODY_BOTTOM = 47.31f

/** Что нарисовано внутри «колпашки» клавиши. */
enum class KeyGlyph {
    Digit1, Digit2, Minus, Correction, Reset, EnterWide,
    LetterF11, Esc, Tilde, F1, F2, EnterSmall,
}

/** «1» — цифра из SVG. */
private const val GLYPH_1 =
    "M26.2354 10.62V25.18C26.2354 25.6827 26.0621 26.1073 25.7154 26.454C25.3514 26.818 24.9181 27 " +
        "24.4154 27C23.9128 27 23.4881 26.818 23.1414 26.454C22.7774 26.1073 22.5954 25.6827 22.5954 25.18V13.766" +
        "L21.6854 14.286C21.2521 14.5287 20.7928 14.5893 20.3074 14.468C19.8221 14.3293 19.4494 14.0433 " +
        "19.1894 13.61C18.9468 13.1767 18.8948 12.7173 19.0334 12.232C19.1548 11.7467 19.4321 11.374 " +
        "19.8654 11.114L23.5054 9.034C24.1294 8.68733 24.7361 8.68733 25.3254 9.034C25.9321 9.398 " +
        "26.2354 9.92667 26.2354 10.62Z"

/** «2» — вторая цифра, тот же шрифтовой контур, что и у «1». */
private const val GLYPH_2 =
    "M28.1 12.05C28.1 13 27.75 13.78 27.05 14.35C26.35 14.92 25.32 15.4 23.96 15.79" +
        "C22.42 16.23 21.5 16.55 21.2 16.75L27.1 16.75C27.52 16.75 27.85 16.9 28.09 17.2" +
        "C28.32 17.5 28.44 17.86 28.44 18.28C28.44 18.7 28.32 19.06 28.09 19.36" +
        "C27.85 19.66 27.52 19.81 27.1 19.81L19.7 19.81C19.28 19.81 18.95 19.66 18.71 19.36" +
        "C18.47 19.06 18.35 18.7 18.35 18.28C18.35 16.72 19.1 15.5 20.6 14.62" +
        "C21.25 14.27 22.1 13.98 23.15 13.75C24 13.55 24.55 13.28 24.8 12.95" +
        "C24.95 12.76 25.02 12.52 25.02 12.23C25.02 11.72 24.74 11.28 24.18 10.91" +
        "C23.62 10.54 22.94 10.36 22.14 10.36C21.5 10.36 20.86 10.49 20.23 10.75" +
        "C19.6 11.01 19.14 11.31 18.85 11.65C18.6 11.94 18.3 12.09 17.95 12.1" +
        "C17.6 12.11 17.29 12 17.02 11.77C16.75 11.53 16.6 11.24 16.57 10.9" +
        "C16.54 10.56 16.65 10.25 16.9 9.98C17.53 9.26 18.36 8.7 19.39 8.29" +
        "C20.42 7.88 21.45 7.67 22.48 7.67C23.15 7.67 23.84 7.76 24.55 7.94" +
        "C25.26 8.12 25.9 8.38 26.47 8.72C27.06 9.07 27.46 9.5 27.68 10.01" +
        "C27.96 10.65 28.1 11.33 28.1 12.05Z"

/** «−» (откат) — толстая черта со скруглёнными концами. */
private const val GLYPH_MINUS =
    "M17.35 16.35H30.65C31.3 16.35 31.84 16.58 32.24 17.03C32.65 17.48 32.85 18.01 32.85 18.6" +
        "C32.85 19.19 32.65 19.72 32.24 20.17C31.84 20.62 31.3 20.85 30.65 20.85H17.35" +
        "C16.7 20.85 16.16 20.62 15.76 20.17C15.35 19.72 15.15 19.19 15.15 18.6" +
        "C15.15 18.01 15.35 17.48 15.76 17.03C16.16 16.58 16.7 16.35 17.35 16.35Z"

/** «Корректировка» →| на клавише шириной 69 (SVG таба). */
private const val GLYPH_CORRECTION =
    "M23.84 17.85H40.34M35.14 12.25L40.74 17.85L35.14 23.45M44.94 10.45V25.25"

/** «Сброс» — backspace с крестиком, клавиша шириной 81 (SVG backspace). */
private const val GLYPH_RESET =
    "M29.54 17.85L36.04 9.85H51.54V25.85H36.04L29.54 17.85Z" +
        "M41.04 14.75L44.14 17.85M44.14 17.85L47.24 20.95M44.14 17.85L47.24 14.75M44.14 17.85L41.04 20.95"

/** ↵ на широкой клавише «старт/продолжить/завершить» (ширина 155). */
private const val GLYPH_ENTER_WIDE =
    "M88.3 9.85V20.35H70.3M75.7 25.55L70.3 20.35L75.7 15.15"

/** ↵ на обычной клавише (ширина 48, SVG enter). */
private const val GLYPH_ENTER_SMALL =
    "M28.6 12.6V22.4H19.4M23 17.4L19.4 22.4L23 27.4"

/** Готовый символ: заливка, обводка или текст. */
private class KeyArt(
    val fill: String? = null,
    val stroke: String? = null,
    val strokeWidth: Float = 3.6f,
    val text: String? = null,
    val textSize: Float = 0f,
)

/** Символ клавиши. */
private fun keyArt(glyph: KeyGlyph): KeyArt = when (glyph) {
    KeyGlyph.Digit1 -> KeyArt(fill = GLYPH_1)
    KeyGlyph.Digit2 -> KeyArt(fill = GLYPH_2)
    KeyGlyph.Minus -> KeyArt(fill = GLYPH_MINUS)
    KeyGlyph.Correction -> KeyArt(stroke = GLYPH_CORRECTION)
    KeyGlyph.Reset -> KeyArt(stroke = GLYPH_RESET, strokeWidth = 2.9f)
    KeyGlyph.EnterWide -> KeyArt(stroke = GLYPH_ENTER_WIDE)
    KeyGlyph.EnterSmall -> KeyArt(stroke = GLYPH_ENTER_SMALL)
    KeyGlyph.LetterF11 -> KeyArt(text = "F11", textSize = 12.6f)
    KeyGlyph.Esc -> KeyArt(text = "ESC", textSize = 12f)
    KeyGlyph.Tilde -> KeyArt(text = "~", textSize = 22f)
    KeyGlyph.F1 -> KeyArt(text = "F1", textSize = 12.6f)
    KeyGlyph.F2 -> KeyArt(text = "F2", textSize = 12.6f)
}

/**
 * Клавиша из макета.
 *
 * [keyW] — ширина в дизайн-единицах (48, 69, 81, 155), [pulse] — 1 сразу после нажатия,
 * затем плавно гаснет: клавиша «проседает» и подсвечивается акцентом.
 */
internal fun DrawScope.drawKeycap(
    glyph: KeyGlyph,
    keyW: Float,
    pulse: Float,
    dark: Boolean,
    measurer: TextMeasurer,
    keyStyle: TextStyle,
    scale: Float,
) {
    // Палитра тёмной темы: подошва темнее, контур и символ — светлые.
    val ink = if (dark) Color(0xFFE9F1F8) else Color(0xFF222222)
    val face = if (dark) Color(0xFF1B2A40) else Color.White
    val base = if (dark) Color(0xFF0E1A2B) else Color(0xFFD7DFE2)
    val flash = if (dark) Color(0xFFDFAF24) else Color(0xFF2070F5)

    withTransform({ scale(scale, scale, Offset.Zero) }) {
        val p = safeT(pulse)
        val dy = 0.9f * p
        val w = keyW
        val art = keyArt(glyph)

        // вспышка нажатия — мягкая акцентная подложка под клавишей
        if (p > 0.01f) {
            drawRoundRect(
                color = flash.copy(alpha = 0.30f * p),
                topLeft = Offset(0.3f, 0.3f),
                size = Size(w + 0.6f, 51.4f),
                cornerRadius = CornerRadius(10.5f),
            )
        }

        // 1) «подошва»: свисает ниже корпуса
        drawRoundRect(
            color = base,
            topLeft = Offset(0.31f, 4.55f + dy),
            size = Size(w - 0.62f, 47.14f),
            cornerRadius = CornerRadius(KEY_RADIUS + 1.5f),
        )
        // 2) «корпус» — второй слой той же заливки
        drawRoundRect(
            color = base,
            topLeft = Offset(BODY_LEFT, BODY_TOP + dy),
            size = Size(w - 2f * BODY_LEFT, BODY_BOTTOM - BODY_TOP),
            cornerRadius = CornerRadius(KEY_RADIUS, KEY_RADIUS),
        )

        // 3) «ножки» из-под колпашки к нижним углам корпуса (SVG: stroke 4 под маской)
        val legTop = CAP_BOTTOM - 0.6f + dy
        drawLine(ink, Offset(CAP_LEFT + 1.13f, legTop), Offset(3.85f, 42.87f + dy), 2f, StrokeCap.Butt)
        drawLine(
            ink,
            Offset(w - CAP_LEFT - 1.13f, legTop),
            Offset(w - 3.85f, 42.87f + dy),
            2f,
            StrokeCap.Butt,
        )

        // 4) «колпашка» — белая, с контуром 2 (в SVG stroke-width 4)
        drawRoundRect(
            color = face,
            topLeft = Offset(CAP_LEFT, CAP_TOP + dy),
            size = Size(w - 2f * CAP_LEFT, CAP_BOTTOM - CAP_TOP),
            cornerRadius = CornerRadius(CAP_RADIUS, CAP_RADIUS),
        )
        drawRoundRect(
            color = ink,
            topLeft = Offset(CAP_LEFT, CAP_TOP + dy),
            size = Size(w - 2f * CAP_LEFT, CAP_BOTTOM - CAP_TOP),
            cornerRadius = CornerRadius(CAP_RADIUS, CAP_RADIUS),
            style = Stroke(width = 2f, join = StrokeJoin.Round),
        )
        // контур клавиши (в SVG stroke-width 4 → 2)
        drawRoundRect(
            color = ink,
            topLeft = Offset(1f, 1f + dy),
            size = Size(w - 2f, 45.31f),
            cornerRadius = CornerRadius(KEY_RADIUS, KEY_RADIUS),
            style = Stroke(width = 2f, join = StrokeJoin.Round),
        )

        // 5) символ; у широких клавиш он центрируется по «колпашке»
        val artShift = when (glyph) {
            KeyGlyph.Correction, KeyGlyph.Reset, KeyGlyph.EnterWide -> 0f
            else -> (w - KEY_BASE_W) / 2f
        }
        withTransform({ translate(artShift, 0f) }) {
            when {
                art.fill != null -> drawPath(PathParser().parsePathString(art.fill).toPath(), ink)
                art.stroke != null -> drawPath(
                    PathParser().parsePathString(art.stroke).toPath(),
                    ink,
                    style = Stroke(width = art.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                art.text != null -> {
                    val style = keyStyle.copy(fontSize = art.textSize.sp, color = ink)
                    val layout = measurer.measure(AnnotatedString(art.text), style)
                    drawText(
                        textLayoutResult = layout,
                        color = ink,
                        topLeft = Offset(
                            x = KEY_BASE_W / 2f - layout.size.width / 2f,
                            y = CAP_TOP + (CAP_BOTTOM - CAP_TOP) / 2f - layout.size.height / 2f,
                        ),
                    )
                }
            }
        }
    }
}

/** Compose-обёртка над [drawKeycap] для отдельной клавиши (полоса «ХОТКЕИ», превью). */
@Composable
fun HotkeyKeycap(
    glyph: KeyGlyph,
    modifier: Modifier = Modifier,
    width: Dp = 48.dp,
    pulse: Float = 0f,
    dark: Boolean = false,
) {
    val type = ScoreKeeperTheme.typography
    val measurer = rememberTextMeasurer()
    val w = width.value
    Canvas(modifier.size(width, KEY_SVG_H.dp)) {
        val scale = size.width / w
        drawKeycap(glyph, w, pulse, dark, measurer, type.hotkeyKey, scale)
    }
}

/** Кнопка «назад» в карточке: белый круг с рамкой #E3F0F9 и стрелкой #06326D. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CardBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ScoreKeeperTheme.colors
    val dark = colors.dark
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val hover = animateFloatAsState(
        if (hovered) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 350f), label = "cardBackHover",
    )
    val press = animateFloatAsState(
        if (pressed) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "cardBackPress",
    )

    Canvas(
        modifier
            .size(HotkeyPanel.BACK_SIZE.dp)
            .graphicsLayer {
                val s = 1f + 0.05f * safeT(hover.value) - 0.08f * safeT(press.value)
                scaleX = s
                scaleY = s
            }
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        val u = size.width / HotkeyPanel.BACK_SIZE
        withTransform({ scale(u, u, Offset.Zero) }) {
            val c = Offset(30.5f, 30.5f)
            drawCircle(if (dark) colors.cardSurface else Color.White, radius = 30.5f, center = c)
            drawCircle(
                color = if (dark) colors.accent else Color(0xFFE3F0F9),
                radius = 29f,
                center = c,
                style = Stroke(width = 3f),
            )
            // стрелка 29.8×23.3 по центру круга
            val fg = if (dark) colors.accent else Color(0xFF06326D)
            val sw = 3.4f
            val cx = c.x
            val cy = c.y
            drawLine(fg, Offset(cx - 14.2f, cy), Offset(cx + 14.2f, cy), sw, StrokeCap.Round)
            drawLine(fg, Offset(cx - 14.2f, cy), Offset(cx - 4.2f, cy - 10.2f), sw, StrokeCap.Round)
            drawLine(fg, Offset(cx - 14.2f, cy), Offset(cx - 4.2f, cy + 10.2f), sw, StrokeCap.Round)
        }
    }
}

// ───────────────────────── Строка подсказки ─────────────────────────

/**
 * Плавно «вспыхивающая» единица: возвращает 1 сразу после смены [tick] и гаснет за [durationMs].
 * Используется, чтобы подсветить нажатую клавишу в окне подсказок.
 */
@Composable
fun rememberHotkeyFlash(tick: Long, durationMs: Int = 420): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(tick) {
        if (tick == 0L) return@LaunchedEffect
        anim.snapTo(1f)
        anim.animateTo(0f, tween(durationMs))
    }
    return anim.value
}
