package ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ───────────────────────── Бренд-палитра (из Figma) ─────────────────────────

object Brand {
    val Blue = Color(0xFF3B7CFF)
    val Green = Color(0xFF69DB7C)
    val Sky = Color(0xFF74C0FC)
    val Red = Color(0xFFFF8787)
    val Yellow = Color(0xFFFFD43B)
}

// ───────────────────────── Цвета темы ─────────────────────────

@Immutable
class ScoreKeeperColors(
    val canvas: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val buttonStart: Color,
    val buttonEnd: Color,
    val buttonGlow: Color,
    val onButton: Color,
    val iconButtonBg: Color,
    val iconButtonBgHover: Color,
    val iconButtonFg: Color,
    val titleTop: Color,
    val titleBottom: Color,
    val titleGlow: Color,
    val logoTop: Color,
    val logoBottom: Color,
    val logoDigits: Color,
    val chipBg: Color,
    val chipText: Color,
    // Белые карточки-оверлеи (настройки, настройка команд/игроков)
    val cardSurface: Color,
    val cardBorder: Color,
    val headingBlue: Color,
    val divider: Color,
    val fieldBg: Color,
    val fieldText: Color,
    val fieldPlaceholder: Color,
    val captionText: Color,
    val doneButtonBg: Color,
    val onDoneButton: Color,
    // Экран матча (админка)
    val pillBg: Color,          // фон плашек-табов, пилюль счёта и статуса
    val pillText: Color,        // цифры на пилюлях счёта
    val headingText: Color,     // крупный тёмно-синий текст («ВОЛЕЙБОЛ», таймер, «НАЗАД»)
    val subText: Color,         // вторичный текст («Счёт матча»)
    val logoPlaceholder: Color, // серый круг, когда лого не загружено
    val previewBorder: Color,   // обводка панели предпросмотра
    val outlineSoft: Color,     // мягкая обводка (пилюля «НАЗАД»)
)

/** Светлая тема (по умолчанию). Тёмная — DarkColors ниже. */
val LightColors = ScoreKeeperColors(
    canvas = Color.White,
    glassFill = Color(0x73FFF8D1),          // rgba(255,248,209,0.45) — стекло менее прозрачное
    glassBorder = Color(0x33AAA6A6),        // rgba(170,166,166,0.2)
    buttonStart = Color(0xFF1D6FF7),
    buttonEnd = Color(0xFF6B8EC7),
    buttonGlow = Color(0xFF2171F4),
    onButton = Color.White,
    iconButtonBg = Color(0xFFE3F2FD),
    iconButtonBgHover = Color(0xFFCFE5FB),
    iconButtonFg = Color(0xFF05306E),
    titleTop = Color(0xFF03020A),
    titleBottom = Color(0xFF0F0F7F),
    titleGlow = Color(0xFF2171F4),
    logoTop = Color(0xFF03020A),
    logoBottom = Color(0xFF0F0F7F),
    logoDigits = Color(0xFFDCB422),
    chipBg = Color.White,
    chipText = Color.Black,
    cardSurface = Color.White,
    cardBorder = Color(0xFFE2EEF7),
    headingBlue = Color(0xFF2070F5),
    divider = Color(0xFFE4F0F8),
    fieldBg = Color(0xFF465B72),
    fieldText = Color.White,
    fieldPlaceholder = Color(0xFFC7D3E0),
    captionText = Color.Black,
    doneButtonBg = Color(0xFF69DB7C),
    onDoneButton = Color(0xFF343F4B),
    pillBg = Color(0xFFE5F1FD),
    pillText = Color(0xFF465B72),
    headingText = Color(0xFF0A2244),
    subText = Color(0xFF5F7186),
    logoPlaceholder = Color(0xFFDDDDDD),
    previewBorder = Color(0xFFBBDAEE),
    outlineSoft = Color(0xFFC9D8E8),
)

