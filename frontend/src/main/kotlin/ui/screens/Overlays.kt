package ui.screens

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.components.LanguageSwitch
import ui.components.ThemeSwitch
import ui.components.drawBlurredRoundRect
import ui.theme.AppLanguage
import ui.theme.DesignStage
import ui.theme.ScoreKeeperTheme
import ui.theme.colorsFor
import ui.theme.stringsFor

// ───────────────────────── Оверлеи поверх меню (Export-2.pdf) ─────────────────────────
//
// Все три экрана — затемнённое меню (чёрный 52%) + белая карточка 792 шириной по центру,
// радиус углов 48. Координаты внутри карточки — как в макете 1920×1080.

/** Общая «рамка» оверлея: затемнение на всё окно + карточка по центру + кнопка «назад». */
@Composable
private fun OverlayScaffold(
    cardWidth: Dp,
    cardHeight: Dp,
    onBack: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = ScoreKeeperTheme.colors
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(160)) }

    DesignStage {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f * appear.value))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* клики по затемнению не проходят в меню */ },
        ) {
            Box(Modifier.align(Alignment.Center).requiredSize(1920.dp, 1080.dp)) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(cardWidth, cardHeight)
                        .graphicsLayer {
                            val s = 0.97f + 0.03f * appear.value
                            scaleX = s
                            scaleY = s
                            alpha = appear.value
                        }
                        .clip(RoundedCornerShape(48.dp))
                        .background(colors.cardSurface)
                        .border(3.dp, colors.cardBorder, RoundedCornerShape(48.dp)),
                ) {
                    content()
                    BackArrowButton(
                        onBack,
                        Modifier.align(Alignment.TopEnd).offset(x = (-33).dp, y = 16.dp),
                    )
                }
            }
        }
    }
}

