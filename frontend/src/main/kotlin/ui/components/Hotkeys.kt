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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
//   общие:  H и Esc — назад, ~ — настройки, F1 — закрыть, F2 — свернуть, Enter — готово.
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
    BACK_H,
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

/**
 * Пружина может «перелетать» границы 0…1, а `lerp(Color, Color, t)` в Compose смешивает
 * цвета в пространстве Oklab: при t вне диапазона получается цвет вне Oklab/sRGB, и Skia
 * выбрасывает IllegalArgumentException. Поэтому параметр анимации всегда зажимаем.
 */
internal fun safeT(t: Float): Float = if (t.isNaN()) 0f else t.coerceIn(0f, 1f)

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

/** Смешивание цветов, устойчивое к «перелёту» пружины (см. [safeT]). */
internal fun lerpSafe(a: Color, b: Color, t: Float): Color = lerp(a, b, safeT(t))

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

    /** Клавиша: корпус 46.2×49.31, «подошва» до 51.7. */
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
    HotkeyRow(HotkeyId.ADD_POINT_1, KeyGlyph.Digit1, 37f, 152f, 47.04f, 97f, 176.2f) { it.hotkeyAddPoint1 },
    HotkeyRow(HotkeyId.ADD_POINT_2, KeyGlyph.Digit2, 37f, 211.7f, 47.04f, 97f, 236.4f) { it.hotkeyAddPoint2 },
    HotkeyRow(HotkeyId.UNDO, KeyGlyph.Minus, 37f, 271f, 47.04f, 97f, 300.5f) { it.hotkeyUndo },
    HotkeyRow(HotkeyId.CORRECTION, KeyGlyph.Correction, 37f, 330f, 69f, 117.7f, 361.1f) { it.hotkeyCorrection },
    HotkeyRow(HotkeyId.RESET, KeyGlyph.Reset, 37f, 392f, 81f, 133f, 420.8f) { it.hotkeyReset },
    HotkeyRow(HotkeyId.START_MATCH, KeyGlyph.EnterWide, 37f, 455f, 155f, 207f, 472.2f) { it.hotkeyStartMatch },
)

/** Правая колонка — «ОСНОВНЫЕ». */
internal fun mainRows(): List<HotkeyRow> = listOf(
    HotkeyRow(HotkeyId.BACK_H, KeyGlyph.LetterH, 431.9f, 157f, 47.04f, 493.1f, 186.1f) { it.hotkeyBack },
    HotkeyRow(HotkeyId.BACK_ESC, KeyGlyph.Esc, 431.9f, 217f, 47.04f, 493.1f, 246.1f) { it.hotkeyBack },
    HotkeyRow(HotkeyId.SETTINGS, KeyGlyph.Tilde, 431.9f, 277f, 47.04f, 493.1f, 304.1f) { it.hotkeySettings },
    HotkeyRow(HotkeyId.CLOSE_APP, KeyGlyph.F1, 431.9f, 337f, 47.04f, 492.5f, 357f) { it.hotkeyCloseApp },
    HotkeyRow(HotkeyId.MINIMIZE, KeyGlyph.F2, 431.9f, 397f, 47.04f, 492.5f, 417f) { it.hotkeyMinimize },
    HotkeyRow(HotkeyId.DONE, KeyGlyph.EnterSmall, 431.9f, 459f, 47.04f, 536.9f, 483.5f) { it.hotkeyDone },
)

// ───────────────────────── Иконки внутри клавиш ─────────────────────────
// Координаты — от левого верхнего угла «подошвы» клавиши (в макете это x, y строки).