/** Тёмная тема: тот же фирменный синий и яркие овалы, но на тёмно-синем фоне. */
val DarkColors = ScoreKeeperColors(
    canvas = Color(0xFF0B1220),
    glassFill = Color(0x73141E33),          // тёмно-синее «стекло», 45%
    glassBorder = Color(0x334A6CA8),
    buttonStart = Color(0xFF1D6FF7),
    buttonEnd = Color(0xFF6B8EC7),
    buttonGlow = Color(0xFF2171F4),
    onButton = Color.White,
    iconButtonBg = Color(0xFF1D2A42),
    iconButtonBgHover = Color(0xFF27395C),
    iconButtonFg = Color(0xFFDCE6FF),
    titleTop = Color(0xFFF2F6FF),
    titleBottom = Color(0xFF7C9BFF),
    titleGlow = Color(0xFF2171F4),
    logoTop = Color(0xFF03020A),
    logoBottom = Color(0xFF0F0F7F),
    logoDigits = Color(0xFFDCB422),
    chipBg = Color(0xFF1D2A42),
    chipText = Color(0xFFE8EEF9),
    cardSurface = Color(0xFF141F31),
    cardBorder = Color(0xFF22314A),
    headingBlue = Color(0xFF6FA8FF),
    divider = Color(0xFF22314A),
    fieldBg = Color(0xFF2A3B57),
    fieldText = Color.White,
    fieldPlaceholder = Color(0xFF8FA3C0),
    captionText = Color(0xFFB9C6D8),
    doneButtonBg = Color(0xFF69DB7C),
    onDoneButton = Color(0xFF343F4B),
    pillBg = Color(0xFF1E2E47),
    pillText = Color(0xFFC7D6EA),
    headingText = Color(0xFFEAF2FC),
    subText = Color(0xFF9FB4CC),
    logoPlaceholder = Color(0xFF2A3B57),
    previewBorder = Color(0xFF2E4A6E),
    outlineSoft = Color(0xFF33465F),
)

// ───────────────────────── Шрифты и типографика ─────────────────────────
// Положите .ttf в frontend/src/main/resources/fonts/ с ТОЧНО такими именами.
// Если файла нет, приложение не падает, а берёт системный шрифт.

private fun resourceExists(path: String): Boolean =
    Thread.currentThread().contextClassLoader?.getResource(path) != null

private val clearSansLoaded = resourceExists("fonts/ClearSans-Bold.ttf")
private val aqumLoaded = resourceExists("fonts/AqumTwo-Classic.ttf")

private val ClearSans: FontFamily =
    if (clearSansLoaded) FontFamily(Font("fonts/ClearSans-Bold.ttf", FontWeight.Bold))
    else FontFamily.SansSerif

private val AqumTwo: FontFamily =
    if (aqumLoaded) FontFamily(Font("fonts/AqumTwo-Classic.ttf", FontWeight.Normal))
    else FontFamily.SansSerif

@Immutable
class ScoreKeeperTypography(
    val display: TextStyle,     // «ScoreKeeper»
    val logoDigits: TextStyle,  // «30:45» в логотипе
    val button: TextStyle,      // подписи кнопок
    val chip: TextStyle,        // мелкие плашки внизу
    val heading: TextStyle,     // «НАСТРОЙКИ», «ТЕМА ПРИЛОЖЕНИЯ»…
    val cardTitle: TextStyle,   // двустрочный заголовок настройки команд
    val teamLabel: TextStyle,   // «КОМАНДА 1» / «ИГРОК 1»
    val caption: TextStyle,     // «РАЗРАБОТАНО ДЛЯ СПОРТИВНОЙ КОМИССИИ»
    val doneButton: TextStyle,  // «ГОТОВО»
    val field: TextStyle,       // текст в полях ввода
)

val DefaultTypography = ScoreKeeperTypography(
    display = TextStyle(fontFamily = ClearSans, fontWeight = FontWeight.Bold, fontSize = 100.sp),
    logoDigits = TextStyle(fontFamily = ClearSans, fontWeight = FontWeight.Bold, fontSize = 80.sp),
    button = TextStyle(
        fontFamily = AqumTwo,
        fontWeight = if (aqumLoaded) FontWeight.Normal else FontWeight.Bold,
        fontSize = 32.sp,
    ),
    // Подписи внизу: в Figma — Sans Serif Collection Regular 15.
    chip = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
    // Тексты карточек в Figma — Aqum two Classic (заголовок настройки — 28, остальное замерено по макету).
    heading = TextStyle(
        fontFamily = AqumTwo,
        fontWeight = if (aqumLoaded) FontWeight.Normal else FontWeight.Bold,
        fontSize = 34.sp,
    ),
    cardTitle = TextStyle(
        fontFamily = AqumTwo,
        fontWeight = if (aqumLoaded) FontWeight.Normal else FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 35.sp, textAlign = TextAlign.Center,
    ),
    teamLabel = TextStyle(
        fontFamily = AqumTwo,
        fontWeight = if (aqumLoaded) FontWeight.Normal else FontWeight.Bold,
        fontSize = 32.sp,
    ),
    caption = TextStyle(
        fontFamily = AqumTwo,
        fontWeight = if (aqumLoaded) FontWeight.Normal else FontWeight.Bold,
        fontSize = 20.sp,
    ),
    doneButton = TextStyle(
        fontFamily = AqumTwo,
        fontWeight = if (aqumLoaded) FontWeight.Normal else FontWeight.Bold,
        fontSize = 31.sp,
    ),
    field = TextStyle(fontFamily = AqumTwo, fontSize = 20.sp),
)

