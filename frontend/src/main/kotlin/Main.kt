import androidx.compose.animation.core.Spring
import ui.screens.MenuScreen
import ui.screens.SettingsOverlay
import ui.screens.TeamSetupOverlay
import ui.theme.AppLanguage
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import com.example.ApiClient
import com.example.ScoreResponse
import com.example.VolleyballSetScore
import org.jetbrains.skia.Image
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
                    tickerText = tickerText, onTickerUpdate = { tickerText = it } // 👈 ПЕРЕДАЕМ ТЕКСТ
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

// ============ АНИМАЦИЯ КНОПОК ============
@Composable
fun AnimatedClickableImage(painter: Painter, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow
        ), label = "scale"
    )

    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(width = 370.dp, height = 140.dp).scale(scale).clickable(
            interactionSource = interactionSource, indication = null, onClick = onClick
        )
    )
}

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


@Composable
fun AnimatedIconButton(
    painter: Painter, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Чуть более быстрая и четкая анимация для маленьких иконок
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
        ), label = "icon_scale"
    )

    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.scale(scale).clickable(
            interactionSource = interactionSource, indication = null, onClick = onClick
        )
    )
}

// ============ ЭКРАН АДМИНКИ ИГРЫ ============
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
    onTickerUpdate: (String) -> Unit
) {
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var secondsElapsed by remember { mutableStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var showTossDialog by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showCorrectionDialog by remember { mutableStateOf(false) }
    var courtSide by remember { mutableStateOf("L") } // Сторона L/R

    LaunchedEffect(errorMsg) {
        if (errorMsg != null) {
            kotlinx.coroutines.delay(3000L); errorMsg = null
        }
    }
    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            while (true) {
                kotlinx.coroutines.delay(1000L); secondsElapsed++
            }
        }
    }

    val apply: (ScoreResponse?) -> Unit = { r -> if (r != null) onMatchUpdate(MatchState.fromResponse(r)) }

    val startMatchAction = { showTossDialog = true }
    val endMatchAction = {
        ApiClient.forceEnd(onResult = { r ->
            if (r != null) {
                onMatchUpdate(MatchState.fromResponse(r)); isTimerRunning = false
            }
        }, onError = { errorMsg = it })
    }

    val (currentBtnImage, currentBtnAction) = when (match.status) {
        "NOT_STARTED" -> painterResource("startmatch.png") to startMatchAction
        "IN_PROGRESS" -> painterResource("ENDMATCH.png") to endMatchAction
        else -> painterResource("continuematch.png") to startMatchAction
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource("ВОЛЕЙБОЛЗАДНИЙФОН.png"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // ПРЕВЬЮ ДЛЯ ЗРИТЕЛЕЙ СЛЕВА
        Box(
            modifier = Modifier.offset(x = 110.dp, y = 110.dp).size(width = 950.dp, height = 615.dp)
                .clip(RoundedCornerShape(16.dp)).border(4.dp, Color(0xFF64B5F6), RoundedCornerShape(16.dp))
        ) {
            AudienceScreen(
                match = match,
                logo1 = logo1,
                logo2 = logo2,
                modifier = Modifier.fillMaxSize(),
                tickerText = tickerText,
                courtSide = courtSide
            )

            // Кнопка ВЫВЕСТИ ТЕКСТ
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Text(
                    "ВЫВЕСТИ ТЕКСТ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color(0xFF1A1A2E).copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                        .clickable { showTextDialog = true }.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // ScorePad СНИЗУ
        ScorePad(
            logo1 = logo1,
            logo2 = logo2,
            name1 = name1.ifBlank { "КОМАНДА 1" },
            name2 = name2.ifBlank { "КОМАНДА 2" },
            score1 = match.sets1, // 👈 ИЗМЕНЕНО: теперь передаем партии
            score2 = match.sets2,
            currentSet = match.currentSet,
            modifier = Modifier.offset(x = 110.dp, y = 815.dp)
        )

        // ВЕРХНЯЯ ПАНЕЛЬ
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(60.dp),
            modifier = Modifier.offset(x = 1460.dp, y = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable { isTimerRunning = !isTimerRunning }) {
                Image(
                    painter = painterResource("timer.png"),
                    contentDescription = null,
                    modifier = Modifier.size(width = 30.dp, height = 35.dp)
                )
                Text(formatTime(secondsElapsed), color = Color.Gray, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            AnimatedIconButton(
                painter = painterResource("setingingame.png"),
                onClick = { isTimerRunning = !isTimerRunning },
                modifier = Modifier.size(width = 35.dp, height = 35.dp)
            )
            Box(
                modifier = Modifier.size(
                    width = 185.dp,
                    height = 55.dp
                )
            ) { AnimatedClickableImage(painterResource("back.png")) { onBack() } }
        }

        Image(
            painter = painterResource("Group.png"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 490.dp, height = 120.dp).offset(x = 1320.dp, y = 160.dp)
        )

        // КАРТОЧКА МАТЧА
        MatchCard(
            logo1,
            name1.ifBlank { "Игрок 1" },
            logo2,
            name2.ifBlank { "Игрок 2" },
            statusText = when (match.status) {
                "FINISHED" -> "Завершено"; "NOT_STARTED" -> "Не начато"; else -> "В процессе"
            },
            isFinished = match.finished,
            score1 = match.score1,
            score2 = match.score2,
            tennisPoints = match.tennisPointsDisplay,
            status = match.status,
            onAddPoint1 = { ApiClient.addPoint(0, apply) { errorMsg = it } },
            onAddPoint2 = { ApiClient.addPoint(1, apply) { errorMsg = it } },
            modifier = Modifier.offset(x = 1320.dp, y = 300.dp)
        )

        // КНОПКИ СБРОС / ОТКАТ
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.offset(x = 1307.dp, y = 575.dp).width(456.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AnimatedGameButton(
                    painterResource("reset.png"),
                    onClick = { isTimerRunning = false; secondsElapsed = 0; ApiClient.reset(apply) { errorMsg = it } },
                    modifier = Modifier.fillMaxWidth().height(95.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                AnimatedGameButton(
                    painterResource("rollback.png"),
                    onClick = { ApiClient.removePoint(apply) { errorMsg = it } },
                    modifier = Modifier.fillMaxWidth().height(95.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // КНОПКА КОРРЕКТИРОВКА
        Box(modifier = Modifier.offset(x = 1307.dp, y = 685.dp)) {
            AnimatedGameButton(
                painterResource("adjustment.png"),
                onClick = { showCorrectionDialog = true },
                modifier = Modifier.size(width = 456.dp, height = 95.dp),
                contentScale = ContentScale.Fit
            )
        }

        // ГЛАВНАЯ КНОПКА
        Box(modifier = Modifier.offset(x = 1307.dp, y = 805.dp)) {
            AnimatedGameButton(
                painter = currentBtnImage,
                onClick = { currentBtnAction() },
                modifier = Modifier.size(width = 456.dp, height = 95.dp),
                contentScale = ContentScale.FillBounds
            )
        }

        // ОШИБКА
        errorMsg?.let { msg ->
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                    .background(Color(0xCCB00020), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(msg, color = Color.White, fontSize = 16.sp)
            }
        }
    }

    // ДИАЛОГ: ЖЕРЕБЬЁВКА
    if (showTossDialog) {
        Dialog(onDismissRequest = { showTossDialog = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.width(400.dp)) {
                Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ЖЕРЕБЬЁВКА", color = Color(0xFF2979FF), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Кто подает первым?", color = Color.Black, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = {
                                ApiClient.startMatch(
                                    sport,
                                    name1.ifBlank { "Игрок 1" },
                                    name2.ifBlank { "Игрок 2" },
                                    0,
                                    onResult = { r ->
                                        if (r != null) {
                                            onMatchUpdate(MatchState.fromResponse(r)); isTimerRunning =
                                                true; secondsElapsed = 0; showTossDialog = false
                                        }
                                    },
                                    onError = { errorMsg = it })
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF))
                        ) { Text("КОМАНДА 1") }

                        Button(
                            onClick = {
                                ApiClient.startMatch(
                                    sport,
                                    name1.ifBlank { "Игрок 1" },
                                    name2.ifBlank { "Игрок 2" },
                                    1,
                                    onResult = { r ->
                                        if (r != null) {
                                            onMatchUpdate(MatchState.fromResponse(r)); isTimerRunning =
                                                true; secondsElapsed = 0; showTossDialog = false
                                        }
                                    },
                                    onError = { errorMsg = it })
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                        ) { Text("КОМАНДА 2") }
                    }
                }
            }
        }
    }

    // ДИАЛОГ: ТЕКСТ
    if (showTextDialog) {
        var textInput by remember { mutableStateOf(tickerText) }
        Dialog(onDismissRequest = { showTextDialog = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.width(500.dp)) {
                Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "ВВЕДИТЕ ТЕКСТ ДЛЯ ВЫВОДА\nЕГО НА ЭКРАН.",
                        color = Color(0xFF2979FF),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                            .background(Color(0xFF455A64), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    if (textInput.isEmpty()) Text(
                                        "...",
                                        color = Color.LightGray
                                    ); innerTextField()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            onTickerUpdate(textInput)
                            ApiClient.sendText(textInput, onResult = {})
                            showTextDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81C784)),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Вывести", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    }

    // ДИАЛОГ: КОРРЕКТИРОВКА
    if (showCorrectionDialog) {
        var t1Score by remember { mutableStateOf(match.score1.toString()) }
        var t2Score by remember { mutableStateOf(match.score2.toString()) }
        var t1Sets by remember { mutableStateOf(match.sets1.toString()) }
        var t2Sets by remember { mutableStateOf(match.sets2.toString()) }

        Dialog(onDismissRequest = { showCorrectionDialog = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.width(600.dp)) {
                Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "КОРРЕКТИРОВКА",
                            color = Color(0xFF2979FF),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        IconButton(onClick = { showCorrectionDialog = false }) {
                            Text(
                                "←",
                                fontSize = 24.sp,
                                color = Color(0xFF2979FF)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("КОМАНДА 1", color = Color(0xFF2979FF), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("ОЧКИ", fontSize = 12.sp)
                            BasicTextField(
                                value = t1Score,
                                onValueChange = { t1Score = it },
                                modifier = Modifier.width(60.dp).height(40.dp)
                                    .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp)),
                                textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 18.sp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("ПАРТИИ", fontSize = 12.sp)
                            BasicTextField(
                                value = t1Sets,
                                onValueChange = { t1Sets = it },
                                modifier = Modifier.width(60.dp).height(40.dp)
                                    .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp)),
                                textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 18.sp)
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("СТОРОНА\nПОДАЧИ", fontSize = 10.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "L",
                                    fontWeight = FontWeight.Bold,
                                    color = if (courtSide == "L") Color(0xFF2979FF) else Color.Gray
                                )
                                Switch(
                                    checked = courtSide == "R",
                                    onCheckedChange = { courtSide = if (it) "R" else "L" })
                                Text(
                                    "R",
                                    fontWeight = FontWeight.Bold,
                                    color = if (courtSide == "R") Color(0xFF2979FF) else Color.Gray
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("КОМАНДА 2", color = Color(0xFF2979FF), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("ОЧКИ", fontSize = 12.sp)
                            BasicTextField(
                                value = t2Score,
                                onValueChange = { t2Score = it },
                                modifier = Modifier.width(60.dp).height(40.dp)
                                    .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp)),
                                textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 18.sp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("ПАРТИИ", fontSize = 12.sp)
                            BasicTextField(
                                value = t2Sets,
                                onValueChange = { t2Sets = it },
                                modifier = Modifier.width(60.dp).height(40.dp)
                                    .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp)),
                                textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 18.sp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            ApiClient.setScore(
                                VolleyballSetScore(
                                    sets = listOf(t1Score.toIntOrNull() ?: 0, t2Score.toIntOrNull() ?: 0),
                                    setsWon = listOf(t1Sets.toIntOrNull() ?: 0, t2Sets.toIntOrNull() ?: 0),
                                    currentSet = match.currentSet,
                                    server = if (courtSide == "L") 0 else 1,
                                    status = match.status,
                                    winner = match.score1
                                ), onResult = { r -> if (r != null) onMatchUpdate(MatchState.fromResponse(r)) })
                            showCorrectionDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81C784)),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("ГОТОВО", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ScorePad(
    logo1: ImageBitmap?,
    logo2: ImageBitmap?,
    name1: String,
    name2: String,
    score1: Int,
    score2: Int,
    currentSet: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.width(1115.dp).height(685.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- ИГРОК 1 ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.size(45.dp).clip(CircleShape).background(Color(0xFFE0E0E0)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (logo1 != null) Image(bitmap = logo1, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(text = name1, color = Color(0xFF2979FF), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))

                    // Только счет, без кнопки +1
                    Box(
                        modifier = Modifier.background(Color(0xFFE3F2FD), RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(String.format("%02d", score1), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F0F0)))

                // --- ИГРОК 2 ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.size(45.dp).clip(CircleShape).background(Color(0xFFE0E0E0)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (logo2 != null) Image(bitmap = logo2, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(text = name2, color = Color(0xFF2979FF), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))

                    // Только счет, без кнопки +1
                    Box(
                        modifier = Modifier.background(Color(0xFFE3F2FD), RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(String.format("%02d", score2), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // Плашка "ПАРТИЯ X"
        Box(
            modifier = Modifier.background(Color(0xFFE3F2FD), RoundedCornerShape(50))
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text(text = "ПАРТИЯ $currentSet", color = Color(0xFF2979FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// 👇 НОВАЯ ФУНКЦИЯ ДЛЯ АНИМАЦИИ ТЕКСТОВОЙ КНОПКИ +1
@Composable
fun AnimatedPlusOneButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Анимация пружины
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f, // Уменьшается до 85% при нажатии
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "plus_one_scale"
    )

    Box(
        modifier = Modifier
            .scale(scale) // 👈 Применяем анимацию
            .background(Color(0xFF66BB6A), RoundedCornerShape(50))
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Убираем стандартную волну, оставляем только пружину
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text("+1", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun AnimatedGameButton(
    painter: Painter, onClick: () -> Unit, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f, // Вдавливание при клике
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
        ), label = "scale_game"
    )

    Image(
        painter = painter,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier.scale(scale).clickable(
            interactionSource = interactionSource, indication = null, onClick = onClick
        )
    )
}

@Composable
fun MatchCard(
    logo1: ImageBitmap?,
    name1: String,
    logo2: ImageBitmap?,
    name2: String,
    statusText: String,
    isFinished: Boolean,
    score1: Int,
    score2: Int,
    tennisPoints: List<String>? = null,
    status: String = "NOT_STARTED",
    onAddPoint1: () -> Unit = {},
    onAddPoint2: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 15.dp,
        modifier = modifier.width(440.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(60.dp).clip(CircleShape)
                        .background(if (logo1 != null) Color.Transparent else Color(0xFFE0E0E0))
                ) {
                    if (logo1 != null) Image(
                        bitmap = logo1,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    name1,
                    color = Color(0xFF2979FF),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (status == "IN_PROGRESS") {
                        AnimatedPlusOneButton(onClick = onAddPoint1)
                    }
                    Box(
                        modifier = Modifier.background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("$score1", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            if (tennisPoints != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        "${tennisPoints.getOrElse(0) { "" }} : ${tennisPoints.getOrElse(1) { "" }}",
                        color = Color(0xFF1976D2),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEEEEE)))
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(60.dp).clip(CircleShape)
                        .background(if (logo2 != null) Color.Transparent else Color(0xFFE0E0E0))
                ) {
                    if (logo2 != null) Image(
                        bitmap = logo2,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    name2,
                    color = Color(0xFF2979FF),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (status == "IN_PROGRESS") {
                        AnimatedPlusOneButton(onClick = onAddPoint2)
                    }
                    Box(
                        modifier = Modifier.background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("$score2", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val statusColor = when (statusText) {
                "Не начато" -> Color(0xFF9E9E9E); "Завершено" -> Color.Red; else -> Color(0xFF4CAF50)
            }
            val statusBgColor = when (statusText) {
                "Не начато" -> Color(0xFFF5F5F5); "Завершено" -> Color(0xFFFFEBEE); else -> Color(0xFFE8F5E9)
            }

            Box(
                modifier = Modifier.background(statusBgColor, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = statusText, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
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