/** «Корректировка» →| (obj 65; клавиша 37…105.17, 330…379.31). */
private const val CORRECTION_ICON_PATH =
"M23.53 16.05 C22.53 16.05 21.73 16.86 21.73 17.85 C21.73 18.84 22.53 19.65 23.53 19.65 L23.53 17.85 L23.53 16.05 M40.03 19.65 C41.02 19.65 41.83 18.84 " +
    " 41.83 17.85 C41.83 16.86 41.02 16.05 40.03 16.05 L40.03 17.85 L40.03 19.65 M36.10 10.98 C35.40 10.27 34.26 10.27 33.55 10.98 C32.85 11.68 32.85 12.82 " +
    " 33.55 13.52 L34.83 12.25 L36.10 10.98 M40.43 17.85 L41.70 19.12 C42.04 18.79 42.23 18.33 42.23 17.85 C42.23 17.37 42.04 16.91 41.70 16.58 L40.43 17.8 " +
    "5 M33.55 22.18 C32.85 22.88 32.85 24.02 33.55 24.72 C34.26 25.43 35.40 25.43 36.10 24.72 L34.83 23.45 L33.55 22.18 M46.43 10.45 C46.43 9.46 45.62 8.65 " +
    " 44.63 8.65 C43.63 8.65 42.83 9.46 42.83 10.45 L44.63 10.45 L46.43 10.45 M42.83 25.25 C42.83 26.24 43.63 27.05 44.63 27.05 C45.62 27.05 46.43 26.24 46 " +
    ".43 25.25 L44.63 25.25 L42.83 25.25 M23.53 17.85 L23.53 19.65 L40.03 19.65 L40.03 17.85 L40.03 16.05 L23.53 16.05 L23.53 17.85 M34.83 12.25 L33.55 13. " +
    "52 L39.15 19.12 L40.43 17.85 L41.70 16.58 L36.10 10.98 L34.83 12.25 M40.43 17.85 L39.15 16.58 L33.55 22.18 L34.83 23.45 L36.10 24.72 L41.70 19.12 L40. " +
    "43 17.85 M44.63 10.45 L42.83 10.45 L42.83 25.25 L44.63 25.25 L46.43 25.25 L46.43 10.45 L44.63 10.45 "

/** «Сброс» — стрелка в рамке (obj 56; клавиша 37.3…117.47, 396.5…443.64). */
private const val RESET_ICON_PATH =
"M29.54 17.85 L28.41 16.94 C27.98 17.47 27.98 18.23 28.41 18.76 L29.54 17.85 M36.04 9.85 L36.04 8.40 C35.60 8.40 35.19 8.60 34.91 8.94 L36.04 9.85 M51. " +
    "54 9.85 L52.99 9.85 C52.99 9.05 52.34 8.40 51.54 8.40 L51.54 9.85 M51.54 25.85 L51.54 27.30 C52.34 27.30 52.99 26.65 52.99 25.85 L51.54 25.85 M36.04 2 " +
    "5.85 L34.91 26.76 C35.19 27.10 35.60 27.30 36.04 27.30 L36.04 25.85 M29.54 17.85 L30.67 18.76 L37.17 10.76 L36.04 9.85 L34.91 8.94 L28.41 16.94 L29.54 " +
    " 17.85 M36.04 9.85 L36.04 11.30 L51.54 11.30 L51.54 9.85 L51.54 8.40 L36.04 8.40 L36.04 9.85 M51.54 9.85 L50.09 9.85 L50.09 25.85 L51.54 25.85 L52.99  " +
    "25.85 L52.99 9.85 L51.54 9.85 M51.54 25.85 L51.54 24.40 L36.04 24.40 L36.04 25.85 L36.04 27.30 L51.54 27.30 L51.54 25.85 M36.04 25.85 L37.17 24.94 L30 " +
    ".67 16.94 L29.54 17.85 L28.41 18.76 L34.91 26.76 L36.04 25.85 M42.07 13.72 C41.50 13.16 40.58 13.16 40.01 13.72 C39.45 14.29 39.45 15.21 40.01 15.78 L " +
    "41.04 14.75 L42.07 13.72 M46.21 21.98 C46.78 22.54 47.70 22.54 48.27 21.98 C48.83 21.41 48.83 20.49 48.27 19.92 L47.24 20.95 L46.21 21.98 M48.27 15.78 " +
    " C48.83 15.21 48.83 14.29 48.27 13.72 C47.70 13.16 46.78 13.16 46.21 13.72 L47.24 14.75 L48.27 15.78 M40.01 19.92 C39.45 20.49 39.45 21.41 40.01 21.98 " +
    " C40.58 22.54 41.50 22.54 42.07 21.98 L41.04 20.95 L40.01 19.92 M41.04 14.75 L40.01 15.78 L46.21 21.98 L47.24 20.95 L48.27 19.92 L42.07 13.72 L41.04 1 " +
    "4.75 M47.24 14.75 L46.21 13.72 L40.01 19.92 L41.04 20.95 L42.07 21.98 L48.27 15.78 L47.24 14.75 "

