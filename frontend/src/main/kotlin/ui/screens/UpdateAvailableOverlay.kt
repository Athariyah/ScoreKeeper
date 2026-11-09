package ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.components.drawBlurredRoundRect
import ui.theme.AppLanguage
import ui.theme.DesignStage
import ui.theme.ScoreKeeperTheme
import ui.theme.colorsFor
import ui.theme.stringsFor

/** Compact update notice drawn on the home screen using the supplied 830×259 SVG geometry. */
@Composable
fun UpdateAvailableOverlay(
    language: AppLanguage,
    downloading: Boolean,
    errorMessage: String?,
    onInstallNow: () -> Unit,
    onInstallLater: () -> Unit,
    onDismiss: () -> Unit,
) {
    ScoreKeeperTheme(colors = colorsFor(false), strings = stringsFor(language)) {
        val type = ScoreKeeperTheme.typography
        val russian = language == AppLanguage.RU
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, tween(180)) }

        DesignStage {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.align(Alignment.TopStart)
                        .requiredSize(1920.dp, 1080.dp)
                        .graphicsLayer {
                            alpha = appear.value
                            translationY = -10.dp.toPx() * (1f - appear.value)
                        },
                ) {
                    Box(
                        Modifier.offset(11.dp, 17.dp).size(794.dp, 209.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(50.dp),
                                ambientColor = Color.Black.copy(alpha = 0.61f),
                                spotColor = Color.Black.copy(alpha = 0.61f),
                            )
                            .clip(RoundedCornerShape(50.dp))
                            .background(Color.White),
                    )

                    FigmaText(
                        if (russian) "НОВОЕ ОБНОВЛЕНИЕ!" else "NEW UPDATE!",
                        type.cardTitle.copy(fontSize = 28.sp, lineHeight = 34.sp),
                        Color(0xFF2070F5),
                        Modifier.offset(11.dp, 43.dp),
                        TextAlign.Center,
                        794.dp,
                    )

                    BasicText(
                        text = if (russian) {
                            "ВЫШЛО НОВОЕ ОБНОВЛЕНИЕ ПРИЛОЖЕНИЯ.\nУСТАНОВИТЬ СЕЙЧАС ИЛИ ПОСЛЕ ЗАКРЫТИЯ?"
                        } else {
                            "A NEW APP UPDATE IS AVAILABLE.\nINSTALL NOW OR AFTER QUITTING?"
                        },
                        style = type.teamLabel.copy(
                            fontSize = 20.sp,
                            lineHeight = 27.sp,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.offset(49.dp, 82.dp).size(718.dp, 56.dp),
                        maxLines = 2,
                        softWrap = false,
                    )

                    if (errorMessage != null || downloading) {
                        BasicText(
                            text = errorMessage ?: if (russian) "СКАЧИВАНИЕ ОБНОВЛЕНИЯ…" else "DOWNLOADING UPDATE…",
                            style = type.caption.copy(fontSize = 13.sp, color = Color(0xFFB3261E)),
                            modifier = Modifier.offset(78.dp, 137.dp).size(674.dp, 22.dp),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }

                    UpdateActionButton(
                        text = if (downloading) {
                            if (russian) "ЗАГРУЗКА…" else "DOWNLOADING…"
                        } else if (russian) "СЕЙЧАС" else "NOW",
                        modifier = Modifier.offset(186.dp, 181.dp),
                        enabled = !downloading,
                        onClick = onInstallNow,
                    )
                    UpdateActionButton(
                        text = if (russian) "ПОЗЖЕ" else "LATER",
                        modifier = Modifier.offset(430.dp, 181.dp),
                        enabled = !downloading,
                        onClick = onInstallLater,
                    )
                    BackArrowButton(
                        onClick = onDismiss,
                        modifier = Modifier.offset(725.dp, 42.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun UpdateActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val type = ScoreKeeperTheme.typography
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier.size(200.dp, 32.dp)
            .drawBehind {
                val inset = 11.dp.toPx()
                drawBlurredRoundRect(
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2f, size.height - inset * 2f),
                    corner = 16.dp.toPx(),
                    color = Color(0x3800E74C),
                    sigma = 27.4.dp.toPx(),
                )
            }
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Color(0xFF69DB7C) else Color(0xFFB9D9BF))
            .border(4.dp, Color(0xFFA7E29E), RoundedCornerShape(16.dp))
            .hoverable(interaction)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = type.teamLabel.copy(fontSize = 18.sp, color = Color(0xFF343F4B), textAlign = TextAlign.Center),
            maxLines = 1,
            softWrap = false,
        )
    }
}
