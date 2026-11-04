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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
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
    // Картинка извлечена из вашего SVG (resources/volleyball_ball.png, 800×800).
    // В SVG: круг r=36 с этой картинкой + белая обводка 3 — то же самое здесь.
    Image(
        painter = painterResource("volleyball_ball.png"),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier.clip(CircleShape).border(3.dp, Color.White, CircleShape),
    )
}

// Пути из вашего SVG теннисного мяча (viewBox 75×75): тело мяча — #B9D613, швы — #F7F7F7,
// поверх — белый круг-обводка (r=36, толщина 3). Рисуются точь-в-точь по SVG.
private const val TENNIS_BALL_PATH =
    "M74.9913 37.5005C74.9902 44.9161 72.7903 52.1649 68.6697 58.3302C64.5491 64.4956 " +
    "58.6927 69.3005 51.8413 72.1376C44.99 74.9747 37.4513 75.7166 30.1783 74.2693C22.9054 " +
    "72.822 16.2248 69.2507 10.9815 64.0068C5.7382 58.7629 2.16765 52.082 0.721209 " +
    "44.8089C-0.725234 37.5357 0.017379 29.997 2.85529 23.146C5.6932 16.2949 10.4989 " +
    "10.4392 16.6647 6.31932C22.8305 2.19941 30.0795 0.000213665 37.4951 0C47.4401 " +
    "0.00114561 56.9773 3.95263 64.0091 10.9852C71.0409 18.0178 74.9913 27.5555 74.9913 37.5005Z"

private const val TENNIS_SEAMS_PATH =
    "M42.5228 0.372477C40.8733 0.139494 39.2101 0.0153966 37.5442 0.000976562C35.205 " +
    "8.41265 30.435 15.9468 23.8322 21.6592C17.2295 27.3717 9.08774 31.0084 0.42707 " +
    "32.1136C0.176827 33.7709 0.0393757 35.4433 0.015625 37.1192L0.21324 37.0911C10.1229 " +
    "35.9516 19.4586 31.8502 27.0012 25.3226C34.5437 18.795 39.9425 10.1445 42.4925 " +
    "0.501012L42.5228 0.372477ZM74.6203 37.8385C65.7751 40.013 57.7087 44.6039 51.3222 " +
    "51.0983C44.9357 57.5928 40.4806 65.735 38.4545 74.6154L38.3779 74.9534C40.1217 " +
    "74.9115 41.8604 74.7473 43.5812 74.4621C45.5523 66.9641 49.4475 60.1108 54.8812 " +
    "54.5809C60.3149 49.051 67.0987 45.036 74.561 42.9336C74.816 41.2169 74.9559 " +
    "39.4852 74.9799 37.7499L74.6203 37.8385Z"

@Composable
fun TennisBallIcon(modifier: Modifier = Modifier) {
    val ballPath = remember { PathParser().parsePathString(TENNIS_BALL_PATH).toPath() }
    val seamPath = remember { PathParser().parsePathString(TENNIS_SEAMS_PATH).toPath() }
    Canvas(modifier) {
        val s = size.minDimension / 75f
        withTransform({ scale(s, s, Offset.Zero) }) {
            val ballClip = Path().apply { addOval(Rect(Offset(37.5f, 37.5f), 37.5f)) }
            drawPath(ballPath, Color(0xFFB9D613))
            clipPath(ballClip) { drawPath(seamPath, Color(0xFFF7F7F7)) }
            drawCircle(Color.White, radius = 36f, center = Offset(37.5f, 37.5f), style = Stroke(3f))
        }
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
fun MinimizeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // У окна нет системной рамки (undecorated), поэтому сворачивание — своя кнопка «–».
    RoundIconButton(onClick, modifier, hoverTurn = 0f) { tint, m ->
        Canvas(m.size(24.dp)) {
            val w = 5.5.dp.toPx()
            drawLine(
                tint, Offset(w / 2f, size.height / 2f), Offset(size.width - w / 2f, size.height / 2f),
                w, StrokeCap.Round,
            )
        }
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
