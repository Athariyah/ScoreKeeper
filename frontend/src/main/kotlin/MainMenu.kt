import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * ============================================================================
 * ГЛАВНЫЙ ЭКРАН (МЕНЮ) — полностью переписан по макету Export.pdf (стр. 1).
 * ============================================================================
 *
 * Все размеры и координаты в этом файле — в "дизайн-пикселях" макета
 * 1920x1080 (замерены попиксельно по исходному арту background.png и PDF)
 * и масштабируются под окно через коэффициент u: 1 дизайн-пиксель = u dp.
 * На мониторе 1920x1080 в полноэкранном режиме u = 1dp, т.е. 1:1 с макетом.
 *
 * Как устроен экран:
 *  1) Фон рисуется кодом (Canvas), а не картинкой: цветные капсулы под углом
 *     -18.43° (наклон -1/3) с мягкими тенями. Поэтому фон можно анимировать:
 *     все капсулы непрерывно и циклично "плывут" вдоль своей оси вверх-вправо
 *     и бесшовно зацикливаются с периодом [MENU_CAPSULE_PERIOD].
 *  2) Центральная карточка — настоящее "матовое стекло": внутри неё тот же
 *     фон рисуется второй раз (в тех же координатах!), размывается и
 *     накрывается белой вуалью 21%. Поэтому размытые пятна в карточке движутся
 *     синхронно с капсулами снаружи.
 *  3) Логотип — исходный файл дизайна (menu_logo.png, 500x500).
 *     Надпись ScoreKeeper — настоящий текст: шрифт Clear Sans Bold 100
 *     (fonts/ClearSans-Bold.ttf), градиент, обводка и тень по параметрам Figma.
 *     Подписи внизу — PNG из дизайн-файла (tools/extract_menu_assets.py).
 *  4) Кнопки ВОЛЕЙБОЛ/ТЕННИС — исходные PNG из макета в масштабе ровно 25%
 *     (1844x924 -> 461x231). Кликабельна только сама "пилюля" внутри PNG,
 *     а не прозрачное свечение вокруг неё.
 *
 * Настройки "под себя": скорость фона — [MENU_CAPSULE_CYCLE_MS],
 * сила размытия стекла — [MENU_CARD_BLUR], белая вуаль — [MENU_CARD_VEIL].
 */

// --- Дизайн-пространство макета ---
private const val MENU_DESIGN_W = 1920f
private const val MENU_DESIGN_H = 1080f

// --- Карточка: x=459, y=162, 1002x801, радиус 40 (замерено по арту) ---
private const val MENU_CARD_X = 459f
private const val MENU_CARD_Y = 162f
private const val MENU_CARD_W = 1002f
private const val MENU_CARD_H = 801f
private const val MENU_CARD_RADIUS = 40f

/**
 * Радиус размытия "стекла" карточки (дизайн-пиксели).
 * Compose пересчитывает радиус в сигму гауссианы: sigma = 0.57735 * radius + 0.5,
 * поэтому для замеренной по макету сигмы ~45 нужен радиус ~78.
 */
private const val MENU_CARD_BLUR = 78f

/** Белая вуаль поверх размытия: в оригинале ровно 21% белого. */
private const val MENU_CARD_VEIL = 0.21f

/** Запас вокруг карточки для честного блюра по краям (дизайн-пиксели). */
private const val MENU_BLUR_MARGIN = 120f

// --- Фон: капсулы ---
/** Угол капсул: atan(1/3) — все капсулы макета наклонены именно так. */
private const val MENU_CAPSULE_ANGLE = -18.4349f

/** Период зацикливания фона вдоль оси капсул (дизайн-пиксели). */
private const val MENU_CAPSULE_PERIOD = 2400f

/** Длительность одного цикла "плавания" фона. Больше = медленнее. */
private const val MENU_CAPSULE_CYCLE_MS = 48_000

// Цвета капсул — точная палитра макета.
private val MenuGreen = Color(0xFF69DB7C)
private val MenuPink = Color(0xFFFF8787)
private val MenuBlue = Color(0xFF3B7CFF)
private val MenuLightBlue = Color(0xFF74C0FC)
private val MenuYellow = Color(0xFFFFD43B)

