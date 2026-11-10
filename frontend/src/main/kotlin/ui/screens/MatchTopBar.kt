package ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import ui.components.HotkeyId
import ui.components.rememberHotkeyFlash
import ui.components.safeT
import ui.theme.ScoreKeeperTheme

// ───────────────────────── Верхняя полоса экрана матча ─────────────────────────
//
// Полоса 616×53 из макета «Group 7.pdf» — координаты один в один:
//   «ХОТКЕИ» 0…133 (r 10), разделители 141 и 311 и 413, таймер 166…279,
//   шестерёнка в круге 337…389, пилюля «← НАЗАД» 439…616 (r 26.5).
// В светлой теме — голубые плашки #E6F3FD и синий текст #2070F5,
// в тёмной (скрин матча) — тёмные плашки #141F31 и золотой #DFAF24.

/** Ширина полосы по макету. */
internal const val TOP_BAR_WIDTH = 616f
internal const val TOP_BAR_HEIGHT = 53f

// ── пути из макета ──
private const val CHIP_PILL_PATH =
    "M10 5 L123 5 C128.52 5 133 9.48 133 15 L133 37 C133 42.52 128.52 47 123 47 L10 47 C4.48 47 0 42.52 0 37 " +
        "L0 15 C0 9.48 4.48 5 10 5"

private const val COG_CIRCLE_PATH =
    "M389 26.5 C389 40.58 377.36 52 363 52 C348.64 52 337 40.58 337 26.5 C337 12.42 348.64 1 363 1 " +
        "C377.36 1 389 12.42 389 26.5"

private const val COG_PATH =
    "M374.9 28.57 C374.97 28.07 375 27.55 375 27 C375 26.47 374.97 25.93 374.88 25.43 L378.27 22.8 " +
        "C378.41 22.68 378.51 22.51 378.55 22.33 C378.58 22.14 378.56 21.95 378.47 21.78 L375.27 16.25 " +
        "C375.17 16.08 375.02 15.95 374.84 15.89 C374.66 15.82 374.46 15.82 374.28 15.88 L370.3 17.48 " +
        "C369.47 16.85 368.58 16.32 367.6 15.92 L367 11.68 C366.97 11.49 366.87 11.32 366.73 11.19 " +
        "C366.58 11.07 366.39 11 366.2 11 L359.8 11 C359.4 11 359.08 11.28 359.02 11.68 L358.42 15.92 " +
        "C357.43 16.32 356.53 16.87 355.72 17.48 L351.73 15.88 C351.37 15.75 350.95 15.88 350.75 16.25 " +
        "L347.57 21.78 C347.37 22.13 347.43 22.57 347.77 22.8 L351.15 25.43 C351.07 25.93 351 26.48 351 27 " +
        "C351 27.52 351.03 28.07 351.12 28.57 L347.73 31.2 C347.59 31.32 347.49 31.49 347.45 31.67 " +
        "C347.41 31.86 347.44 32.05 347.53 32.22 L350.73 37.75 C350.93 38.12 351.35 38.23 351.72 38.12 " +
        "L355.7 36.52 C356.53 37.15 357.42 37.68 358.4 38.08 L359 42.32 C359.08 42.72 359.4 43 359.8 43 " +
        "L366.2 43 C366.6 43 366.93 42.72 366.98 42.32 L367.58 38.08 C368.57 37.68 369.47 37.15 370.28 36.52 " +
        "L374.27 38.12 C374.63 38.25 375.05 38.12 375.25 37.75 L378.45 32.22 C378.65 31.85 378.57 31.43 " +
        "378.25 31.2 L374.9 28.57 " +
        "M363 33 C359.7 33 357 30.3 357 27 C357 23.7 359.7 21 363 21 C366.3 21 369 23.7 369 27 " +
        "C369 30.3 366.3 33 363 33"

/** Таймер (стопwatch): кнопка, дуга, стрелка. */
private const val TIMER_PATH =
    "M186 8.67 L176 8.67 L176 12 L186 12 L186 8.67 " +
        "M179.33 30.33 L182.67 30.33 L182.67 20.33 L179.33 20.33 L179.33 30.33 " +
        "M192.72 19.32 L195.08 16.95 C194.37 16.1 193.58 15.3 192.73 14.6 L190.37 16.97 " +
        "C187.71 14.83 184.41 13.67 181 13.67 C172.72 13.67 166 20.38 166 28.67 " +
        "C166 36.95 172.7 43.67 181 43.67 C183.82 43.67 186.59 42.87 188.98 41.37 " +
        "C191.37 39.87 193.29 37.72 194.52 35.18 C195.74 32.63 196.22 29.79 195.9 26.99 " +
        "C195.58 24.18 194.48 21.52 192.72 19.32 " +
        "M181 40.33 C174.55 40.33 169.33 35.12 169.33 28.67 C169.33 22.22 174.55 17 181 17 " +
        "C187.45 17 192.67 22.22 192.67 28.67 C192.67 35.12 187.45 40.33 181 40.33"

