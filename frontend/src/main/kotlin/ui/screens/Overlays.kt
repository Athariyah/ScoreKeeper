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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.components.LanguageSwitch
import ui.components.ThemeSwitch
import ui.components.drawBlurredRoundRect
import ui.components.trackTextFocus
import ui.theme.AppLanguage
import ui.theme.DesignStage
import ui.theme.ScoreKeeperTheme
import ui.theme.colorsFor
import ui.theme.stringsFor

// ───────────────────────── Оверлеи поверх меню (Export-2.pdf) ─────────────────────────
//
// Все три экрана — затемнённое меню (чёрный 52%) + белая карточка 792 шириной по центру,
// радиус углов 50. Геометрия, цвета и пути — один в один из Figma и присланных SVG:
// кнопка «назад» 55×55, кнопка загрузки фото 220×220, поле ввода 249×52,
// кнопка «ГОТОВО» 177×55 (текст в RU — оригинальные векторные глифы из SVG).

/**
 * Обводка синих текстов из Figma: #E2EEF7, 1 px, снаружи.
 * В тёмной теме светлая обводка «светила» бы вокруг букв, поэтому там она = цвет карточки.
 */
@Composable
private fun textStrokeColor(): Color {
    val colors = ScoreKeeperTheme.colors
    return if (colors.dark) colors.cardSurface else Color(0xFFE2EEF7)
}

/**
 * Текст как в Figma: заливка + внешняя обводка 1 design-px (штрих 2 px по центру контура,
 * внутренняя половина перекрывается заливкой). BasicText так не умеет — рисуем через Canvas.
 */
@Composable
internal fun FigmaText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    fixedWidth: Dp? = null,
) {
    val measurer = rememberTextMeasurer()
    val dens = LocalDensity.current
    val layout = remember(text, style, textAlign, fixedWidth, dens) {
        measurer.measure(
            text = AnnotatedString(text),
            style = style.copy(textAlign = textAlign),
            constraints = if (fixedWidth != null) {
                // minWidth = maxWidth: параграф занимает всю заданную ширину,
                // иначе он сжимается до ширины текста и TextAlign.Center не работает.
                val wpx = with(dens) { fixedWidth.roundToPx() }
                Constraints(minWidth = wpx, maxWidth = wpx)
            } else {
                Constraints()
            },
        )
    }
    val w = fixedWidth ?: with(dens) { layout.size.width.toDp() }
    val h = with(dens) { layout.size.height.toDp() }
    Canvas(modifier.size(w, h)) {
        drawText(
            layout,
            color = textStrokeColor(),
            drawStyle = Stroke(width = 2f * dens.density, join = StrokeJoin.Round),
        )
        drawText(layout, color = color)
    }
}

/** Общая «рамка» оверлея: затемнение на всё окно + карточка по центру + кнопка «назад». */
@Composable
internal fun OverlayScaffold(
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
                        .clip(RoundedCornerShape(50.dp))
                        .background(colors.cardSurface)
                        .border(3.dp, colors.cardBorder, RoundedCornerShape(50.dp)),
                ) {
                    content()
                    BackArrowButton(
                        onBack,
                        Modifier.align(Alignment.TopEnd).offset(x = (-25).dp, y = 25.dp),
                    )
                }
            }
        }
    }
}

// ───────────────────────── Кнопка «назад» (точный SVG 55×55) ─────────────────────────

