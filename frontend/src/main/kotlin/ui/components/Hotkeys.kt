package ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.theme.ScoreKeeperTheme

// ───────────────────────── Хоткеи: идентификаторы и группы ─────────────────────────
//
// Горячие клавиши — ровно те, что в макете «Хоткеи.pdf»:
//   матч:   1 / 2 — очко команде 1 / 2, «−», →| (корректировка), ⌫ (сброс), Enter (старт/финиш);
//   общие:  H и Esc — назад, ~ — настройки, F1 — закрыть приложение, F2 — свернуть, Enter — готово.
// В окне подсказок они же работают как кнопки (см. HotkeysOverlay).

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

/** Колонка подсказок: 0 — «КНОПКИ МАТЧА», 1 — «ОСНОВНЫЕ». */
enum class HotkeyGroup { Match, Main }

/** Как нарисовать «клавишу» в списке. */
enum class KeyGlyph { Digit1, Digit2, Minus, Correction, Reset, EnterWide, LetterH, Esc, Tilde, F1, F2, EnterSmall }

class HotkeyRow(
    val id: HotkeyId,
    val glyph: KeyGlyph,
    /** Ширина клавиши в дизайн-пикселях (у макета 46, у широкой около 154). */
    val keyWidth: Float = 46f,
    val label: (ui.theme.AppStrings) -> String,
)

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

// ───────────────────────── Кнопка «ХОТКЕИ» в верхней панели ─────────────────────────
//
// Как на скрине: пилюля с подписью и мини-клавишей. В светлой теме — голубая плашка
// #E6F3FD с тёмно-синим текстом #475D74, в тёмной — плашка #141F31 с золотым текстом #DFAF24.
// Нажатие пружинит и «вдавливает» иконку клавиши (та же анимация, что у остальных кнопок).

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HotkeysChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 132.dp,
    height: Dp = 41.dp,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    val strings = ScoreKeeperTheme.strings
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val hover = animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 320f),
        label = "hotkeysHover",
    )
    val pressSpec: AnimationSpec<Float> =
        if (pressed) tween(70) else spring(dampingRatio = 0.45f, stiffness = 520f)
    val press = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "hotkeysPress",
    )

    val chipFill = if (colors.dark) colors.cardSurface else colors.iconButtonBg
    val chipFillHover = if (colors.dark) Color(0xFF1D2A42) else colors.iconButtonBgHover
    val content = if (colors.dark) colors.accent else Color(0xFF475D74)

    Box(
        modifier
            .size(width, height)
            .graphicsLayer {
                val s = 1f + 0.04f * hover.value - 0.07f * press.value
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(height / 2f))
            .background(lerp(chipFill, chipFillHover, hover.value))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            BasicText(
                strings.hotkeys,
                style = type.caption.copy(fontSize = 19.sp, color = content),
                maxLines = 1,
                softWrap = false,
            )
            Box(Modifier.width(7.dp))
            MiniKeycap(
                modifier = Modifier.size(20.dp),
                pressure = press.value,
                color = content,
            )
        }
    }
}

/** Мини-клавиша (как в макете): рамка с «подошвой» и буквой H. */
@Composable
private fun MiniKeycap(modifier: Modifier = Modifier, pressure: Float, color: Color) {
    Canvas(modifier) {
        val s = size.width / 20f
        withTransform({ scale(s, s, Offset.Zero) }) {
            val stroke = 2.1f
            val top = 1.5f + 0.8f * pressure
            val body = Path().apply {
                addRoundRect(RoundRect(2.2f, top, 17.8f, 14f + 0.8f * pressure, CornerRadius(4.2f)))
            }
            // «подошва» — как у объёмной клавиши на скриншоте
            drawPath(
                Path().apply {
                    moveTo(3.2f, 13.4f + 0.8f * pressure)
                    lineTo(16.8f, 13.4f + 0.8f * pressure)
                    lineTo(18f, 17.4f)
                    lineTo(2f, 17.4f)
                    close()
                },
                color.copy(alpha = 0.35f),
            )
            drawPath(body, color, style = Stroke(stroke, join = StrokeJoin.Round))
            // буква H
            val cy = top + 6.3f
            drawLine(color, Offset(6.6f, cy - 3f), Offset(6.6f, cy + 3f), stroke, StrokeCap.Round)
            drawLine(color, Offset(13.4f, cy - 3f), Offset(13.4f, cy + 3f), stroke, StrokeCap.Round)
            drawLine(color, Offset(6.6f, cy), Offset(13.4f, cy), stroke, StrokeCap.Round)
        }
    }
}

// ───────────────────────── Клавиша в окне подсказок ─────────────────────────

/**
 * «Клавиша» из макета: белая колпашка с тёмным контуром 3 px, скошенными боками и
 * серо-голубой «подошвой» #D7DFE2. При нажатии горячей клавиши оверлей передаёт
 * [pulse] 1 → 0: колпашка проседает, подошва сжимается, появляется цветная вспышка.
 */
