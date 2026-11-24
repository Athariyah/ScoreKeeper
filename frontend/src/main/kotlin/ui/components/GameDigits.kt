package ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import ui.theme.gameNumbers

/** Высота цифр в обоих шрифтах (Blue Curve Bold и Aqum Two) — 0,70 em, база строки у обоих на нуле. */
private const val DIGIT_HEIGHT_EM = 0.70f

/**
 * Цифры очков/геймов по правилу игр («0» — Blue Curve Bold, 1–9 — Aqum Two), которые не «прыгают»:
 *  • каждая цифра стоит в слоте одинаковой ширины (как в табличных цифрах), поэтому «04» и «14» занимают одно место;
 *  • база (линия цифр) и высота строки фиксированы и не зависят от того, какие шрифты в строке:
 *    у Blue Curve и Aqum разные ascent/descent, и обычный Text смещал строку по вертикали при смене цифры.
 * Буквы и знаки («AD», «:») рисуются шрифтом стиля на той же базе.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun GameDigitsText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    caretAtEnd: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fontSizePx = with(density) { style.fontSize.toPx() }
    val colored = style.copy(color = color)

    fun layoutOf(ch: Char): TextLayoutResult =
        measurer.measure(gameNumbers(ch.toString()), colored, softWrap = false, maxLines = 1)

    // Ширина слота = самая широкая цифра.
    val slot = remember(style, fontSizePx) {
        ('0'..'9').maxOf { layoutOf(it).size.width }
    }
    val layouts = remember(text, style, fontSizePx) { text.map { layoutOf(it) } }
    val widths = text.mapIndexed { i, ch -> if (ch in '0'..'9') slot else layouts[i].size.width }
    val totalPx = widths.sum()
    val heightPx = fontSizePx * 1.3f
    val baselineY = heightPx / 2f + fontSizePx * DIGIT_HEIGHT_EM / 2f

    Canvas(modifier.size(with(density) { totalPx.toDp() }, with(density) { heightPx.toDp() })) {
        var x = 0f
        layouts.forEachIndexed { i, layout ->
            val dx = (widths[i] - layout.size.width) / 2f
            drawText(layout, topLeft = Offset(x + dx, baselineY - layout.firstBaseline))
            x += widths[i]
        }
        if (caretAtEnd) {
            drawLine(
                color, Offset(totalPx + 2f, baselineY - fontSizePx * DIGIT_HEIGHT_EM - 2f),
                Offset(totalPx + 2f, baselineY + 2f), strokeWidth = 2f,
            )
        }
    }
}