private const val BACK_ARROW_PATH = "M41.3332 28H14.6665M24.6665 18L14.6665 28L24.6665 38"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun BackArrowButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
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
    val arrowPath = remember { PathParser().parsePathString(BACK_ARROW_PATH).toPath() }

    Canvas(
        modifier
            .size(55.dp)
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
        val s = size.width / 55f
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawCircle(colors.backButtonBg, radius = 26f, center = Offset(27.5f, 27.5f))
            drawCircle(
                colors.backButtonBorder, radius = 26f, center = Offset(27.5f, 27.5f),
                style = Stroke(3f),
            )
            drawPath(
                arrowPath, colors.backButtonArrow,
                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
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
                Modifier.fillMaxWidth().offset(y = 22.dp).height(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                FigmaText(strings.settingsTitle, type.heading, colors.accentText)
            }

            Box(
                Modifier.offset(46.dp, 124.dp).size(560.dp, 50.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                FigmaText(strings.appTheme, type.heading, colors.accentText)
            }
            ThemeSwitch(darkTheme, onToggleTheme, Modifier.offset(634.dp, 124.dp))
            Box(Modifier.offset(46.dp, 177.dp).size(700.dp, 2.dp).background(colors.divider))

            Box(
                Modifier.offset(46.dp, 213.dp).size(560.dp, 50.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                FigmaText(strings.appLanguage, type.heading, colors.accentText)
            }
            LanguageSwitch(language, onLanguageChange, Modifier.offset(634.dp, 183.dp))
            Box(Modifier.offset(46.dp, 266.dp).size(700.dp, 2.dp).background(colors.divider))

            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)) {
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
            FigmaText(
                text = if (forTennis) strings.setupPlayersTitle else strings.setupTeamsTitle,
                style = type.cardTitle,
                color = colors.accentText,
                modifier = Modifier.offset(159.dp, 20.dp),
                textAlign = TextAlign.Center,
                fixedWidth = 475.dp,
            )

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
            Box(Modifier.offset(395.dp, 128.dp).size(2.dp, 313.dp).background(colors.divider))

            TeamLabel(
                if (forTennis) strings.player1 else strings.team1,
                Modifier.offset(96.dp, 366.dp),
            )
            TeamLabel(
                if (forTennis) strings.player2 else strings.team2,
                Modifier.offset(477.dp, 366.dp),
            )

            NameField(name1, { name1 = it }, Modifier.offset(82.dp, 456.dp))
            NameField(name2, { name2 = it }, Modifier.offset(461.dp, 456.dp))

            DoneButton(
                text = strings.done,
                usePathText = language == AppLanguage.RU,
                onClick = { onDone(logo1, name1, logo2, name2) },
                modifier = Modifier.offset(307.5.dp, 538.dp),
            )
        }
    }
}

@Composable
private fun TeamLabel(text: String, modifier: Modifier = Modifier) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    Box(modifier.size(220.dp, 44.dp), contentAlignment = Alignment.Center) {
        FigmaText(text, type.teamLabel, colors.accentText)
    }
}

// ───────────────────────── Кнопка загрузки фото (точный SVG 220×220) ─────────────────────────

private const val UPLOAD_PATH =
    "M75 155C72.25 155 69.8967 154.022 67.94 152.065C65.9833 150.108 65.0033 147.753 65 145V75C65 72.25 65.98 69.8967 67.94 67.94C69.9 65.9833 72.2533 65.0033 75 65H111.875C113.542 65 114.813 65.6883 115.69 67.065C116.567 68.4417 116.712 69.92 116.125 71.5C115.708 72.9167 115.417 74.3333 115.25 75.75C115.083 77.1667 115 78.5833 115 80C115 88.3333 117.917 95.4167 123.75 101.25C129.583 107.083 136.667 110 145 110C145.667 110 146.375 109.98 147.125 109.94C147.875 109.9 148.583 109.795 149.25 109.625C150.75 109.458 152.083 109.833 153.25 110.75C154.417 111.667 155 112.833 155 114.25V145C155 147.75 154.022 150.105 152.065 152.065C150.108 154.025 147.753 155.003 145 155H75Z" +
    "M85 135H135C136 135 136.75 134.542 137.25 133.625C137.75 132.708 137.667 131.833 137 131L123.25 112.625C122.75 111.958 122.083 111.625 121.25 111.625C120.417 111.625 119.75 111.958 119.25 112.625L106.25 130L97 117.625C96.5 116.958 95.8333 116.625 95 116.625C94.1667 116.625 93.5 116.958 93 117.625L83 131C82.3333 131.833 82.25 132.708 82.75 133.625C83.25 134.542 84 135 85 135Z" +
    "M141.44 98.56C140.48 97.6033 140 96.4167 140 95V79.125L135.5 83.5C134.583 84.4167 133.438 84.8967 132.065 84.94C130.692 84.9833 129.503 84.5033 128.5 83.5C127.583 82.5833 127.125 81.4167 127.125 80C127.125 78.5833 127.583 77.4167 128.5 76.5L141.5 63.5C142 63 142.542 62.625 143.125 62.375C143.708 62.125 144.333 62 145 62C145.667 62 146.292 62.125 146.875 62.375C147.458 62.625 148 63 148.5 63.5L161.5 76.5C162.417 77.4167 162.897 78.5633 162.94 79.94C162.983 81.3167 162.503 82.5033 161.5 83.5C160.583 84.4167 159.417 84.875 158 84.875C156.583 84.875 155.417 84.4167 154.5 83.5L150 79.125V95C150 96.4167 149.522 97.605 148.565 98.565C147.608 99.525 146.42 100.003 145 100C143.58 99.9967 142.393 99.5167 141.44 98.56Z"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun UploadCircle(image: ImageBitmap?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ScoreKeeperTheme.colors
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
    val uploadPath = remember { PathParser().parsePathString(UPLOAD_PATH).toPath() }

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
            Canvas(Modifier.fillMaxSize()) {
                val s = size.width / 220f
                withTransform({ scale(s, s, Offset.Zero) }) {
                    drawCircle(colors.logoPlaceholder, radius = 110f, center = Offset(110f, 110f))
                    // На тёмной теме «белая» иконка загрузки сливалась с серым кругом — берём цвет текста.
                    drawPath(uploadPath, if (colors.dark) colors.iconButtonFg else Color.White)
                }
            }
        }
    }
}