// ───────────────────────── Язык интерфейса ─────────────────────────

enum class AppLanguage { RU, EN }

@Immutable
class AppStrings(
    val volleyball: String,
    val tennis: String,
    val settingsTitle: String,
    val appTheme: String,
    val appLanguage: String,
    val developedFor: String,
    val setupTeamsTitle: String,
    val setupPlayersTitle: String,
    val team1: String,
    val team2: String,
    val player1: String,
    val player2: String,
    val done: String,
    // Экран матча (админка)
    val back: String,
    val matchScore: String,
    val pointsTab: String,
    val setTab: String,
    val preview: String,
    val displayTextBtn: String,
    val statusNotStarted: String,
    val statusInProgress: String,
    val statusFinished: String,
    val reset: String,
    val undo: String,
    val correction: String,
    val startMatch: String,
    val endMatch: String,
    val continueMatch: String,
    val tossTitle: String,
    val confirm: String,
    val display: String,
    val enterTextTitle: String,
    val setsLabel: String,
    val courtSideLabel: String,
    val winnerLabel: String,
)

val RuStrings = AppStrings(
    volleyball = "ВОЛЕЙБОЛ",
    tennis = "ТЕННИС",
    settingsTitle = "НАСТРОЙКИ",
    appTheme = "ТЕМА ПРИЛОЖЕНИЯ",
    appLanguage = "ЯЗЫК ПРИЛОЖЕНИЯ",
    developedFor = "РАЗРАБОТАНО ДЛЯ СПОРТИВНОЙ КОМИССИИ",
    setupTeamsTitle = "ВВЕДИТЕ НАЗВАНИЕ КОМАНД\nИ ЗАГРУЗИТЕ ИХ ЛОГО.",
    setupPlayersTitle = "ВВЕДИТЕ ИМЕНА ИГРОКОВ И\nЗАГРУЗИТЕ ИХ ЛОГО.",
    team1 = "КОМАНДА 1",
    team2 = "КОМАНДА 2",
    player1 = "ИГРОК 1",
    player2 = "ИГРОК 2",
    done = "ГОТОВО",
    back = "НАЗАД",
    matchScore = "Счёт матча",
    pointsTab = "ОЧКИ",
    setTab = "ПАРТИЯ",
    preview = "ПРЕДПРОСМОТР",
    displayTextBtn = "ВЫВЕСТИ ТЕКСТ",
    statusNotStarted = "Не начато",
    statusInProgress = "В процессе",
    statusFinished = "Завершено",
    reset = "СБРОС",
    undo = "ОТКАТ",
    correction = "КОРРЕКТИРОВКА",
    startMatch = "НАЧАТЬ МАТЧ",
    endMatch = "ЗАВЕРШИТЬ МАТЧ",
    continueMatch = "ПРОДОЛЖИТЬ МАТЧ",
    tossTitle = "ЖЕРЕБЬЁВКА",
    confirm = "ПОДТВЕРДИТЬ",
    display = "ВЫВЕСТИ",
    enterTextTitle = "ВВЕДИТЕ ТЕКСТ ДЛЯ ВЫВОДА\nЕГО НА ЭКРАН.",
    setsLabel = "ПАРТИИ",
    courtSideLabel = "СТОРОНА ПОЛЯ\nПОДАЮЩЕЙ КОМАНДЫ",
    winnerLabel = "ПОБЕДИТЕЛЬ",
)

