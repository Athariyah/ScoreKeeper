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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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
import ui.theme.AppStrings
import ui.theme.RuStrings
import ui.theme.Brand
import ui.theme.DesignStage
import ui.theme.LocalStage
import ui.theme.ScoreKeeperTheme
import ui.theme.colorsFor
import ui.theme.stringsFor
import java.io.File
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
    val server: Int = 0,
    val winner: Int? = null
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
                server = r.server,
                winner = r.winner
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
        icon = painterResource("app_icon.png"),
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
            icon = painterResource("app_icon.png"),
            state = rememberWindowState(placement = WindowPlacement.Maximized),
            undecorated = true,
        ) {
            // Дизайн «Экран предпросмотра»: табло по центру на чёрном, свечение σ≈111.
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val fitScale = minOf(maxWidth.value / 1575f, maxHeight.value / 1144f)
                    val baseDensity = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(baseDensity.density * fitScale, 1f),
                    ) {
                        Box(Modifier.align(Alignment.Center).requiredSize(1575.dp, 1144.dp)) {
                            Canvas(Modifier.fillMaxSize()) {
                                val u = 1.dp.toPx()
                                drawBlurredRoundRect(
                                    topLeft = Offset(229.7f * u, 229.7f * u),
                                    size = Size(1115f * u, 684f * u),
                                    corner = 40f * u,
                                    color = Color(0xFF91C0DC),
                                    sigma = 111.35f * u,
                                )
                            }
                            AudienceScreen(
                                match = match,
                                logo1 = gameLogo1,
                                logo2 = gameLogo2,
                                modifier = Modifier.offset(223.dp, 223.dp),
                                tickerText = tickerText,
                                strings = stringsFor(language),
                            ) // 👈 ПЕРЕДАЕМ ТЕКСТ
                        }
                    }
                }
            }
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
// Векторные пути — один в один из присланных SVG кнопок и табло.

