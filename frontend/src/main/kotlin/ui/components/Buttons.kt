package ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.theme.ScoreKeeperTheme

// ───────────────────────── Кнопка вида спорта (370×140) ─────────────────────────
//
// Анимация:
//  • наведение: кнопка плавно «всплывает» (+5% и вверх на 6 dp), свечение растёт и опускается ниже,
//    иконка слегка поворачивается;
//  • нажатие: быстрое «вдавливание» (до ~93%), при отпускании пружинит с небольшим отскоком.

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SportButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconStart: Dp = 35.dp,
    labelCenterX: Dp = 231.5.dp,
    icon: @Composable (Modifier) -> Unit,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val hover = animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "hover",
    )
    val pressSpec: AnimationSpec<Float> =
        if (pressed) tween(80) else spring(dampingRatio = 0.45f, stiffness = 500f)
    val press = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "press",
    )

    Box(
        modifier
            .size(370.dp, 140.dp)
            .graphicsLayer {
                val h = hover.value
                val p = press.value
                val s = 1f + 0.05f * h - 0.07f * p
                scaleX = s
                scaleY = s
                translationY = -6.dp.toPx() * h + 2.dp.toPx() * p
            }
            .drawBehind {
                // свечение из Figma: 0 / 14 / blur 56.4 / spread -11, цвет #2171F4
                val h = hover.value
                val p = press.value
                val inset = 11.dp.toPx()
                val glowW = size.width - 2 * inset
                val glowH = size.height - 2 * inset
                drawBlurredRoundRect(
                    topLeft = Offset(inset - 3.dp.toPx(), inset + (14f + 10f * h - 8f * p).dp.toPx()),
                    size = Size(glowW, glowH),
                    corner = glowH / 2f,
                    color = colors.buttonGlow,
                    sigma = (28.2f + 6f * h).dp.toPx(),
                )
            }
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(colors.buttonStart, colors.buttonEnd)))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        icon(
            Modifier
                .offset(iconStart, 32.dp)
                .size(75.dp)
                .graphicsLayer { rotationZ = 18f * hover.value },
        )
        Box(
            Modifier.offset(labelCenterX - 103.5.dp, 51.dp).size(207.dp, 47.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = label,
                style = type.button.copy(color = colors.onButton),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        }
    }
}

// ───────────────────────── Иконки видов спорта ─────────────────────────

@Composable
fun VolleyballIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource("voleibollboll.png"),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape).border(3.dp, Color.White, CircleShape),
    )
}

@Composable
fun TennisBallIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(Color(0xFFB4D81B), r, c)
        val ball = Path().apply { addOval(Rect(c, r)) }
        clipPath(ball) {
            val seam = Stroke(width = size.minDimension * 0.075f)
            drawCircle(Color.White, radius = r * 0.92f, center = Offset(c.x - r * 1.05f, c.y), style = seam)
            drawCircle(Color.White, radius = r * 0.92f, center = Offset(c.x + r * 1.05f, c.y), style = seam)
        }
        drawCircle(Color.White, radius = r - 1.5.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
    }
}

// ───────────────────────── Круглые кнопки (настройки, закрыть) ─────────────────────────
//
// Наведение: кнопка увеличивается, фон темнеет, иконка проворачивается.
// Нажатие: сжимается, при отпускании пружинит.

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RoundIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hoverTurn: Float = 90f,
    icon: @Composable (tint: Color, modifier: Modifier) -> Unit,
) {
    val colors = ScoreKeeperTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val hover = animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 350f),
        label = "hover",
    )
    val pressSpec: AnimationSpec<Float> =
        if (pressed) tween(70) else spring(dampingRatio = 0.45f, stiffness = 500f)
    val press = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "press",
    )

    Box(
        modifier
            .size(52.dp)
            .graphicsLayer {
                val s = 1f + 0.12f * hover.value - 0.14f * press.value
                scaleX = s
                scaleY = s
            }
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clip(CircleShape)
            .drawBehind {
                drawRect(
                    lerp(colors.iconButtonBg, colors.iconButtonBgHover, hover.value.coerceIn(0f, 1f)),
                )
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon(colors.iconButtonFg, Modifier.graphicsLayer { rotationZ = hoverTurn * hover.value })
    }
}

@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    RoundIconButton(onClick, modifier, hoverTurn = 60f) { tint, m ->
        Icon(Icons.Filled.Settings, contentDescription = "Настройки", modifier = m.size(32.dp), tint = tint)
    }
}

@Composable
fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    RoundIconButton(onClick, modifier, hoverTurn = 90f) { tint, m ->
        Canvas(m.size(24.dp)) {
            val w = 5.5.dp.toPx()
            val pad = w / 2f
            drawLine(tint, Offset(pad, pad), Offset(size.width - pad, size.height - pad), w, StrokeCap.Round)
            drawLine(tint, Offset(size.width - pad, pad), Offset(pad, size.height - pad), w, StrokeCap.Round)
        }
    }
}

// ───────────────────────── Мелкая плашка (авторы, версия) ─────────────────────────

@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    Box(
        modifier
            .height(22.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.chipBg)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = type.chip.copy(color = colors.chipText), maxLines = 1, softWrap = false)
    }
}
