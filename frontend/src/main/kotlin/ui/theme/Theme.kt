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

/**
 * Тёмная палитра экрана матча — цвета сняты пипеткой с макетов
 * «Волейбол (В процессе).png» и «Меню настроек.png»:
 *  • фон окна #070F2B (тёмно-синий), панели/карточки #141F31, обводки/плашки #212E44;
 *  • акцент — жёлтый #DFAF24 (кнопки, подписи, таймер), крупный заголовок — молочно-белый #E9F1F8;
 *  • вторичный текст (счёт матча, логотип-заглушка) — приглушённый сизый #6E93B8.
 */
object Dark {
    val Window = Color(0xFF070F2B)
    val Card = Color(0xFF141F31)
    val Pill = Color(0xFF212E44)
    val Gold = Color(0xFFDFAF24)
    val GoldSoft = Color(0xFFFFCA1E)
    val HeaderText = Color(0xFFE9F1F8)
    val SectionText = Color(0xFF5994C2)
    val White = Color(0xFFF4F8FC)
    val Green = Color(0xFF4ED27A)
    val GreenText = Color(0xFF7BE39B)
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
    // ── Дополнительные токены (нужны, чтобы тёмная тема не «ломалась») ──
    val dark: Boolean,              // какая тема сейчас активна (для рисования фона и свечений)
    val accent: Color,              // акцент экрана матча: кнопки, пилюли, обводки, таймер, «+1»
    val accentText: Color,          // текст на акцентной заливке / активная подпись
    val accentSoft: Color,          // приглушённый акцент (неактивные подписи, обводки на тёмном)
    val screenTitle: Color,         // крупный заголовок вида спорта на экране матча
    val screenSubtitle: Color,      // «Счёт матча» и прочий вторичный текст
    val panelOnAccent: Color,       // текст/иконки поверх акцентной заливки
    val highlightFill: Color,       // основной CTA («НАЧАТЬ / ЗАВЕРШИТЬ МАТЧ»)
    val onHighlight: Color,         // текст на основном CTA
    val highlightBorder: Color,     // обводка основного CTA
    val tabFill: Color,             // плашка-вкладка («ОЧКИ», «ПАРТИЯ N»)
    val previewStrip: Color,        // фон панели предпросмотра вокруг табло
    val fieldStroke: Color,         // обводка полей ввода
    val dangerText: Color,          // предупреждения (сброс счёта, ошибки)
    val isLightSurface: Boolean,    // карточки/диалоги светлые (нужно для контраста текста)
    val bodyText: Color,            // обычный текст на карточке/диалоге
    val statusOnline: Color,        // «зелёная» кнопка подтверждения / индикатор «в процессе»
    val statusOnlineBorder: Color,  // её обводка
    val onStatusOnline: Color,      // подпись на зелёной кнопке
    val switchOffFill: Color,       // выключенный переключатель
    val backButtonBg: Color,        // круглая кнопка «назад» в углу карточек
    val backButtonBorder: Color,    // её обводка
    val backButtonArrow: Color,     // стрелка внутри неё
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
    // Кнопки настроек, сворачивания и закрытия на главном экране — по макету:
    // тёмно-синий круг #141F31 с жёлтым знаком #FFC300 (в тёмной теме — золото макета).
    iconButtonBg = Color(0xFF141F31),
    iconButtonBgHover = Color(0xFF1E2A3F),
    iconButtonFg = Color(0xFFFFC300),
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
    dark = false,
    accent = Color(0xFF2070F5),
    accentText = Color(0xFF2070F5),
    accentSoft = Color(0xFFC9D8E8),
    screenTitle = Color(0xFF0A2244),
    screenSubtitle = Color(0xFF5F7186),
    panelOnAccent = Color.White,
    highlightFill = Color(0xFF6B8EC7),
    onHighlight = Color.White,
    highlightBorder = Color(0xFF2171F4),
    tabFill = Color(0xFFE5F1FD),
    previewStrip = Color(0xFF91C0DC),
    fieldStroke = Color(0xFF9EC8E2),
    dangerText = Color(0xFFFF0000),
    isLightSurface = true,
    bodyText = Color.Black,
    statusOnline = Color(0xFF69DB7C),
    statusOnlineBorder = Color(0xFFA7E29E),
    onStatusOnline = Color(0xFF343F4B),
    switchOffFill = Color(0xFFB9D9BF),
    backButtonBg = Color.White,
    backButtonBorder = Color(0xFFE3F0F9),
    backButtonArrow = Color(0xFF06326D),
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
    // Знак кнопок «свернуть/настройки/закрыть» на главном экране — #FFC300 вместо
    // прежнего светло-голубого #DCE6FF (как на макете).
    iconButtonFg = Color(0xFFFFC300),
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
    dark = true,
    accent = Dark.Gold,
    accentText = Dark.Gold,
    accentSoft = Dark.SectionText,
    screenTitle = Dark.HeaderText,
    screenSubtitle = Color(0xFF6E93B8),
    panelOnAccent = Dark.Card,
    highlightFill = Dark.Gold,
    onHighlight = Dark.Card,
    highlightBorder = Color(0xFFC79A18),
    tabFill = Dark.Pill,
    previewStrip = Dark.Window,
    // обводки полей ввода в тёмной теме — #22314A (правило раунда 6: вместо #9EC8E2)
    fieldStroke = Color(0xFF22314A),
    dangerText = Color(0xFFFF7A8A),
    isLightSurface = false,
    bodyText = Dark.HeaderText,
    statusOnline = Color(0xFF4ED27A),
    statusOnlineBorder = Color(0xFF7BE39B),
    onStatusOnline = Dark.Window,
    switchOffFill = Color(0xFF2A3B57),
    backButtonBg = Color(0xFF1D2A42),
    backButtonBorder = Color(0xFF2C3D5A),
    backButtonArrow = Dark.HeaderText,
)

