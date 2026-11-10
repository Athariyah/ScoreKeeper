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
import androidx.compose.ui.text.ExperimentalTextApi
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
    // Заголовки и подписи в оверлеях/диалогах (в тёмной теме ярче, чем акцент на экране матча)
    val overlayHeading: Color,
    val textStroke: Color,      // внешний контур текста «как в Figma»
    val langUnselected: Color,  // неактивный язык в переключателе
    // Верхняя панель экрана матча: хоткеи, таймер, настройки, «НАЗАД»
    val barPill: Color,         // фон пилюли «ХОТКЕИ»
    val barBackFill: Color,     // фон пилюли «НАЗАД»
    val barBackBorder: Color,
    val barDivider: Color,
    val barFg: Color,           // «ХОТКЕИ», таймер
    val barFgStrong: Color,     // стрелка «НАЗАД»
    val barText: Color,         // подпись «НАЗАД»
    // Кнопка «назад» в оверлеях
    val backFill: Color,
    val backRing: Color,
    val backArrow: Color,
    // Кнопки экрана матча
    val outlineGlow: Color,     // свечение контурных кнопок (СБРОС / ОТКАТ / КОРРЕКТИРОВКА)
    val mainButtonStart: Color, // главная кнопка («ЗАВЕРШИТЬ МАТЧ»): цвет слева-внизу
    val mainButtonEnd: Color,   // …и справа-вверху
    val mainButtonGlow: Color,
    val onMainButton: Color,
    // Рамка предпросмотра табло
    val frameStart: Color,
    val frameEnd: Color,
    val frameGlow: Color,
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
    overlayHeading = Color(0xFF2070F5),
    textStroke = Color(0xFFE2EEF7),
    langUnselected = Color(0xFFBCBCBC),
    barPill = Color(0xFFE6F3FD),
    barBackFill = Color(0xFFFBFCFD),
    barBackBorder = Color(0xFFE3F0F9),
    barDivider = Color(0xFFE2EEF7),
    barFg = Color(0xFF465B72),
    barFgStrong = Color(0xFF06326D),
    barText = Color(0xFF21386A),
    backFill = Color.White,
    backRing = Color(0xFFE3F0F9),
    backArrow = Color(0xFF06326D),
    outlineGlow = Color(0x852171F4),
    mainButtonStart = Color(0xFF1D6FF7),
    mainButtonEnd = Color(0xFF6B8EC7),
    mainButtonGlow = Color(0xFF2171F4),
    onMainButton = Color.White,
    frameStart = Color(0xFFCFE6F5),
    frameEnd = Color(0xFF81B6D6),
    frameGlow = Color(0xFF91C0DC),
)

/**
 * Тёмная тема. Палитра снята с векторного макета Export1.pdf:
 * фон #141F31, обводки/разделители #22314A, золотой акцент #DFAF24 (в оверлеях ярче — #FFC300),
 * вторичный текст #B9C6D8. Зелёные «+1»/«ГОТОВО» и синие кнопки меню остаются фирменными.
 */
val DarkColors = ScoreKeeperColors(
    canvas = Color(0xFF141F31),
    glassFill = Color(0x33FFF8D1),          // меню: rgba(255,248,209,0.20), как в макете
    glassBorder = Color(0x1CAAA6A6),        // rgba(170,166,166,0.11)
    buttonStart = Color(0xFF1D6FF7),        // кнопки меню остаются синими
    buttonEnd = Color(0xFF6B8EC7),
    buttonGlow = Color(0xFF2171F4),
    onButton = Color.White,
    iconButtonBg = Color(0xFF141F31),
    iconButtonBgHover = Color(0xFF22314A),
    iconButtonFg = Color(0xFFDFAF24),
    titleTop = Color.White,
    titleBottom = Color.White,
    titleGlow = Color(0xFF8E8FCE),
    logoTop = Color(0xFF0A0227),
    logoBottom = Color(0xFF0F0F7F),
    logoDigits = Color(0xFFDCB422),
    chipBg = Color(0xFF141F31),
    chipText = Color(0xFFB9C6D8),
    cardSurface = Color(0xFF141F31),
    cardBorder = Color(0xFF22314A),
    headingBlue = Color(0xFFDFAF24),
    divider = Color(0xFF22314A),
    fieldBg = Color(0xFF2A3B57),
    fieldText = Color.White,
    fieldPlaceholder = Color(0xFF8FA3C0),
    captionText = Color(0xFFB9C6D8),
    doneButtonBg = Color(0xFF69DB7C),
    onDoneButton = Color(0xFF343F4B),
    pillBg = Color(0xFF22314A),
    pillText = Color(0xFFB9C6D8),
    headingText = Color(0xFFB9C6D8),
    subText = Color(0xFFCBCCCD),
    logoPlaceholder = Color(0xFFD9D9D9),
    previewBorder = Color(0xFF22314A),
    outlineSoft = Color(0xFF212E44),
    overlayHeading = Color(0xFFFFC300),
    textStroke = Color(0xFF22314A),
    langUnselected = Color(0xFFB9C6D8),
    barPill = Color(0xFF141F31),
    barBackFill = Color(0xFF141F31),
    barBackBorder = Color(0xFF212E44),
    barDivider = Color(0xFF212E44),
    barFg = Color(0xFFDFAF24),
    barFgStrong = Color(0xFFDFAF24),
    barText = Color(0xFFDFAF24),
    backFill = Color(0xFF141F31),
    backRing = Color(0xFF22314A),
    backArrow = Color(0xFFFFC300),
    outlineGlow = Color(0x55DFAF24),
    mainButtonStart = Color(0xFFE1BE5A),
    mainButtonEnd = Color(0xFFDFB026),
    mainButtonGlow = Color(0x66DFAF24),
    onMainButton = Color(0xFF141F31),
    frameStart = Color(0xFFFFE07A),
    frameEnd = Color(0xFFFFC300),
    frameGlow = Color(0xFFFFC300),
)

