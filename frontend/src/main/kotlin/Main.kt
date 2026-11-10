import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import ui.hotkeys.HotKey
import ui.hotkeys.Hotkeys
import ui.hotkeys.MatchHotkeyActions
import ui.hotkeys.RegisterHotkeyLayer
import ui.hotkeys.RegisterMatchHotkeys
import ui.hotkeys.rememberHotkeyPress
import ui.hotkeys.toHotKey
import ui.hotkeys.trackTyping
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpSize
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
import androidx.compose.ui.platform.LocalFocusManager
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
import com.example.TennisSetScore
import com.example.VolleyballSetScore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import ui.components.OvalsCanvas
import ui.components.SettingsButton
import ui.components.TennisBallIcon
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
import ui.theme.ArialFamily
import ui.theme.BloggerSans
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

enum class VolleyballPreset { SHORT, LONG, CUSTOM }

data class VolleyballMatchSettings(
    val preset: VolleyballPreset = VolleyballPreset.CUSTOM,
    val setCount: Int = 3,
    /** Очки партий 1–4 и решающей (ТБ — всегда индекс 4). Партии, скрытые в режиме до 3 партий, сохраняются. */
    val setPoints: List<Int> = listOf(15, 25, 15, 25, 15),
) {
    fun toApiConfig(): com.example.VolleyballMatchConfig = com.example.VolleyballMatchConfig(
        setsToWin = setCount / 2 + 1,
        // Бэкенд ждёт пять значений. В игре до 3 партий решающая — третья, её очки берём из ТБ.
        setPoints = if (setCount == 3) {
            listOf(setPoints[0], setPoints[1], setPoints[4], setPoints[4], setPoints[4])
        } else {
            setPoints.take(5)
        },
    )
}

private fun volleyballPresetFor(setCount: Int, points: List<Int>): VolleyballPreset = when {
    setCount == 3 && points[0] == 15 && points[1] == 15 && points[4] == 15 -> VolleyballPreset.SHORT
    setCount == 5 && points.take(4).all { it == 25 } && points[4] == 15 -> VolleyballPreset.LONG
    else -> VolleyballPreset.CUSTOM
}

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
    val tennisPoints: List<Int>? = null,
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
                tennisPoints = r.points,
                tennisGames = r.games,
                server = r.server,
                winner = r.winner
            )
        }
    }
}

fun main() {
    forceExtendedDisplayOnWindows()
    runApp()
}

private fun runApp() = application {
    var currentScreen by remember { mutableStateOf(Screen.menu) }
    var showSetupDialog by remember { mutableStateOf(false) }
    var selectedGame by remember { mutableStateOf(Screen.tennis) }

    var gameLogo1 by remember { mutableStateOf<ImageBitmap?>(null) }
    var gameName1 by remember { mutableStateOf("") }
    var gameLogo2 by remember { mutableStateOf<ImageBitmap?>(null) }
    var gameName2 by remember { mutableStateOf("") }

    var match by remember { mutableStateOf(MatchState()) }
    var volleyballSettings by remember { mutableStateOf(VolleyballMatchSettings()) }
    var showVolleyballSettings by remember { mutableStateOf(false) }
    var courtSide by remember { mutableStateOf("L") } // сторона поля подающей команды (общая для админки и табло)
    // Внешний экран (ТВ по HDMI): табло открывается само, пока открыт экран счёта игры.
    val externalDisplay by rememberExternalDisplay()
    var tickerText by remember { mutableStateOf("") } // 👈 ТЕКСТ ДЛЯ ЗРИТЕЛЕЙ

    // Настройки приложения (экран «Настройки»): тема и язык.
    var darkTheme by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(AppLanguage.RU) }
    var showSettings by remember { mutableStateOf(false) }
    var showHotkeys by remember { mutableStateOf(false) }

    // Загрузочный экран: при запуске приложения и при переходах меню ↔ режим игры.
    var splashVisible by remember { mutableStateOf(true) }
    var splashKey by remember { mutableStateOf(0) }
    LaunchedEffect(splashKey) {
        splashVisible = true
        delay(if (splashKey == 0) 1_100 else 700)
        splashVisible = false
    }

    // Безрамочное окно на весь экран: Maximized + undecorated — верхней системной
    // панельки не видно, но это обычное окно (не эксклюзивный Fullscreen), поэтому
    // Alt+Tab работает мгновенно, а свернуть можно кнопкой «–» в меню или с панели задач.
    val windowState = rememberWindowState(placement = WindowPlacement.Maximized)

    // ── Горячие клавиши (список — на экране «ХОТКЕИ») ──
    // Window.onPreviewKeyEvent видит клавиши раньше любых компонентов, поэтому работает при любом фокусе.
    // Пока курсор в текстовом поле (Hotkeys.typing), 1 / 2 / - / Space / Tab / ⌫ — обычный ввод.
    val pressedKeys = remember { mutableSetOf<Key>() }
    val onHotkey: (KeyEvent) -> Boolean = handler@{ e ->
        if (e.type == KeyEventType.KeyUp) {
            pressedKeys.remove(e.key)
            e.toHotKey()?.let(Hotkeys::onKeyUp)
            // При открытом окне хоткеев съедаем и KeyUp (в Compose clickable реагирует на Space по KeyUp),
            // чтобы ни одна кнопка под оверлеем не сработала и не отправила запрос на бэкенд.
            return@handler showHotkeys && !splashVisible
        }
        // В AWT/Skiko символьный ввод приходит отдельным событием KeyEventType.Unknown (KEY_TYPED) —
        // пока открыты хоткеи, блокируем и его, чтобы ни один элемент под оверлеем не получил ввод.
        if (e.type != KeyEventType.KeyDown) return@handler showHotkeys && !splashVisible

        // Пока открыто окно «ХОТКЕИ», страничка работает исключительно в режиме демонстрации нажатий:
        // любая горячая клавиша (включая F1/F2/~/Enter/1/2/-/Tab/⌫/Space) лишь проигрывает
        // анимацию нажатия на своей клавише и не отправляет команды на бэкенд / в приложение,
        // кроме Esc («Назад»), закрывающего само окно хоткеев.
        if (showHotkeys && !splashVisible) {
            val hk = e.toHotKey() ?: return@handler true
            val first = pressedKeys.add(e.key)
            if (first) {
                Hotkeys.onKeyDown(hk)
                if (hk == HotKey.ESC) {
                    Hotkeys.layers.lastOrNull()?.onBack?.invoke() ?: run { showHotkeys = false }
                }
            }
            return@handler true
        }

        val hk = e.toHotKey() ?: return@handler false
        val first = pressedKeys.add(e.key) // автоповтор зажатой клавиши игнорируем

        if (first && !splashVisible && (!Hotkeys.typing || hk == HotKey.ESC || hk == HotKey.ENTER || hk == HotKey.F1 || hk == HotKey.F2)) {
            Hotkeys.onKeyDown(hk)
        }

        // F1 / F2 работают вне окна хоткеев всегда
        if (hk == HotKey.F1) { if (first) exitProcess(0); return@handler true }
        if (hk == HotKey.F2) { if (first) windowState.isMinimized = true; return@handler true }

        if (splashVisible) return@handler false
        val top = Hotkeys.layers.lastOrNull()
        val inGame = currentScreen == Screen.tennis || currentScreen == Screen.valeball
        // при наборе текста обрабатываем только Esc и Enter
        if (Hotkeys.typing && hk != HotKey.ESC && hk != HotKey.ENTER) return@handler false

        when (hk) {
            HotKey.ESC -> {
                if (top != null) { if (first) top.onBack?.invoke(); return@handler true }
                if (inGame) { if (first) Hotkeys.matchActions?.back?.invoke(); return@handler true }
                return@handler false
            }
            HotKey.ENTER -> {
                if (top != null) { if (first) top.onConfirm?.invoke(); return@handler true }
                return@handler false
            }
            HotKey.TILDE -> {
                if (first) {
                    if (showSettings) showSettings = false
                    else if (top == null) showSettings = true
                }
                return@handler true
            }
            HotKey.H -> {
                // H только открывает экран хоткеев (закрывается он стрелкой «назад» или Esc).
                if (inGame && top == null) { if (first) showHotkeys = true; return@handler true }
                return@handler false
            }
            else -> Unit
        }

        // клавиши матча — только на экране игры и когда не открыт ни один оверлей
        if (top != null || !inGame) return@handler false
        val actions = Hotkeys.matchActions ?: return@handler false
        if (first) {
            when (hk) {
                HotKey.ONE -> actions.point1?.invoke()
                HotKey.TWO -> actions.point2?.invoke()
                HotKey.MINUS -> actions.undo?.invoke()
                HotKey.TAB -> actions.correction?.invoke()
                HotKey.BACKSPACE -> actions.reset?.invoke()
                HotKey.SPACE -> actions.mainAction()
                else -> Unit
            }
        }
        true
    }

    Window(
        onCloseRequest = ::exitApplication, title = "ScoreKeeperApp",
        icon = painterResource("app_icon.png"),
        state = windowState, undecorated = true,
        onPreviewKeyEvent = onHotkey,
    ) {
        // Если окно потеряло фокус или было свёрнуто, KeyUp мог не прийти — забываем «зажатые» клавиши,
        // иначе следующее нажатие той же клавиши (например, F2) приняли бы за автоповтор.
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(windowFocused, windowState.isMinimized) {
            pressedKeys.clear()
            Hotkeys.clearPressed()
        }

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
                    onBack = { currentScreen = Screen.menu; splashKey++ },
                    name1 = gameName1, name2 = gameName2, logo1 = gameLogo1, logo2 = gameLogo2,
                    match = match, onMatchUpdate = { match = it },
                    volleyballSettings = volleyballSettings,
                    tickerText = tickerText, onTickerUpdate = { tickerText = it }, // 👈 ПЕРЕДАЕМ ТЕКСТ
                    darkTheme = darkTheme,
                    language = language,
                    onSettings = { showSettings = true },
                    onHotkeys = { showHotkeys = true },
                    courtSide = courtSide,
                    onCourtSideChange = { courtSide = it }
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
                        // До старта матча бэкенд ещё не знает названий — кладём их в локальное состояние,
                        // чтобы табло (в т.ч. на ТВ) сразу показывало введённые команды.
                        val st = stringsFor(language)
                        val tennis = selectedGame == Screen.tennis
                        match = MatchState(
                            team1 = name1.ifBlank { if (tennis) st.player1 else st.team1 },
                            team2 = name2.ifBlank { if (tennis) st.player2 else st.team2 },
                            sport = if (tennis) "tennis" else "volleyball",
                        )
                        showSetupDialog = false
                        val isVolleyball = selectedGame == Screen.valeball
                        // Volleyball rules are configured over the home screen, immediately after team setup.
                        // Enter the match screen only after the user confirms those settings.
                        currentScreen = if (isVolleyball) Screen.menu else selectedGame
                        showVolleyballSettings = isVolleyball
                        if (!isVolleyball) splashKey++
                    },
                )
            }

            if (showHotkeys) {
                HotkeysOverlay(
                    darkTheme = darkTheme,
                    language = language,
                    onBack = { showHotkeys = false },
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

            if (showVolleyballSettings) {
                VolleyballSettingsDialog(
                    settings = volleyballSettings,
                    darkTheme = darkTheme,
                    language = language,
                    onBack = { showVolleyballSettings = false },
                    onConfirm = { selected ->
                        volleyballSettings = selected
                        showVolleyballSettings = false
                        currentScreen = selectedGame
                        splashKey++
                    },
                )
            }

            SplashOverlay(visible = splashVisible, language = language)
        }
    }

    val inGame = currentScreen == Screen.tennis || currentScreen == Screen.valeball
    val tv = externalDisplay
    if (inGame && tv != null) {
        ExternalScoreboardWindow(
            display = tv,
            match = match,
            logo1 = gameLogo1,
            logo2 = gameLogo2,
            tickerText = tickerText,
            courtSide = courtSide,
            strings = stringsFor(language),
        )
    }
}

