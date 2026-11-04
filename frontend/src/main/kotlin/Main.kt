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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import com.example.ApiClient
import com.example.ScoreResponse
import com.example.VolleyballSetScore
import kotlinx.coroutines.delay
import org.jetbrains.skia.Image
import ui.components.OvalsCanvas
import ui.components.SettingsButton
import ui.components.TennisBallIcon
import ui.components.VolleyballIcon
import ui.components.drawBlurredRoundRect
import ui.components.rememberOvalsAnimation
import ui.screens.BackArrowButton
import ui.screens.DoneButton
import ui.screens.FigmaText
import ui.screens.MenuScreen
import ui.screens.SettingsOverlay
import ui.screens.TeamSetupOverlay
import ui.theme.AppLanguage
import ui.theme.Brand
import ui.theme.DesignStage
import ui.theme.LocalStage
import ui.theme.ScoreKeeperTheme
import ui.theme.colorsFor
import ui.theme.stringsFor
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.system.exitProcess

fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

// ============ ЭКРАНЫ ============
enum class Screen { menu, tennis, valeball }

data class MatchState(
    val team1: String = "КОМАНДА 1",
    val team2: String = "КОМАНДА 2",
    val score1: Int = 0,
    val score2: Int = 0,
    val sets1: Int = 0,
    val sets2: Int = 0,
    val currentSet: Int = 1,
    val finished: Boolean = false,
    val status: String = "NOT_STARTED",
    val sport: String = "volleyball",
    val tennisPointsDisplay: List<String>? = null,
    val tennisGames: List<Int>? = null,
    val server: Int = 0
) {
    companion object {
        fun fromResponse(r: ScoreResponse): MatchState {
            val isTennis = r.sport == "tennis"
            return MatchState(
                team1 = r.player1,
                team2 = r.player2,
                score1 = if (isTennis) r.games?.getOrNull(0) ?: 0
                else r.sets?.getOrNull(0) ?: 0,
                score2 = if (isTennis) r.games?.getOrNull(1) ?: 0
                else r.sets?.getOrNull(1) ?: 0,
                sets1 = r.setsWon?.getOrNull(0) ?: 0,
                sets2 = r.setsWon?.getOrNull(1) ?: 0,
                currentSet = r.currentSet ?: (r.setsWon?.sum()?.plus(1) ?: 1),
                finished = r.isFinished,
                status = r.status,
                sport = r.sport,
                tennisPointsDisplay = r.pointsDisplay,
                tennisGames = r.games,
                server = r.server
            )
        }
    }
}

fun main() = application {
    var currentScreen by remember { mutableStateOf(Screen.menu) }
    var showSetupDialog by remember { mutableStateOf(false) }
    var selectedGame by remember { mutableStateOf(Screen.tennis) }

    var gameLogo1 by remember { mutableStateOf<ImageBitmap?>(null) }
    var gameName1 by remember { mutableStateOf("") }
    var gameLogo2 by remember { mutableStateOf<ImageBitmap?>(null) }
    var gameName2 by remember { mutableStateOf("") }

    var match by remember { mutableStateOf(MatchState()) }
    var showAudience by remember { mutableStateOf(false) }
    var tickerText by remember { mutableStateOf("") } // 👈 ТЕКСТ ДЛЯ ЗРИТЕЛЕЙ

    // Настройки приложения (экран «Настройки»): тема и язык.
    var darkTheme by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(AppLanguage.RU) }
    var showSettings by remember { mutableStateOf(false) }

    // Безрамочное окно на весь экран: Maximized + undecorated — верхней системной
    // панельки не видно, но это обычное окно (не эксклюзивный Fullscreen), поэтому
    // Alt+Tab работает мгновенно, а свернуть можно кнопкой «–» в меню или с панели задач.
    val windowState = rememberWindowState(placement = WindowPlacement.Maximized)

    Window(
        onCloseRequest = ::exitApplication, title = "ScoreKeeperApp",
        state = windowState, undecorated = true,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                Screen.menu -> MenuScreen(
                    onTennis = { selectedGame = Screen.tennis; showSetupDialog = true },
                    onVolleyball = { selectedGame = Screen.valeball; showSetupDialog = true },
                    onSettings = { showSettings = true },
                    onExit = { exitProcess(0) },
                    onMinimize = { windowState.isMinimized = true },
                    darkTheme = darkTheme,
                    language = language,
                )

                Screen.tennis, Screen.valeball -> VoleyballScreen(
                    sport = if (currentScreen == Screen.tennis) "tennis" else "volleyball",
                    onBack = { currentScreen = Screen.menu },
                    name1 = gameName1, name2 = gameName2, logo1 = gameLogo1, logo2 = gameLogo2,
                    match = match, onMatchUpdate = { match = it },
                    tickerText = tickerText, onTickerUpdate = { tickerText = it }, // 👈 ПЕРЕДАЕМ ТЕКСТ
                    darkTheme = darkTheme,
                    language = language,
                    onSettings = { showSettings = true }
                )
            }

            // Оверлеи поверх экрана: настройка команд/игроков и настройки приложения.
            if (showSetupDialog) {
                TeamSetupOverlay(
                    forTennis = selectedGame == Screen.tennis,
                    darkTheme = darkTheme,
                    language = language,
                    onPickImage = {
                        loadImageFromFile(
                            if (language == AppLanguage.RU) "Выберите логотип" else "Choose a logo"
                        )
                    },
                    onBack = { showSetupDialog = false },
                    onDone = { logo1, name1, logo2, name2 ->
                        gameLogo1 = logo1; gameName1 = name1; gameLogo2 = logo2; gameName2 = name2
                        showSetupDialog = false; currentScreen = selectedGame
                    },
                )
            }

            if (showSettings) {
                SettingsOverlay(
                    darkTheme = darkTheme,
                    onToggleTheme = { darkTheme = !darkTheme },
                    language = language,
                    onLanguageChange = { language = it },
                    onBack = { showSettings = false },
                )
            }
        }
    }

    if (showAudience) {
        Window(
            onCloseRequest = { showAudience = false },
            title = "ScoreKeeper - Табло",
            state = rememberWindowState(placement = WindowPlacement.Maximized),
            undecorated = true,
        ) {
            AudienceScreen(
                match = match,
                logo1 = gameLogo1,
                logo2 = gameLogo2,
                tickerText = tickerText
            ) // 👈 ПЕРЕДАЕМ ТЕКСТ
        }
    }
}