private const val ICON_RESET = "M69.6977 102.52C70.6976 104.817 72.0474 106.816 73.7471 108.515C75.4468 110.215 77.4451 111.565 79.7422 112.565C82.0393 113.565 84.5026 114.064 87.1321 114.064C87.7281 114.064 88.2273 113.863 88.6298 113.459C89.0323 113.055 89.2342 112.555 89.2356 111.961C89.237 111.366 89.0351 110.866 88.6298 110.461C88.2245 110.056 87.7253 109.855 87.1321 109.857C83.0301 109.857 79.5501 108.428 76.6921 105.57C73.834 102.712 72.4057 99.2329 72.4071 95.1324C72.4085 91.0318 73.8368 87.5518 76.6921 84.6924C79.5473 81.8329 83.0273 80.4046 87.1321 80.4074C91.2368 80.4102 94.7161 81.8386 97.5699 84.6924C100.424 87.5462 101.853 91.0262 101.857 95.1324V95.4479L100.069 93.6599C99.6483 93.2392 99.1574 93.0372 98.5965 93.0541C98.0355 93.0709 97.5447 93.2728 97.124 93.6599C96.7033 94.0806 96.4838 94.5798 96.4656 95.1576C96.4473 95.7354 96.6493 96.2353 97.0714 96.6575L102.488 102.074C102.909 102.495 103.4 102.705 103.961 102.705C104.522 102.705 105.012 102.495 105.433 102.074L110.85 96.6575C111.27 96.2367 111.472 95.7368 111.456 95.1576C111.439 94.5784 111.219 94.0792 110.797 93.6599C110.376 93.2742 109.886 93.0723 109.325 93.0541C108.764 93.0358 108.273 93.2378 107.852 93.6599L106.064 95.4479V95.1324C106.064 92.5029 105.564 90.0396 104.564 87.7426C103.564 85.4455 102.215 83.4471 100.515 81.7474C98.8153 80.0477 96.8169 78.6979 94.5198 77.698C92.2227 76.6981 89.7601 76.1989 87.1321 76.2003C84.504 76.2017 82.0407 76.7009 79.7422 77.698C77.4437 78.6951 75.4454 80.0449 73.7471 81.7474C72.0488 83.4499 70.699 85.4483 69.6977 87.7426C68.6964 90.0368 68.1972 92.5001 68.2 95.1324C68.2028 97.7646 68.702 100.227 69.6977 102.52Z"
private const val ICON_UNDO = "M109.34 107.276V83.1237C109.34 79.1211 105.872 76.591 103.05 78.5349L89.8407 88.1983V84.6333C89.8407 81.131 86.6615 78.9171 84.0747 80.618L68.0036 91.1848C65.3419 92.9348 65.3419 97.4653 68.0036 99.2153L84.0747 109.782C86.6615 111.483 89.8407 109.269 89.8407 105.767V102.202L103.05 111.865C105.872 113.809 109.34 111.279 109.34 107.276Z"
private const val ICON_CORRECTION = "M137.783 101.7C137.169 101.7 136.655 101.492 136.241 101.076C135.826 100.66 135.618 100.146 135.617 99.5337V90.867C135.617 90.2531 135.825 89.7389 136.241 89.3244C136.657 88.9098 137.171 88.7018 137.783 88.7004H143.2C143.814 88.7004 144.329 88.9084 144.745 89.3244C145.161 89.7404 145.368 90.2546 145.367 90.867V99.5337C145.367 100.148 145.159 100.663 144.743 101.079C144.327 101.495 143.812 101.702 143.2 101.7H137.783ZM138.867 98.4504H142.117V91.9504H138.867V98.4504ZM118.283 98.4504H123.158C123.628 98.4504 124.016 98.6042 124.324 98.9119C124.632 99.2195 124.785 99.6074 124.783 100.075C124.782 100.543 124.628 100.932 124.322 101.241C124.015 101.55 123.628 101.703 123.158 101.7H117.2C116.586 101.7 116.072 101.492 115.657 101.076C115.243 100.66 115.035 100.146 115.033 99.5337V96.2837C115.033 95.6698 115.241 95.1556 115.657 94.741C116.073 94.3265 116.587 94.1185 117.2 94.117H121.533V91.9504H116.658C116.189 91.9504 115.801 91.7965 115.495 91.4889C115.188 91.1812 115.035 90.7934 115.033 90.3254C115.032 89.8574 115.186 89.4695 115.495 89.1619C115.804 88.8542 116.192 88.7004 116.658 88.7004H122.617C123.23 88.7004 123.745 88.9084 124.161 89.3244C124.577 89.7404 124.785 90.2546 124.783 90.867V94.117C124.783 94.7309 124.575 95.2459 124.159 95.6619C123.743 96.0779 123.229 96.2851 122.617 96.2837H118.283V98.4504ZM112.867 112.534C111.675 112.534 110.655 112.11 109.807 111.262C108.959 110.414 108.535 109.393 108.533 108.2V82.2004C108.533 81.0087 108.958 79.9889 109.807 79.141C110.657 78.2931 111.676 77.8685 112.867 77.867H119.367V75.7004C119.367 75.0865 119.575 74.5723 119.991 74.1577C120.407 73.7431 120.921 73.5351 121.533 73.5337C122.146 73.5323 122.661 73.7403 123.078 74.1577C123.495 74.5751 123.703 75.0894 123.7 75.7004V77.867H136.7V75.7004C136.7 75.0865 136.908 74.5723 137.324 74.1577C137.74 73.7431 138.254 73.5351 138.867 73.5337C139.479 73.5323 139.994 73.7403 140.411 74.1577C140.829 74.5751 141.036 75.0894 141.033 75.7004V77.867H147.533C148.725 77.867 149.745 78.2917 150.595 79.141C151.444 79.9904 151.868 81.0101 151.867 82.2004V108.2C151.867 109.392 151.443 110.413 150.595 111.262C149.747 112.111 148.726 112.535 147.533 112.534H112.867ZM112.867 108.2H128.575V106.575C128.575 106.106 128.729 105.718 129.036 105.412C129.344 105.106 129.732 104.952 130.2 104.95C130.668 104.949 131.056 105.103 131.366 105.412C131.675 105.721 131.828 106.109 131.825 106.575V108.2H147.533V82.2004H131.825V83.8254C131.825 84.2948 131.671 84.6834 131.363 84.991C131.056 85.2987 130.668 85.4518 130.2 85.4504C129.732 85.4489 129.344 85.2951 129.036 84.9889C128.729 84.6826 128.575 84.2948 128.575 83.8254V82.2004H112.867V108.2ZM129.036 92.5744C128.729 92.2667 128.575 91.8781 128.575 91.4087C128.575 90.9393 128.729 90.5514 129.036 90.2452C129.344 89.939 129.732 89.7851 130.2 89.7837C130.668 89.7823 131.056 89.9361 131.366 90.2452C131.675 90.5543 131.828 90.9421 131.825 91.4087C131.822 91.8753 131.668 92.2638 131.363 92.5744C131.059 92.8849 130.671 93.038 130.2 93.0337C129.729 93.0294 129.341 92.8755 129.036 92.5722M129.036 100.156C128.729 99.8493 128.575 99.4615 128.575 98.992C128.575 98.5226 128.729 98.1348 129.036 97.8285C129.344 97.5223 129.732 97.3685 130.2 97.367C130.668 97.3656 131.056 97.5194 131.366 97.8285C131.675 98.1376 131.828 98.5255 131.825 98.992C131.822 99.4586 131.668 99.8471 131.363 100.158C131.059 100.468 130.671 100.621 130.2 100.617C129.729 100.613 129.341 100.459 129.036 100.156Z"
private const val ICON_END = "M124.4 97.3999H142.4V79.3999H124.4V97.3999ZM133.4 110.9C130.287 110.9 127.362 110.309 124.625 109.127C121.887 107.945 119.506 106.342 117.481 104.319C115.456 102.295 113.853 99.9139 112.673 97.1749C111.492 94.4359 110.901 91.5109 110.9 88.3999C110.898 85.2889 111.489 82.3639 112.673 79.6249C113.856 76.8859 115.459 74.5047 117.481 72.4812C119.503 70.4577 121.884 68.8549 124.625 67.6729C127.365 66.4909 130.29 65.8999 133.4 65.8999C136.509 65.8999 139.434 66.4909 142.175 67.6729C144.915 68.8549 147.297 70.4577 149.319 72.4812C151.341 74.5047 152.944 76.8859 154.129 79.6249C155.314 82.3639 155.904 85.2889 155.9 88.3999C155.895 91.5109 155.304 94.4359 154.127 97.1749C152.949 99.9139 151.347 102.295 149.319 104.319C147.291 106.342 144.909 107.946 142.175 109.129C139.44 110.313 136.515 110.903 133.4 110.9Z"
private const val ICON_CONTINUE = "M85.5627 68.7917V44.2083C85.5627 43.5118 85.7987 42.9284 86.2707 42.458C86.7427 41.9876 87.3261 41.7516 88.021 41.75C88.7159 41.7484 89.3001 41.9844 89.7738 42.458C90.2474 42.9316 90.4826 43.5151 90.4793 44.2083V68.7917C90.4793 69.4882 90.2433 70.0725 89.7713 70.5445C89.2993 71.0165 88.7159 71.2516 88.021 71.25C87.3261 71.2484 86.7427 71.0124 86.2707 70.542C85.7987 70.0716 85.5627 69.4882 85.5627 68.7917ZM58.521 66.6406V46.3594C58.521 45.6219 58.7668 45.0278 59.2585 44.5771C59.7502 44.1264 60.3238 43.9011 60.9793 43.9011C61.1842 43.9011 61.4095 43.9215 61.6554 43.9625C61.9012 44.0035 62.1266 44.1059 62.3314 44.2698L77.5731 54.4719C77.9418 54.7177 78.2188 55.0152 78.404 55.3643C78.5892 55.7133 78.681 56.0919 78.6793 56.5C78.6777 56.9081 78.5859 57.2875 78.404 57.6382C78.2221 57.9889 77.9451 58.2856 77.5731 58.5281L62.3314 68.7302C62.1266 68.8941 61.9012 68.9965 61.6554 69.0375C61.4095 69.0785 61.1842 69.099 60.9793 69.099C60.3238 69.099 59.7502 68.8736 59.2585 68.4229C58.7668 67.9722 58.521 67.3781 58.521 66.6406Z"
private const val ICON_START = "M130.703 56.8111C130.703 62.1778 103.51 79.3332 100.426 76.2482C97.3405 73.1633 97.0457 40.7539 100.426 37.3741C103.809 33.9943 130.703 51.4487 130.703 56.8111Z"
private const val PANEL_LEFT = "M657.8 333.7C574.894 305.037 526.182 304.71 443.6 333.7C433.162 333.7 424.7 377.016 424.7 430.45V752.95C424.7 806.384 433.162 849.7 443.6 849.7H657.8C668.238 849.7 676.7 806.384 676.7 752.95V430.45C676.7 377.016 668.238 333.7 657.8 333.7Z"
private const val PANEL_RIGHT = "M1131.8 333.7C1048.89 305.037 1000.18 304.71 917.6 333.7C907.162 333.7 898.7 377.016 898.7 430.45V752.95C898.7 806.384 907.162 849.7 917.6 849.7H1131.8C1142.24 849.7 1150.7 806.384 1150.7 752.95V430.45C1150.7 377.016 1142.24 333.7 1131.8 333.7Z"