/** Загрузочный экран на всё окно: логотип, название и спиннер. */
@Composable
private fun SplashOverlay(visible: Boolean, language: AppLanguage) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(250),
        label = "splashAlpha",
    )
    if (alpha <= 0.01f) return
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(
                Brush.verticalGradient(listOf(Color(0xFF160B3D), Color(0xFF070F2B)))
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* на время загрузки клики не проходят в экран под сплэшем */ },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource("app_icon.png"),
                contentDescription = null,
                modifier = Modifier.size(180.dp).clip(RoundedCornerShape(40.dp)),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "ScoreKeeper",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 42.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(26.dp))
            CircularProgressIndicator(
                color = Color(0xFFFFC838),
                strokeWidth = 6.dp,
                modifier = Modifier.size(54.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                if (language == AppLanguage.RU) "Загрузка..." else "Loading...",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 20.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
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

/** Пружинный масштаб при наведении/нажатии (мышь или соответствующий хоткей). */
@Composable
private fun rememberPressScale(
    interaction: MutableInteractionSource,
    hotKey: HotKey? = null,
    enabledWhenOverlayOpen: Boolean = false,
): Float {
    val hovered by interaction.collectIsHoveredAsState()
    val mousePressed by interaction.collectIsPressedAsState()
    val hkPress = rememberHotkeyPress(
        hotKey,
        enabled = enabledWhenOverlayOpen || Hotkeys.layers.isEmpty(),
    )
    val pressed = mousePressed || hkPress > 0.5f
    val hover = animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "btnHover",
    )
    val pressSpec: AnimationSpec<Float> =
        if (pressed) tween(80) else spring(dampingRatio = 0.45f, stiffness = 500f)
    val press = animateFloatAsState(
        targetValue = if (mousePressed) 1f else hkPress,
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

/** Светло-синяя пилюля со счётом («00»): цифры — Blue Curve Bold 32 (type.scoreDigits).
 *  В метриках BlueCurve-Bold descent значительно больше верхнего поля над прописными цифрами,
 *  поэтому смещаем текст на +2 dp вниз, чтобы видимый глиф «00» стоял ровно по центру пилюли. */
@Composable
private fun ScorePill(
    value: String,
    modifier: Modifier = Modifier,
    width: Dp = 92.dp,
    height: Dp = 64.dp,
    corner: Dp = 20.dp,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    Box(
        modifier.size(width, height).clip(RoundedCornerShape(corner)).background(colors.pillBg),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            value,
            style = type.scoreDigits.copy(color = colors.pillText),
            modifier = Modifier.offset(y = 2.dp),
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

/** Жёлтый индикатор подачи: кружок #FFBF00 r15 со свечением (фильтр из SVG панельки подач). */
@Composable
private fun ServeGlowDot(modifier: Modifier = Modifier) {
    Canvas(modifier.size(40.dp)) {
        drawBlurredRoundRect(
            topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
            size = Size(36.dp.toPx(), 36.dp.toPx()),
            corner = 18.dp.toPx(),
            color = Color(0xFFFFBF00).copy(alpha = 0.30f),
            sigma = 4.4.dp.toPx(),
        )
        drawCircle(
            Color(0xFFFFBF00),
            radius = 15.dp.toPx(),
            center = Offset(20.dp.toPx(), 20.dp.toPx()),
        )
    }
}

/**
 * Панелька партий и подачи под предпросмотром — один в один по присланному SVG:
 * карточка 1088×205 r49.5, диски команд r40 с центрами (96.5, 51.5) и (96.5, 155.5),
 * пилюли счёта 90×53 r26.5, вкладка «ПАРТИЯ N» 213×43 на верхнем крае и линии по бокам неё,
 * разделитель строк и жёлтый кружок справа-снизу от диска подающей команды.
 */
@Composable
private fun SetsServePanel(
    name1: String,
    name2: String,
    logo1: ImageBitmap?,
    logo2: ImageBitmap?,
    sets1: Int,
    sets2: Int,
    server: Int,
    showServe: Boolean,
    tabText: String,
    modifier: Modifier = Modifier,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    Box(modifier.size(1088.dp, 205.dp)) {
        // сама карточка
        Box(
            Modifier.fillMaxSize()
                .cardShadow(49.5.dp)
                .clip(RoundedCornerShape(49.5.dp))
                .background(colors.cardSurface)
                .border(1.dp, colors.divider, RoundedCornerShape(49.5.dp)),
        )
        // тонкие линии по бокам вкладки «ПАРТИЯ N»
        Box(Modifier.offset(161.5.dp, 10.dp).size(250.dp, 2.dp).clip(RoundedCornerShape(1.dp)).background(colors.divider))
        Box(Modifier.offset(675.5.dp, 11.dp).size(250.dp, 2.dp).clip(RoundedCornerShape(1.dp)).background(colors.divider))
        // разделитель между строками команд
        Box(Modifier.offset(55.5.dp, 103.dp).size(976.dp, 2.dp).background(colors.divider))
        // диски команд и индикатор подачи справа-снизу от диска
        TeamLogoCircle(logo1, Modifier.offset(56.5.dp, 11.5.dp))
        TeamLogoCircle(logo2, Modifier.offset(56.5.dp, 115.5.dp))
        if (showServe && server == 0) ServeGlowDot(Modifier.offset(101.5.dp, 57.5.dp))
        if (showServe && server == 1) ServeGlowDot(Modifier.offset(101.5.dp, 161.5.dp))
        // названия команд
        Box(
            Modifier.offset(163.5.dp, 11.5.dp).size(720.dp, 80.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                name1, style = type.teamLabel.copy(color = colors.headingBlue),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.offset(163.5.dp, 115.5.dp).size(720.dp, 80.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                name2, style = type.teamLabel.copy(color = colors.headingBlue),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        // пилюли выигранных партий
        ScorePill("%02d".format(sets1), Modifier.offset(941.5.dp, 24.5.dp), width = 90.dp, height = 53.dp, corner = 26.5.dp)
        ScorePill("%02d".format(sets2), Modifier.offset(941.5.dp, 128.5.dp), width = 90.dp, height = 53.dp, corner = 26.5.dp)
        // вкладка «ПАРТИЯ N» на верхнем крае карточки
        Box(
            Modifier.offset(436.5.dp, (-11.5).dp).size(213.dp, 43.dp)
                .clip(RoundedCornerShape(21.5.dp)).background(colors.pillBg),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                tabText,
                style = type.teamLabel.copy(color = colors.headingBlue, lineHeight = 32.sp),
                maxLines = 1, softWrap = false,
            )
        }
    }
}

/** Зелёная кнопка «+1» (видна только по ходу матча). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlusOnePill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hotKey: HotKey? = null,
) {
    val type = ScoreKeeperTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, hotKey = hotKey)
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
    hotKey: HotKey? = null,
    icon: @Composable () -> Unit,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, hotKey = hotKey)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val inset = 11.dp.toPx()
                drawBlurredRoundRect(
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    corner = (size.height - 2 * inset) / 2f,
                    color = colors.outlineGlow,
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
    val scale = rememberPressScale(interaction, hotKey = HotKey.SPACE)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val inset = 11.dp.toPx()
                drawBlurredRoundRect(
                    topLeft = Offset(inset - 3.dp.toPx(), inset + 14.dp.toPx()),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    corner = (size.height - 2 * inset) / 2f,
                    color = colors.mainButtonGlow,
                    sigma = 28.2.dp.toPx(),
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(colors.mainButtonEnd, colors.mainButtonStart),
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
                1 -> SvgPathIcon(ICON_END, 110.9f, 65.9f, 45.dp, 45.dp, colors.onMainButton)
                2 -> SvgPathIcon(ICON_CONTINUE, 58.5f, 41.8f, 32.dp, 30.dp, colors.onMainButton)
                else -> SvgPathIcon(ICON_START, 100.4f, 37.4f, 31.dp, 39.dp, colors.onMainButton, strokeWidth = 3f)
            }
            BasicText(
                text,
                style = type.button.copy(fontSize = 28.sp, color = colors.onMainButton),
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
    val scale = rememberPressScale(interaction, hotKey = HotKey.ESC)
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
    val scale = rememberPressScale(interaction, hotKey = HotKey.ENTER, enabledWhenOverlayOpen = true)
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

/** Монета подачи: золотой жетон «СК» с кольцом — выбранная команда, серый жетон — нет (ассеты из макета). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ServeCoin(
    selected: Boolean,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        Image(
            painter = painterResource(if (selected) "coin_gold.png" else "coin_gray.png"),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Стрелка выбора стороны поля: точная геометрия из SVG 164×56; заливка #FFD95D — выбранная сторона, #848484 — нет. */
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
    val linePath = remember(toLeft) {
        PathParser().parsePathString(
            if (toLeft) "M156.889 28L17.9998 28" else "M6.49999 28L145.389 28"
        ).toPath()
    }
    val outerPath = remember(toLeft) {
        PathParser().parsePathString(
            if (toLeft) "M1.9915 26.4296C0.976059 27.2303 0.976062 28.7697 1.9915 29.5705L27.5116 49.695C28.8233 50.7294 30.75 49.795 30.75 48.1245V7.87548C30.75 6.20498 28.8233 5.27064 27.5116 6.30503L1.9915 26.4296Z" else "M161.397 29.5704C162.413 28.7697 162.413 27.2303 161.397 26.4295L135.877 6.30503C134.565 5.27063 132.639 6.20498 132.639 7.87548V48.1245C132.639 49.795 134.565 50.7294 135.877 49.695L161.397 29.5704Z"
        ).toPath()
    }
    val innerPath = remember(toLeft) {
        PathParser().parsePathString(
            if (toLeft) "M5.10278 26.931C4.55767 27.3341 4.56315 28.1513 5.11362 28.547L26.4163 43.8615C27.0778 44.3371 28 43.8643 28 43.0495V11.9831C28 11.1622 27.0655 10.691 26.4054 11.1791L5.10278 26.931Z" else "M158.286 29.069C158.831 28.6659 158.826 27.8487 158.275 27.453L136.972 12.1385C136.311 11.6629 135.389 12.1357 135.389 12.9505V44.0169C135.389 44.8378 136.323 45.309 136.983 44.8209L158.286 29.069Z"
        ).toPath()
    }
    Canvas(
        modifier.size(164.dp, 56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        val u = 1.dp.toPx()
        val fill = if (selected) CoinGold else CoinGray
        withTransform({ scale(u, u, Offset.Zero) }) {
            drawPath(linePath, CoinRing, style = Stroke(width = 13f, cap = StrokeCap.Round))
            drawPath(outerPath, CoinRing)
            drawRoundRect(
                fill,
                Offset(if (toLeft) 12f else 2.38867f, 24f),
                Size(149f, 8f),
                CornerRadius(4f, 4f),
            )
            drawPath(innerPath, fill)
        }
    }
}

/** Новый круглый значок «1 | 2» с круговой стрелкой между стрелками стороны поля (SVG 58×58).
 *  Поддерживает светлую и тёмную темы; при смене стороны круговая стрелка прокручивается на 360°,
 *  а цифры плавно меняются местами («1 | 2» ↔ «2 | 1»). Нажатие на сам значок тоже меняет сторону. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LrBadge(
    courtSide: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ScoreKeeperTheme.colors
    val isDark = colors.cardSurface != Color.White
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)

    val onePath = remember {
        PathParser().parsePathString(
            "M22.5439 23.8946L22.5439 33.9746C22.5439 34.3226 22.4239 34.6166 22.1839 34.8566C21.9319 35.1086 21.6319 35.2346 21.2839 35.2346C20.9359 35.2346 20.6419 35.1086 20.4019 34.8566C20.1499 34.6166 20.0239 34.3226 20.0239 33.9746L20.0239 26.0726L19.3939 26.4326C19.0939 26.6006 18.7759 26.6426 18.4399 26.5586C18.1039 26.4626 17.8459 26.2646 17.6659 25.9646C17.4979 25.6646 17.4619 25.3466 17.5579 25.0106C17.6419 24.6746 17.8339 24.4166 18.1339 24.2366L20.6539 22.7966C21.0859 22.5566 21.5059 22.5566 21.9139 22.7966C22.3339 23.0486 22.5439 23.4146 22.5439 23.8946Z"
        ).toPath()
    }
    val twoPath = remember {
        PathParser().parsePathString(
            "M42.4119 33.0746C42.6639 33.3266 42.7899 33.6266 42.7899 33.9746C42.7899 34.3226 42.6639 34.6166 42.4119 34.8566C42.1719 35.1086 41.8779 35.2346 41.5299 35.2346L32.8899 35.2346C32.5419 35.2346 32.2419 35.1086 31.9899 34.8566C31.7499 34.6166 31.6299 34.3226 31.6299 33.9746C31.6299 32.5586 32.3019 31.4246 33.6459 30.5726C34.1619 30.2486 34.7859 29.9546 35.5179 29.6906C35.8059 29.5946 36.1299 29.4926 36.4899 29.3846L36.9579 29.2586L37.3899 29.1326L37.7679 29.0246L38.1279 28.9346C38.3799 28.8506 38.5959 28.7786 38.7759 28.7186C39.4119 28.5026 39.8439 28.2086 40.0719 27.8366C40.1559 27.7046 40.2099 27.5666 40.2339 27.4226C40.2459 27.3746 40.2579 27.3206 40.2699 27.2606L40.2699 27.1886L40.2699 27.1346C40.2699 26.5826 39.9699 26.1146 39.3699 25.7306C38.7819 25.3466 38.0619 25.1546 37.2099 25.1546C36.5379 25.1546 35.8719 25.2986 35.2119 25.5866C34.6119 25.8506 34.1499 26.1626 33.8259 26.5226C33.5979 26.7866 33.3099 26.9306 32.9619 26.9546C32.6139 26.9786 32.3079 26.8766 32.0439 26.6486C31.7919 26.4086 31.6539 26.1146 31.6299 25.7666C31.6059 25.4306 31.7079 25.1306 31.9359 24.8666C32.5239 24.1946 33.3039 23.6546 34.2759 23.2466C35.2359 22.8386 36.2139 22.6346 37.2099 22.6346C38.7939 22.6346 40.1199 23.0666 41.1879 23.9306C42.2559 24.7826 42.7899 25.8506 42.7899 27.1346C42.7899 28.0466 42.5139 28.8446 41.9619 29.5286C41.3979 30.2246 40.6119 30.7466 39.6039 31.0946C39.3159 31.2026 38.8059 31.3586 38.0739 31.5626C36.5379 31.9826 35.4999 32.3666 34.9599 32.7146L41.5299 32.7146C41.8779 32.7146 42.1719 32.8346 42.4119 33.0746Z"
        ).toPath()
    }
    val arrowArcPath = remember {
        PathParser().parsePathString(
            "M38.8487 13.6216C46.4491 18.2897 49.7011 27.9375 46.1101 36.3974C42.1537 45.718 31.3913 50.0663 22.0707 46.1099C12.7502 42.1536 8.40188 31.3912 12.3582 22.0706C15.2952 15.1515 21.9853 10.9706 29.0512 10.8968"
        ).toPath()
    }
    val arrowHeadPath = remember {
        PathParser().parsePathString(
            "M26.0654 17.9308L29.2173 10.5054C29.2737 10.3724 29.3034 10.2297 29.3047 10.0852C29.306 9.94076 29.2788 9.79748 29.2246 9.66354C29.1705 9.52961 29.0906 9.40764 28.9893 9.30461C28.8881 9.20158 28.7675 9.1195 28.6346 9.06306L21.2091 5.91116"
        ).toPath()
    }
    val innerClip = remember {
        Path().apply { addOval(androidx.compose.ui.geometry.Rect(29.2339f - 17f, 29.2346f - 17f, 29.2339f + 17f, 29.2346f + 17f)) }
    }

    // Прокручиваем круговую стрелку по часовой стрелке на 360° при каждом переключении стороны.
    var prevSide by remember { mutableStateOf(courtSide) }
    var spinCount by remember { mutableStateOf(0) }
    LaunchedEffect(courtSide) {
        if (courtSide != prevSide) {
            prevSide = courtSide
            spinCount++
        }
    }
    val arrowAngle by animateFloatAsState(
        targetValue = spinCount * 360f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "lrArrowSpin",
    )
    val swapT by animateFloatAsState(
        targetValue = if (courtSide == "R") 1f else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "lrDigitsSwap",
    )

    val innerFill = if (isDark) colors.pillBg else Color.White
    val digitColor = colors.overlayHeading // #2070F5 в светлой теме, #FFC300 в тёмной
    val dividerColor = if (isDark) colors.fieldPlaceholder else CoinGray

    Canvas(
        modifier.size(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle),
    ) {
        val u = size.minDimension / 58f
        val pivot = Offset(29.2339f, 29.2349f)
        withTransform({ scale(u, u, Offset.Zero) }) {
            drawCircle(CoinGray, radius = 19f, center = pivot)
            drawCircle(CoinRing, radius = 19f, center = pivot, style = Stroke(2f))
            drawCircle(innerFill, radius = 17f, center = Offset(29.2339f, 29.2346f))
            clipPath(innerClip) {
                withTransform({ translate(18.462f * swapT, 0f) }) {
                    drawPath(onePath, digitColor)
                }
                withTransform({ translate(-15.928f * swapT, 0f) }) {
                    drawPath(twoPath, digitColor)
                }
            }
            drawLine(
                dividerColor,
                Offset(29.2339f, 18.2346f),
                Offset(29.2339f, 40.7346f),
                strokeWidth = 1f,
                cap = StrokeCap.Round,
            )
            val arrowStroke = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            withTransform({ rotate(arrowAngle, pivot) }) {
                drawPath(arrowArcPath, CoinRing, style = arrowStroke)
                drawPath(arrowHeadPath, CoinRing, style = arrowStroke)
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
            textStyle = type.scoreDigits.copy(
                color = colors.pillText, textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(colors.pillText),
            modifier = Modifier.fillMaxWidth().offset(y = 2.dp).padding(horizontal = 6.dp).trackTyping(),
        )
    }
}

/** Общая «рамка» диалогов матча: затемнение + карточка 794×H по центру (тень: чёрная 25%, σ40.9) + стрелка «назад». */
@Composable
private fun MatchDialogScaffold(
    cardHeight: Dp,
    onBack: () -> Unit,
    outerGlass: Boolean = false,
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
            if (outerGlass) {
                Box(
                    Modifier.align(Alignment.Center).offset(y = 24.dp).size(1002.dp, 802.dp)
                        .drawBehind {
                            drawBlurredRoundRect(
                                topLeft = Offset.Zero,
                                size = size,
                                corner = 40.dp.toPx(),
                                color = Color.Black.copy(alpha = 0.20f),
                                sigma = 35.dp.toPx(),
                            )
                        }
                        .clip(RoundedCornerShape(40.dp))
                        .background(colors.glassFill)
                        .border(1.dp, colors.glassBorder, RoundedCornerShape(40.dp)),
                )
            }
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

// ───────────────────────── «Настрой свой матч» ─────────────────────────
//
// Геометрия, размеры шрифта и цвета ЗАМЕРЕНЫ по Export.pdf (3 страницы, 1920×1080 @2x).
// Все координаты — px макета (1 dp = 1 px) внутри карточки 794×612 (её левый верх на макете = 563, 234).
//
// Шрифт Aqum Two Classic: заголовок 32, подписи секций 23, пресеты 19, «3/5 партий» 20,
// цифры в шапках колонок 32, значения 15/25 — 20 с разрядкой 2.2. Тексты ставятся по БАЗОВОЙ ЛИНИИ
// (в макете это нижний край заглавных букв), поэтому положение не зависит от метрик шрифта.

private val MsLight = Color(0xFFE2EEF7)       // обводки, перемычка шапки колонки, внешний контур текста
private val MsDark = Color(0xFF545E67)        // тёмная подложка контролов и панели
private val MsGray = Color(0xFFD8D8D8)        // невыбранная кнопка
private val MsGrayHover = Color(0xFFE4E4E4)   // невыбранная кнопка под курсором
private val MsTextOn = Color(0xFF1F70F5)      // текст выбранной кнопки / шапок колонок
private val MsTextOff = Color(0xFF72A6FF)     // текст невыбранной кнопки
private val MsStrokeOn = Color(0xFFE2EEF7)    // внешний контур текста на белом
private val MsStrokeOff = Color(0xFFDBDFE3)   // внешний контур текста на сером

private class MsText(val text: String, val cx: Float, val baseline: Float)

/** Кнопка внутри контрола: x, y, w, h — относительно левого верха контрола. */
private class MsOption(val x: Float, val y: Float, val w: Float, val h: Float, val texts: List<MsText>)

/** Позиция колонки очков на макете (левый верх колонки 70×140 в координатах карточки). */
private class MsColumnSpot(val x: Float, val y: Float)

// Режим «до 3 партий» (страницы 1–2 макета): колонки 1, 2, ТБ; ТБ на макете приподнята на 2 px.
private val MsSpots3 = listOf(MsColumnSpot(227f, 343f), MsColumnSpot(362f, 343f), null, null, MsColumnSpot(497f, 341f))

// Режим «до 5 партий» (страница 3): колонки 1, 2, 3, 4, ТБ.
private val MsSpots5 = listOf(
    MsColumnSpot(192f, 342f), MsColumnSpot(275f, 342f), MsColumnSpot(362f, 342f),
    MsColumnSpot(447f, 342f), MsColumnSpot(532f, 342f),
)

/** Две кнопки 15 / 25 в колонке (45×40, радиус 8). */
private val MsValueOptions = listOf(
    MsOption(12.5f, 46f, 45f, 40f, listOf(MsText("15", 34.6f, 73f))),
    MsOption(12.5f, 91f, 45f, 40f, listOf(MsText("25", 34.6f, 118f))),
)

/**
 * Текст как в Figma: заливка + внешний контур 1 px (штрих 2 px по центру, внутренняя половина
 * перекрыта заливкой). Позиция — по центру [cx] и базовой линии [baseline] (px макета).
 * [letterGap] > 0 — разрядка: символы рисуются по одному (не зависит от того, считает ли
 * движок текста замыкающий интервал).
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.msText(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    color: Color,
    stroke: Color,
    cx: Float,
    baseline: Float,
    letterGap: Float = 0f,
) {
    val u = density
    val strokeStyle = Stroke(width = 2f * u, join = StrokeJoin.Round)
    if (letterGap == 0f) {
        val layout = measurer.measure(text, style)
        val w = layout.getLineRight(0) - layout.getLineLeft(0)
        val topLeft = Offset(cx * u - w / 2f, baseline * u - layout.firstBaseline)
        drawText(layout, color = stroke, topLeft = topLeft, drawStyle = strokeStyle)
        drawText(layout, color = color, topLeft = topLeft)
    } else {
        val layouts = text.map { measurer.measure(it.toString(), style) }
        val widths = layouts.map { it.getLineRight(0) - it.getLineLeft(0) }
        val total = widths.sum() + letterGap * u * (text.length - 1)
        var x = cx * u - total / 2f
        layouts.forEachIndexed { i, layout ->
            val topLeft = Offset(x, baseline * u - layout.firstBaseline)
            drawText(layout, color = stroke, topLeft = topLeft, drawStyle = strokeStyle)
            drawText(layout, color = color, topLeft = topLeft)
            x += widths[i] + letterGap * u
        }
    }
}

/** Плавное появление секции: прозрачность 0→1 и подъём на 10 px, с задержкой [delayMs]. */
@Composable
private fun rememberMsEnter(delayMs: Int): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(260, delayMillis = delayMs, easing = FastOutSlowInEasing))
    }
    return progress.asState()
}

private fun Modifier.msEnter(progress: State<Float>): Modifier = graphicsLayer {
    alpha = progress.value
    translationY = (1f - progress.value) * 10f * density
}

/** Статичная подпись по базовой линии (заголовок и названия секций). */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun MsLabel(
    text: String,
    style: TextStyle,
    color: Color,
    cx: Float,
    baseline: Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val stroke = ScoreKeeperTheme.colors.textStroke
    Canvas(modifier.fillMaxSize()) {
        msText(measurer, text, style, color, stroke, cx, baseline)
    }
}

/**
 * Группа взаимоисключающих кнопок: серые плашки, белый «бегунок» выбранной (плавно переезжает
 * пружиной), цвет текста перетекает серый→синий, нажатие — сжатие, наведение — подсветка.
 * Координаты кнопок и текстов — относительно левого верха группы ([size]).
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTextApi::class)
@Composable
private fun MsOptionGroup(
    size: DpSize,
    options: List<MsOption>,
    selected: Int,
    radius: Dp,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    letterGap: Float = 0f,
    interactive: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val target = options[selected]
    val slide = spring<Float>(dampingRatio = 0.74f, stiffness = 420f)
    val thumbX by animateFloatAsState(target.x, slide, label = "msThumbX")
    val thumbY by animateFloatAsState(target.y, slide, label = "msThumbY")

    val sources = remember(options.size) { List(options.size) { MutableInteractionSource() } }
    val on = options.indices.map { i ->
        animateFloatAsState(if (i == selected) 1f else 0f, tween(200), label = "msOn$i").value
    }
    val pressed = sources.map { it.collectIsPressedAsState().value }
    val hovered = sources.map { it.collectIsHoveredAsState().value }
    val press = options.indices.map { i ->
        animateFloatAsState(
            targetValue = if (pressed[i]) 0.94f else 1f,
            animationSpec = if (pressed[i]) tween(70) else spring(dampingRatio = 0.45f, stiffness = 500f),
            label = "msPress$i",
        ).value
    }
    val hover = options.indices.map { i ->
        animateFloatAsState(if (hovered[i] && i != selected) 1f else 0f, tween(140), label = "msHover$i").value
    }
    val shape = RoundedCornerShape(radius)

    Box(modifier.size(size)) {
        // 1. серые плашки
        options.forEachIndexed { i, o ->
            Box(
                Modifier.offset(o.x.dp, o.y.dp).size(o.w.dp, o.h.dp)
                    .graphicsLayer { scaleX = press[i]; scaleY = press[i] }
                    .clip(shape)
                    .background(lerp(MsGray, MsGrayHover, hover[i])),
            )
        }
        // 2. белый бегунок выбранной кнопки
        Box(
            Modifier.size(target.w.dp, target.h.dp)
                .graphicsLayer {
                    translationX = thumbX * density
                    translationY = thumbY * density
                    scaleX = press[selected]; scaleY = press[selected]
                }
                .clip(shape)
                .background(Color.White),
        )
        // 3. тексты
        Canvas(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, o ->
                val color = lerp(MsTextOff, MsTextOn, on[i])
                val stroke = lerp(MsStrokeOff, MsStrokeOn, on[i])
                o.texts.forEach { t ->
                    msText(measurer, t.text, textStyle, color, stroke, t.cx, t.baseline, letterGap)
                }
            }
        }
        // 4. зоны нажатия
        if (interactive) {
            options.forEachIndexed { i, o ->
                Box(
                    Modifier.offset(o.x.dp, o.y.dp).size(o.w.dp, o.h.dp)
                        .hoverable(sources[i])
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(interactionSource = sources[i], indication = null) { onSelect(i) },
                )
            }
        }
    }
}

/** Колонка очков партии: рамка 70×140, шапка с номером, перемычка и две кнопки 15 / 25. */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun MsColumn(
    label: String,
    value: Int,
    headerStyle: TextStyle,
    valueStyle: TextStyle,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    onValue: (Int) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val ring = ScoreKeeperTheme.colors.cardBorder
    val shape = RoundedCornerShape(20.5.dp)
    Box(modifier.size(70.dp, 140.dp)) {
        Box(Modifier.fillMaxSize().clip(shape).background(MsDark).border(5.dp, ring, shape))
        Canvas(Modifier.fillMaxSize()) {
            val u = density
            // белая шапка 60×33 со скруглением 15.5 сверху
            val header = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 5f * u, top = 5f * u, right = 65f * u, bottom = 38f * u,
                        topLeftCornerRadius = CornerRadius(15.5f * u),
                        topRightCornerRadius = CornerRadius(15.5f * u),
                        bottomRightCornerRadius = CornerRadius.Zero,
                        bottomLeftCornerRadius = CornerRadius.Zero,
                    ),
                )
            }
            drawPath(header, Color.White)
            // перемычка под шапкой — на всю ширину колонки, 5 px
            drawRect(ring, topLeft = Offset(0f, 38f * u), size = Size(70f * u, 5f * u))
            msText(measurer, label, headerStyle, MsTextOn, MsStrokeOn, 35f, 33f)
        }
        MsOptionGroup(
            size = DpSize(70.dp, 140.dp),
            options = MsValueOptions,
            selected = if (value == 15) 0 else 1,
            radius = 8.dp,
            textStyle = valueStyle,
            letterGap = 2.2f,
            interactive = interactive,
            onSelect = { onValue(if (it == 0) 15 else 25) },
        )
    }
}

/**
 * Колонка, которая переезжает между раскладками «3 партии» ↔ «5 партий». Колонки 3 и 4 в режиме
 * до 3 партий скрыты: при переключении они проявляются на своих местах, остальные расходятся.
 */
@Composable
private fun MsAnimatedColumn(
    spot3: MsColumnSpot?,
    spot5: MsColumnSpot,
    five: Boolean,
    label: String,
    value: Int,
    headerStyle: TextStyle,
    valueStyle: TextStyle,
    enter: State<Float>,
    onValue: (Int) -> Unit,
) {
    val visible = five || spot3 != null
    val spot = if (five) spot5 else (spot3 ?: spot5)
    val move = spring<Float>(dampingRatio = 0.82f, stiffness = 330f)
    val x by animateFloatAsState(spot.x, move, label = "msColX")
    val y by animateFloatAsState(spot.y, move, label = "msColY")
    val appear by animateFloatAsState(if (visible) 1f else 0f, tween(240), label = "msColAlpha")
    if (visible || appear > 0.01f) {
        MsColumn(
            label = label,
            value = value,
            headerStyle = headerStyle,
            valueStyle = valueStyle,
            interactive = visible,
            modifier = Modifier
                .msEnter(enter)
                .graphicsLayer {
                    translationX = x * density
                    translationY = y * density
                    alpha = appear
                    val s = 0.88f + 0.12f * appear
                    scaleX = s; scaleY = s
                },
            onValue = onValue,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun VolleyballSettingsDialog(
    settings: VolleyballMatchSettings,
    darkTheme: Boolean,
    language: AppLanguage,
    onBack: () -> Unit,
    onConfirm: (VolleyballMatchSettings) -> Unit,
) {
    ScoreKeeperTheme(colors = colorsFor(darkTheme), strings = stringsFor(language)) {
        val colors = ScoreKeeperTheme.colors
        val type = ScoreKeeperTheme.typography
        val strings = ScoreKeeperTheme.strings
        var draft by remember(settings) { mutableStateOf(settings) }
        val russian = language == AppLanguage.RU
        RegisterHotkeyLayer(onBack = onBack, onConfirm = { onConfirm(draft) })

        fun changeSetCount(count: Int) {
            draft = draft.copy(
                preset = volleyballPresetFor(count, draft.setPoints),
                setCount = count,
            )
        }

        fun changeSetPoints(index: Int, value: Int) {
            val points = draft.setPoints.toMutableList()
            points[index] = value
            draft = draft.copy(
                preset = volleyballPresetFor(draft.setCount, points),
                setPoints = points,
            )
        }

        fun selectPreset(index: Int) {
            draft = when (index) {
                0 -> VolleyballMatchSettings(VolleyballPreset.SHORT, 3, listOf(15, 15, 15, 25, 15))
                1 -> VolleyballMatchSettings(VolleyballPreset.LONG, 5, listOf(25, 25, 25, 25, 15))
                else -> draft.copy(preset = VolleyballPreset.CUSTOM)
            }
        }

        val title32 = type.teamLabel.copy(fontSize = 32.sp)
        val label23 = type.teamLabel.copy(fontSize = 23.sp)
        val preset19 = type.teamLabel.copy(fontSize = 19.sp)
        val sets20 = type.teamLabel.copy(fontSize = 20.sp)
        val value20 = type.teamLabel.copy(fontSize = 20.sp)

        val presetTexts = if (russian) {
            listOf("КОРОТКАЯ" to "ИГРА", "ДЛИННАЯ" to "ИГРА", "СВОЯ" to "ИГРА")
        } else {
            listOf("SHORT" to "GAME", "LONG" to "GAME", "CUSTOM" to "GAME")
        }
        val presetOptions = presetTexts.mapIndexed { i, (l1, l2) ->
            val x = 10f + 135f * i
            MsOption(
                x, 10f, 130f, 45f,
                listOf(MsText(l1, x + 65f, 28f), MsText(l2, x + 65f, 52f)),
            )
        }
        val setsOptions = listOf(
            MsOption(12f, 10f, 130f, 45f, listOf(MsText(if (russian) "3 ПАРТИИ" else "3 SETS", 77f, 40f))),
            MsOption(154f, 10f, 130f, 45f, listOf(MsText(if (russian) "5 ПАРТИЙ" else "5 SETS", 219f, 40f))),
        )

        val eTitle = rememberMsEnter(0)
        val ePresets = rememberMsEnter(50)
        val eSets = rememberMsEnter(100)
        val ePoints = rememberMsEnter(150)
        val eDone = rememberMsEnter(200)

        MatchDialogScaffold(cardHeight = 612.dp, onBack = onBack, outerGlass = true) {
            // Заголовок
            MsLabel(
                if (russian) "НАСТРОЙ СВОЙ МАТЧ" else "SET UP YOUR MATCH",
                title32, colors.overlayHeading, cx = 396.5f, baseline = 62f,
                modifier = Modifier.msEnter(eTitle),
            )

            // Пресеты
            MsLabel(
                if (russian) "ПРЕСЕТЫ" else "PRESETS",
                label23, colors.overlayHeading, cx = 389f, baseline = 109f,
                modifier = Modifier.msEnter(ePresets),
            )
            Box(Modifier.offset(183.dp, 115.dp).size(420.dp, 65.dp).msEnter(ePresets)) {
                val shape = RoundedCornerShape(20.dp)
                Box(Modifier.fillMaxSize().clip(shape).background(MsDark).border(5.dp, colors.cardBorder, shape))
                MsOptionGroup(
                    size = DpSize(420.dp, 65.dp),
                    options = presetOptions,
                    selected = draft.preset.ordinal,
                    radius = 12.dp,
                    textStyle = preset19,
                    onSelect = { selectPreset(it) },
                )
            }

            // Количество партий
            MsLabel(
                if (russian) "КОЛИЧЕСТВО ПАРТИЙ" else "NUMBER OF SETS",
                label23, colors.overlayHeading, cx = 396.5f, baseline = 212f,
                modifier = Modifier.msEnter(eSets),
            )
            Box(Modifier.offset(249.dp, 219.dp).size(295.dp, 65.dp).msEnter(eSets)) {
                val shape = RoundedCornerShape(20.dp)
                Box(Modifier.fillMaxSize().clip(shape).background(MsDark).border(5.dp, colors.cardBorder, shape))
                MsOptionGroup(
                    size = DpSize(295.dp, 65.dp),
                    options = setsOptions,
                    selected = if (draft.setCount == 3) 0 else 1,
                    radius = 10.dp,
                    textStyle = sets20,
                    onSelect = { changeSetCount(if (it == 0) 3 else 5) },
                )
            }

            // Количество очков в каждой партии
            MsLabel(
                if (russian) "КОЛИЧЕСТВО ОЧКОВ В КАЖДОЙ ПАРТИИ" else "POINTS IN EACH SET",
                label23, colors.overlayHeading, cx = 396.5f, baseline = 316f,
                modifier = Modifier.msEnter(ePoints),
            )
            run {
                val shape = RoundedCornerShape(20.dp)
                Box(
                    Modifier.offset(166.dp, 322.dp).size(462.dp, 180.dp).msEnter(ePoints)
                        .clip(shape).background(MsDark).border(5.dp, colors.cardBorder, shape),
                )
            }
            val five = draft.setCount == 5
            val labels = listOf("1", "2", "3", "4", if (russian) "ТБ" else "TB")
            for (index in 0..4) {
                MsAnimatedColumn(
                    spot3 = MsSpots3[index],
                    spot5 = MsSpots5[index],
                    five = five,
                    label = labels[index],
                    value = draft.setPoints[index],
                    headerStyle = title32,
                    valueStyle = value20,
                    enter = ePoints,
                    onValue = { changeSetPoints(index, it) },
                )
            }

            // Готово
            DoneButton(
                text = strings.done,
                usePathText = language == AppLanguage.RU,
                onClick = { onConfirm(draft) },
                modifier = Modifier.offset(308.dp, 539.dp).msEnter(eDone),
            )
        }
    }
}

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
    RegisterHotkeyLayer(onBack = onBack, onConfirm = { onConfirm(serverSel) })
    MatchDialogScaffold(cardHeight = 582.dp, onBack = onBack) {
        FigmaText(
            strings.tossTitle,
            type.cardTitle.copy(fontSize = 40.sp, lineHeight = 44.sp),
            colors.overlayHeading,
            Modifier.offset(0.dp, 36.dp), TextAlign.Center, 794.dp,
        )
        Box(
            Modifier.offset(107.dp, 138.dp).size(205.dp, 42.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                name1, style = type.teamLabel.copy(color = colors.overlayHeading),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.offset(478.dp, 138.dp).size(205.dp, 42.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                name2, style = type.teamLabel.copy(color = colors.overlayHeading),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.offset(395.dp, 130.dp).size(4.dp, 310.dp).background(colors.divider, RoundedCornerShape(2.dp)))
        ServeCoin(serverSel == 0, 125.dp, onClick = { serverSel = 0 }, modifier = Modifier.offset(141.5.dp, 264.5.dp))
        ServeCoin(serverSel == 1, 125.dp, onClick = { serverSel = 1 }, modifier = Modifier.offset(519.5.dp, 264.5.dp))
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
    RegisterHotkeyLayer(onBack = onBack, onConfirm = { onDisplay(textInput) })
    MatchDialogScaffold(cardHeight = 394.dp, onBack = onBack) {
        FigmaText(
            strings.enterTextTitle,
            type.cardTitle.copy(lineHeight = 40.sp),
            colors.overlayHeading,
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).trackTyping(),
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
    // Keep side edits local until Done is confirmed; backing out must leave the match unchanged.
    var selectedCourtSide by remember(courtSide) { mutableStateOf(courtSide) }
    val submit: () -> Unit = {
        onCourtSideChange(selectedCourtSide)
        onSubmit(
            t1Score.toIntOrNull() ?: 0,
            t2Score.toIntOrNull() ?: 0,
            t1Sets.toIntOrNull() ?: 0,
            t2Sets.toIntOrNull() ?: 0,
            serverSel,
        )
    }
    RegisterHotkeyLayer(onBack = onBack, onConfirm = submit)

    MatchDialogScaffold(cardHeight = 598.dp, onBack = onBack) {
        FigmaText(
            strings.correction, type.cardTitle, colors.overlayHeading,
            Modifier.offset(0.dp, 20.dp), TextAlign.Center, 794.dp,
        )
        // команды и монеты подающего
        Box(
            Modifier.offset(81.dp, 79.dp).size(205.dp, 42.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                name1, style = type.teamLabel.copy(color = colors.overlayHeading),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        ServeCoin(serverSel == 0, 51.dp, onClick = { serverSel = 0 }, modifier = Modifier.offset(285.5.dp, 70.5.dp))
        Box(
            Modifier.offset(447.dp, 79.dp).size(205.dp, 42.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                name2, style = type.teamLabel.copy(color = colors.overlayHeading),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        ServeCoin(serverSel == 1, 51.dp, onClick = { serverSel = 1 }, modifier = Modifier.offset(662.5.dp, 70.5.dp))

        Box(Modifier.offset(391.dp, 79.dp).size(4.dp, 310.dp).background(colors.divider, RoundedCornerShape(2.dp)))

        // очки и партии
        val labelStyle = type.teamLabel.copy(fontSize = 28.sp, color = colors.captionText)
        Box(Modifier.offset(74.dp, 154.dp).size(170.dp, 53.dp), contentAlignment = Alignment.CenterStart) {
            BasicText(strings.pointsTab, style = labelStyle, maxLines = 1, softWrap = false)
        }
        Box(Modifier.offset(445.dp, 154.dp).size(170.dp, 53.dp), contentAlignment = Alignment.CenterStart) {
            BasicText(strings.pointsTab, style = labelStyle, maxLines = 1, softWrap = false)
        }
        Box(Modifier.offset(74.dp, 260.dp).size(170.dp, 53.dp), contentAlignment = Alignment.CenterStart) {
            BasicText(strings.setsLabel, style = labelStyle, maxLines = 1, softWrap = false)
        }
        Box(Modifier.offset(445.dp, 260.dp).size(170.dp, 53.dp), contentAlignment = Alignment.CenterStart) {
            BasicText(strings.setsLabel, style = labelStyle, maxLines = 1, softWrap = false)
        }
        NumPillField(t1Score, { t1Score = it }, Modifier.offset(257.dp, 154.dp))
        NumPillField(t2Score, { t2Score = it }, Modifier.offset(628.dp, 154.dp))
        NumPillField(t1Sets, { t1Sets = it }, Modifier.offset(257.dp, 260.dp))
        NumPillField(t2Sets, { t2Sets = it }, Modifier.offset(628.dp, 260.dp))

        // сторона поля подающей команды
        FigmaText(
            strings.courtSideLabel,
            type.cardTitle.copy(fontSize = 20.sp, lineHeight = 22.sp),
            colors.overlayHeading,
            Modifier.offset(143.dp, 403.dp), TextAlign.Center, 500.dp,
        )
        SideArrow(
            toLeft = true, selected = selectedCourtSide == "L",
            onClick = { selectedCourtSide = "L" },
            modifier = Modifier.offset(200.dp, 446.dp),
        )
        LrBadge(
            courtSide = selectedCourtSide,
            onToggle = { selectedCourtSide = if (selectedCourtSide == "L") "R" else "L" },
            modifier = Modifier.offset(364.dp, 445.dp),
        )
        SideArrow(
            toLeft = false, selected = selectedCourtSide == "R",
            onClick = { selectedCourtSide = "R" },
            modifier = Modifier.offset(422.dp, 446.dp),
        )

        DoneButton(
            text = strings.done,
            usePathText = usePathText,
            onClick = submit,
            modifier = Modifier.offset(308.dp, 524.dp),
        )
    }
}

// ───────────────────────── Предупреждение о закрытии матча ─────────────────────────
//
// Геометрия замерена по макету «Предупреждение.pdf» (1920×1080, карточка 794×411, r50):
// заголовок «ВЫ УВЕРЕНЫ?» 48 (базовая линия 71), три строки 36 с шагом 45
// (базовые линии 179 / 224 / 269, последняя — красная #FF2828), кнопка «ПОДТВЕРДИТЬ»
// 324×55 в точке (235, 320) — та же зелёная кнопка, что в жеребьёвке/выводе текста.
// Цвета берутся из темы, поэтому диалог одинаково выглядит в светлой и тёмной теме:
// карточка — cardSurface, заголовок — overlayHeading, текст — captionText.

/** Красная строка предупреждения («СБРОШЕН») — в обеих темах, как в макете. */
private val CloseWarningRed = Color(0xFFFF2828)

@OptIn(ExperimentalTextApi::class)
@Composable
private fun CloseMatchWarningDialog(
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    val strings = ScoreKeeperTheme.strings
    val measurer = rememberTextMeasurer()
    RegisterHotkeyLayer(onBack = onBack, onConfirm = onConfirm)

    val title48 = type.teamLabel.copy(fontSize = 48.sp)
    val body36 = type.teamLabel.copy(fontSize = 36.sp)

    MatchDialogScaffold(cardHeight = 411.dp, onBack = onBack) {
        Canvas(Modifier.fillMaxSize()) {
            msText(
                measurer, strings.areYouSure, title48,
                colors.overlayHeading, colors.textStroke, 397f, 71f,
            )
            // В RU строки уже умещаются в карточку 794 с запасом (~57 dp с краёв), а в EN средняя строка
            // шире карточки — пропорционально уменьшаем кегль всех трёх строк, чтобы сохранялись поля ≥ 52 dp.
            val lines = listOf(strings.closeWarningLine1, strings.closeWarningLine2, strings.closeWarningLine3)
            val maxW = lines.maxOf { line ->
                val l = measurer.measure(line, body36)
                l.getLineRight(0) - l.getLineLeft(0)
            }
            val maxAllowedW = 690f * density
            val fittedBody = if (maxW > maxAllowedW) {
                body36.copy(fontSize = (36f * (maxAllowedW / maxW)).sp)
            } else {
                body36
            }
            centeredText(measurer, strings.closeWarningLine1, fittedBody, colors.captionText, 397f, 179f)
            centeredText(measurer, strings.closeWarningLine2, fittedBody, colors.captionText, 397f, 224f)
            centeredText(measurer, strings.closeWarningLine3, fittedBody, CloseWarningRed, 397f, 269f)
        }
        GreenActionButton(
            strings.confirm, 324.dp,
            onClick = onConfirm,
            modifier = Modifier.offset(235.dp, 320.dp),
        )
    }
}


// ───────────────────────── Верхняя панель экрана матча ─────────────────────────
//
// Геометрия — из присланного SVG 616×53 (его левый верх стоит на экране в точке (1193, 26)):
// пилюля «ХОТКЕИ» (0,5; 133×42, r10) с клавишей «H», разделители на x=142/312/414, таймер,
// шестерёнка (круг 52×51 с центром 363;26.5) и пилюля «НАЗАД» (439,0; 177×53).

private const val ICON_TIMER_SVG =
    "M186 8.6665H176V11.9998H186V8.6665ZM179.333 30.3332H182.667V20.3332H179.333V30.3332ZM192.717 19.3165L195.083 16.9498C194.367 16.0998 193.583 15.2998 192.733 14.5998L190.367 16.9665C187.712 14.8301 184.407 13.6657 181 13.6665C172.717 13.6665 166 20.3832 166 28.6665C166 36.9498 172.7 43.6665 181 43.6665C183.824 43.668 186.591 42.8719 188.982 41.3698C191.373 39.8677 193.291 37.7207 194.516 35.1761C195.74 32.6315 196.221 29.7928 195.903 26.9869C195.584 24.1811 194.48 21.5222 192.717 19.3165ZM181 40.3332C174.55 40.3332 169.333 35.1165 169.333 28.6665C169.333 22.2165 174.55 16.9998 181 16.9998C187.45 16.9998 192.667 22.2165 192.667 28.6665C192.667 35.1165 187.45 40.3332 181 40.3332Z"
private const val KEYCAP_ICON_OUTER =
    "M102.019 13H116.098C118.898 13 121.168 15.4096 121.168 18.382V33.1479C121.168 36.1203 118.898 38.5299 116.098 38.5299H102.019C99.2189 38.5299 96.949 36.1203 96.949 33.1479V18.382C96.949 15.4096 99.2189 13 102.019 13Z"
private const val KEYCAP_ICON_TOP =
    "M103.657 13H114.512C116.02 13 117.242 14.2975 117.242 15.898V27.3519C117.242 28.9524 116.02 30.2499 114.512 30.2499H103.657C102.149 30.2499 100.927 28.9524 100.927 27.3519V15.898C100.927 14.2975 102.149 13 103.657 13Z"
private const val KEYCAP_ICON_H =
    "M104.362 16.12C104.586 15.9067 104.853 15.8 105.162 15.8C105.471 15.8 105.738 15.9067 105.962 16.12C106.175 16.344 106.282 16.6107 106.282 16.92V20.28H111.722V16.92C111.722 16.6107 111.829 16.344 112.042 16.12C112.266 15.9067 112.533 15.8 112.842 15.8C113.151 15.8 113.418 15.9067 113.642 16.12C113.855 16.344 113.962 16.6107 113.962 16.92V25.88C113.962 26.1893 113.855 26.456 113.642 26.68C113.418 26.8933 113.151 27 112.842 27C112.533 27 112.266 26.8933 112.042 26.68C111.829 26.456 111.722 26.1893 111.722 25.88V22.52H106.282V25.88C106.282 26.1893 106.175 26.456 105.962 26.68C105.738 26.8933 105.471 27 105.162 27C104.853 27 104.586 26.8933 104.362 26.68C104.149 26.456 104.042 26.1893 104.042 25.88V16.92C104.042 16.6107 104.149 16.344 104.362 16.12Z"
private const val BACK_ARROW_BAR = "M498.5 27H476.5M484.75 19L476.5 27L484.75 35"

/** Текст без контура, прижатый влево, по базовой линии (px макета). */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.plainText(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    color: Color,
    x: Float,
    baseline: Float,
) {
    val u = density
    val layout = measurer.measure(text, style)
    drawText(layout, color = color, topLeft = Offset(x * u, baseline * u - layout.firstBaseline))
}

/** Текст без контура, центрированный в точке [cx], по базовой линии (px макета). */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.centeredText(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    color: Color,
    cx: Float,
    baseline: Float,
) {
    val u = density
    val layout = measurer.measure(text, style)
    val w = layout.getLineRight(0) - layout.getLineLeft(0)
    drawText(layout, color = color, topLeft = Offset(cx * u - w / 2f, baseline * u - layout.firstBaseline))
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalTextApi::class)
@Composable
private fun ScoreTopBar(
    timeText: String,
    hotkeysText: String,
    backText: String,
    onHotkeys: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    val measurer = rememberTextMeasurer()
    val timerPath = remember {
        PathParser().parsePathString(ICON_TIMER_SVG).toPath().apply { fillType = PathFillType.EvenOdd }
    }
    val keyOuter = remember { PathParser().parsePathString(KEYCAP_ICON_OUTER).toPath() }
    val keyTop = remember { PathParser().parsePathString(KEYCAP_ICON_TOP).toPath() }
    val keyH = remember { PathParser().parsePathString(KEYCAP_ICON_H).toPath() }
    val arrowPath = remember { PathParser().parsePathString(BACK_ARROW_BAR).toPath() }
    val label20 = type.teamLabel.copy(fontSize = 20.sp)
    val digits24 = type.teamLabel.copy(fontSize = 24.sp)
    val back15 = type.teamLabel.copy(fontSize = 15.sp)

    Box(modifier.size(616.dp, 53.dp)) {
        // разделители (2 px, скруглённые концы)
        for (x in listOf(141f, 311f, 413f)) {
            Box(
                Modifier.offset(x.dp, 2.dp).size(2.dp, 50.dp)
                    .clip(RoundedCornerShape(1.dp)).background(colors.barDivider),
            )
        }

        // таймер: иконка и цифры (не кликабельны)
        Canvas(Modifier.fillMaxSize()) {
            val u = density
            withTransform({ scale(u, u, Offset.Zero) }) { drawPath(timerPath, colors.barFg) }
            plainText(measurer, timeText, digits24, colors.barFg, 211.5f, 35f)
        }

        // пилюля «ХОТКЕИ [H]»
        val hotInteraction = remember { MutableInteractionSource() }
        val hotScale = rememberPressScale(hotInteraction, hotKey = HotKey.H)
        Box(
            Modifier.offset(0.dp, 5.dp).size(133.dp, 42.dp)
                .graphicsLayer { scaleX = hotScale; scaleY = hotScale }
                .clip(RoundedCornerShape(10.dp)).background(colors.barPill)
                .hoverable(hotInteraction).pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = hotInteraction, indication = null, onClick = onHotkeys),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val u = density
                plainText(measurer, hotkeysText, label20, colors.barFg, 8.8f, 28f)
                // клавиша «H» из SVG (координаты SVG минус смещение пилюли по Y = 5)
                withTransform({ translate(0f, -5f * u); scale(u, u, Offset.Zero) }) {
                    drawPath(keyOuter, colors.barPill)
                    drawPath(keyOuter, colors.barFg, style = Stroke(2f))
                    clipPath(keyOuter) {
                        drawLine(colors.barFg, Offset(101.564f, 29.8911f), Offset(97.989f, 36.0321f), 2f)
                        drawLine(colors.barFg, Offset(116.644f, 30.0293f), Offset(120.349f, 36.5153f), 2f)
                    }
                    drawPath(keyTop, colors.barFg, style = Stroke(2f))
                    drawPath(keyH, colors.barFg)
                }
            }
        }

        // шестерёнка
        SettingsButton(onClick = onSettings, modifier = Modifier.offset(337.dp, 1.dp))

        // пилюля «← НАЗАД»
        val backInteraction = remember { MutableInteractionSource() }
        val backScale = rememberPressScale(backInteraction, hotKey = HotKey.ESC)
        Box(
            Modifier.offset(439.dp, 0.dp).size(177.dp, 53.dp)
                .graphicsLayer { scaleX = backScale; scaleY = backScale }
                .clip(RoundedCornerShape(26.5.dp)).background(colors.barBackFill)
                .border(2.dp, colors.barBackBorder, RoundedCornerShape(26.5.dp))
                .hoverable(backInteraction).pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = backInteraction, indication = null, onClick = onBack),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val u = density
                withTransform({ translate(-439f * u, 0f); scale(u, u, Offset.Zero) }) {
                    drawPath(
                        arrowPath, colors.barFgStrong,
                        style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                plainText(measurer, backText, back15, colors.barText, 85.9f, 32f)
            }
        }
    }
}

// ───────────────────────── Экран «ХОТКЕИ» ─────────────────────────
//
// Геометрия замерена по векторному макету Export_1.pdf (стр. 2). Карточка 794×564, все координаты —
// внутри неё. Клавиши нарисованы по SVG клавиш: основание, тело, белая грань, диагонали,
// обводка #222 и значок. Список клавиш — тот же, что реально обрабатывает приложение.

private val KeyBaseColor = Color(0xFFD7DFE2)
private val KeyOutlineColor = Color(0xFF222222)
private val KeyPressedTopColor = Color(0xFFD6EAFF)

/** Что нарисовать внутри клавиши. (H здесь нет: H только открывает этот экран, см. runApp.) */
private enum class KeyGlyph(val hotKey: HotKey) {
    ONE(HotKey.ONE),
    TWO(HotKey.TWO),
    MINUS(HotKey.MINUS),
    TAB(HotKey.TAB),
    BACKSPACE(HotKey.BACKSPACE),
    SPACE(HotKey.SPACE),
    ESC(HotKey.ESC),
    TILDE(HotKey.TILDE),
    F1(HotKey.F1),
    F2(HotKey.F2),
    ENTER(HotKey.ENTER),
}

private class KeycapSpec(val glyph: KeyGlyph, val x: Float, val y: Float, val bodyW: Float)

// Основные клавиши начинаются наравне с кнопками матча (первый ряд — y≈154/159)
// и идут с тем же шагом ~60 px, что и раньше.
private val HotkeyCaps = listOf(
    KeycapSpec(KeyGlyph.ONE, 38.7f, 154.0f, 43.04f),
    KeycapSpec(KeyGlyph.TWO, 38.7f, 213.7f, 43.04f),
    KeycapSpec(KeyGlyph.MINUS, 38.7f, 273.0f, 43.04f),
    KeycapSpec(KeyGlyph.TAB, 38.7f, 332.0f, 65.0f),
    KeycapSpec(KeyGlyph.BACKSPACE, 39.0f, 394.0f, 77.0f),
    KeycapSpec(KeyGlyph.SPACE, 39.0f, 457.0f, 151.0f),
    KeycapSpec(KeyGlyph.ESC, 433.7f, 159.0f, 43.04f),
    KeycapSpec(KeyGlyph.TILDE, 433.7f, 219.0f, 43.04f),
    KeycapSpec(KeyGlyph.F1, 433.7f, 279.0f, 43.04f),
    KeycapSpec(KeyGlyph.F2, 433.7f, 339.0f, 43.04f),
    KeycapSpec(KeyGlyph.ENTER, 434.0f, 401.0f, 85.0f),
)

/** Тело клавиши в локальных координатах (левый верх тела = 0,0), единицы макета.
 *  [dy] — ход верхней грани вниз при нажатии (основание стоит на месте, передняя юбка сжимается). */
private fun DrawScope.drawKeycapShape(bodyW: Float, dy: Float, pressTint: Float) {
    val bodyH = 45.31f
    val bodyRadius = CornerRadius(9.01f, 9.55f)
    val body = Path().apply {
        addRoundRect(RoundRect(0f, dy, bodyW, bodyH, bodyRadius))
    }
    val topFill = lerp(Color.White, KeyPressedTopColor, pressTint)
    // основание: остаётся на месте, клавиша утапливается в него
    drawRoundRect(
        KeyBaseColor, Offset(-1.69f, 2.55f), Size(bodyW + 3.17f, 47.14f), CornerRadius(9.7f, 10.3f),
    )
    drawPath(body, KeyBaseColor)
    clipPath(body) {
        // светлая левая грань
        val bevel = Path().apply {
            moveTo(-4.04f, -5.04f + dy); lineTo(10.30f, -5.04f + dy); lineTo(10.30f, 27.67f + dy)
            lineTo(8.66f, 29.98f + dy); lineTo(1.85f, 40.87f); lineTo(-0.92f, 45.77f)
            lineTo(-4.04f, 47.76f); close()
        }
        drawPath(bevel, topFill)
    }
    // верхняя грань клавиши
    drawRoundRect(
        topFill, Offset(7.07f, dy), Size(bodyW - 7.07f - 6.98f, 30.61f), CornerRadius(4.85f, 5.14f),
    )
    clipPath(body) {
        drawLine(KeyOutlineColor, Offset(8.20f, 29.98f + dy), Offset(1.85f, 40.87f), 4f)
        drawLine(KeyOutlineColor, Offset(bodyW - 8.04f, 30.22f + dy), Offset(bodyW - 1.46f, 41.73f), 4f)
    }
    drawPath(body, KeyOutlineColor, style = Stroke(4f))
    drawRoundRect(
        KeyOutlineColor, Offset(7.07f, dy), Size(bodyW - 7.07f - 6.98f, 30.61f), CornerRadius(4.85f, 5.14f),
        style = Stroke(4f),
    )
}

/** Линейные значки клавиш (координаты — от левого верха тела клавиши). */
private fun DrawScope.drawKeycapGlyph(glyph: KeyGlyph) {
    val ink = Color.Black
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float) =
        drawLine(ink, Offset(x1, y1), Offset(x2, y2), w, StrokeCap.Round)
    fun poly(w: Float, closed: Boolean, vararg p: Float) {
        val path = Path().apply {
            moveTo(p[0], p[1])
            var i = 2
            while (i < p.size) { lineTo(p[i], p[i + 1]); i += 2 }
            if (closed) close()
        }
        drawPath(path, ink, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    when (glyph) {
        KeyGlyph.MINUS -> line(16.97f, 15.4f, 25.61f, 15.4f, 5.04f)
        KeyGlyph.TAB -> {
            line(21.83f, 15.85f, 38.33f, 15.85f, 3.6f)
            poly(3.6f, false, 33.13f, 10.25f, 38.73f, 15.85f, 33.13f, 21.45f)
            line(42.93f, 8.45f, 42.93f, 23.25f, 3.6f)
        }
        KeyGlyph.BACKSPACE -> {
            poly(2.9f, true, 27.54f, 15.85f, 34.04f, 7.85f, 49.54f, 7.85f, 49.54f, 23.85f, 34.04f, 23.85f)
            line(39.04f, 12.75f, 45.24f, 18.95f, 2.9f)
            line(45.24f, 12.75f, 39.04f, 18.95f, 2.9f)
        }
        KeyGlyph.SPACE -> poly(3.6f, false, 60.54f, 14.05f, 60.54f, 21.55f, 90.54f, 21.55f, 90.54f, 14.05f)
        KeyGlyph.ENTER -> {
            poly(3.6f, false, 51.54f, 7.85f, 51.54f, 18.35f, 33.54f, 18.35f)
            poly(3.6f, false, 38.94f, 23.55f, 33.54f, 18.35f, 38.94f, 13.15f)
        }
        else -> Unit
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun HotkeysOverlay(
    darkTheme: Boolean,
    language: AppLanguage,
    onBack: () -> Unit,
) {
    ScoreKeeperTheme(colors = colorsFor(darkTheme), strings = stringsFor(language)) {
        val colors = ScoreKeeperTheme.colors
        val type = ScoreKeeperTheme.typography
        val russian = language == AppLanguage.RU
        val measurer = rememberTextMeasurer()
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current
        var closingByEsc by remember { mutableStateOf(false) }
        // Снимаем фокус с кнопок и полей ввода под оверлеем, чтобы нажатия в окне хоткеев
        // гарантированно не активировали элементы матча и не отправляли команды на бэкенд.
        LaunchedEffect(Unit) {
            focusManager.clearFocus(force = true)
            Hotkeys.typing = false
        }
        DisposableEffect(Unit) {
            onDispose { Hotkeys.clearPressed() }
        }
        // По Esc сначала показываем короткий ход клавиши Esc вниз, затем закрываем окно;
        // Enter оставляем свободным (onConfirm = null), чтобы его тоже можно было проверить в окне хоткеев.
        RegisterHotkeyLayer(
            onBack = {
                if (!closingByEsc) {
                    closingByEsc = true
                    scope.launch { delay(125); onBack() }
                }
            },
            onConfirm = null,
        )

        val pressMap = KeyGlyph.entries.associateWith { g -> rememberHotkeyPress(g.hotKey) }

        val title48 = type.teamLabel.copy(fontSize = 48.sp)
        val section24 = type.teamLabel.copy(fontSize = 24.sp)
        val label14 = type.teamLabel.copy(fontSize = 14.sp)
        val glyph26 = type.teamLabel.copy(fontSize = 26.sp)
        val glyph13 = type.teamLabel.copy(fontSize = 13.sp)
        // «Esc» — шрифт BloggerSans-Medium (fonts/BloggerSans-Medium.ttf), как в макете.
        val escStyle = TextStyle(fontFamily = BloggerSans, fontSize = 12.sp)

        // подпись: привязанная клавиша (для подсветки при нажатии), текст, x, базовая линия
        class Lbl(val glyph: KeyGlyph, val text: String, val x: Float, val y: Float)
        val labels = if (russian) listOf(
            Lbl(KeyGlyph.ONE, "ДОБАВИТЬ ОЧКО", 97f, 175f), Lbl(KeyGlyph.ONE, "КОМАНДЕ 1", 97f, 193f),
            Lbl(KeyGlyph.TWO, "ДОБАВИТЬ ОЧКО", 97f, 234.7f), Lbl(KeyGlyph.TWO, "КОМАНДЕ 2", 97f, 252.7f),
            Lbl(KeyGlyph.MINUS, "ОТКАТ", 97f, 300f),
            Lbl(KeyGlyph.TAB, "КОРРЕКТИРОВКА", 117.7f, 361f),
            Lbl(KeyGlyph.BACKSPACE, "СБРОС", 133f, 421f),
            Lbl(KeyGlyph.SPACE, "НАЧАТЬ/", 207f, 468f),
            Lbl(KeyGlyph.SPACE, "ПРОДОЛЖИТЬ/", 207f, 486f),
            Lbl(KeyGlyph.SPACE, "ЗАВЕРШИТЬ МАТЧ", 207f, 504f),
            Lbl(KeyGlyph.ESC, "НАЗАД", 492.2f, 184f),
            Lbl(KeyGlyph.TILDE, "НАСТРОЙКИ", 492f, 244f),
            Lbl(KeyGlyph.F1, "ЗАКРЫТЬ", 492f, 297f), Lbl(KeyGlyph.F1, "ПРИЛОЖЕНИЕ", 492f, 315f),
            Lbl(KeyGlyph.F2, "СВЕРНУТЬ", 492f, 357f), Lbl(KeyGlyph.F2, "ПРИЛОЖЕНИЕ", 492f, 375f),
            Lbl(KeyGlyph.ENTER, "ГОТОВО/", 536f, 422f), Lbl(KeyGlyph.ENTER, "ПОДТВЕРДИТЬ", 536f, 440f),
        ) else listOf(
            Lbl(KeyGlyph.ONE, "ADD POINT TO", 97f, 175f), Lbl(KeyGlyph.ONE, "TEAM 1", 97f, 193f),
            Lbl(KeyGlyph.TWO, "ADD POINT TO", 97f, 234.7f), Lbl(KeyGlyph.TWO, "TEAM 2", 97f, 252.7f),
            Lbl(KeyGlyph.MINUS, "UNDO", 97f, 300f),
            Lbl(KeyGlyph.TAB, "CORRECTION", 117.7f, 361f),
            Lbl(KeyGlyph.BACKSPACE, "RESET", 133f, 421f),
            Lbl(KeyGlyph.SPACE, "START /", 207f, 468f),
            Lbl(KeyGlyph.SPACE, "CONTINUE /", 207f, 486f),
            Lbl(KeyGlyph.SPACE, "FINISH MATCH", 207f, 504f),
            Lbl(KeyGlyph.ESC, "BACK", 492.2f, 184f),
            Lbl(KeyGlyph.TILDE, "SETTINGS", 492f, 244f),
            Lbl(KeyGlyph.F1, "CLOSE", 492f, 297f), Lbl(KeyGlyph.F1, "THE APP", 492f, 315f),
            Lbl(KeyGlyph.F2, "MINIMIZE", 492f, 357f), Lbl(KeyGlyph.F2, "THE APP", 492f, 375f),
            Lbl(KeyGlyph.ENTER, "DONE /", 536f, 422f), Lbl(KeyGlyph.ENTER, "CONFIRM", 536f, 440f),
        )

        MatchDialogScaffold(cardHeight = 564.dp, onBack = onBack) {
            Canvas(Modifier.fillMaxSize()) {
                val u = density
                // заголовок и названия колонок
                msText(
                    measurer, if (russian) "ХОТКЕИ" else "HOTKEYS", title48,
                    colors.overlayHeading, colors.textStroke, 396.2f, 70f,
                )
                msText(
                    measurer, if (russian) "КНОПКИ МАТЧА" else "MATCH KEYS", section24,
                    colors.overlayHeading, colors.textStroke, 197f, 124f,
                )
                msText(
                    measurer, if (russian) "ОСНОВНЫЕ" else "GENERAL", section24,
                    colors.overlayHeading, colors.textStroke, 593.7f, 124f,
                )
                // вертикальный разделитель колонок
                drawRoundRect(
                    colors.cardBorder, Offset(396f * u, 123f * u), Size(2f * u, 400f * u),
                    CornerRadius(1f * u),
                )
                // подписи (при нажатии клавиши её описание подсвечивается акцентным цветом темы)
                for (l in labels) {
                    val p = (pressMap[l.glyph] ?: 0f).coerceIn(0f, 1f)
                    val col = lerp(colors.captionText, colors.overlayHeading, p)
                    plainText(measurer, l.text, label14, col, l.x + 2.5f * p, l.y)
                }
                // клавиши
                for (cap in HotkeyCaps) {
                    val rawPress = pressMap[cap.glyph] ?: 0f
                    val p = rawPress.coerceIn(0f, 1f)
                    val dy = (6.5f * rawPress).coerceIn(-0.8f, 8.5f)
                    withTransform({ translate(cap.x * u, cap.y * u); scale(u, u, Offset.Zero) }) {
                        drawKeycapShape(cap.bodyW, dy = dy, pressTint = p)
                        withTransform({ translate(0f, dy) }) {
                            drawKeycapGlyph(cap.glyph)
                        }
                    }
                    // значки-буквы смещаются вниз вместе с верхней гранью клавиши
                    when (cap.glyph) {
                        KeyGlyph.ONE -> plainText(measurer, "1", glyph26, Color.Black, cap.x + 16.6f, cap.y + 25f + dy)
                        KeyGlyph.TWO -> plainText(measurer, "2", glyph26, Color.Black, cap.x + 12.6f, cap.y + 25f + dy)
                        // «Esc» целиком шрифтом BloggerSans-Medium: строчные «sc» там естественно меньше «E».
                        KeyGlyph.ESC -> plainText(measurer, "Esc", escStyle, Color.Black, cap.x + 11.87f, cap.y + 19f + dy)
                        KeyGlyph.TILDE -> plainText(measurer, "~", glyph26, Color.Black, cap.x + 13.6f, cap.y + 24.7f + dy)
                        KeyGlyph.F1 -> plainText(measurer, "F1", glyph13, Color.Black, cap.x + 14.6f, cap.y + 20f + dy)
                        KeyGlyph.F2 -> plainText(measurer, "F2", glyph13, Color.Black, cap.x + 12.6f, cap.y + 20f + dy)
                        else -> Unit
                    }
                }
            }
        }
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
    volleyballSettings: VolleyballMatchSettings,
    tickerText: String,
    onTickerUpdate: (String) -> Unit,
    darkTheme: Boolean,
    language: AppLanguage,
    onSettings: () -> Unit,
    onHotkeys: () -> Unit,
    courtSide: String,
    onCourtSideChange: (String) -> Unit
) {
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var secondsElapsed by remember { mutableStateOf(0) }
    var showTossDialog by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showCorrectionDialog by remember { mutableStateOf(false) }
    var showCloseWarning by remember { mutableStateOf(false) }

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
    // Reopen the finished match by changing only its status. Keep the current set/game,
    // points, serving team and team names instead of calling /start (which resets everything).
    val continueMatchAction = {
        if (sport == "tennis") {
            ApiClient.setScore(
                TennisSetScore(
                    points = match.tennisPoints ?: listOf(0, 0),
                    games = match.tennisGames ?: listOf(match.score1, match.score2),
                    setsWon = listOf(match.sets1, match.sets2),
                    server = match.server,
                    status = "IN_PROGRESS",
                    winner = null,
                ),
                onResult = apply,
                onError = { errorMsg = it },
            )
        } else {
            ApiClient.setScore(
                VolleyballSetScore(
                    sets = listOf(match.score1, match.score2),
                    setsWon = listOf(match.sets1, match.sets2),
                    currentSet = match.currentSet,
                    server = match.server,
                    status = "IN_PROGRESS",
                    winner = null,
                ),
                onResult = apply,
                onError = { errorMsg = it },
            )
        }
    }

    val isTennisSport = sport == "tennis"
    // Только счёт в ноль — команды, статус матча, подающий и таймер сохраняются.
    val resetAction: () -> Unit = {
        if (isTennisSport) {
            ApiClient.setScore(
                TennisSetScore(
                    points = listOf(0, 0),
                    games = listOf(0, 0),
                    setsWon = listOf(0, 0),
                    server = match.server,
                    status = match.status,
                    winner = null,
                ),
                onResult = apply,
                onError = { errorMsg = it },
            )
        } else {
            ApiClient.setScore(
                VolleyballSetScore(
                    sets = listOf(0, 0),
                    setsWon = listOf(0, 0),
                    currentSet = 1,
                    server = match.server,
                    status = match.status,
                    winner = null,
                ),
                onResult = apply,
                onError = { errorMsg = it },
            )
        }
    }
    val undoAction: () -> Unit = { ApiClient.removePoint(apply) { errorMsg = it } }
    val correctionAction: () -> Unit = { showCorrectionDialog = true }
    // При выходе сразу скрываем сообщение на табло: локально и пустой рассылкой по WS.
    // Сбрасываем матч на сервере (/reset), затем уходим на главный экран.
    val doBack: () -> Unit = {
        onTickerUpdate("")
        ApiClient.sendText("", onResult = {})
        ApiClient.reset(onResult = apply, onError = { errorMsg = it })
        onBack()
    }
    // «Назад» при матче в процессе или завершённом — через предупреждение о закрытии
    // (счёт будет сброшен); при неначатом матче выходим сразу, терять нечего.
    val backAction: () -> Unit = {
        if (match.status == "IN_PROGRESS" || match.status == "FINISHED") showCloseWarning = true
        else doBack()
    }
    val mainAction: () -> Unit = when (match.status) {
        "IN_PROGRESS" -> endMatchAction
        "FINISHED" -> continueMatchAction
        else -> startMatchAction
    }

    // Клавиатура: те же действия, что у кнопок, и с теми же условиями доступности.
    RegisterMatchHotkeys(
        MatchHotkeyActions(
            point1 = if (match.status == "IN_PROGRESS") ({ ApiClient.addPoint(0, apply) { errorMsg = it } }) else null,
            point2 = if (match.status == "IN_PROGRESS") ({ ApiClient.addPoint(1, apply) { errorMsg = it } }) else null,
            undo = if (match.status == "IN_PROGRESS") undoAction else null,
            correction = if (match.status != "NOT_STARTED") correctionAction else null,
            reset = if (match.status != "NOT_STARTED") resetAction else null,
            mainAction = mainAction,
            back = backAction,
        ),
    )

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
                    .background(if (darkTheme) Color.Black.copy(alpha = 0.23f) else colors.canvas.copy(alpha = 0.74f)),
            )

            Box(Modifier.align(Alignment.Center).requiredSize(1920.dp, 1080.dp)) {

                // ── Предпросмотр табло зрителей ──
                // Табло как в макете: рамка 1115×684 в точке (68, 85), пространство табло
                // 1129×698 (обводка 14 по центру контура рамки) — в масштабе 1:1, без панели-обёртки.
                Box(
                    Modifier.offset(61.dp, 78.dp).size(1129.dp, 698.dp)
                        .drawBehind {
                            drawBlurredRoundRect(
                                topLeft = Offset(7.dp.toPx(), 7.dp.toPx()),
                                size = Size(1115.dp.toPx(), 684.dp.toPx()),
                                corner = 40.dp.toPx(),
                                color = colors.frameGlow.copy(alpha = if (darkTheme) 0.38f else 0.55f),
                                sigma = 28.dp.toPx(),
                            )
                        },
                ) {
                    AudienceScreen(
                        match = match,
                        logo1 = logo1,
                        logo2 = logo2,
                        tickerText = tickerText,
                        courtSide = courtSide,
                        strings = strings,
                        preview = true,
                        frameStart = colors.frameStart,
                        frameEnd = colors.frameEnd,
                        onDisplayText = { showTextDialog = true },
                    )
                }

                // ── Нижняя левая карточка: партии и подача (геометрия — по присланному SVG) ──
                SetsServePanel(
                    name1 = displayName1,
                    name2 = displayName2,
                    logo1 = logo1,
                    logo2 = logo2,
                    sets1 = match.sets1,
                    sets2 = match.sets2,
                    server = match.server,
                    showServe = match.status != "NOT_STARTED",
                    tabText = "${strings.setTab} ${match.currentSet}",
                    modifier = Modifier.offset(81.dp, 812.dp),
                )

                // ── Верхняя панель: хоткеи, таймер (не кликабелен), настройки, назад ──
                ScoreTopBar(
                    timeText = formatTime(secondsElapsed),
                    hotkeysText = if (language == AppLanguage.RU) "ХОТКЕИ" else "KEYS",
                    backText = strings.back,
                    onHotkeys = onHotkeys,
                    onSettings = onSettings,
                    onBack = backAction,
                    modifier = Modifier.offset(1193.dp, 26.dp),
                )

                // ── Заголовок вида спорта ──
                if (isTennis) {
                    TennisBallIcon(Modifier.offset(1273.dp, 156.dp).size(112.dp))
                } else {
                    Image(
                        painter = painterResource("volleyball_icon.png"),
                        contentDescription = null,
                        modifier = Modifier.offset(1276.dp, 159.dp).size(106.dp),
                    )
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
                            hotKey = HotKey.ONE,
                        )
                        PlusOnePill(
                            onClick = { ApiClient.addPoint(1, apply) { errorMsg = it } },
                            modifier = Modifier.offset(366.dp, 191.dp),
                            hotKey = HotKey.TWO,
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
                        onClick = resetAction,
                        modifier = Modifier.offset(
                            if (match.status == "FINISHED") 1450.dp else 1322.dp,
                            685.dp,
                        ).size(223.dp, 93.dp),
                        hotKey = HotKey.BACKSPACE,
                    ) { ResetIcon(colors.headingBlue) }
                    if (match.status != "FINISHED") {
                        OutlineActionButton(
                            text = strings.undo,
                            onClick = undoAction,
                            modifier = Modifier.offset(1581.dp, 685.dp).size(223.dp, 93.dp),
                            hotKey = HotKey.MINUS,
                        ) { UndoIcon(colors.headingBlue) }
                    }
                    OutlineActionButton(
                        text = strings.correction,
                        onClick = correctionAction,
                        modifier = Modifier.offset(1322.dp, 800.dp).size(481.dp, 93.dp),
                        hotKey = HotKey.TAB,
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
                    onClick = mainAction,
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
                            config = if (isTennis) null else volleyballSettings.toApiConfig(),
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
                    onCourtSideChange = onCourtSideChange,
                    onBack = { showCorrectionDialog = false },
                    onSubmit = { s1, s2, sets1, sets2, server ->
                        if (isTennis) {
                            ApiClient.setScore(
                                TennisSetScore(
                                    points = listOf(0, 0),
                                    games = listOf(s1, s2),
                                    setsWon = listOf(sets1, sets2),
                                    server = server,
                                    status = match.status,
                                    winner = match.winner,
                                ),
                                onResult = apply,
                                onError = { errorMsg = it },
                            )
                        } else {
                            ApiClient.setScore(
                                VolleyballSetScore(
                                    sets = listOf(s1, s2),
                                    setsWon = listOf(sets1, sets2),
                                    currentSet = match.currentSet,
                                    server = server,
                                    status = match.status,
                                    winner = match.winner,
                                ),
                                onResult = apply,
                                onError = { errorMsg = it },
                            )
                        }
                        showCorrectionDialog = false
                    },
                )
            }

            if (showCloseWarning) {
                CloseMatchWarningDialog(
                    onBack = { showCloseWarning = false },
                    onConfirm = { showCloseWarning = false; doBack() },
                )
            }
        }
    }
}

// ============ ТАБЛО ДЛЯ ЗРИТЕЛЕЙ («Экран предпросмотра» из Figma SVG) ============
// Пространство дизайна 1129×698: рамка табло 1115×684 r40 в точке (7,7),
// обводка 14 (градиент #CFE6F5→#81B6D6). Все координаты — из присланного SVG.
// Шрифт табло — Arial. В режиме preview (предпросмотр в админке) поверх рамки —
// пилюли «ПРЕДПРОСМОТР» и «ВЫВЕСТИ ТЕКСТ» из того же SVG.

/** Текст табло (Arial), центрированный в точке (centerX, centerY) пространства дизайна. */
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
            fontFamily = ArialFamily,
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
    strings: AppStrings = RuStrings,
    preview: Boolean = false,
    frameStart: Color = Color(0xFFCFE6F5),
    frameEnd: Color = Color(0xFF81B6D6),
    onDisplayText: () -> Unit = {}
) {
    var live by remember { mutableStateOf(match) }

    LaunchedEffect(match) { live = match }

    // Сообщение на табло: показываем 4 секунды после появления, затем прячем.
    var shownText by remember { mutableStateOf(tickerText) }
    var textNonce by remember { mutableStateOf(0) }
    LaunchedEffect(tickerText) {
        shownText = tickerText
        textNonce++
    }
    LaunchedEffect(textNonce) {
        if (shownText.isNotBlank()) {
            delay(4_000)
            shownText = ""
        }
    }

    DisposableEffect(Unit) {
        // Подписка у каждого табло своя (предпросмотр + ТВ), общее соединение закрывается по последнему.
        val unsubscribe = ApiClient.subscribeWs(
            onScore = { r -> live = MatchState.fromResponse(r) },
            onText = { t -> shownText = t; textNonce++ },
        )
        onDispose { unsubscribe() }
    }

    val panelLeft = remember { PathParser().parsePathString(PANEL_LEFT).toPath() }
    val panelRight = remember { PathParser().parsePathString(PANEL_RIGHT).toPath() }

    // In the admin preview, a right-side serving team appears on the opposite side of
    // the audience board. The real external display keeps its normal team ordering.
    val swapPreviewSides = preview && courtSide == "R"
    val leftTeam = if (swapPreviewSides) live.team2 else live.team1
    val rightTeam = if (swapPreviewSides) live.team1 else live.team2
    val leftSets = if (swapPreviewSides) live.sets2 else live.sets1
    val rightSets = if (swapPreviewSides) live.sets1 else live.sets2
    val leftScore = if (swapPreviewSides) live.score2 else live.score1
    val rightScore = if (swapPreviewSides) live.score1 else live.score2
    val leftLogo = if (swapPreviewSides) logo2 else logo1
    val rightLogo = if (swapPreviewSides) logo1 else logo2
    val visibleServer = if (swapPreviewSides) 1 - live.server else live.server

    Box(modifier = modifier.size(width = 1129.dp, height = 698.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val u = 1.dp.toPx()
            withTransform({ scale(u, u, Offset.Zero) }) {
                val frame = Path().apply {
                    addRoundRect(RoundRect(7f, 7f, 1122f, 691f, CornerRadius(40f, 40f)))
                }
                drawPath(frame, Color(0xFF070F2B))
                clipPath(frame) {
                    // тёмно-синяя диагональная полоса (в SVG — с прозрачностью 0.6)
                    val navy = Path().apply {
                        moveTo(7f, 691f); lineTo(743.3f, 7f); lineTo(1122f, 7f); lineTo(1122f, 212.2f); close()
                    }
                    drawPath(
                        navy,
                        Brush.verticalGradient(listOf(Color(0xFF2A5298), Color(0xFF1E3C72)), startY = 7f, endY = 691f),
                        alpha = 0.6f,
                    )
                    // красная диагональ (в SVG — с прозрачностью 0.9)
                    val red = Path().apply {
                        moveTo(7f, 691f); lineTo(1122f, 100f); lineTo(1122f, 691f); close()
                    }
                    drawPath(
                        red,
                        Brush.horizontalGradient(listOf(Color(0xFFBA0C24), Color(0xFFF21B3F)), startX = 7f, endX = 1122f),
                        alpha = 0.9f,
                    )
                    // матовые панели команд (белый 10%, без контура — как в Figma)
                    withTransform({ translate(-222.7f, -222.7f) }) {
                        drawPath(panelLeft, Color.White.copy(alpha = 0.10f))
                        drawPath(panelRight, Color.White.copy(alpha = 0.10f))
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
                    // The side-direction badge is part of the actual audience board, not its preview.
                    if (!preview) {
                        val bc = Offset(564f, 462f)
                        val swapped = courtSide == "R"
                        drawCircle(Color(0xFF848484), 37f, bc)
                        drawArc(
                            if (swapped) Color(0xFF18305F) else Color(0xFFC31332),
                            startAngle = 90f, sweepAngle = 180f, useCenter = true,
                            topLeft = Offset(bc.x - 32.3f, bc.y - 32.3f), size = Size(64.6f, 64.6f),
                        )
                        drawArc(
                            if (swapped) Color(0xFFC31332) else Color(0xFF18305F),
                            startAngle = -90f, sweepAngle = 180f, useCenter = true,
                            topLeft = Offset(bc.x - 32.3f, bc.y - 32.3f), size = Size(64.6f, 64.6f),
                        )
                        drawLine(Color.White, Offset(564f, 436f), Offset(564f, 486f), strokeWidth = 2f)
                        drawCircle(Color(0xFF070F2B), 36f, bc, style = Stroke(2f))
                    }
                }
                // рамка: градиентная обводка 14
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(frameStart, frameEnd),
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
        if (leftLogo != null) {
            Box(Modifier.offset(253.dp, 123.dp).size(150.dp).clip(CircleShape)) {
                Image(bitmap = leftLogo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        if (rightLogo != null) {
            Box(Modifier.offset(727.dp, 123.dp).size(150.dp).clip(CircleShape)) {
                Image(bitmap = rightLogo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        // Рисуем индикатор после логотипов: если оставить его в Canvas-фоне, загруженные
        // изображения перекроют его, тогда как заглушки (нарисованные Canvas) — нет.
        if (live.status != "NOT_STARTED") {
            ServeGlowDot(
                Modifier.offset(
                    if (visibleServer == 0) 351.dp else 825.dp,
                    237.dp,
                ),
            )
        }

        // Team and score columns are swapped only in the admin preview when the serving
        // team is assigned to the right-hand side.
        BoardText(leftTeam.uppercase(), 328f, 298f, 35.sp, FontWeight.Normal)
        BoardText("$leftSets", 328f, 370f, 62.sp, FontWeight.Bold)
        BoardText(strings.setsLabel, 328f, 419f, 22.sp, FontWeight.Normal)
        BoardText("$leftScore", 328f, 506f, 124.sp, FontWeight.Bold)
        BoardText(strings.pointsTab, 328f, 591f, 36.sp, FontWeight.Normal)

        BoardText(rightTeam.uppercase(), 802f, 298f, 35.sp, FontWeight.Normal)
        BoardText("$rightSets", 802f, 370f, 62.sp, FontWeight.Bold)
        BoardText(strings.setsLabel, 802f, 419f, 22.sp, FontWeight.Normal)
        BoardText("$rightScore", 802f, 506f, 124.sp, FontWeight.Bold)
        BoardText(strings.pointsTab, 802f, 591f, 36.sp, FontWeight.Normal)

        // The side-direction badge is hidden in the admin preview.
        BoardText("${strings.setTab} ${live.currentSet}", 564f, 600f, 35.sp, FontWeight.Normal, width = 211f)
        if (!preview) {
            BoardText(if (courtSide == "R") "R" else "L", 549.5f, 462f, 35.sp, FontWeight.Bold, width = 34f)
            BoardText(if (courtSide == "R") "L" else "R", 579.5f, 462f, 35.sp, FontWeight.Bold, width = 34f)
        }

        // Пилюли предпросмотра (только в панели админки, по SVG «Экран предпросмотра»)
        if (preview) {
            Box(
                Modifier.offset(42.dp, 37.dp).size(230.dp, 53.dp)
                    .clip(RoundedCornerShape(26.5.dp)).background(Color(0xFF032351)),
            )
            Box(
                Modifier.offset(54.dp, 56.dp).size(15.dp)
                    .clip(CircleShape).background(Color(0xFFFA4243)),
            )
            BoardText(strings.preview, 169.3f, 64.9f, 21.sp, FontWeight.Bold, width = 190f)

            val chipInteraction = remember { MutableInteractionSource() }
            val chipScale = rememberPressScale(chipInteraction)
            Box(
                Modifier.offset(857.dp, 37.dp).size(230.dp, 53.dp)
                    .graphicsLayer { scaleX = chipScale; scaleY = chipScale }
                    .clip(RoundedCornerShape(26.5.dp)).background(Color(0xFF032351))
                    .hoverable(chipInteraction).pointerHoverIcon(PointerIcon.Hand)
                    .clickable(interactionSource = chipInteraction, indication = null) { onDisplayText() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    strings.displayTextBtn,
                    color = Color.White,
                    fontFamily = ArialFamily,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 21.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        if (shownText.isNotBlank()) {
            // Пилюля масштабируется под длину текста.
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = 96.dp)
                    .height(124.dp).widthIn(min = 460.dp, max = 1020.dp)
                    .clip(RoundedCornerShape(42.dp))
                    .background(Color(0xFF162C5A))
                    .padding(horizontal = 80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    shownText,
                    color = Color.White,
                    fontFamily = ArialFamily,
                    fontSize = 50.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 50.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}