// ============ ЭКРАН МЕНЮ ============
// Главный экран — ui.screens.MenuScreen (вызывается прямо из main()):
// анимированный фон из капсул, стеклянная карточка, анимации кнопок.

// ============ УТИЛИТА ДЛЯ ЗАГРУЗКИ КАРТИНКИ ============
fun loadImageFromFile(title: String = "Выберите логотип"): ImageBitmap? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        name.endsWith(".png", ignoreCase = true) || name.endsWith(".jpg", ignoreCase = true) || name.endsWith(
            ".jpeg",
            ignoreCase = true
        )
    }
    dialog.isVisible = true

    val fileName = dialog.file
    val directory = dialog.directory

    if (fileName != null && directory != null) {
        return try {
            val file = File(directory, fileName)
            Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    return null
}

// ============ ОКНО НАСТРОЙКИ КОМАНД ============
// Теперь это оверлей поверх меню: ui.screens.TeamSetupOverlay (Overlays.kt),
// для тенниса — «ИГРОК 1/2», для волейбола — «КОМАНДА 1/2».

// ============ ЭКРАН АДМИНКИ ИГРЫ (дизайн Export-3) ============
// Вёрстка — в координатах макета 1920×1080 (DesignStage: 1 dp = 1 px макета).
// Логика (вызовы ApiClient, состояния диалогов, сторона поля) — прежняя,
// полностью перерисован только внешний вид под дизайн.
//
// ТАЙМЕР: идёт только вместе с игрой — пока статус матча IN_PROGRESS.
// Запускается стартом матча (жеребьёвка → startMatch), останавливается
// завершением или сбросом. Клик по таймеру ничего не делает,
// шестерёнка открывает настройки приложения.

private val StatusGray = Color(0xFF9C9E9C)
private val StatusGreen = Color(0xFF44C46E)
private val StatusRed = Color(0xFFEC6366)
private val CoinGold = Color(0xFFFFD95D)
private val CoinGoldDark = Color(0xFFC9992B)
private val CoinGray = Color(0xFF848484)
private val CoinRing = Color(0xFF9EC8E2)

/** Мягкая тень под карточками экрана матча (как в макете). */
private fun Modifier.cardShadow(corner: Dp): Modifier = drawBehind {
    drawBlurredRoundRect(
        topLeft = Offset(0f, 10.dp.toPx()),
        size = size,
        corner = corner.toPx(),
        color = Color.Black.copy(alpha = 0.10f),
        sigma = 9.dp.toPx(),
    )
}

/** Пружинный масштаб при наведении/нажатии (как у остальных кнопок приложения). */
@Composable
private fun rememberPressScale(interaction: MutableInteractionSource): Float {
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val hover = animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "btnHover",
    )
    val pressSpec: AnimationSpec<Float> =
        if (pressed) tween(80) else spring(dampingRatio = 0.45f, stiffness = 500f)
    val press = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "btnPress",
    )
    return 1f + 0.03f * hover.value - 0.05f * press.value
}

/** Круг 80×80 с логотипом команды (или серая заглушка из макета). */
@Composable
private fun TeamLogoCircle(logo: ImageBitmap?, modifier: Modifier = Modifier) {
    val colors = ScoreKeeperTheme.colors
    Box(
        modifier.size(80.dp).clip(CircleShape)
            .background(if (logo != null) Color.Transparent else colors.logoPlaceholder),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(
                bitmap = logo, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Светло-синяя пилюля со счётом («00»). */
@Composable
private fun ScorePill(
    value: String,
    modifier: Modifier = Modifier,
    width: Dp = 92.dp,
    height: Dp = 64.dp,
    fontSize: TextUnit = 36.sp,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    Box(
        modifier.size(width, height).clip(RoundedCornerShape(20.dp)).background(colors.pillBg),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            value,
            style = type.teamLabel.copy(fontSize = fontSize, color = colors.pillText),
            maxLines = 1, softWrap = false,
        )
    }
}

/** Плашка-вкладка на верхнем крае карточки («ОЧКИ», «ПАРТИЯ 1»). */
@Composable
private fun CardTab(text: String, modifier: Modifier = Modifier, width: Dp = 230.dp) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    Box(
        modifier.size(width, 52.dp).clip(RoundedCornerShape(26.dp)).background(colors.pillBg),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = type.teamLabel.copy(fontSize = 26.sp, color = colors.headingBlue),
            maxLines = 1, softWrap = false,
        )
    }
}