val EnStrings = AppStrings(
    volleyball = "VOLLEYBALL",
    tennis = "TENNIS",
    settingsTitle = "SETTINGS",
    appTheme = "APP THEME",
    appLanguage = "APP LANGUAGE",
    developedFor = "DEVELOPED FOR THE SPORTS COMMISSION",
    setupTeamsTitle = "ENTER TEAM NAMES\nAND UPLOAD THEIR LOGOS.",
    setupPlayersTitle = "ENTER PLAYER NAMES\nAND UPLOAD THEIR LOGOS.",
    team1 = "TEAM 1",
    team2 = "TEAM 2",
    player1 = "PLAYER 1",
    player2 = "PLAYER 2",
    done = "DONE",
    back = "BACK",
    matchScore = "Match score",
    pointsTab = "POINTS",
    setTab = "SET",
    preview = "PREVIEW",
    displayTextBtn = "DISPLAY TEXT",
    statusNotStarted = "Not started",
    statusInProgress = "In progress",
    statusFinished = "Finished",
    reset = "RESET",
    undo = "UNDO",
    correction = "CORRECTION",
    startMatch = "START MATCH",
    endMatch = "END MATCH",
    continueMatch = "CONTINUE MATCH",
    tossTitle = "TOSS",
    confirm = "CONFIRM",
    display = "DISPLAY",
    enterTextTitle = "ENTER TEXT TO DISPLAY\nIT ON THE SCREEN.",
    setsLabel = "SETS",
    courtSideLabel = "SERVING TEAM'S\nCOURT SIDE",
    winnerLabel = "WINNER",
)

fun stringsFor(language: AppLanguage): AppStrings =
    if (language == AppLanguage.RU) RuStrings else EnStrings

fun colorsFor(darkTheme: Boolean): ScoreKeeperColors =
    if (darkTheme) DarkColors else LightColors

// ───────────────────────── Доступ к теме ─────────────────────────

private val LocalColors = staticCompositionLocalOf { LightColors }
private val LocalTypography = staticCompositionLocalOf { DefaultTypography }
private val LocalStrings = staticCompositionLocalOf { RuStrings }

object ScoreKeeperTheme {
    val colors: ScoreKeeperColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
    val typography: ScoreKeeperTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current
    val strings: AppStrings
        @Composable @ReadOnlyComposable get() = LocalStrings.current
}

@Composable
fun ScoreKeeperTheme(
    colors: ScoreKeeperColors = LightColors,
    typography: ScoreKeeperTypography = DefaultTypography,
    strings: AppStrings = RuStrings,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides typography,
        LocalStrings provides strings,
        content = content,
    )
}

// ───────────────────────── Масштабирование под размер окна ─────────────────────────
// Макет нарисован на 1920×1080. DesignStage подменяет Density так, что 1 dp = 1 px макета,
// а весь холст 1920×1080 вписывается в окно. Все размеры в коде можно писать как в Figma.
// Масштаб операционной системы (125%, 150%) при этом не влияет: берётся реальный размер окна в пикселях.

const val DESIGN_WIDTH = 1920f
const val DESIGN_HEIGHT = 1080f

@Immutable
class StageMetrics(val windowWidth: Dp, val windowHeight: Dp) {
    /** Насколько окно шире макета с каждой стороны (если пропорции не 16:9). */
    val originX: Dp get() = (windowWidth - DESIGN_WIDTH.dp) / 2f
    val originY: Dp get() = (windowHeight - DESIGN_HEIGHT.dp) / 2f
}

val LocalStage = staticCompositionLocalOf { StageMetrics(DESIGN_WIDTH.dp, DESIGN_HEIGHT.dp) }

/**
 * Занимает всё окно. Внутри 1 dp = 1 px макета 1920×1080.
 * Контент макета центрируйте через Modifier.align(Alignment.Center).requiredSize(1920.dp, 1080.dp),
 * а то, что должно липнуть к краям окна (иконки, плашки), выравнивайте по краям самого окна.
 */
@Composable
fun DesignStage(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = minOf(constraints.maxWidth / DESIGN_WIDTH, constraints.maxHeight / DESIGN_HEIGHT)
        if (scale <= 0f) return@BoxWithConstraints
        val stage = StageMetrics(
            windowWidth = (constraints.maxWidth / scale).dp,
            windowHeight = (constraints.maxHeight / scale).dp,
        )
        CompositionLocalProvider(
            LocalDensity provides Density(density = scale, fontScale = 1f),
            LocalStage provides stage,
        ) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}