// ───────────────────────── Поле ввода (точный SVG 249×52) ─────────────────────────

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    Box(
        modifier
            .size(249.dp, 52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.fieldBg)
            .border(3.dp, colors.fieldStroke, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            // Три точки-подсказки из SVG: круги r5 #DDDDDD в (25/39/52, 36).
            Canvas(Modifier.fillMaxSize()) {
                val s = size.width / 249f
                withTransform({ scale(s, s, Offset.Zero) }) {
                    for (cx in listOf(25f, 39f, 52f)) {
                        drawCircle(Color(0xFFDDDDDD), radius = 5f, center = Offset(cx, 36f))
                    }
                }
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = type.field.copy(color = colors.fieldText),
            cursorBrush = SolidColor(colors.fieldText),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .trackTextFocus("teamName"),
        )
    }
}

// ───────────────────────── Кнопка «ГОТОВО» (точный SVG 177×55 + тень) ─────────────────────────

// Глифы «ГОТОВО» из SVG (координаты холста 265×143; кнопка начинается в (43.8, 43.8)).
private const val GOTOVO_PATH =
    "M57.5892 81.35V61.75C57.5892 61.0734 57.8225 60.49 58.2892 60C58.7792 59.5334 59.3625 59.3 60.0392 59.3H74.0392C74.7158 59.3 75.2992 59.5334 75.7892 60C76.2558 60.49 76.4892 61.0734 76.4892 61.75C76.4892 62.4267 76.2558 62.9984 75.7892 63.465C75.2992 63.955 74.7158 64.2 74.0392 64.2H62.4892V81.35C62.4892 82.0267 62.2558 82.5984 61.7892 83.065C61.2992 83.555 60.7158 83.8 60.0392 83.8C59.3625 83.8 58.7792 83.555 58.2892 83.065C57.8225 82.5984 57.5892 82.0267 57.5892 81.35Z" +
    "M95.6719 66.37C94.2486 64.9234 92.5219 64.2 90.4919 64.2C88.4619 64.2 86.7352 64.9234 85.3119 66.37C83.8652 67.7934 83.1419 69.52 83.1419 71.55C83.1419 73.58 83.8652 75.3067 85.3119 76.73C86.7352 78.1767 88.4619 78.9 90.4919 78.9C92.5219 78.9 94.2486 78.1767 95.6719 76.73C97.1186 75.3067 97.8419 73.58 97.8419 71.55C97.8419 69.52 97.1186 67.7934 95.6719 66.37Z" +
    "M99.1369 80.195C96.7569 82.5984 93.8752 83.8 90.4919 83.8C87.1086 83.8 84.2269 82.5984 81.8469 80.195C79.4436 77.815 78.2419 74.9334 78.2419 71.55C78.2419 68.1667 79.4436 65.285 81.8469 62.905C84.2269 60.5017 87.1086 59.3 90.4919 59.3C93.8752 59.3 96.7569 60.5017 99.1369 62.905C101.54 65.285 102.742 68.1667 102.742 71.55C102.742 74.9334 101.54 77.815 99.1369 80.195Z" +
    "M112.882 64.2H106.932C106.256 64.2 105.672 63.9667 105.182 63.5C104.716 63.01 104.482 62.4267 104.482 61.75C104.482 61.0734 104.716 60.49 105.182 60C105.672 59.5334 106.256 59.3 106.932 59.3H123.732C124.409 59.3 124.992 59.5334 125.482 60C125.949 60.49 126.182 61.0734 126.182 61.75C126.182 62.4267 125.949 63.01 125.482 63.5C124.992 63.9667 124.409 64.2 123.732 64.2H117.782V81.35C117.782 82.0267 117.549 82.5984 117.082 83.065C116.592 83.555 116.009 83.8 115.332 83.8C114.656 83.8 114.084 83.555 113.617 83.065C113.127 82.5984 112.882 82.0267 112.882 81.35V64.2Z" +
    "M145.349 66.37C143.925 64.9234 142.199 64.2 140.169 64.2C138.139 64.2 136.412 64.9234 134.989 66.37C133.542 67.7934 132.819 69.52 132.819 71.55C132.819 73.58 133.542 75.3067 134.989 76.73C136.412 78.1767 138.139 78.9 140.169 78.9C142.199 78.9 143.925 78.1767 145.349 76.73C146.795 75.3067 147.519 73.58 147.519 71.55C147.519 69.52 146.795 67.7934 145.349 66.37Z" +
    "M148.814 80.195C146.434 82.5984 143.552 83.8 140.169 83.8C136.785 83.8 133.904 82.5984 131.524 80.195C129.12 77.815 127.919 74.9334 127.919 71.55C127.919 68.1667 129.12 65.285 131.524 62.905C133.904 60.5017 136.785 59.3 140.169 59.3C143.552 59.3 146.434 60.5017 148.814 62.905C151.217 65.285 152.419 68.1667 152.419 71.55C152.419 74.9334 151.217 77.815 148.814 80.195Z" +
    "M162.56 78.9H172.71C173.387 78.9 173.959 78.655 174.425 78.165C174.915 77.6984 175.16 77.1267 175.16 76.4501C175.16 75.7734 174.915 75.2017 174.425 74.735C173.959 74.245 173.387 74 172.71 74H162.56V78.9Z" +
    "M162.56 64.2V69.1H171.31C171.987 69.1 172.559 68.855 173.025 68.365C173.515 67.8984 173.76 67.3267 173.76 66.65C173.76 65.9734 173.515 65.4017 173.025 64.935C172.559 64.445 171.987 64.2 171.31 64.2H162.56Z" +
    "M160.11 59.3H171.31C173.34 59.3 175.067 60.0234 176.49 61.47C177.937 62.8934 178.66 64.62 178.66 66.65C178.66 68.1667 178.229 69.555 177.365 70.815C179.162 72.285 180.06 74.1634 180.06 76.4501C180.06 78.48 179.337 80.2067 177.89 81.63C176.467 83.0767 174.74 83.8 172.71 83.8H160.11C159.434 83.8 158.862 83.555 158.395 83.065C157.905 82.5984 157.66 82.0267 157.66 81.35V61.75C157.66 61.0734 157.905 60.49 158.395 60C158.862 59.5334 159.434 59.3 160.11 59.3Z" +
    "M201.349 66.37C199.925 64.9234 198.199 64.2 196.169 64.2C194.139 64.2 192.412 64.9234 190.989 66.37C189.542 67.7934 188.819 69.52 188.819 71.55C188.819 73.58 189.542 75.3067 190.989 76.73C192.412 78.1767 194.139 78.9 196.169 78.9C198.199 78.9 199.925 78.1767 201.349 76.73C202.795 75.3067 203.519 73.58 203.519 71.55C203.519 69.52 202.795 67.7934 201.349 66.37Z" +
    "M204.814 80.195C202.434 82.5984 199.552 83.8 196.169 83.8C192.785 83.8 189.904 82.5984 187.524 80.195C185.12 77.815 183.919 74.9334 183.919 71.55C183.919 68.1667 185.12 65.285 187.524 62.905C189.904 60.5017 192.785 59.3 196.169 59.3C199.552 59.3 202.434 60.5017 204.814 62.905C207.217 65.285 208.419 68.1667 208.419 71.55C208.419 74.9334 207.217 77.815 204.814 80.195Z"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DoneButton(
    text: String,
    usePathText: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    val gotovoPath = remember {
        val parsed = PathParser().parsePathString(GOTOVO_PATH).toPath()
        Path().apply { addPath(parsed, Offset(-43.8f, -43.8f)) }
    }

    Box(
        modifier
            .size(177.dp, 55.dp)
            .graphicsLayer {
                val s = 1f + 0.05f * hover.value - 0.07f * press.value
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                // Тень из SVG-фильтра: spread −11, blur σ27.4, цвет rgba(0,231,76,0.22), без смещения.
                val inset = 11.dp.toPx()
                drawBlurredRoundRect(
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    corner = 14.dp.toPx(),
                    color = Color(0x3800E74C),
                    sigma = 27.4f.dp.toPx(),
                )
            }
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clip(RoundedCornerShape(25.dp))
            .background(colors.doneButtonBg)
            .border(4.dp, Color(0xFFA7E29E), RoundedCornerShape(25.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (usePathText) {
            Canvas(Modifier.fillMaxSize()) {
                val s = size.width / 177f
                withTransform({ scale(s, s, Offset.Zero) }) {
                    drawPath(gotovoPath, Color(0xFF343F4B))
                }
            }
        } else {
            BasicText(
                text,
                style = type.doneButton.copy(color = colors.onDoneButton),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        }
    }
}