/** Иконка из SVG-пути: исходный bbox (srcX, srcY) в координатах SVG, рисуется в размер width×height (1 ед. SVG = 1 dp). */
@Composable
private fun SvgPathIcon(
    pathData: String,
    srcX: Float,
    srcY: Float,
    width: Dp,
    height: Dp,
    color: Color,
    strokeWidth: Float = 0f,
) {
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    Canvas(Modifier.size(width, height)) {
        val u = 1.dp.toPx()
        withTransform({ scale(u, u, Offset.Zero); translate(-srcX, -srcY) }) {
            drawPath(path, color)
            if (strokeWidth > 0f) {
                drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun ResetIcon(color: Color) {
    SvgPathIcon(ICON_RESET, 68.2f, 76.2f, 44.dp, 38.dp, color)
}

@Composable
private fun UndoIcon(color: Color) {
    SvgPathIcon(ICON_UNDO, 68.0f, 78.5f, 42.dp, 34.dp, color)
}

/** Иконка «корректировка» (календарь со счётом) из SVG кнопки. */
@Composable
private fun SlidersIcon(color: Color, knobFill: Color) {
    SvgPathIcon(ICON_CORRECTION, 108.5f, 73.5f, 44.dp, 39.dp, color)
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

/** Белая кнопка-капсула с синей обводкой 2dp и синим свечением: СБРОС / ОТКАТ / КОРРЕКТИРОВКА. По SVG: glow #2171F4 52%, σ29.1, erode 11. */
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
            .drawBehind {
                val inset = 11.dp.toPx()
                drawBlurredRoundRect(
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    corner = (size.height - 2 * inset) / 2f,
                    color = Color(0x852171F4),
                    sigma = 29.1.dp.toPx(),
                )
            }
            .clip(RoundedCornerShape(50))
            .background(colors.cardSurface)
            .border(2.dp, colors.headingBlue, RoundedCornerShape(50))
            .hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            icon()
            FigmaText(text, type.teamLabel.copy(fontSize = 28.sp), colors.headingBlue)
        }
    }
}

/** Главная градиентная кнопка-капсула. kind: 0 — ▶ начать, 1 — ◉■ завершить, 2 — ▶❚ продолжить.
 *  По SVG: градиент #6B8EC7 (правый верх) → #1D6FF7 (левый низ), тень #2171F4, смещение (−3;14), σ28.2, erode 11. */
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
                val inset = 11.dp.toPx()
                drawBlurredRoundRect(
                    topLeft = Offset(inset - 3.dp.toPx(), inset + 14.dp.toPx()),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    corner = (size.height - 2 * inset) / 2f,
                    color = Color(0xFF2171F4),
                    sigma = 28.2.dp.toPx(),
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(colors.buttonEnd, colors.buttonStart),
                        start = Offset(size.width, size.height * 0.257f),
                        end = Offset(0f, size.height * 0.692f),
                    ),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
            .hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (kind) {
                1 -> SvgPathIcon(ICON_END, 110.9f, 65.9f, 45.dp, 45.dp, Color.White)
                2 -> SvgPathIcon(ICON_CONTINUE, 58.5f, 41.8f, 32.dp, 30.dp, Color.White)
                else -> SvgPathIcon(ICON_START, 100.4f, 37.4f, 31.dp, 39.dp, Color.White, strokeWidth = 3f)
            }
            BasicText(
                text,
                style = type.button.copy(fontSize = 28.sp, color = colors.onButton),
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

/** Монета подачи: оригинал жетона (coin.png) — выбранная команда, серая с кольцом #9EC8E2 — нет. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ServeCoin(
    selected: Boolean,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coinFraction: Float = 1f,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        modifier.size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Image(
                painter = painterResource("coin.png"),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(coinFraction),
            )
        } else {
            Box(
                Modifier.fillMaxSize().clip(CircleShape)
                    .background(CoinGray)
                    .border(5.dp, CoinRing, CircleShape),
            )
        }
    }
}

/** Стрелка выбора стороны поля: контур #9EC8E2, заливка жёлтая — выбранная, серая — нет. Размер 172×48 по SVG корректировки. */
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
        modifier.size(172.dp, 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        val u = 1.dp.toPx()
        val fill = if (selected) CoinGold else CoinGray
        fun x(local: Float) = if (toLeft) local else 172f - local
        withTransform({ scale(u, u, Offset.Zero) }) {
            drawLine(CoinRing, Offset(x(18f), 24f), Offset(x(157f), 24f), strokeWidth = 13f, cap = StrokeCap.Round)
            val headOuter = Path().apply {
                moveTo(x(2f), 24f); lineTo(x(31f), 3f); lineTo(x(31f), 45f); close()
            }
            drawPath(headOuter, CoinRing)
            drawLine(fill, Offset(x(10f), 24f), Offset(x(157f), 24f), strokeWidth = 8f, cap = StrokeCap.Round)
            val headInner = Path().apply {
                moveTo(x(5f), 24f); lineTo(x(28f), 7f); lineTo(x(28f), 41f); close()
            }
            drawPath(headInner, fill)
        }
    }
}

/** Круглый индикатор L/R между стрелками: выбранная половина белая (буква синяя), вторая — серая (буква #BCBCBC). */
@Composable
private fun LrBadge(courtSide: String, modifier: Modifier = Modifier) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    Box(modifier.size(40.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(CoinGray, r, c)
            drawArc(
                Color.White,
                startAngle = if (courtSide == "L") 90f else -90f,
                sweepAngle = 180f, useCenter = true,
                topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r),
            )
            drawCircle(CoinRing, r - 1.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight(), Alignment.Center) {
                BasicText(
                    "L",
                    style = type.caption.copy(
                        fontSize = 19.sp,
                        color = if (courtSide == "L") colors.headingBlue else Color(0xFFBCBCBC),
                    ),
                    maxLines = 1, softWrap = false,
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight(), Alignment.Center) {
                BasicText(
                    "R",
                    style = type.caption.copy(
                        fontSize = 19.sp,
                        color = if (courtSide == "R") colors.headingBlue else Color(0xFFBCBCBC),
                    ),
                    maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

/** Пилюля-поле для цифр в корректировке: 90×53, r20 (по SVG). */
@Composable
private fun NumPillField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    Box(
        modifier.size(90.dp, 53.dp).clip(RoundedCornerShape(20.dp)).background(colors.pillBg),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { v -> onValueChange(v.filter { it.isDigit() }.take(3)) },
            singleLine = true,
            textStyle = type.teamLabel.copy(
                fontSize = 28.sp, color = colors.pillText, textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(colors.pillText),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        )
    }
}

/** Общая «рамка» диалогов матча: затемнение + карточка 794×H по центру (тень: чёрная 25%, σ40.9) + стрелка «назад». */
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
                Modifier.align(Alignment.Center).size(794.dp, cardHeight)
                    .graphicsLayer {
                        val s = 0.97f + 0.03f * appear.value
                        scaleX = s; scaleY = s; alpha = appear.value
                    }
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
    MatchDialogScaffold(cardHeight = 582.dp, onBack = onBack) {
        FigmaText(
            strings.tossTitle, type.cardTitle, colors.headingBlue,
            Modifier.offset(278.dp, 43.dp), TextAlign.Center, 239.dp,
        )
        Box(
            Modifier.offset(107.dp, 138.dp).size(205.dp, 42.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                name1, style = type.teamLabel.copy(color = colors.headingBlue),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.offset(478.dp, 138.dp).size(205.dp, 42.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                name2, style = type.teamLabel.copy(color = colors.headingBlue),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.offset(395.dp, 130.dp).size(4.dp, 310.dp).background(colors.divider, RoundedCornerShape(2.dp)))
        ServeCoin(serverSel == 0, 120.dp, onClick = { serverSel = 0 }, modifier = Modifier.offset(144.dp, 267.dp), coinFraction = 0.9f)
        ServeCoin(serverSel == 1, 120.dp, onClick = { serverSel = 1 }, modifier = Modifier.offset(522.dp, 267.dp), coinFraction = 0.9f)
        GreenActionButton(
            strings.confirm, 324.dp,
            onClick = { onConfirm(serverSel) },
            modifier = Modifier.offset(235.dp, 500.dp),
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
    MatchDialogScaffold(cardHeight = 394.dp, onBack = onBack) {
        FigmaText(
            strings.enterTextTitle,
            type.cardTitle.copy(lineHeight = 40.sp),
            colors.headingBlue,
            Modifier.offset(134.dp, 44.dp), TextAlign.Center, 528.dp,
        )
        Box(
            Modifier.offset(101.dp, 198.dp).size(593.dp, 49.dp)
                .clip(RoundedCornerShape(16.5.dp)).background(colors.fieldBg)
                .border(3.dp, Color(0xFF9EC8E2), RoundedCornerShape(16.5.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (textInput.isEmpty()) {
                Canvas(Modifier.fillMaxSize()) {
                    for (cx in listOf(23f, 37f, 50f)) {
                        drawCircle(
                            Color(0xFFDDDDDD), radius = 5.dp.toPx(),
                            center = Offset(cx.dp.toPx(), 35.dp.toPx()),
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
            strings.display, 209.dp,
            onClick = { onDisplay(textInput) },
            modifier = Modifier.offset(292.dp, 315.dp),
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

    MatchDialogScaffold(cardHeight = 598.dp, onBack = onBack) {
        FigmaText(
            strings.correction, type.cardTitle, colors.headingBlue,
            Modifier.offset(249.dp, 20.dp), TextAlign.Center, 298.dp,
        )
        // команды и монеты подающего
        Box(
            Modifier.offset(81.dp, 79.dp).size(205.dp, 42.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                name1, style = type.teamLabel.copy(color = colors.headingBlue),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        ServeCoin(serverSel == 0, 46.dp, onClick = { serverSel = 0 }, modifier = Modifier.offset(288.dp, 73.dp))
        Box(
            Modifier.offset(447.dp, 79.dp).size(205.dp, 42.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                name2, style = type.teamLabel.copy(color = colors.headingBlue),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        ServeCoin(serverSel == 1, 46.dp, onClick = { serverSel = 1 }, modifier = Modifier.offset(665.dp, 73.dp))

        Box(Modifier.offset(391.dp, 79.dp).size(4.dp, 310.dp).background(colors.divider, RoundedCornerShape(2.dp)))

        // очки и партии
        val labelStyle = type.teamLabel.copy(fontSize = 28.sp, color = colors.captionText)
        BasicText(strings.pointsTab, style = labelStyle, modifier = Modifier.offset(74.dp, 162.dp), maxLines = 1, softWrap = false)
        BasicText(strings.pointsTab, style = labelStyle, modifier = Modifier.offset(445.dp, 162.dp), maxLines = 1, softWrap = false)
        BasicText(strings.setsLabel, style = labelStyle, modifier = Modifier.offset(74.dp, 268.dp), maxLines = 1, softWrap = false)
        BasicText(strings.setsLabel, style = labelStyle, modifier = Modifier.offset(445.dp, 268.dp), maxLines = 1, softWrap = false)
        NumPillField(t1Score, { t1Score = it }, Modifier.offset(257.dp, 154.dp))
        NumPillField(t2Score, { t2Score = it }, Modifier.offset(628.dp, 154.dp))
        NumPillField(t1Sets, { t1Sets = it }, Modifier.offset(257.dp, 260.dp))
        NumPillField(t2Sets, { t2Sets = it }, Modifier.offset(628.dp, 260.dp))

        // сторона поля подающей команды
        FigmaText(
            strings.courtSideLabel,
            type.cardTitle.copy(lineHeight = 22.sp),
            colors.headingBlue,
            Modifier.offset(269.dp, 403.dp), TextAlign.Center, 248.dp,
        )
        SideArrow(
            toLeft = true, selected = courtSide == "L",
            onClick = { onCourtSideChange("L") },
            modifier = Modifier.offset(200.dp, 450.dp),
        )
        LrBadge(courtSide, Modifier.offset(373.dp, 454.dp))
        SideArrow(
            toLeft = false, selected = courtSide == "R",
            onClick = { onCourtSideChange("R") },
            modifier = Modifier.offset(413.dp, 450.dp),
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
            modifier = Modifier.offset(308.dp, 524.dp),
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
                    // Табло 1129×698 — равномерно вписываем в панель предпросмотра (контент 1086×675).
                    Box(
                        Modifier.offset(7.dp, 11.dp).requiredSize(1129.dp, 698.dp)
                            .graphicsLayer {
                                val fit = 1086f / 1129f
                                scaleX = fit
                                scaleY = fit
                                transformOrigin = TransformOrigin(0f, 0f)
                            },
                    ) {
                        AudienceScreen(
                            match = match,
                            logo1 = logo1,
                            logo2 = logo2,
                            tickerText = tickerText,
                            courtSide = courtSide,
                            strings = strings,
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
                        modifier = Modifier.offset(1322.dp, 685.dp).size(223.dp, 93.dp),
                    ) { ResetIcon(colors.headingBlue) }
                    OutlineActionButton(
                        text = strings.undo,
                        onClick = { ApiClient.removePoint(apply) { errorMsg = it } },
                        modifier = Modifier.offset(1581.dp, 685.dp).size(223.dp, 93.dp),
                    ) { UndoIcon(colors.headingBlue) }
                    OutlineActionButton(
                        text = strings.correction,
                        onClick = { showCorrectionDialog = true },
                        modifier = Modifier.offset(1322.dp, 800.dp).size(481.dp, 93.dp),
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

// ============ ТАБЛО ДЛЯ ЗРИТЕЛЕЙ («Экран предпросмотра» из Figma SVG) ============
// Пространство дизайна 1129×698: рамка табло 1115×684 r40 в точке (7,7),
// обводка 14 (градиент #CFE6F5→#81B6D6). Все координаты — из присланного SVG.

/** Белый текст табло, центрированный в точке (centerX, centerY) пространства дизайна. */
@Composable
private fun BoardText(
    text: String,
    centerX: Float,
    centerY: Float,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    width: Float = 252f,
    color: Color = Color.White,
) {
    val h = fontSize.value * 1.3f
    Box(
        Modifier.offset((centerX - width / 2).dp, (centerY - h / 2).dp).size(width.dp, h.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = fontSize,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AudienceScreen(
    match: MatchState,
    logo1: ImageBitmap?,
    logo2: ImageBitmap?,
    modifier: Modifier = Modifier,
    tickerText: String = "",
    courtSide: String = "L",
    strings: AppStrings = RuStrings
) {
    var live by remember { mutableStateOf(match) }

    LaunchedEffect(match) { live = match }

    DisposableEffect(Unit) {
        ApiClient.connectScoreWs(onScore = { r -> live = MatchState.fromResponse(r) }, onText = { })
        onDispose { ApiClient.disconnectWs() }
    }

    val panelLeft = remember { PathParser().parsePathString(PANEL_LEFT).toPath() }
    val panelRight = remember { PathParser().parsePathString(PANEL_RIGHT).toPath() }

    Box(modifier = modifier.size(width = 1129.dp, height = 698.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val u = 1.dp.toPx()
            withTransform({ scale(u, u, Offset.Zero) }) {
                val frame = Path().apply {
                    addRoundRect(RoundRect(7f, 7f, 1122f, 691f, CornerRadius(40f, 40f)))
                }
                drawPath(frame, Color(0xFF070F2B))
                clipPath(frame) {
                    // тёмно-синяя диагональная полоса
                    val navy = Path().apply {
                        moveTo(7f, 691f); lineTo(743.3f, 7f); lineTo(1122f, 7f); lineTo(1122f, 212.2f); close()
                    }
                    drawPath(
                        navy,
                        Brush.verticalGradient(listOf(Color(0xFF2A5298), Color(0xFF1E3C72)), startY = 7f, endY = 691f),
                    )
                    // красная диагональ
                    val red = Path().apply {
                        moveTo(7f, 691f); lineTo(1122f, 100f); lineTo(1122f, 691f); close()
                    }
                    drawPath(
                        red,
                        Brush.horizontalGradient(listOf(Color(0xFFBA0C24), Color(0xFFF21B3F)), startX = 7f, endX = 1122f),
                    )
                    // матовые панели команд (белый 10%, контур #BABABA 2)
                    withTransform({ translate(-222.7f, -222.7f) }) {
                        drawPath(panelLeft, Color.White.copy(alpha = 0.10f))
                        drawPath(panelLeft, Color(0xFFBABABA), style = Stroke(2f))
                        drawPath(panelRight, Color.White.copy(alpha = 0.10f))
                        drawPath(panelRight, Color(0xFFBABABA), style = Stroke(2f))
                    }
                    // плашки партий и пилюля «ПАРТИЯ N» (белый 10%)
                    drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(228f, 332f), Size(200f, 102f), CornerRadius(17f, 17f))
                    drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(702f, 332f), Size(200f, 102f), CornerRadius(17f, 17f))
                    drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(459f, 542f), Size(211f, 116f), CornerRadius(19f, 19f))
                    // диски логотипов команд
                    drawCircle(Color(0xFFD9D9D9), 75f, Offset(328f, 198f))
                    drawCircle(Color(0xFFD9D9D9), 75f, Offset(802f, 198f))
                    // точки-двоеточие между партиями
                    drawCircle(Color.White, 15f, Offset(565f, 285f))
                    drawCircle(Color.White, 15f, Offset(565f, 350f))
                    // бейдж L/R: кольцо #848484, левая половина #C31332, правая #18305F
                    val bc = Offset(564f, 462f)
                    drawCircle(Color(0xFF848484), 37f, bc)
                    drawArc(
                        Color(0xFFC31332), startAngle = 90f, sweepAngle = 180f, useCenter = true,
                        topLeft = Offset(bc.x - 32.3f, bc.y - 32.3f), size = Size(64.6f, 64.6f),
                    )
                    drawArc(
                        Color(0xFF18305F), startAngle = -90f, sweepAngle = 180f, useCenter = true,
                        topLeft = Offset(bc.x - 32.3f, bc.y - 32.3f), size = Size(64.6f, 64.6f),
                    )
                    drawLine(Color.White, Offset(564f, 436f), Offset(564f, 486f), strokeWidth = 2f)
                    drawCircle(Color(0xFF070F2B), 36f, bc, style = Stroke(2f))
                    // точка подачи (на ободе диска подающей команды)
                    drawCircle(
                        Color(0xFFFFBF00), 15f,
                        if (live.server == 0) Offset(371f, 257f) else Offset(759f, 257f),
                    )
                }
                // рамка: градиентная обводка 14
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFCFE6F5), Color(0xFF81B6D6)),
                        start = Offset(1122f, 7f), end = Offset(7f, 691f),
                    ),
                    topLeft = Offset(7f, 7f),
                    size = Size(1115f, 684f),
                    cornerRadius = CornerRadius(40f, 40f),
                    style = Stroke(14f),
                )
            }
        }

        // Логотипы организаций (слева направо)
        Image(
            painter = painterResource("org_logo_1.png"), contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.offset(412.dp, 21.dp).size(85.dp, 85.dp),
        )
        Image(
            painter = painterResource("org_logo_2.png"), contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.offset(523.dp, 21.dp).size(85.dp, 85.dp),
        )
        Image(
            painter = painterResource("org_logo_3.png"), contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.offset(634.dp, 21.dp).size(85.dp, 81.dp),
        )

        // Логотипы команд поверх серых дисков
        if (logo1 != null) {
            Box(Modifier.offset(253.dp, 123.dp).size(150.dp).clip(CircleShape)) {
                Image(bitmap = logo1, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        if (logo2 != null) {
            Box(Modifier.offset(727.dp, 123.dp).size(150.dp).clip(CircleShape)) {
                Image(bitmap = logo2, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }

        // Левая колонка
        BoardText(live.team1.uppercase(), 328f, 301f, 36.sp, FontWeight.Medium)
        BoardText("${live.sets1}", 328f, 370f, 62.sp, FontWeight.Bold)
        BoardText(strings.setsLabel, 328f, 419f, 24.sp, FontWeight.Medium)
        BoardText("${live.score1}", 328f, 506f, 118.sp, FontWeight.Black)
        BoardText(strings.pointsTab, 328f, 591f, 33.sp, FontWeight.Medium)

        // Правая колонка
        BoardText(live.team2.uppercase(), 802f, 301f, 36.sp, FontWeight.Medium)
        BoardText("${live.sets2}", 802f, 370f, 62.sp, FontWeight.Bold)
        BoardText(strings.setsLabel, 802f, 419f, 24.sp, FontWeight.Medium)
        BoardText("${live.score2}", 802f, 506f, 118.sp, FontWeight.Black)
        BoardText(strings.pointsTab, 802f, 591f, 33.sp, FontWeight.Medium)

        // Центр: «ПАРТИЯ N» и буквы бейджа
        BoardText("${strings.setTab} ${live.currentSet}", 564f, 600f, 33.sp, FontWeight.Medium, width = 211f)
        BoardText("L", 549.5f, 462f, 32.sp, FontWeight.Bold, width = 34f)
        BoardText("R", 579.5f, 462f, 32.sp, FontWeight.Bold, width = 34f)

        // Баннер победителя поверх затемнённого табло / пилюля сообщения сверху
        val winnerName = when (live.winner) {
            0 -> live.team1
            1 -> live.team2
            else -> null
        }
        if (live.finished && winnerName != null) {
            Box(
                Modifier.offset(7.dp, 7.dp).size(1115.dp, 684.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(Color.Black.copy(alpha = 0.52f)),
            )
            Box(
                Modifier.offset(294.dp, 246.dp).size(545.dp, 208.dp)
                    .clip(RoundedCornerShape(45.dp))
                    .background(Color(0xFF162C5A)),
            ) {
                Box(Modifier.offset(0.dp, 30.dp).size(545.dp, 80.dp), contentAlignment = Alignment.Center) {
                    Text(
                        strings.winnerLabel,
                        color = Color(0xFFFFBF00),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 64.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Box(Modifier.offset(0.dp, 130.dp).size(545.dp, 50.dp), contentAlignment = Alignment.Center) {
                    Text(
                        winnerName.uppercase(),
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 42.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        } else if (tickerText.isNotBlank()) {
            Box(
                Modifier.offset(311.dp, 96.dp).size(533.dp, 124.dp)
                    .clip(RoundedCornerShape(42.dp))
                    .background(Color(0xFF162C5A)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tickerText,
                    color = Color.White,
                    fontSize = 50.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 50.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 36.dp),
                )
            }
        }
    }
}