private const val BACK_PILL_PATH =
    "M465.5 0 L589.5 0 C604.14 0 616 11.86 616 26.5 C616 41.14 604.14 53 589.5 53 L465.5 53 " +
        "C450.86 53 439 41.14 439 26.5 C439 11.86 450.86 0 465.5 0"

private const val BACK_ARROW_PATH =
    "M498.5 27 L476.5 27 M484.75 19 L476.5 27 L484.75 35"

/** Полоса целиком: рисуется и как отдельный элемент, и как оверлей поверх матча. */
@Composable
internal fun MatchTopBar(
    seconds: Int,
    backLabel: String,
    dark: Boolean,
    pulseTick: Long = 0L,
    pulseId: HotkeyId? = null,
    onHotkeys: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    val measurer = rememberTextMeasurer()
    val strings = ScoreKeeperTheme.strings

    val chipBg = if (dark) Color(0xFF141F31) else Color(0xFFE6F3FD)
    val chipInk = if (dark) Color(0xFFDFAF24) else Color(0xFF475D74)
    val timerInk = if (dark) Color(0xFFDFAF24) else Color(0xFF465B72)
    val cogInk = if (dark) Color(0xFFDFAF24) else Color(0xFF06326D)
    val pillBg = if (dark) Color(0xFF141F31) else Color(0xFFFBFCFD)
    val pillStroke = if (dark) Color(0xFFDFAF24) else Color(0xFFE3F0F9)
    val pillArrow = if (dark) Color(0xFFDFAF24) else Color(0xFF06326D)
    val pillText = if (dark) Color(0xFFDFAF24) else Color(0xFF21386A)
    val dividerColor = if (dark) Color(0xFF212E44) else Color(0xFFE2EEF7)
    val timerSpan = remember { PathParser().parsePathString(TIMER_PATH).toPath() }
    val cogSpan = remember { PathParser().parsePathString(COG_PATH).toPath() }
    val cogCircle = remember { PathParser().parsePathString(COG_CIRCLE_PATH).toPath() }
    val backPill = remember { PathParser().parsePathString(BACK_PILL_PATH).toPath() }
    val backArrow = remember { PathParser().parsePathString(BACK_ARROW_PATH).toPath() }
    val chipPill = remember { PathParser().parsePathString(CHIP_PILL_PATH).toPath() }
    // вспышки пилюли «ХОТКЕИ» и шестерёнки при нажатии H/Esc/~/F1/F2
    val chipFlash = rememberHotkeyFlash(
        if (pulseId == HotkeyId.BACK_H || pulseId == HotkeyId.BACK_ESC) pulseTick else 0L,
    )
    val cogFlash = rememberHotkeyFlash(
        if (pulseId == HotkeyId.SETTINGS) pulseTick else 0L,
    )

    Box(modifier.size(TOP_BAR_WIDTH.dp, TOP_BAR_HEIGHT.dp)) {
        // Фон, разделители, таймер, шестерёнка, пилюля и стрелка — точно по макету.
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / TOP_BAR_WIDTH
            withTransform({ scale(u, u, Offset.Zero) }) {
                // разделители 2×52
                listOf(141f, 311f, 413f).forEach { x ->
                    drawRoundRect(dividerColor, Offset(x, 0f), Size(2f, 52f), CornerRadius(1f))
                }
                // «ХОТКЕИ»: пилюля + мини-клавиша (текст — живой, ниже)
                if (chipFlash > 0.01f) {
                    drawPath(chipPill, chipInk.copy(alpha = 0.18f * safeT(chipFlash)))
                }
                drawPath(chipPill, chipBg)
                drawMiniKeycap(chipInk, Offset(96f, 12f))
                // таймер
                drawPath(timerSpan, timerInk)
                // шестерёнка
                drawPath(cogCircle, chipBg)
                if (cogFlash > 0.01f) {
                    drawPath(cogCircle, cogInk.copy(alpha = 0.22f * safeT(cogFlash)))
                }
                drawPath(cogSpan, cogInk)
                // пилюля «назад»
                drawPath(backPill, pillBg)
                drawPath(backPill, pillStroke, style = Stroke(width = 2f))
                drawPath(backArrow, pillArrow, style = Stroke(width = 3f, cap = StrokeCap.Round))
            }
            // тексты — живые, чтобы шрифт и язык работали как в остальном приложении
            // «ХОТКЕИ» — левый край 8.75, базовая линия 34.03
            val chipLayout = measurer.measure(AnnotatedString(strings.hotkeys), type.hotkeysChip)
            drawText(
                chipLayout,
                color = chipInk,
                topLeft = Offset(8.75f * u, baselineTop(chipLayout, 34.03f * u)),
            )
            // «00:00» — 210.86…279.23, базовая линия 34.88
            val timerLayout = measurer.measure(AnnotatedString(formatClock(seconds)), type.timerText)
            drawText(
                timerLayout,
                color = timerInk,
                topLeft = Offset(210.86f * u, baselineTop(timerLayout, 34.88f * u)),
            )
            // «НАЗАД» — 524.94…579.91, базовая линия 31.5
            val backLayout = measurer.measure(AnnotatedString(backLabel), type.pillLabel)
            drawText(
                backLayout,
                color = pillText,
                topLeft = Offset(525f * u, baselineTop(backLayout, 31.5f * u)),
            )
        }

        // ── зоны нажатия ──
        ClickArea(x = 0f, y = 5f, w = 133f, h = 42f, onClick = onHotkeys)
        ClickArea(x = 337f, y = 1f, w = 52f, h = 51f, onClick = onSettings)
        ClickArea(x = 439f, y = 0f, w = 177f, h = 53f, onClick = onBack)
    }
}