@Composable
fun HotkeyKeycap(
    glyph: KeyGlyph,
    modifier: Modifier = Modifier,
    width: Dp = 46.dp,
    pulse: Float = 0f,
    dark: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val type = ScoreKeeperTheme.typography
    val ink = if (dark) Color(0xFFE9F1F8) else Color(0xFF222222)
    val face = if (dark) Color(0xFF1B2A40) else Color.White
    val base = if (dark) Color(0xFF0F1B2D) else Color(0xFFD7DFE2)
    val flash = if (dark) Color(0xFFDFAF24) else Color(0xFF2070F5)

    Canvas(modifier.size(width, 46.dp)) {
        val unit = size.height / 46f
        withTransform({ scale(unit, unit, Offset.Zero) }) {
            val w = size.width / unit
            drawKeycap(w, ink, face, base, flash, pulse)
            // Векторные иконки клавиш (стрелки/⌫) — в тех же «дизайн-единицах», что и корпус.
            glyphIcon(glyph, w, ink, pulse)?.invoke(this)
        }
        // Символ на клавише — живой текст: он не «плывёт» при масштабе окна.
        val text = glyphText(glyph)
        if (text.isNotEmpty()) {
            val textSize = when (glyph) {
                KeyGlyph.Esc -> 11.5f
                KeyGlyph.F1, KeyGlyph.F2 -> 13f
                else -> 25f
            }
            val layout = measurer.measure(
                text = androidx.compose.ui.text.AnnotatedString(text),
                style = type.hotkeyKey.copy(fontSize = textSize.sp, color = ink),
            )
            // Центр «колпашки» в пикселях: верх 1.5 + 16 = 17.5 дизайн-единиц от верха клавиши.
            val capCenterY = (17.9f + 0.8f * pulse) * unit
            drawText(
                textLayoutResult = layout,
                color = ink,
                topLeft = Offset(
                    x = size.width / 2f - layout.size.width / 2f,
                    y = capCenterY - layout.size.height / 2f,
                ),
            )
        }
    }
}

private fun glyphText(glyph: KeyGlyph): String = when (glyph) {
    KeyGlyph.Digit1 -> "1"
    KeyGlyph.Digit2 -> "2"
    KeyGlyph.Esc -> "Esc"
    KeyGlyph.LetterH -> "H"
    KeyGlyph.Tilde -> "~"
    KeyGlyph.F1 -> "F1"
    KeyGlyph.F2 -> "F2"
    else -> ""
}

/** Иконки-кривые внутри клавиш (корректировка →|, сброс ⌫, Enter ↵). */
private fun glyphIcon(glyph: KeyGlyph, keyWidth: Float, ink: Color, pulse: Float): (DrawScope.() -> Unit)? {
    val dy = 0.8f * pulse
    return when (glyph) {
        KeyGlyph.Correction -> {
            {
                val y = 13.73f + dy
                drawLine(ink, Offset(keyWidth / 2f - 8f, y), Offset(keyWidth / 2f + 4f, y), 2.6f, StrokeCap.Round)
                drawLine(ink, Offset(keyWidth / 2f + 4f, y), Offset(keyWidth / 2f + 0.4f, y - 3.4f), 2.6f, StrokeCap.Round)
                drawLine(ink, Offset(keyWidth / 2f + 4f, y), Offset(keyWidth / 2f + 0.4f, y + 3.4f), 2.6f, StrokeCap.Round)
                drawLine(ink, Offset(keyWidth / 2f + 9f, y - 4.4f), Offset(keyWidth / 2f + 9f, y + 4.4f), 2.6f, StrokeCap.Round)
            }
        }
        KeyGlyph.Reset -> {
            {
                val cx = keyWidth / 2f
                val y = 13.73f + dy
                val back = Path().apply {
                    moveTo(cx + 3.2f, y - 5.6f)
                    lineTo(cx - 6.2f, y - 5.6f)
                    lineTo(cx - 6.2f, y + 5.6f)
                    lineTo(cx + 3.2f, y + 5.6f)
                    close()
                }
                val stroke = 2.4f
                // «⌫»: контур стрелки + крестик
                drawPath(back, ink, style = Stroke(stroke, join = StrokeJoin.Round))
                drawLine(ink, Offset(cx - 2.6f, y - 2.2f), Offset(cx + 2.2f, y + 2.2f), stroke, StrokeCap.Round)
                drawLine(ink, Offset(cx + 2.2f, y - 2.2f), Offset(cx - 2.6f, y + 2.2f), stroke, StrokeCap.Round)
            }
        }
        KeyGlyph.EnterWide, KeyGlyph.EnterSmall -> {
            {
                val cx = if (glyph == KeyGlyph.EnterWide) keyWidth / 2f else keyWidth / 2f + 3f
                val y = 13.73f + dy
                val stroke = 2.6f
                val start = if (glyph == KeyGlyph.EnterWide) cx - 6f else cx - 4.5f
                drawLine(ink, Offset(cx + 7.5f, y - 4.2f), Offset(cx + 7.5f, y + 0.2f), stroke, StrokeCap.Round)
                drawLine(ink, Offset(cx + 7.5f, y + 0.2f), Offset(start, y + 0.2f), stroke, StrokeCap.Round)
                // галочка «↵»
                val tip = Offset(start - 4.2f, y + 0.2f)
                drawLine(ink, Offset(tip.x + 3.6f, y - 3.4f), Offset(tip.x, tip.y), stroke, StrokeCap.Round)
                drawLine(ink, Offset(tip.x, tip.y), Offset(tip.x + 3.6f, y + 3.8f), stroke, StrokeCap.Round)
            }
        }
        KeyGlyph.Minus -> {
            {
                val cx = keyWidth / 2f
                val y = 13.73f + dy
                drawLine(ink, Offset(cx - 6f, y), Offset(cx + 6f, y), 2.8f, StrokeCap.Round)
            }
        }
        else -> null
    }
}