/** Enter ↵ на широкой клавише (obj 46; клавиша 37.3…191.47). */
private const val ENTER_WIDE_PATH =
"M64.34 16.05 C64.34 15.06 63.53 14.25 62.54 14.25 C61.55 14.25 60.74 15.06 60.74 16.05 L62.54 16.05 L64.34 16.05 M62.54 23.55 L60.74 23.55 C60.74 24.5 " +
    "4 61.55 25.35 62.54 25.35 L62.54 23.55 M92.54 23.55 L92.54 25.35 C93.53 25.35 94.34 24.54 94.34 23.55 L92.54 23.55 M94.34 16.05 C94.34 15.06 93.53 14. " +
    "25 92.54 14.25 C91.55 14.25 90.74 15.06 90.74 16.05 L92.54 16.05 L94.34 16.05 M62.54 16.05 L60.74 16.05 L60.74 23.55 L62.54 23.55 L64.34 23.55 L64.34  " +
    "16.05 L62.54 16.05 M62.54 23.55 L62.54 25.35 L92.54 25.35 L92.54 23.55 L92.54 21.75 L62.54 21.75 L62.54 23.55 M92.54 23.55 L94.34 23.55 L94.34 16.05 L " +
    "92.54 16.05 L90.74 16.05 L90.74 23.55 L92.54 23.55 "

/** Enter ↵ на обычной клавише — stroke-версия «↵» 14×6.5. */
private const val ENTER_SMALL_PATH =
    "M16.6 16 L16.6 22.4 L30.6 22.4 L30.6 16"

// ───────────────────────── Клавиша в окне подсказок ─────────────────────────