/** Зелёная кнопка «+1» (видна только по ходу матча). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlusOnePill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val type = ScoreKeeperTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        modifier.size(94.dp, 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(24.dp)).background(Brand.Green)
            .hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "+1",
            style = type.teamLabel.copy(fontSize = 28.sp, color = Color(0xFF37474D)),
            maxLines = 1, softWrap = false,
        )
    }
}

// ───────────────────────── Иконки кнопок ─────────────────────────

@Composable
private fun ResetIcon(color: Color) {
    Canvas(Modifier.size(34.dp)) {
        val sw = 4.dp.toPx()
        val r = size.minDimension / 2f - sw
        val c = Offset(size.width / 2f, size.height / 2f)
        drawArc(
            color, startAngle = -50f, sweepAngle = 285f, useCenter = false,
            topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r),
            style = Stroke(sw, cap = StrokeCap.Round),
        )
        val a = Math.toRadians(-50.0)
        val tip = Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())
        val head = Path().apply {
            moveTo(tip.x + 10.dp.toPx(), tip.y - 2.dp.toPx())
            lineTo(tip.x - 4.dp.toPx(), tip.y - 5.dp.toPx())
            lineTo(tip.x + 1.dp.toPx(), tip.y + 9.dp.toPx())
            close()
        }
        drawPath(head, color)
    }
}

@Composable
private fun UndoIcon(color: Color) {
    Canvas(Modifier.size(36.dp, 30.dp)) {
        val w = size.width
        val h = size.height
        val triW = w * 0.52f
        fun tri(x0: Float) = Path().apply {
            moveTo(x0 + triW, 0f); lineTo(x0 + triW, h); lineTo(x0, h / 2f); close()
        }
        drawPath(tri(0f), color)
        drawPath(tri(w - triW), color)
    }
}

@Composable
private fun SlidersIcon(color: Color, knobFill: Color) {
    Canvas(Modifier.size(36.dp, 30.dp)) {
        val w = size.width
        val h = size.height
        val sw = 3.6.dp.toPx()
        val rowsY = listOf(0.18f, 0.5f, 0.82f)
        val knobs = listOf(0.68f, 0.32f, 0.55f)
        rowsY.forEachIndexed { i, fy ->
            val y = h * fy
            drawLine(color, Offset(1.dp.toPx(), y), Offset(w - 1.dp.toPx(), y), sw, StrokeCap.Round)
            val kx = w * knobs[i]
            drawCircle(knobFill, 5.2.dp.toPx(), Offset(kx, y))
            drawCircle(color, 5.2.dp.toPx(), Offset(kx, y), style = Stroke(sw * 0.85f))
        }
    }
}

@Composable
private fun TimerIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(34.dp)) {
        val sw = 3.4.dp.toPx()
        val r = size.minDimension / 2f - sw - 1.dp.toPx()
        val c = Offset(size.width / 2f, size.height / 2f + 2.5.dp.toPx())
        drawCircle(color, r, c, style = Stroke(sw))
        drawLine(color, Offset(c.x, c.y - r - sw / 2f), Offset(c.x, c.y - r - sw / 2f - 4.dp.toPx()), sw, StrokeCap.Round)
        drawLine(
            color,
            Offset(c.x - 5.dp.toPx(), c.y - r - sw / 2f - 4.dp.toPx()),
            Offset(c.x + 5.dp.toPx(), c.y - r - sw / 2f - 4.dp.toPx()),
            sw, StrokeCap.Round,
        )
        drawLine(color, c, Offset(c.x, c.y - r * 0.55f), sw, StrokeCap.Round)
        drawLine(color, c, Offset(c.x + r * 0.45f, c.y), sw, StrokeCap.Round)
    }
}

// ───────────────────────── Кнопки экрана матча ─────────────────────────

/** Белая кнопка с синей обводкой: СБРОС / ОТКАТ / КОРРЕКТИРОВКА. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun OutlineActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .cardShadow(28.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.cardSurface)
            .border(3.dp, colors.headingBlue, RoundedCornerShape(28.dp))
            .hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            icon()
            BasicText(
                text,
                style = type.teamLabel.copy(fontSize = 26.sp, color = colors.headingBlue),
                maxLines = 1, softWrap = false,
            )
        }
    }
}

/** Главная градиентная кнопка. kind: 0 — ▶ начать, 1 — ■ завершить, 2 — ▶❚ продолжить. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MainMatchButton(
    text: String,
    kind: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                drawBlurredRoundRect(
                    topLeft = Offset(6.dp.toPx(), 12.dp.toPx()),
                    size = Size(size.width - 12.dp.toPx(), size.height - 8.dp.toPx()),
                    corner = 28.dp.toPx(),
                    color = colors.buttonGlow.copy(alpha = 0.45f),
                    sigma = 14.dp.toPx(),
                )
            }
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.horizontalGradient(listOf(colors.buttonStart, colors.buttonEnd)))
            .hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Canvas(Modifier.size(36.dp)) {
                val w = size.width
                val h = size.height
                when (kind) {
                    1 -> drawRoundRect(
                        Color.White,
                        Offset(w * 0.22f, h * 0.22f), Size(w * 0.56f, h * 0.56f),
                        CornerRadius(4.dp.toPx()),
                    )

                    2 -> {
                        val tri = Path().apply {
                            moveTo(w * 0.08f, h * 0.14f); lineTo(w * 0.08f, h * 0.86f)
                            lineTo(w * 0.58f, h * 0.5f); close()
                        }
                        drawPath(tri, Color.White)
                        drawRoundRect(
                            Color.White,
                            Offset(w * 0.68f, h * 0.14f), Size(w * 0.18f, h * 0.72f),
                            CornerRadius(3.dp.toPx()),
                        )
                    }

                    else -> {
                        val tri = Path().apply {
                            moveTo(w * 0.24f, h * 0.12f); lineTo(w * 0.24f, h * 0.88f)
                            lineTo(w * 0.86f, h * 0.5f); close()
                        }
                        drawPath(tri, Color.White)
                    }
                }
            }
            BasicText(
                text,
                style = type.button.copy(color = colors.onButton),
                maxLines = 1, softWrap = false,
            )
        }
    }
}

/** Пилюля «← НАЗАД» в верхней панели. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BackPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        modifier.size(173.dp, 49.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(24.dp))
            .background(colors.cardSurface)
            .border(2.dp, colors.outlineSoft, RoundedCornerShape(24.dp))
            .hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Canvas(Modifier.size(24.dp, 20.dp)) {
                val w = size.width
                val h = size.height
                val sw = 3.dp.toPx()
                val midY = h / 2f
                drawLine(colors.headingText, Offset(3.dp.toPx(), midY), Offset(w - 2.dp.toPx(), midY), sw, StrokeCap.Round)
                drawLine(colors.headingText, Offset(3.dp.toPx(), midY), Offset(11.dp.toPx(), 2.5.dp.toPx()), sw, StrokeCap.Round)
                drawLine(colors.headingText, Offset(3.dp.toPx(), midY), Offset(11.dp.toPx(), h - 2.5.dp.toPx()), sw, StrokeCap.Round)
            }
            BasicText(
                text,
                style = type.caption.copy(fontSize = 22.sp, color = colors.headingText),
                maxLines = 1, softWrap = false,
            )
        }
    }
}

/** Зелёная кнопка произвольной ширины (ПОДТВЕРДИТЬ / ВЫВЕСТИ) в стиле «ГОТОВО». */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GreenActionButton(
    text: String,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        modifier.size(width, 55.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val inset = 11.dp.toPx()
                drawBlurredRoundRect(
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    corner = 14.dp.toPx(),
                    color = Color(0x3800E74C),
                    sigma = 27.4f.dp.toPx(),
                )
            }
            .clip(RoundedCornerShape(25.dp))
            .background(colors.doneButtonBg)
            .border(4.dp, Color(0xFFA7E29E), RoundedCornerShape(25.dp))
            .hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = type.doneButton.copy(color = colors.onDoneButton),
            maxLines = 1, softWrap = false,
        )
    }
}