/** Верх текстового блока по базовой линии: layout.firstBaseline уже учитывает метрики шрифта. */
private fun baselineTop(layout: TextLayoutResult, baseline: Float): Float =
    baseline - layout.firstBaseline

/** Мини-клавиша «H» в кнопке хоткеев (координаты от левого верхнего угла полосы). */
private fun DrawScope.drawMiniKeycap(ink: Color, at: Offset) {
    withTransform({ translate(at.x, at.y) }) {
        // подошва 95.95…122.17, 13.47…41
        drawRoundRect(
            color = ink.copy(alpha = 0.30f),
            topLeft = Offset(0.6f, 14.2f),
            size = Size(25.7f, 13.1f),
            cornerRadius = CornerRadius(5.4f),
        )
        drawRoundRect(
            color = ink,
            topLeft = Offset(0.6f, 0.6f),
            size = Size(25.7f, 26.4f),
            cornerRadius = CornerRadius(5.6f),
            style = Stroke(width = 2.2f, join = StrokeJoin.Round),
        )
        // «колпашка» внутри корпуса — как у объёмной клавиши в карточке
        drawRoundRect(
            color = ink,
            topLeft = Offset(5.6f, 5.2f),
            size = Size(15.7f, 12.4f),
            cornerRadius = CornerRadius(2.6f),
            style = Stroke(width = 1.7f, join = StrokeJoin.Round),
        )
        // «ножки» к подошве
        val legY = 17.6f
        drawLine(ink, Offset(6f, legY), Offset(3.5f, 24.6f), 1.8f, StrokeCap.Round)
        drawLine(ink, Offset(20.9f, legY), Offset(23.4f, 24.6f), 1.8f, StrokeCap.Round)
        // буква H
        val cy = 11.4f
        val sw = 2.1f
        drawLine(ink, Offset(9.3f, cy - 3.4f), Offset(9.3f, cy + 3.4f), sw, StrokeCap.Round)
        drawLine(ink, Offset(17.6f, cy - 3.4f), Offset(17.6f, cy + 3.4f), sw, StrokeCap.Round)
        drawLine(ink, Offset(9.3f, cy), Offset(17.6f, cy), sw, StrokeCap.Round)
    }
}

private fun formatClock(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

/** Прозрачная зона нажатия внутри полосы (координаты макета 616×53). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ClickArea(x: Float, y: Float, w: Float, h: Float, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .graphicsLayer {
                translationX = x.dp.toPx()
                translationY = y.dp.toPx()
                val s = 1f + 0.03f * safeT(if (hovered) 1f else 0f)
                scaleX = s
                scaleY = s
            }
            .size(w.dp, h.dp)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    )
}