/**
 * Одна капсула фона в повёрнутой системе координат (s, q):
 * s — вдоль оси капсулы (вверх-вправо), q — перпендикулярно ей.
 * [tipStart]/[tipEnd] — кончики капсулы по оси s, [center] — её ось по q,
 * [thickness] — толщина. Значения замерены по background.png; концы,
 * спрятанные под карточкой/за краем экрана, восстановлены по размытым
 * пятнам внутри карточки оригинала.
 */
private data class MenuCapsule(
    val tipStart: Float,
    val tipEnd: Float,
    val center: Float,
    val thickness: Float,
    val color: Color
)

// Порядок = порядок отрисовки (сзади -> вперёд), как в макете.
private val MENU_CAPSULES = listOf(
    MenuCapsule(605f, 1950f, 315f, 238f, MenuBlue),       // синяя сверху по центру
    MenuCapsule(-450f, 506f, 272f, 240f, MenuGreen),      // зелёная слева сверху
    MenuCapsule(-450f, 620f, 548f, 178f, MenuYellow),     // жёлтая слева
    MenuCapsule(380f, 980f, 632f, 220f, MenuPink),        // розовая под карточкой (видна как пятно в стекле)
    MenuCapsule(1092f, 1950f, 640f, 218f, MenuLightBlue), // голубая справа сверху
    MenuCapsule(-450f, 950f, 798f, 189f, MenuPink),       // розовая слева снизу
    MenuCapsule(560f, 1376f, 902f, 192f, MenuBlue),       // синяя справа по центру (кончик виден у карточки)
    MenuCapsule(1494f, 1950f, 902f, 192f, MenuPink),      // розовая справа (та же "дорожка")
    MenuCapsule(485f, 1100f, 1247f, 190f, MenuYellow),    // жёлтый кончик под карточкой снизу
    MenuCapsule(1000f, 1950f, 1170f, 242f, MenuGreen),    // зелёная справа
    MenuCapsule(-650f, 354f, 1064f, 226f, MenuBlue),      // синяя слева снизу
    MenuCapsule(980f, 1950f, 1467f, 242f, MenuPink)       // розовая справа снизу
)

// Тень капсул: в макете это серая тень ~25% со сдвигом (+10, +14) в экранных
// координатах. В повёрнутых координатах (s, q) этот сдвиг равен (+5, +16.4).
private const val MENU_SHADOW_S = 5f
private const val MENU_SHADOW_Q = 16.4f

/**
 * Рисует все капсулы фона со сдвигом анимации [shift] (0..[MENU_CAPSULE_PERIOD]).
 * [origin] — где на холсте находится точка дизайн-координат (0,0),
 * [pxPerUnit] — пикселей холста в одном дизайн-пикселе.
 * Каждая капсула рисуется в трёх копиях (-период, 0, +период): когда одна
 * уходит за экран, следующая уже заходит — движение бесшовно зациклено.
 */
private fun DrawScope.drawMenuCapsules(
    shift: Float,
    origin: Offset,
    pxPerUnit: Float,
    shadowLayers: Int,
    shadowLayerAlpha: Float
) {
    withTransform({
        translate(origin.x, origin.y)
        scale(pxPerUnit, pxPerUnit, Offset.Zero)
        rotate(MENU_CAPSULE_ANGLE, Offset.Zero)
    }) {
        for (capsule in MENU_CAPSULES) {
            val length = capsule.tipEnd - capsule.tipStart
            val r = capsule.thickness / 2f
            val top = capsule.center - r
            for (copy in -1..1) {
                val start = capsule.tipStart + shift + copy * MENU_CAPSULE_PERIOD
                val end = start + length
                // Капсула целиком вне экрана по оси s — пропускаем.
                if (end < -520f || start > 2020f) continue

                // Мягкая тень: несколько расширяющихся полупрозрачных копий.
                for (layer in shadowLayers downTo 1) {
                    val grow = layer * 2.4f
                    drawRoundRect(
                        color = Color.Black.copy(alpha = shadowLayerAlpha),
                        topLeft = Offset(start + MENU_SHADOW_S - grow, top + MENU_SHADOW_Q - grow),
                        size = Size(length + grow * 2f, capsule.thickness + grow * 2f),
                        cornerRadius = CornerRadius(r + grow, r + grow)
                    )
                }
                drawRoundRect(
                    color = capsule.color,
                    topLeft = Offset(start, top),
                    size = Size(length, capsule.thickness),
                    cornerRadius = CornerRadius(r, r)
                )
            }
        }
    }
}