// ───────────────────────── Шрифты и типографика ─────────────────────────
// Положите .ttf в frontend/src/main/resources/fonts/ с ТОЧНО такими именами.
// Если файла нет, приложение не падает, а берёт системный шрифт.

private fun resourceExists(path: String): Boolean =
    Thread.currentThread().contextClassLoader?.getResource(path) != null

private val clearSansLoaded = resourceExists("fonts/ClearSans-Bold.ttf")
private val aqumLoaded = resourceExists("fonts/AqumTwo-Classic.ttf")
// «Esc» на клавише в макете набран Blogger Sans Medium. Файл кладётся в
//   frontend/src/main/resources/fonts/
// Имя может быть любым из списка ниже (часто скачивают с пробелом в названии).
// Если файла ещё нет, берётся Aqum (или системный шрифт) — приложение не падает.
private val bloggerCandidates = listOf(
    "fonts/BloggerSans-Medium.ttf",
    "fonts/Blogger Sans Medium.ttf",
    "fonts/BloggerSansMedium.ttf",
    "fonts/BloggerSans.ttf",
    "fonts/Blogger Sans.ttf",
)
private val bloggerPath: String? = bloggerCandidates.firstOrNull { resourceExists(it) }

private val ClearSans: FontFamily =
    if (clearSansLoaded) FontFamily(Font("fonts/ClearSans-Bold.ttf", FontWeight.Bold))
    else FontFamily.SansSerif

private val AqumTwo: FontFamily =
    if (aqumLoaded) FontFamily(Font("fonts/AqumTwo-Classic.ttf", FontWeight.Normal))
    else FontFamily.SansSerif