// ───────────────────────── Элементы диалогов ─────────────────────────

/** Монета подачи: золотая с «СК» — выбранная команда, серая — нет. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ServeCoin(
    selected: Boolean,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = ScoreKeeperTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        modifier.size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (selected) CoinGold else CoinGray)
            .border((size * 0.04f).coerceAtLeast(2.dp), CoinRing, CircleShape)
            .hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.fillMaxSize(0.8f).border(2.dp, Color(0xFFE3BC45), CircleShape))
            BasicText(
                "СК",
                style = type.teamLabel.copy(
                    fontSize = (size.value * 0.27f).sp,
                    color = CoinGoldDark,
                ),
                maxLines = 1, softWrap = false,
            )
        }
    }
}

/** Стрелка выбора стороны поля (жёлтая — выбранная, серая — нет). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SideArrow(
    toLeft: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Canvas(
        modifier.size(154.dp, 40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        val w = size.width
        val h = size.height
        val headW = h * 0.9f
        val shaftH = h * 0.36f
        val top = (h - shaftH) / 2f
        val path = Path()
        if (toLeft) {
            path.moveTo(0f, h / 2f)
            path.lineTo(headW, 0f); path.lineTo(headW, top)
            path.lineTo(w, top); path.lineTo(w, top + shaftH)
            path.lineTo(headW, top + shaftH); path.lineTo(headW, h)
            path.close()
        } else {
            path.moveTo(w, h / 2f)
            path.lineTo(w - headW, 0f); path.lineTo(w - headW, top)
            path.lineTo(0f, top); path.lineTo(0f, top + shaftH)
            path.lineTo(w - headW, top + shaftH); path.lineTo(w - headW, h)
            path.close()
        }
        drawPath(path, if (selected) CoinGold else CoinGray)
        drawPath(path, CoinRing, style = Stroke(3.dp.toPx()))
    }
}

/** Круглый индикатор L/R между стрелками. */
@Composable
private fun LrBadge(courtSide: String, modifier: Modifier = Modifier) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    Box(modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color.White, r, c)
            // серым залита невыбранная половина
            drawArc(
                Color(0xFFD9D9D9),
                startAngle = if (courtSide == "R") 90f else -90f,
                sweepAngle = 180f, useCenter = true,
                topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r),
            )
            drawCircle(CoinRing, r - 1.5.dp.toPx(), c, style = Stroke(3.dp.toPx()))
        }
        Row {
            BasicText(
                "L",
                style = type.caption.copy(
                    fontSize = 19.sp,
                    color = if (courtSide == "L") colors.headingBlue else Color(0xFF8A8A8A),
                ),
                maxLines = 1, softWrap = false,
            )
            BasicText(
                "R",
                style = type.caption.copy(
                    fontSize = 19.sp,
                    color = if (courtSide == "R") colors.headingBlue else Color(0xFF8A8A8A),
                ),
                maxLines = 1, softWrap = false,
            )
        }
    }
}

/** Пилюля-поле для цифр в корректировке («00»). */
@Composable
private fun NumPillField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    Box(
        modifier.size(88.dp, 53.dp).clip(RoundedCornerShape(16.dp)).background(colors.pillBg),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { v -> onValueChange(v.filter { it.isDigit() }.take(3)) },
            singleLine = true,
            textStyle = type.teamLabel.copy(
                fontSize = 30.sp, color = colors.pillText, textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(colors.pillText),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        )
    }
}