@Composable
fun MainMenuScreen(
    onTennis: () -> Unit,
    onVolleyball: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit
) {
    // Бесконечная анимация фона: капсулы плывут в одну сторону (вверх-вправо
    // вдоль своей оси) и циклично повторяются с периодом MENU_CAPSULE_PERIOD.
    val infiniteTransition = rememberInfiniteTransition(label = "menuCapsules")
    val capsuleShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = MENU_CAPSULE_PERIOD,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = MENU_CAPSULE_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "capsuleShift"
    )

    // Появление экрана: карточка плавно всплывает, остальное подтягивается.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val cardIn by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "cardIn"
    )
    val chromeIn by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "chromeIn"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Масштаб дизайн-пространства 1920x1080 под текущее окно (с центрированием).
        val scaleF = minOf(maxWidth.value / MENU_DESIGN_W, maxHeight.value / MENU_DESIGN_H)
        val u: Dp = scaleF.dp // размер одного дизайн-пикселя в dp
        val originX: Dp = (maxWidth - u * MENU_DESIGN_W) / 2
        val originY: Dp = (maxHeight - u * MENU_DESIGN_H) / 2
        fun dx(v: Float): Dp = originX + u * v
        fun dy(v: Float): Dp = originY + u * v
        fun ds(v: Float): Dp = u * v
        val density = LocalDensity.current
        val cardLiftPx = with(density) { ds(22f).toPx() }

        // ---------- 1) Анимированный фон из капсул ----------
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pxPerUnit = minOf(size.width / MENU_DESIGN_W, size.height / MENU_DESIGN_H)
            val origin = Offset(
                (size.width - MENU_DESIGN_W * pxPerUnit) / 2f,
                (size.height - MENU_DESIGN_H * pxPerUnit) / 2f
            )
            drawMenuCapsules(
                shift = capsuleShift,
                origin = origin,
                pxPerUnit = pxPerUnit,
                shadowLayers = 5,
                shadowLayerAlpha = 0.052f
            )
        }

        // ---------- 2) Центральная "стеклянная" карточка ----------
        val cardShape = RoundedCornerShape(ds(MENU_CARD_RADIUS))
        Box(
            modifier = Modifier
                .offset(x = dx(MENU_CARD_X), y = dy(MENU_CARD_Y))
                .size(width = ds(MENU_CARD_W), height = ds(MENU_CARD_H))
                .graphicsLayer {
                    alpha = cardIn
                    scaleX = 0.965f + 0.035f * cardIn
                    scaleY = 0.965f + 0.035f * cardIn
                    translationY = (1f - cardIn) * cardLiftPx
                }
                .shadow(elevation = ds(16f), shape = cardShape, clip = false)
                .clip(cardShape)
                .border(width = ds(1.5f), color = Color.White.copy(alpha = 0.55f), shape = cardShape)
        ) {
            // Тот же фон, нарисованный в координатах карточки и размытый —
            // получается настоящее матовое стекло поверх движущихся капсул.
            // ВАЖНО: холст специально БОЛЬШЕ карточки (запас под блюр), поэтому
            // нужен requiredSize: обычный size родитель обрежет по своим
            // ограничениям до размера карточки — тогда масштаб и позиция
            // размытого фона "поплывут", а справа/снизу останутся полосы без блюра.
            Canvas(
                modifier = Modifier
                    .offset(x = ds(-MENU_BLUR_MARGIN), y = ds(-MENU_BLUR_MARGIN))
                    .requiredSize(
                        width = ds(MENU_CARD_W + MENU_BLUR_MARGIN * 2f),
                        height = ds(MENU_CARD_H + MENU_BLUR_MARGIN * 2f)
                    )
                    .blur(ds(MENU_CARD_BLUR))
            ) {
                val pxPerUnit = size.width / (MENU_CARD_W + MENU_BLUR_MARGIN * 2f)
                val origin = Offset(
                    -(MENU_CARD_X - MENU_BLUR_MARGIN) * pxPerUnit,
                    -(MENU_CARD_Y - MENU_BLUR_MARGIN) * pxPerUnit
                )
                // Белый фон дизайн-пространства (чтобы блюр у краёв не темнел).
                drawRect(
                    color = Color.White,
                    topLeft = Offset(-80f * pxPerUnit, -80f * pxPerUnit),
                    size = Size(
                        (MENU_DESIGN_W + 160f) * pxPerUnit,
                        (MENU_DESIGN_H + 160f) * pxPerUnit
                    )
                )
                drawMenuCapsules(
                    shift = capsuleShift,
                    origin = origin,
                    pxPerUnit = pxPerUnit,
                    shadowLayers = 1,
                    shadowLayerAlpha = 0.25f
                )
            }
            // Белая вуаль 21% — как в макете.
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = MENU_CARD_VEIL)))

            // --- Логотип: исходный файл дизайна (500x500), плитка 250x250 ---
            Image(
                painter = painterResource("menu_logo.png"),
                contentDescription = "30:45",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .offset(x = ds(43f), y = ds(87f))
                    .size(ds(250f))
            )

            // --- Заголовок ScoreKeeper: Clear Sans Bold 100, параметры из Figma ---
            MenuTitle(
                scaleF = scaleF,
                modifier = Modifier
                    .offset(x = ds(282f), y = ds(140f))
                    .size(width = ds(720f), height = ds(200f))
            )

            // --- Кнопки видов спорта. Изображения 461x231 (PNG в масштабе 25%),
            // сама "пилюля" внутри них: сдвиг (48.5, 31.5), размер 370x140. ---
            MenuSportButton(
                painterRes = "valeiball.png",
                contentDescription = "Волейбол",
                entranceDelayMs = 180L,
                u = u,
                onClick = onVolleyball,
                modifier = Modifier.offset(x = ds(7.5f), y = ds(509.5f))
            )
            MenuSportButton(
                painterRes = "tennis.png",
                contentDescription = "Теннис",
                entranceDelayMs = 300L,
                u = u,
                onClick = onTennis,
                modifier = Modifier.offset(x = ds(527.5f), y = ds(509.5f))
            )
        }

        // ---------- 3) Круглые иконки справа сверху (52x51) ----------
        MenuCircleIconButton(
            painterRes = "seting.png",
            contentDescription = "Настройки",
            rotateOnHover = true,
            u = u,
            onClick = onSettings,
            modifier = Modifier
                .offset(x = dx(1741f), y = dy(26f))
                .graphicsLayer { alpha = chromeIn }
        )
        MenuCircleIconButton(
            painterRes = "exit.png",
            contentDescription = "Выход",
            rotateOnHover = false,
            u = u,
            onClick = onExit,
            modifier = Modifier
                .offset(x = dx(1824f), y = dy(26f))
                .graphicsLayer { alpha = chromeIn }
        )

        // ---------- 4) Подписи-плашки в нижних углах ----------
        // Белая подложка по геометрии макета + вырезанная из арта плашка сверху.
        Box(
            modifier = Modifier
                .offset(x = dx(20f), y = dy(1038f))
                .size(width = ds(285f), height = ds(28f))
                .graphicsLayer { alpha = chromeIn }
        ) {
            Box(
                modifier = Modifier
                    .offset(x = ds(3f), y = ds(3f))
                    .size(width = ds(279f), height = ds(22f))
                    .clip(RoundedCornerShape(ds(11f)))
                    .background(Color.White)
            )
            Image(
                painter = painterResource("menu_footer_left.png"),
                contentDescription = "Made by Athariyyah and R_CODER",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .offset(x = dx(1751f), y = dy(1038f))
                .size(width = ds(138f), height = ds(28f))
                .graphicsLayer { alpha = chromeIn }
        ) {
            Box(
                modifier = Modifier
                    .offset(x = ds(3f), y = ds(3f))
                    .size(width = ds(132f), height = ds(22f))
                    .clip(RoundedCornerShape(ds(11f)))
                    .background(Color.White)
            )
            Image(
                painter = painterResource("menu_footer_right.png"),
                contentDescription = "version: beta 0.0.1",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Заголовок "ScoreKeeper" настоящим текстом, параметры — из Figma:
 * Clear Sans Bold, размер 100, заливка — вертикальный градиент
 * #03020A (0%) -> #0F0F7F (80%), обводка 1px снаружи #8E8FCE 76%,
 * тень #2171F4 71% без сдвига, blur 35.
 *
 * Холст компонента: 720x200 дизайн-пикселей (см. вызов). Надпись сажается
 * по реальному чернильному контуру глифов: верх левого края чернил — точка
 * (55, 46) холста = абсолютные (796, 348) макета 1920x1080, как в экспорте.
 */
@Composable
private fun MenuTitle(scaleF: Float, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val fontFamily = remember {
        FontFamily(Font(resource = "fonts/ClearSans-Bold.ttf", weight = FontWeight.Bold))
    }
    val text = "ScoreKeeper"
    val fontSize = (100f * scaleF).sp
    val layout = remember(textMeasurer, fontSize) {
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize
            )
        )
    }
    Canvas(modifier = modifier) {
        val pxPerDesign = size.width / 720f
        var inkTop = Float.MAX_VALUE
        for (i in text.indices) {
            val b = layout.getBoundingBox(i)
            if (b.top < inkTop) inkTop = b.top
        }
        val firstGlyph = layout.getBoundingBox(0)
        val topLeft = Offset(
            x = 55f * pxPerDesign - firstGlyph.left,
            y = 46f * pxPerDesign - inkTop
        )
        // Слой 1: обводка + голубая тень. Stroke в Compose центрируется на
        // контуре глифа, поэтому ширина 2 = 1px наружу (как в Figma) + 1px,
        // который закроется заливкой сверху.
        drawText(
            textLayoutResult = layout,
            color = Color(0xFF8E8FCE).copy(alpha = 0.76f),
            topLeft = topLeft,
            shadow = Shadow(
                color = Color(0xFF2171F4).copy(alpha = 0.71f),
                offset = Offset.Zero,
                blurRadius = 35f * pxPerDesign
            ),
            drawStyle = Stroke(width = 2f * pxPerDesign, join = StrokeJoin.Round)
        )
        // Слой 2: заливка градиентом на высоте текстового фрейма Figma (130).
        drawText(
            textLayoutResult = layout,
            brush = Brush.linearGradient(
                0.0f to Color(0xFF03020A),
                0.8f to Color(0xFF0F0F7F),
                1.0f to Color(0xFF0F0F7F),
                start = Offset(0f, inkTop),
                end = Offset(0f, inkTop + 130f * pxPerDesign)
            ),
            topLeft = topLeft
        )
    }
}

/**
 * Большая кнопка вида спорта из PNG макета (с прозрачным свечением вокруг).
 * Анимация: появление с задержкой (подъём + проявление), при наведении —
 * пружинный подъём и увеличение, при нажатии — "вдавливание" с отскоком.
 */
@Composable
private fun MenuSportButton(
    painterRes: String,
    contentDescription: String,
    entranceDelayMs: Long,
    u: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(entranceDelayMs)
        shown = true
    }
    val entrance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "buttonEntrance"
    )
    val interactScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.93f
            isHovered -> 1.045f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "buttonScale"
    )
    val density = LocalDensity.current
    val pxPerDesign = with(density) { u.toPx() }
    val liftPx by animateFloatAsState(
        targetValue = if (isHovered && !isPressed) -4f * pxPerDesign else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "buttonLift"
    )

    Box(
        modifier = modifier
            .size(width = u * 461f, height = u * 231f)
            .graphicsLayer {
                alpha = entrance
                val s = interactScale * (0.85f + 0.15f * entrance)
                scaleX = s
                scaleY = s
                translationY = (1f - entrance) * 26f * pxPerDesign + liftPx
            }
    ) {
        Image(
            painter = painterResource(painterRes),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        // Кликабельна только сама пилюля (370x140), а не прозрачное свечение PNG.
        Box(
            modifier = Modifier
                .offset(x = u * 48.5f, y = u * 31.5f)
                .size(width = u * 370f, height = u * 140f)
                .hoverable(interactionSource)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        )
    }
}

/**
 * Круглая иконка-кнопка 52x51 (шестерёнка/крестик из макета).
 * При наведении увеличивается; шестерёнка вдобавок приятно проворачивается.
 */
@Composable
private fun MenuCircleIconButton(
    painterRes: String,
    contentDescription: String,
    rotateOnHover: Boolean,
    u: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.86f
            isHovered -> 1.14f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iconScale"
    )
    val rotation by animateFloatAsState(
        targetValue = if (rotateOnHover && isHovered) 55f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "iconRotation"
    )

    Box(
        modifier = modifier
            .size(width = u * 52f, height = u * 51f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
    ) {
        Image(
            painter = painterResource(painterRes),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hoverable(interactionSource)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        )
    }
}