/** Шрифт подписи «Esc» на клавише (Blogger Sans Medium из макета). */
internal val KeyLabelFamily: FontFamily =
    bloggerPath?.let { FontFamily(Font(it, FontWeight.Medium)) } ?: AqumTwo

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
    // Окно подсказок «ХОТКЕИ»
    val panelTitle: TextStyle,  // «ХОТКЕИ» в шапке карточки
    val panelHeader: TextStyle, // «КНОПКИ МАТЧА» / «ОСНОВНЫЕ»
    val hotkeyLabel: TextStyle, // подпись рядом с клавишей
    val hotkeyKey: TextStyle,   // символ на клавише («1», «Esc», «F1»…)
    val hotkeysChip: TextStyle, // подпись кнопки «ХОТКЕИ» в верхней полосе
    val timerText: TextStyle,   // «00:00» в верхней полосе
    val pillLabel: TextStyle,   // «НАЗАД» в пилюле верхней полосы
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
    // Размеры ниже замерены по макетам: «ХОТКЕИ» — 46, заголовки колонок — 24,
    // подписи строк — 13.9 (ширина строки в макете / ширина глифов Aqum), символы на клавишах — 22.
    panelTitle = TextStyle(
        fontFamily = AqumTwo,
        fontWeight = if (aqumLoaded) FontWeight.Normal else FontWeight.Bold,
        fontSize = 45.6.sp,
    ),
    panelHeader = TextStyle(
        fontFamily = AqumTwo,
        fontWeight = if (aqumLoaded) FontWeight.Normal else FontWeight.Bold,
        fontSize = 23.3.sp,
    ),
    hotkeyLabel = TextStyle(
        fontFamily = AqumTwo,
        fontWeight = if (aqumLoaded) FontWeight.Normal else FontWeight.Bold,
        fontSize = 13.9.sp, lineHeight = 18.2.sp,
    ),
    hotkeyKey = TextStyle(
        fontFamily = AqumTwo,
        fontWeight = if (aqumLoaded) FontWeight.Normal else FontWeight.Bold,
        fontSize = 22.sp,
    ),
    // Подпись на кнопке «ХОТКЕИ» в верхней полосе: 68 dp в макете 616×53 → 18.5 sp.
    hotkeysChip = TextStyle(
        fontFamily = AqumTwo,
        fontWeight = if (aqumLoaded) FontWeight.Normal else FontWeight.Bold,
        fontSize = 18.5.sp,
    ),
    // «00:00» в верхней полосе — 68.4 dp шириной → 22.6 sp.
    timerText = TextStyle(
        fontFamily = AqumTwo,
        fontWeight = if (aqumLoaded) FontWeight.Normal else FontWeight.Bold,
        fontSize = 22.6.sp,
    ),
    // «НАЗАД» в пилюле — 54 dp шириной → 14.8 sp.
    pillLabel = TextStyle(
        fontFamily = AqumTwo,
        fontWeight = if (aqumLoaded) FontWeight.Normal else FontWeight.Bold,
        fontSize = 14.8.sp,
    ),
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
    // Хоткеи (окно подсказок)
    val hotkeys: String,
    val hotkeysMatchButtons: String,
    val hotkeysMain: String,
    val hotkeyAddPoint1: String,
    val hotkeyAddPoint2: String,
    val hotkeyUndo: String,
    val hotkeyCorrection: String,
    val hotkeyReset: String,
    val hotkeyStartMatch: String,
    val hotkeyBack: String,
    val hotkeySettings: String,
    val hotkeyCloseApp: String,
    val hotkeyMinimize: String,
    val hotkeyDone: String,
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
    hotkeys = "ХОТКЕИ",
    hotkeysMatchButtons = "КНОПКИ МАТЧА",
    hotkeysMain = "ОСНОВНЫЕ",
    hotkeyAddPoint1 = "ДОБАВИТЬ ОЧКО\nКОМАНДЕ 1",
    hotkeyAddPoint2 = "ДОБАВИТЬ ОЧКО\nКОМАНДЕ 2",
    hotkeyUndo = "ОТКАТ",
    hotkeyCorrection = "КОРРЕКТИРОВКА",
    hotkeyReset = "СБРОС",
    hotkeyStartMatch = "НАЧАТЬ/\nПРОДОЛЖИТЬ/\nЗАВЕРШИТЬ МАТЧ",
    hotkeyBack = "НАЗАД",
    hotkeySettings = "НАСТРОЙКИ",
    hotkeyCloseApp = "ЗАКРЫТЬ\nПРИЛОЖЕНИЕ",
    hotkeyMinimize = "СВЕРНУТЬ\nПРИЛОЖЕНИЕ",
    hotkeyDone = "ГОТОВО/\nПОДТВЕРДИТЬ",
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
    hotkeys = "HOTKEYS",
    hotkeysMatchButtons = "MATCH BUTTONS",
    hotkeysMain = "GENERAL",
    hotkeyAddPoint1 = "ADD A POINT\nTO TEAM 1",
    hotkeyAddPoint2 = "ADD A POINT\nTO TEAM 2",
    hotkeyUndo = "UNDO",
    hotkeyCorrection = "CORRECTION",
    hotkeyReset = "RESET",
    hotkeyStartMatch = "START/\nCONTINUE/\nEND MATCH",
    hotkeyBack = "BACK",
    hotkeySettings = "SETTINGS",
    hotkeyCloseApp = "CLOSE\nAPP",
    hotkeyMinimize = "MINIMIZE\nAPP",
    hotkeyDone = "DONE/\nCONFIRM",
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