/** Общая «рамка» диалогов матча: затемнение + карточка 792×H по центру + стрелка «назад». */
@Composable
private fun MatchDialogScaffold(
    cardHeight: Dp,
    onBack: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = ScoreKeeperTheme.colors
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(160)) }
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f * appear.value))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* клики по затемнению не проходят в экран */ },
    ) {
        Box(Modifier.align(Alignment.Center).requiredSize(1920.dp, 1080.dp)) {
            Box(
                Modifier.align(Alignment.Center).size(792.dp, cardHeight)
                    .graphicsLayer {
                        val s = 0.97f + 0.03f * appear.value
                        scaleX = s; scaleY = s; alpha = appear.value
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

// ───────────────────────── Диалоги ─────────────────────────

@Composable
private fun TossDialogOverlay(
    name1: String,
    name2: String,
    onBack: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    val strings = ScoreKeeperTheme.strings
    var serverSel by remember { mutableStateOf(0) }
    MatchDialogScaffold(cardHeight = 580.dp, onBack = onBack) {
        FigmaText(
            strings.tossTitle, type.heading, colors.headingBlue,
            Modifier.offset(159.dp, 34.dp), TextAlign.Center, 475.dp,
        )
        Box(
            Modifier.offset(18.dp, 143.dp).size(360.dp, 44.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                name1, style = type.teamLabel.copy(color = colors.headingBlue),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.offset(414.dp, 143.dp).size(360.dp, 44.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                name2, style = type.teamLabel.copy(color = colors.headingBlue),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.offset(395.dp, 133.dp).size(2.dp, 314.dp).background(colors.divider))
        ServeCoin(serverSel == 0, 128.dp, onClick = { serverSel = 0 }, modifier = Modifier.offset(134.dp, 274.dp))
        ServeCoin(serverSel == 1, 128.dp, onClick = { serverSel = 1 }, modifier = Modifier.offset(530.dp, 274.dp))
        GreenActionButton(
            strings.confirm, 324.dp,
            onClick = { onConfirm(serverSel) },
            modifier = Modifier.offset(234.dp, 503.dp),
        )
    }
}

@Composable
private fun TextDialogOverlay(
    initial: String,
    onBack: () -> Unit,
    onDisplay: (String) -> Unit,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    val strings = ScoreKeeperTheme.strings
    var textInput by remember { mutableStateOf(initial) }
    MatchDialogScaffold(cardHeight = 392.dp, onBack = onBack) {
        FigmaText(
            strings.enterTextTitle,
            type.cardTitle.copy(fontSize = 30.sp, lineHeight = 38.sp),
            colors.headingBlue,
            Modifier.offset(76.dp, 37.dp), TextAlign.Center, 640.dp,
        )
        Box(
            Modifier.offset(100.dp, 190.dp).size(593.dp, 52.dp)
                .clip(RoundedCornerShape(18.dp)).background(colors.fieldBg)
                .border(3.dp, Color(0xFF9EC8E2), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (textInput.isEmpty()) {
                Canvas(Modifier.fillMaxSize()) {
                    for (cx in listOf(25f, 39f, 52f)) {
                        drawCircle(
                            Color(0xFFDDDDDD), radius = 5.dp.toPx(),
                            center = Offset(cx.dp.toPx(), 36.dp.toPx()),
                        )
                    }
                }
            }
            BasicTextField(
                value = textInput,
                onValueChange = { textInput = it },
                singleLine = true,
                textStyle = type.field.copy(color = colors.fieldText),
                cursorBrush = SolidColor(colors.fieldText),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
        }
        GreenActionButton(
            strings.display, 210.dp,
            onClick = { onDisplay(textInput) },
            modifier = Modifier.offset(291.dp, 317.dp),
        )
    }
}

@Composable
private fun CorrectionDialogOverlay(
    match: MatchState,
    name1: String,
    name2: String,
    courtSide: String,
    usePathText: Boolean,
    onCourtSideChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: (score1: Int, score2: Int, sets1: Int, sets2: Int, server: Int) -> Unit,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    val strings = ScoreKeeperTheme.strings
    var t1Score by remember { mutableStateOf(match.score1.toString()) }
    var t2Score by remember { mutableStateOf(match.score2.toString()) }
    var t1Sets by remember { mutableStateOf(match.sets1.toString()) }
    var t2Sets by remember { mutableStateOf(match.sets2.toString()) }
    var serverSel by remember { mutableStateOf(match.server) }

    MatchDialogScaffold(cardHeight = 596.dp, onBack = onBack) {
        FigmaText(
            strings.correction, type.heading, colors.headingBlue,
            Modifier.offset(159.dp, 12.dp), TextAlign.Center, 475.dp,
        )
        // команды и монеты подающего
        Box(
            Modifier.offset(73.dp, 80.dp).size(200.dp, 40.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                name1, style = type.teamLabel.copy(color = colors.headingBlue),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        ServeCoin(serverSel == 0, 50.dp, onClick = { serverSel = 0 }, modifier = Modifier.offset(282.dp, 74.dp))
        Box(
            Modifier.offset(455.dp, 80.dp).size(175.dp, 40.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                name2, style = type.teamLabel.copy(color = colors.headingBlue),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        ServeCoin(serverSel == 1, 50.dp, onClick = { serverSel = 1 }, modifier = Modifier.offset(662.dp, 74.dp))

        Box(Modifier.offset(395.dp, 80.dp).size(2.dp, 313.dp).background(colors.divider))

        // очки и партии
        val labelStyle = type.teamLabel.copy(fontSize = 36.sp, color = colors.captionText)
        BasicText(strings.pointsTab, style = labelStyle, modifier = Modifier.offset(73.dp, 175.dp), maxLines = 1, softWrap = false)
        BasicText(strings.pointsTab, style = labelStyle, modifier = Modifier.offset(455.dp, 175.dp), maxLines = 1, softWrap = false)
        BasicText(strings.setsLabel, style = labelStyle, modifier = Modifier.offset(73.dp, 275.dp), maxLines = 1, softWrap = false)
        BasicText(strings.setsLabel, style = labelStyle, modifier = Modifier.offset(455.dp, 275.dp), maxLines = 1, softWrap = false)
        NumPillField(t1Score, { t1Score = it }, Modifier.offset(257.dp, 170.dp))
        NumPillField(t2Score, { t2Score = it }, Modifier.offset(647.dp, 170.dp))
        NumPillField(t1Sets, { t1Sets = it }, Modifier.offset(257.dp, 270.dp))
        NumPillField(t2Sets, { t2Sets = it }, Modifier.offset(647.dp, 270.dp))

        // сторона поля подающей команды
        FigmaText(
            strings.courtSideLabel,
            type.caption.copy(fontSize = 22.sp, lineHeight = 27.sp),
            colors.headingBlue,
            Modifier.offset(159.dp, 408.dp), TextAlign.Center, 475.dp,
        )
        SideArrow(
            toLeft = true, selected = courtSide == "L",
            onClick = { onCourtSideChange("L") },
            modifier = Modifier.offset(203.dp, 462.dp),
        )
        LrBadge(courtSide, Modifier.offset(374.dp, 460.dp))
        SideArrow(
            toLeft = false, selected = courtSide == "R",
            onClick = { onCourtSideChange("R") },
            modifier = Modifier.offset(435.dp, 462.dp),
        )

        DoneButton(
            text = strings.done,
            usePathText = usePathText,
            onClick = {
                onSubmit(
                    t1Score.toIntOrNull() ?: 0,
                    t2Score.toIntOrNull() ?: 0,
                    t1Sets.toIntOrNull() ?: 0,
                    t2Sets.toIntOrNull() ?: 0,
                    serverSel,
                )
            },
            modifier = Modifier.offset(307.dp, 528.dp),
        )
    }
}

// ───────────────────────── Сам экран ─────────────────────────

@Composable
fun VoleyballScreen(
    sport: String,
    onBack: () -> Unit,
    name1: String,
    name2: String,
    logo1: ImageBitmap?,
    logo2: ImageBitmap?,
    match: MatchState,
    onMatchUpdate: (MatchState) -> Unit,
    tickerText: String,
    onTickerUpdate: (String) -> Unit,
    darkTheme: Boolean,
    language: AppLanguage,
    onSettings: () -> Unit
) {
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var secondsElapsed by remember { mutableStateOf(0) }
    var showTossDialog by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showCorrectionDialog by remember { mutableStateOf(false) }
    var courtSide by remember { mutableStateOf("L") } // Сторона L/R

    LaunchedEffect(errorMsg) {
        if (errorMsg != null) {
            delay(3000L); errorMsg = null
        }
    }

    // Таймер идёт только вместе с игрой: старт — со стартом матча (IN_PROGRESS),
    // стоп — при завершении/сбросе. Считаем от метки времени (без накопления ошибки).
    LaunchedEffect(match.status) {
        if (match.status == "IN_PROGRESS") {
            val base = secondsElapsed
            val startedAt = System.currentTimeMillis()
            while (true) {
                secondsElapsed = base + ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
                delay(200L)
            }
        }
    }

    val apply: (ScoreResponse?) -> Unit = { r -> if (r != null) onMatchUpdate(MatchState.fromResponse(r)) }

    val startMatchAction = { showTossDialog = true }
    val endMatchAction = { ApiClient.forceEnd(onResult = apply, onError = { errorMsg = it }) }

    ScoreKeeperTheme(colors = colorsFor(darkTheme), strings = stringsFor(language)) {
        val colors = ScoreKeeperTheme.colors
        val type = ScoreKeeperTheme.typography
        val strings = ScoreKeeperTheme.strings

        DesignStage {
            val stage = LocalStage.current
            val ovals = rememberOvalsAnimation()

            val isTennis = sport == "tennis"
            val defaultName1 = if (isTennis) strings.player1 else strings.team1
            val defaultName2 = if (isTennis) strings.player2 else strings.team2
            val displayName1 = name1.ifBlank { defaultName1 }.uppercase()
            val displayName2 = name2.ifBlank { defaultName2 }.uppercase()
            val statusText = when (match.status) {
                "FINISHED" -> strings.statusFinished
                "NOT_STARTED" -> strings.statusNotStarted
                else -> strings.statusInProgress
            }
            val statusColor = when (match.status) {
                "FINISHED" -> StatusRed
                "NOT_STARTED" -> StatusGray
                else -> StatusGreen
            }

            // Фон: пастельные пятна из макета — размытые овалы меню под белой вуалью.
            Box(Modifier.fillMaxSize().background(colors.canvas))
            OvalsCanvas(
                ovals,
                Offset(-stage.originX.value, -stage.originY.value),
                Modifier.fillMaxSize(),
                glass = true,
            )
            Box(
                Modifier.fillMaxSize()
                    .background(colors.canvas.copy(alpha = if (darkTheme) 0.55f else 0.74f)),
            )

            Box(Modifier.align(Alignment.Center).requiredSize(1920.dp, 1080.dp)) {

                // ── Предпросмотр табло зрителей ──
                Box(
                    Modifier.offset(90.dp, 80.dp).size(1100.dp, 694.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .border(5.dp, colors.previewBorder, RoundedCornerShape(30.dp)),
                ) {
                    // Табло нарисовано на 950×615 — масштабируем под панель предпросмотра.
                    Box(
                        Modifier.requiredSize(950.dp, 615.dp)
                            .graphicsLayer {
                                scaleX = 1100f / 950f
                                scaleY = 694f / 615f
                                transformOrigin = TransformOrigin(0f, 0f)
                            },
                    ) {
                        AudienceScreen(
                            match = match,
                            logo1 = logo1,
                            logo2 = logo2,
                            tickerText = tickerText,
                            courtSide = courtSide,
                        )
                    }

                    Row(
                        Modifier.offset(18.dp, 18.dp).height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFF1B2A44).copy(alpha = 0.85f))
                            .padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFFF3B30)))
                        BasicText(
                            strings.preview,
                            style = type.caption.copy(color = Color.White),
                            maxLines = 1, softWrap = false,
                        )
                    }

                    Row(
                        Modifier.align(Alignment.TopEnd).offset(x = (-18).dp, y = 18.dp).height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFF1B2A44).copy(alpha = 0.85f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { showTextDialog = true }
                            .padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            strings.displayTextBtn,
                            style = type.caption.copy(color = Color.White),
                            maxLines = 1, softWrap = false,
                        )
                    }
                }

                // ── Нижняя левая карточка: партии ──
                Box(
                    Modifier.offset(90.dp, 816.dp).size(1100.dp, 200.dp)
                        .cardShadow(36.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(colors.cardSurface),
                ) {
                    TeamLogoCircle(logo1, Modifier.offset(48.dp, 8.dp))
                    TeamLogoCircle(logo2, Modifier.offset(48.dp, 112.dp))
                    Box(
                        Modifier.offset(156.dp, 8.dp).size(720.dp, 80.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicText(
                            displayName1, style = type.teamLabel.copy(color = colors.headingBlue),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier.offset(156.dp, 112.dp).size(720.dp, 80.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicText(
                            displayName2, style = type.teamLabel.copy(color = colors.headingBlue),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(Modifier.offset(48.dp, 99.dp).size(974.dp, 2.dp).background(colors.divider))
                    ScorePill("%02d".format(match.sets1), Modifier.offset(934.dp, 20.dp), width = 88.dp, height = 56.dp, fontSize = 30.sp)
                    ScorePill("%02d".format(match.sets2), Modifier.offset(934.dp, 124.dp), width = 88.dp, height = 56.dp, fontSize = 30.sp)
                }
                CardTab("${strings.setTab} ${match.currentSet}", Modifier.offset(525.dp, 790.dp))

                // ── Верхняя панель: таймер (не кликабелен), настройки, назад ──
                TimerIcon(colors.headingText, Modifier.offset(1356.dp, 36.dp))
                BasicText(
                    formatTime(secondsElapsed),
                    style = type.caption.copy(fontSize = 22.sp, color = colors.headingText),
                    modifier = Modifier.offset(1399.dp, 41.dp),
                    maxLines = 1, softWrap = false,
                )
                SettingsButton(onClick = onSettings, modifier = Modifier.offset(1536.dp, 27.dp))
                BackPill(strings.back, onBack, Modifier.offset(1634.dp, 28.dp))

                // ── Заголовок вида спорта ──
                if (isTennis) {
                    TennisBallIcon(Modifier.offset(1273.dp, 156.dp).size(112.dp))
                } else {
                    VolleyballIcon(Modifier.offset(1273.dp, 156.dp).size(112.dp))
                }
                BasicText(
                    if (isTennis) strings.tennis else strings.volleyball,
                    style = type.heading.copy(fontSize = 54.sp, color = colors.headingText),
                    modifier = Modifier.offset(1428.dp, 150.dp),
                    maxLines = 1, softWrap = false,
                )
                BasicText(
                    strings.matchScore,
                    style = type.heading.copy(fontSize = 30.sp, color = colors.subText),
                    modifier = Modifier.offset(1430.dp, 218.dp),
                    maxLines = 1, softWrap = false,
                )

                // ── Карточка счёта ──
                Box(
                    Modifier.offset(1264.dp, 319.dp).size(596.dp, 335.dp)
                        .cardShadow(36.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(colors.cardSurface),
                ) {
                    TeamLogoCircle(logo1, Modifier.offset(31.dp, 33.dp))
                    TeamLogoCircle(logo2, Modifier.offset(31.dp, 175.dp))
                    val nameWidth = if (match.status == "IN_PROGRESS") 232.dp else 340.dp
                    Box(
                        Modifier.offset(125.dp, 33.dp).size(nameWidth, 80.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicText(
                            displayName1, style = type.teamLabel.copy(color = colors.headingBlue),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier.offset(125.dp, 175.dp).size(nameWidth, 80.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicText(
                            displayName2, style = type.teamLabel.copy(color = colors.headingBlue),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (match.status == "IN_PROGRESS") {
                        PlusOnePill(
                            onClick = { ApiClient.addPoint(0, apply) { errorMsg = it } },
                            modifier = Modifier.offset(366.dp, 49.dp),
                        )
                        PlusOnePill(
                            onClick = { ApiClient.addPoint(1, apply) { errorMsg = it } },
                            modifier = Modifier.offset(366.dp, 191.dp),
                        )
                    }
                    Box(Modifier.offset(31.dp, 143.dp).size(537.dp, 2.dp).background(colors.divider))
                    match.tennisPointsDisplay?.let { pts ->
                        Box(
                            Modifier.offset(0.dp, 128.dp).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                "${pts.getOrElse(0) { "" }} : ${pts.getOrElse(1) { "" }}",
                                style = type.teamLabel.copy(fontSize = 26.sp, color = colors.headingBlue),
                                modifier = Modifier.background(colors.cardSurface)
                                    .padding(horizontal = 16.dp),
                                maxLines = 1, softWrap = false,
                            )
                        }
                    }
                    ScorePill("%02d".format(match.score1), Modifier.offset(476.dp, 41.dp))
                    ScorePill("%02d".format(match.score2), Modifier.offset(476.dp, 183.dp))
                    Box(
                        Modifier.offset(189.dp, 273.dp).size(218.dp, 52.dp)
                            .clip(RoundedCornerShape(26.dp)).background(colors.pillBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(statusColor))
                            BasicText(
                                statusText,
                                style = type.caption.copy(fontSize = 22.sp, color = statusColor),
                                maxLines = 1, softWrap = false,
                            )
                        }
                    }
                }
                CardTab(strings.pointsTab, Modifier.offset(1455.dp, 292.dp), width = 213.dp)

                // ── Кнопки управления ──
                if (match.status != "NOT_STARTED") {
                    OutlineActionButton(
                        text = strings.reset,
                        onClick = {
                            secondsElapsed = 0
                            ApiClient.reset(apply) { errorMsg = it }
                        },
                        modifier = Modifier.offset(1322.dp, 686.dp).size(241.dp, 90.dp),
                    ) { ResetIcon(colors.headingBlue) }
                    OutlineActionButton(
                        text = strings.undo,
                        onClick = { ApiClient.removePoint(apply) { errorMsg = it } },
                        modifier = Modifier.offset(1579.dp, 686.dp).size(241.dp, 90.dp),
                    ) { UndoIcon(colors.headingBlue) }
                    OutlineActionButton(
                        text = strings.correction,
                        onClick = { showCorrectionDialog = true },
                        modifier = Modifier.offset(1322.dp, 797.dp).size(498.dp, 95.dp),
                    ) { SlidersIcon(colors.headingBlue, colors.cardSurface) }
                }
                MainMatchButton(
                    text = when (match.status) {
                        "NOT_STARTED" -> strings.startMatch
                        "IN_PROGRESS" -> strings.endMatch
                        else -> strings.continueMatch
                    },
                    kind = when (match.status) {
                        "NOT_STARTED" -> 0
                        "IN_PROGRESS" -> 1
                        else -> 2
                    },
                    onClick = { if (match.status == "IN_PROGRESS") endMatchAction() else startMatchAction() },
                    modifier = Modifier.offset(1322.dp, 914.dp).size(498.dp, 114.dp),
                )

                // ОШИБКА
                errorMsg?.let { msg ->
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                            .background(Color(0xCCB00020), RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        BasicText(msg, style = TextStyle(color = Color.White, fontSize = 16.sp))
                    }
                }
            }

            // ── Диалоги (оверлеи поверх экрана) ──
            if (showTossDialog) {
                TossDialogOverlay(
                    name1 = displayName1,
                    name2 = displayName2,
                    onBack = { showTossDialog = false },
                    onConfirm = { server ->
                        ApiClient.startMatch(
                            sport,
                            name1.ifBlank { defaultName1 },
                            name2.ifBlank { defaultName2 },
                            server,
                            onResult = { r ->
                                if (r != null) {
                                    secondsElapsed = 0
                                    onMatchUpdate(MatchState.fromResponse(r))
                                    showTossDialog = false
                                }
                            },
                            onError = { errorMsg = it },
                        )
                    },
                )
            }

            if (showTextDialog) {
                TextDialogOverlay(
                    initial = tickerText,
                    onBack = { showTextDialog = false },
                    onDisplay = { text ->
                        onTickerUpdate(text)
                        ApiClient.sendText(text, onResult = {})
                        showTextDialog = false
                    },
                )
            }

            if (showCorrectionDialog) {
                CorrectionDialogOverlay(
                    match = match,
                    name1 = displayName1,
                    name2 = displayName2,
                    courtSide = courtSide,
                    usePathText = language == AppLanguage.RU,
                    onCourtSideChange = { courtSide = it },
                    onBack = { showCorrectionDialog = false },
                    onSubmit = { s1, s2, sets1, sets2, server ->
                        ApiClient.setScore(
                            VolleyballSetScore(
                                sets = listOf(s1, s2),
                                setsWon = listOf(sets1, sets2),
                                currentSet = match.currentSet,
                                server = server,
                                status = match.status,
                                winner = match.score1,
                            ),
                            onResult = apply,
                        )
                        showCorrectionDialog = false
                    },
                )
            }
        }
    }
}

@Composable
fun AudienceScreen(
    match: MatchState,
    logo1: ImageBitmap?,
    logo2: ImageBitmap?,
    modifier: Modifier = Modifier,
    tickerText: String = "",
    courtSide: String = "L"
) {
    var live by remember { mutableStateOf(match) }

    DisposableEffect(Unit) {
        ApiClient.connectScoreWs(onScore = { r -> live = MatchState.fromResponse(r) }, onText = { })
        onDispose { ApiClient.disconnectWs() }
    }

    Box(modifier = modifier.size(width = 950.dp, height = 615.dp)) {
        Image(
            painter = painterResource("adminscreen.png"),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        // ЛЕВАЯ КОМАНДА
        Box(
            modifier = Modifier.offset(x = 170.dp, y = 110.dp).size(110.dp).clip(CircleShape)
                .background(Color(0xFFE0E0E0)), contentAlignment = Alignment.Center
        ) {
            if (logo1 != null) Image(
                bitmap = logo1,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Шарик подачи
        Box(
            modifier = Modifier.offset(x = if (live.server == 0) 255.dp else 720.dp, y = 195.dp).size(20.dp)
                .clip(CircleShape).background(Color(0xFFFFC107))
        )

        Text(
            live.team1.uppercase(),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.offset(x = 150.dp, y = 230.dp)
        )
        Text(
            "${live.sets1}",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 28.sp,
            modifier = Modifier.offset(x = 195.dp, y = 270.dp)
        )
        Text(
            "ПАРТИИ",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            lineHeight = 14.sp,
            modifier = Modifier.offset(x = 185.dp, y = 300.dp)
        )
        Text(
            "${live.score1}",
            color = Color.White,
            fontSize = 90.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 90.sp,
            modifier = Modifier.offset(x = 175.dp, y = 350.dp)
        )
        Text(
            "ОЧКИ",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.offset(x = 175.dp, y = 450.dp)
        )

        // ЦЕНТР
        Text(
            "ПАРТИЯ ${live.currentSet}",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = 425.dp, y = 460.dp)
        )

        // Индикатор L/R
        // Индикатор L/R
        Row(
            modifier = Modifier.offset(x = 450.dp, y = 320.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("L", color = if (courtSide == "L") Color.Yellow else Color.White, fontWeight = FontWeight.Bold, fontSize = 36.sp)
            Text("R", color = if (courtSide == "R") Color.Yellow else Color.White, fontWeight = FontWeight.Bold, fontSize = 36.sp)
        }

        // ПРАВАЯ КОМАНДА
        Box(
            modifier = Modifier.offset(x = 635.dp, y = 110.dp).size(110.dp).clip(CircleShape)
                .background(Color(0xFFE0E0E0)), contentAlignment = Alignment.Center
        ) {
            if (logo2 != null) Image(
                bitmap = logo2,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Text(
            live.team2.uppercase(),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.offset(x = 615.dp, y = 230.dp)
        )
        Text(
            "${live.sets2}",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 28.sp,
            modifier = Modifier.offset(x = 660.dp, y = 270.dp)
        )
        Text(
            "ПАРТИИ",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            lineHeight = 14.sp,
            modifier = Modifier.offset(x = 650.dp, y = 300.dp)
        )
        Text(
            "${live.score2}",
            color = Color.White,
            fontSize = 90.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 90.sp,
            modifier = Modifier.offset(x = 640.dp, y = 350.dp)
        )
        Text(
            "ОЧКИ",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.offset(x = 640.dp, y = 450.dp)
        )

        // ТЕКСТ НА ТАБЛО
        if (tickerText.isNotBlank()) {
            Text(
                tickerText, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}
