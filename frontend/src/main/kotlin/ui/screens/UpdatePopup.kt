package ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.components.drawBlurredRoundRect
import ui.theme.ScoreKeeperTheme

/**
 * Окно «Новое обновление!» на главном экране (макет «Обновление.pdf», кадр 1920×1080):
 * карточка 794×209 в левом верхнем углу, заголовок, две строки текста, кнопки «СЕЙЧАС» и «ПОЗЖЕ»
 * и круглая кнопка «закрыть» со стрелкой справа вверху.
 */
@Composable
fun UpdatePopup(
    onNow: () -> Unit,
    onLater: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ScoreKeeperTheme.colors
    val strings = ScoreKeeperTheme.strings
    val type = ScoreKeeperTheme.typography

    Box(
        modifier.size(794.dp, 209.dp)
            .drawBehind {
                drawBlurredRoundRect(
                    topLeft = Offset.Zero,
                    size = size,
                    corner = 50.dp.toPx(),
                    color = Color.Black.copy(alpha = 0.25f),
                    sigma = 40.9.dp.toPx(),
                )
            }
            .clip(RoundedCornerShape(50.dp))
            .background(colors.cardSurface),
    ) {
        FigmaText(
            strings.updateTitle,
            type.cardTitle.copy(fontSize = 30.sp, lineHeight = 34.sp),
            colors.overlayHeading,
            Modifier.offset(0.dp, 36.dp), TextAlign.Center, 794.dp,
        )
        FigmaText(
            strings.updateBody,
            type.teamLabel.copy(fontSize = 24.sp, lineHeight = 30.sp),
            colors.captionText,
            Modifier.offset(0.dp, 81.dp), TextAlign.Center, 794.dp,
        )

        UpdateButton(strings.updateNow, onNow, Modifier.offset(173.dp, 162.dp))
        UpdateButton(strings.updateLater, onLater, Modifier.offset(417.dp, 162.dp))

        BackArrowButton(
            onClose,
            Modifier.align(Alignment.TopEnd).offset(x = (-25).dp, y = 25.dp),
        )
    }
}

/** Зелёная пилюля 204×36 с светлой обводкой, как кнопки «ГОТОВО» в диалогах. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun UpdateButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.94f
            hovered -> 1.04f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
        label = "updateButton",
    )
    Box(
        modifier.size(204.dp, 36.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(colors.doneButtonBg)
            .border(4.dp, Color(0xFFA7E29E), RoundedCornerShape(18.dp))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = type.doneButton.copy(fontSize = 24.sp, color = colors.onDoneButton),
            maxLines = 1, softWrap = false,
        )
    }
}