/**
 * «Клавиша» из макета: корпус с контуром 2 dp, серо-голубая подошва #D7DFE2 и белая колпашка,
 * скошенные «ножки» к подошве. При нажатии горячей клавиши [pulse] 1 → 0: клавиша проседает
 * и вспыхивает.
 *
 * Рисуется в «дизайн-единицах» от левого верхнего угла клавиши; [keyW] меняет только
 * горизонтальные размеры — углы и толщины остаются как в макете.
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
    val ink = if (dark) Color(0xFFE9F1F8) else Color(0xFF222222)
    val face = if (dark) Color(0xFF1B2A40) else Color.White
    val base = if (dark) Color(0xFF0F1B2D) else Color(0xFFD7DFE2)
    val flash = if (dark) Color(0xFFDFAF24) else Color(0xFF2070F5)
    val icon = glyphIcon(glyph)

    withTransform({ scale(scale, scale, Offset.Zero) }) {
        val p = safeT(pulse)
        val dy = 0.9f * p
        val bodyH = 49.31f
        val radius = 10.2f
        val w = keyW

        if (p > 0.01f) {
            drawRoundRect(
                color = flash.copy(alpha = 0.32f * p),
                topLeft = Offset(0.4f, 0.4f),
                size = Size(w + 0.4f, HotkeyPanel.KEY_H),
                cornerRadius = CornerRadius(11f),
            )
        }

        // 1) «подошва» — шире корпуса и свисает вниз
        drawRoundRect(
            color = base,
            topLeft = Offset(0f, 2.4f + dy),
            size = Size(w, bodyH - 1.8f),
            cornerRadius = CornerRadius(radius + 0.6f),
        )
        // 2) корпус (средний слой)
        drawRoundRect(
            color = base,
            topLeft = Offset(1.7f, dy),
            size = Size(w - 3.4f, bodyH - 3.7f),
            cornerRadius = CornerRadius(radius),
        )
        // 3) «колпашка»
        val faceLeft = 9.1f
        val faceTop = 0.7f + dy
        val faceSize = Size(w - 18.2f, 30.6f)
        drawRoundRect(face, Offset(faceLeft, faceTop), faceSize, CornerRadius(4.9f))
        // 4) контуры
        drawRoundRect(
            color = ink,
            topLeft = Offset(0.9f, 0.9f + dy),
            size = Size(w - 1.8f, bodyH - 1.8f),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = 2f, join = StrokeJoin.Round),
        )
        drawRoundRect(
            color = ink,
            topLeft = Offset(faceLeft, faceTop),
            size = faceSize,
            cornerRadius = CornerRadius(4.9f),
            style = Stroke(width = 1.7f, join = StrokeJoin.Round),
        )
        // 5) «ножки» колпашки — диагонали к подошве
        val legY = faceTop + faceSize.height
        val legBottom = bodyH - 3.4f
        drawLine(ink, Offset(faceLeft + 0.4f, legY), Offset(9.2f, legBottom), 2.2f, StrokeCap.Round)
        drawLine(
            ink,
            Offset(faceLeft + faceSize.width - 0.4f, legY),
            Offset(w - 9.2f, legBottom),
            2.2f, StrokeCap.Round,
        )
        // 6) содержимое
        if (icon != null) {
            if (glyph == KeyGlyph.EnterSmall) {
                drawPath(icon, ink, style = Stroke(width = 2.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawPath(icon, ink)
            }
        } else {
            val text = glyphText(glyph)
            if (text.isNotEmpty()) {
                val style = keyStyle.copy(
                    fontSize = when (glyph) {
                        KeyGlyph.Esc -> 9.5f
                        KeyGlyph.F1, KeyGlyph.F2 -> 11.5f
                        else -> 22f
                    }.sp,
                    color = ink,
                )
                val layout = measurer.measure(AnnotatedString(text), style)
                drawText(
                    textLayoutResult = layout,
                    color = ink,
                    topLeft = Offset(
                        x = (w / 2f) - layout.size.width / 2f,
                        y = faceTop + (faceSize.height - layout.size.height) / 2f,
                    ),
                )
            }
        }
    }
}

/** Compose-обёртка над [drawKeycap] для отдельных клавиш (используется в полосе и превью). */
@Composable
fun HotkeyKeycap(
    glyph: KeyGlyph,
    modifier: Modifier = Modifier,
    width: Dp = 47.04.dp,
    pulse: Float = 0f,
    dark: Boolean = false,
) {
    val type = ScoreKeeperTheme.typography
    val measurer = rememberTextMeasurer()
    val w = width.value
    Canvas(modifier.size(width, HotkeyPanel.KEY_H.dp)) {
        val scale = size.width / w
        drawKeycap(glyph, w, pulse, dark, measurer, type.hotkeyKey, scale)
    }
}

/** Векторные иконки клавиш (→|, ⊠, ↵). */
private fun glyphIcon(glyph: KeyGlyph): Path? = when (glyph) {
    KeyGlyph.Correction -> PathParser().parsePathString(CORRECTION_ICON_PATH).toPath()
    KeyGlyph.Reset -> PathParser().parsePathString(RESET_ICON_PATH).toPath()
    KeyGlyph.EnterWide -> PathParser().parsePathString(ENTER_WIDE_PATH).toPath()
    KeyGlyph.EnterSmall -> PathParser().parsePathString(ENTER_SMALL_PATH).toPath()
    else -> null
}

private fun glyphText(glyph: KeyGlyph): String = when (glyph) {
    KeyGlyph.Digit1 -> "1"
    KeyGlyph.Digit2 -> "2"
    KeyGlyph.Esc -> "Esc"
    KeyGlyph.LetterH -> "H"
    KeyGlyph.Tilde -> "~"
    KeyGlyph.F1 -> "F1"
    KeyGlyph.F2 -> "F2"
    KeyGlyph.Minus -> "–"
    else -> ""
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