/** Корпус клавиши: «подошва» + скошенные бока + белая колпашка с контуром. */
private fun DrawScope.drawKeycap(
    width: Float,
    ink: Color,
    face: Color,
    base: Color,
    flash: Color,
    pulse: Float,
) {
    val sink = 1.6f * pulse
    val stroke = 3f
    val top = 1.5f + 0.8f * pulse
    val faceBottom = 33.5f + 0.8f * pulse

    // вспышка под колпашкой при нажатии горячей клавиши
    if (pulse > 0.01f) {
        drawRoundRect(
            color = flash.copy(alpha = 0.32f * pulse),
            topLeft = Offset(1f, 1f + sink),
            size = Size(width - 2f, 44f),
            cornerRadius = CornerRadius(11f),
        )
    }

    // «подошва» (серо-голубая полоса внизу) — слегка вдавливается при нажатии
    val basePath = Path().apply {
        moveTo(4.5f, faceBottom - 2f)
        lineTo(width - 4.5f, faceBottom - 2f)
        lineTo(width - 2.6f, 43.6f + sink)
        lineTo(2.6f, 43.6f + sink)
        close()
    }
    drawPath(basePath, base)

    // общий контур корпуса
    val body = Path().apply {
        addRoundRect(RoundRect(1.9f, top, width - 1.9f, 43.6f + sink, CornerRadius(10.5f)))
    }
    drawPath(body, ink, style = Stroke(stroke, join = StrokeJoin.Round))

    // колпашка
    val facePath = Path().apply {
        addRoundRect(RoundRect(4.6f, top + 1.9f, width - 4.6f, faceBottom, CornerRadius(7.5f)))
    }
    drawPath(facePath, face)
    drawPath(facePath, ink, style = Stroke(stroke * 0.85f, join = StrokeJoin.Round))

    // «ножки» колпашки — диагонали к подошве, как у объёмной клавиши
    drawLine(
        ink, Offset(4.6f, faceBottom), Offset(width * 0.16f, 43.4f + sink), 2.4f, StrokeCap.Round,
    )
    drawLine(
        ink, Offset(width - 4.6f, faceBottom), Offset(width * 0.84f, 43.4f + sink), 2.4f, StrokeCap.Round,
    )
}

// ───────────────────────── Строка подсказки ─────────────────────────

/**
 * Одна строка списка: клавиша слева + подпись справа.
 * [pulse] — 1 в момент нажатия соответствующей горячей клавиши, плавно гаснет до 0.
 */
@Composable
fun HotkeyRowView(
    row: HotkeyRow,
    label: String,
    modifier: Modifier = Modifier,
    pulse: Float = 0f,
    dark: Boolean = false,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HotkeyKeycap(
            glyph = row.glyph,
            width = row.keyWidth.dp,
            pulse = pulse,
            dark = dark,
        )
        BasicText(
            label,
            style = type.hotkeyLabel.copy(
                color = if (dark) colors.accent else Color(0xFF101010),
                textAlign = TextAlign.Start,
            ),
            maxLines = 3,
        )
    }
}

/** Маленькая подпись-разделитель над колонками. */
@Composable
fun HotkeyColumnHeader(text: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    BasicText(
        text,
        style = type.panelHeader.copy(color = if (dark) colors.accent else colors.accentText),
        modifier = modifier,
        maxLines = 1,
        softWrap = false,
    )
}

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

/** Общий размер карточки подсказок (дизайн-пиксели макета). */
object HotkeyPanel {
    const val WIDTH = 794f
    const val HEIGHT = 564f
    const val RADIUS = 50f
}