// ───────────────────────── Шрифты и типографика ─────────────────────────
// Положите .ttf в frontend/src/main/resources/fonts/ с ТОЧНО такими именами:
//   ClearSans-Bold.ttf, AqumTwo-Classic.ttf, BloggerSans-Medium.ttf, BlueCurve-Bold.ttf.
// Если файла нет, приложение не падает, а берёт системный шрифт.

private fun resourceExists(path: String): Boolean =
    Thread.currentThread().contextClassLoader?.getResource(path) != null

private val clearSansLoaded = resourceExists("fonts/ClearSans-Bold.ttf")
private val aqumLoaded = resourceExists("fonts/AqumTwo-Classic.ttf")
private val bloggerLoaded = resourceExists("fonts/BloggerSans-Medium.ttf")
private val blueCurveLoaded = resourceExists("fonts/BlueCurve-Bold.ttf")

private val ClearSans: FontFamily =
    if (clearSansLoaded) FontFamily(Font("fonts/ClearSans-Bold.ttf", FontWeight.Bold))
    else FontFamily.SansSerif

private val AqumTwo: FontFamily =
    if (aqumLoaded) FontFamily(Font("fonts/AqumTwo-Classic.ttf", FontWeight.Normal))
    else FontFamily.SansSerif

/** Blogger Sans Medium — подпись «Esc» на клавише в экране «ХОТКЕИ». */
val BloggerSans: FontFamily =
    if (bloggerLoaded) FontFamily(Font("fonts/BloggerSans-Medium.ttf", FontWeight.Medium))
    else FontFamily.SansSerif

/** Blue Curve Bold — цифры счёта (очки/партии) и поля корректировки, 32 sp (см. scoreDigits). */
val BlueCurve: FontFamily =
    if (blueCurveLoaded) FontFamily(Font("fonts/BlueCurve-Bold.ttf", FontWeight.Bold))
    else FontFamily.SansSerif

/** Системный Arial — шрифт табло для зрителей (экран предпросмотра). Если Arial
 *  в системе нет, Compose подставит ближайший системный sans-serif. */
@OptIn(ExperimentalTextApi::class)
val ArialFamily: FontFamily = FontFamily("Arial")

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
    val scoreDigits: TextStyle, // цифры очков/партий и полей корректировки — Blue Curve Bold 32
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
    scoreDigits = TextStyle(fontFamily = BlueCurve, fontWeight = FontWeight.Bold, fontSize = 32.sp),
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
    // Предупреждение о закрытии матча («ВЫ УВЕРЕНЫ?»)
    val areYouSure: String,
    val closeWarningLine1: String,
    val closeWarningLine2: String,
    val closeWarningLine3: String, // красная строка («СБРОШЕН»)
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
    areYouSure = "ВЫ УВЕРЕНЫ?",
    closeWarningLine1 = "ЕСЛИ ВЫ ПОДТВЕРДИТЕ",
    closeWarningLine2 = "ЗАКРЫТИЕ МАТЧА, ТО СЧЁТ БУДЕТ",
    closeWarningLine3 = "СБРОШЕН",
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
    areYouSure = "ARE YOU SURE?",
    closeWarningLine1 = "IF YOU CONFIRM",
    closeWarningLine2 = "CLOSING THE MATCH, THE SCORE WILL BE",
    closeWarningLine3 = "RESET",
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