/** Круглая кнопка «←» в правом верхнем углу карточки (d=54, светлая обводка). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BackArrowButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ScoreKeeperTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val hover = animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 350f),
        label = "backHover",
    )
    val pressSpec: AnimationSpec<Float> =
        if (pressed) tween(70) else spring(dampingRatio = 0.45f, stiffness = 500f)
    val press = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "backPress",
    )

    Canvas(
        modifier
            .size(54.dp)
            .graphicsLayer {
                val s = 1f + 0.12f * hover.value - 0.14f * press.value
                scaleX = s
                scaleY = s
                translationX = -2.dp.toPx() * hover.value
            }
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        val c = center
        val ringR = size.minDimension / 2f - 2.5.dp.toPx()
        drawCircle(colors.cardSurface, ringR, c)
        drawCircle(colors.cardBorder, ringR, c, style = Stroke(4.5.dp.toPx()))
        val w = 5.dp.toPx()
        val dx = size.width * 0.26f
        val dy = size.height * 0.185f
        drawLine(colors.iconButtonFg, Offset(c.x - dx, c.y), Offset(c.x + dx, c.y), w, StrokeCap.Round)
        drawLine(
            colors.iconButtonFg,
            Offset(c.x - dx, c.y),
            Offset(c.x - dx * 0.25f, c.y - dy),
            w, StrokeCap.Round,
        )
        drawLine(
            colors.iconButtonFg,
            Offset(c.x - dx, c.y),
            Offset(c.x - dx * 0.25f, c.y + dy),
            w, StrokeCap.Round,
        )
    }
}

// ───────────────────────── Экран «Настройки» ─────────────────────────

@Composable
fun SettingsOverlay(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onBack: () -> Unit,
) {
    ScoreKeeperTheme(colors = colorsFor(darkTheme), strings = stringsFor(language)) {
        val colors = ScoreKeeperTheme.colors
        val type = ScoreKeeperTheme.typography
        val strings = ScoreKeeperTheme.strings

        OverlayScaffold(cardWidth = 792.dp, cardHeight = 562.dp, onBack = onBack) {
            Box(
                Modifier.fillMaxWidth().offset(y = 30.dp).height(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    strings.settingsTitle,
                    style = type.heading.copy(color = colors.headingBlue),
                    maxLines = 1,
                    softWrap = false,
                )
            }

            Box(
                Modifier.offset(46.dp, 124.dp).size(560.dp, 50.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicText(
                    strings.appTheme,
                    style = type.heading.copy(color = colors.headingBlue),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            ThemeSwitch(darkTheme, onToggleTheme, Modifier.offset(635.dp, 124.dp))
            Box(Modifier.offset(46.dp, 177.dp).size(700.dp, 2.dp).background(colors.divider))

            Box(
                Modifier.offset(46.dp, 213.dp).size(560.dp, 50.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicText(
                    strings.appLanguage,
                    style = type.heading.copy(color = colors.headingBlue),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            LanguageSwitch(language, onLanguageChange, Modifier.offset(635.dp, 183.dp))
            Box(Modifier.offset(46.dp, 266.dp).size(700.dp, 2.dp).background(colors.divider))

            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
                BasicText(
                    strings.developedFor,
                    style = type.caption.copy(color = colors.captionText),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

// ───────────────────────── Экран настройки команд / игроков ─────────────────────────

@Composable
fun TeamSetupOverlay(
    forTennis: Boolean,
    darkTheme: Boolean,
    language: AppLanguage,
    onPickImage: () -> ImageBitmap?,
    onBack: () -> Unit,
    onDone: (logo1: ImageBitmap?, name1: String, logo2: ImageBitmap?, name2: String) -> Unit,
) {
    ScoreKeeperTheme(colors = colorsFor(darkTheme), strings = stringsFor(language)) {
        val colors = ScoreKeeperTheme.colors
        val type = ScoreKeeperTheme.typography
        val strings = ScoreKeeperTheme.strings

        var logo1 by remember { mutableStateOf<ImageBitmap?>(null) }
        var logo2 by remember { mutableStateOf<ImageBitmap?>(null) }
        var name1 by remember { mutableStateOf("") }
        var name2 by remember { mutableStateOf("") }

        OverlayScaffold(cardWidth = 792.dp, cardHeight = 610.dp, onBack = onBack) {
            Box(
                Modifier.fillMaxWidth().offset(y = 24.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                BasicText(
                    if (forTennis) strings.setupPlayersTitle else strings.setupTeamsTitle,
                    style = type.cardTitle.copy(color = colors.headingBlue),
                )
            }

            UploadCircle(
                image = logo1,
                onClick = { onPickImage()?.let { logo1 = it } },
                modifier = Modifier.offset(96.dp, 113.dp),
            )
            UploadCircle(
                image = logo2,
                onClick = { onPickImage()?.let { logo2 = it } },
                modifier = Modifier.offset(477.dp, 113.dp),
            )
            Box(Modifier.offset(395.dp, 113.dp).size(2.dp, 391.dp).background(colors.divider))

            TeamLabel(
                if (forTennis) strings.player1 else strings.team1,
                Modifier.offset(96.dp, 366.dp),
            )
            TeamLabel(
                if (forTennis) strings.player2 else strings.team2,
                Modifier.offset(477.dp, 366.dp),
            )

            NameField(name1, { name1 = it }, Modifier.offset(84.dp, 459.dp))
            NameField(name2, { name2 = it }, Modifier.offset(465.dp, 459.dp))

            DoneButton(
                text = strings.done,
                onClick = { onDone(logo1, name1, logo2, name2) },
                modifier = Modifier.offset(308.dp, 538.dp),
            )
        }
    }
}

@Composable
private fun TeamLabel(text: String, modifier: Modifier = Modifier) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    Box(modifier.size(220.dp, 44.dp), contentAlignment = Alignment.Center) {
        BasicText(
            text,
            style = type.teamLabel.copy(color = colors.headingBlue),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}

/** Круг загрузки логотипа (d=220): пустой — серая иконка vsavphoto.png, с лого — сама картинка. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun UploadCircle(image: ImageBitmap?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val hover = animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 320f),
        label = "uploadHover",
    )
    val pressSpec: AnimationSpec<Float> =
        if (pressed) tween(70) else spring(dampingRatio = 0.45f, stiffness = 500f)
    val press = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "uploadPress",
    )

    Box(
        modifier
            .size(220.dp)
            .graphicsLayer {
                val s = 1f + 0.03f * hover.value - 0.04f * press.value
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource("vsavphoto.png"),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Тёмное поле ввода названия (243×46) с подсказкой «...». */
@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    Box(
        modifier
            .size(243.dp, 46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.fieldBg),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = type.field.copy(color = colors.fieldText),
            cursorBrush = SolidColor(colors.fieldText),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        BasicText("...", style = type.field.copy(color = colors.fieldPlaceholder))
                    }
                    innerTextField()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}

/** Зелёная кнопка «ГОТОВО» (176×54) с мягким свечением и пружинной анимацией. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DoneButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val hover = animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "doneHover",
    )
    val pressSpec: AnimationSpec<Float> =
        if (pressed) tween(80) else spring(dampingRatio = 0.45f, stiffness = 500f)
    val press = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "donePress",
    )

    Box(
        modifier
            .size(176.dp, 54.dp)
            .graphicsLayer {
                val s = 1f + 0.05f * hover.value - 0.07f * press.value
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                val inset = 6.dp.toPx()
                drawBlurredRoundRect(
                    topLeft = Offset(inset, inset + (6f + 4f * hover.value - 4f * press.value).dp.toPx()),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    corner = (size.height - 2 * inset) / 2f,
                    color = colors.doneButtonBg,
                    sigma = (13f + 4f * hover.value).dp.toPx(),
                )
            }
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clip(RoundedCornerShape(50))
            .background(colors.doneButtonBg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = type.doneButton.copy(color = colors.onDoneButton),
            maxLines = 1,
            softWrap = false,
        )
    }
}
