package ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.PathFillMode
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.Path as SkPath
import org.jetbrains.skia.Rect as SkRect
import ui.theme.Brand
import ui.theme.LocalStage
import ui.theme.ScoreKeeperTheme
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas

// ───────────────────────── Рисование размытых теней ─────────────────────────
// Координаты и sigma задаются в тех единицах, в которых сейчас настроен DrawScope.

private fun blurPaint(color: Color, sigma: Float) = SkPaint().apply {
    isAntiAlias = true
    this.color = color.toArgb()
    maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, sigma)
}

internal fun DrawScope.drawBlurredRoundRect(
    topLeft: Offset,
    size: Size,
    corner: Float,
    color: Color,
    sigma: Float,
) {
    val paint = blurPaint(color, sigma)
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRRect(
            RRect.makeXYWH(topLeft.x, topLeft.y, size.width, size.height, corner),
            paint,
        )
    }
}

/** Размытая «рамка»: большой прямоугольник минус скруглённый прямоугольник (для внутренней тени). */
private fun DrawScope.drawBlurredFrame(
    outer: Float, cx: Float, cy: Float,
    left: Float, top: Float, right: Float, bottom: Float, corner: Float,
    color: Color, sigma: Float,
) {
    val path = SkPath().apply {
        fillMode = PathFillMode.EVEN_ODD
        addRect(SkRect.makeLTRB(cx - outer, cy - outer, cx + outer, cy + outer))
        addRRect(RRect.makeLTRB(left, top, right, bottom, corner))
    }
    val paint = blurPaint(color, sigma)
    drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPath(path, paint) }
}

// ───────────────────────── Овалы из Figma ─────────────────────────

/** Овал, повёрнутый на -18.44°. cx/cy: центр на холсте 1920×1080; length/thickness: размеры до поворота. */
@Immutable
data class OvalSpec(
    val cx: Float,
    val cy: Float,
    val length: Float,
    val thickness: Float,
    val color: Color,
)

/** Порядок = порядок слоёв в Figma (первый внизу). Белый овал из макета на белом фоне не виден, поэтому его нет. */
val MenuOvals: List<OvalSpec> = listOf(
    OvalSpec(1169.96f, 575.50f, 901.545f, 198.068f, Brand.Blue),
    OvalSpec(1131.60f, 27.00f, 932.720f, 171.865f, Brand.Blue),
    OvalSpec(107.89f, 255.60f, 972.469f, 250.525f, Brand.Green),
    OvalSpec(281.05f, 1033.10f, 943.979f, 235.778f, Brand.Blue),
    OvalSpec(1629.25f, 137.10f, 997.422f, 228.504f, Brand.Sky),
    OvalSpec(222.69f, 772.10f, 894.110f, 199.696f, Brand.Red),
    OvalSpec(121.87f, 542.60f, 919.440f, 188.091f, Brand.Yellow),
    OvalSpec(859.82f, 403.24f, 489.544f, 250.525f, Brand.Red),
    OvalSpec(1052.82f, 888.24f, 489.544f, 250.525f, Brand.Yellow),
    OvalSpec(1875.96f, 613.50f, 980.602f, 251.827f, Brand.Green),
    OvalSpec(1773.96f, 960.50f, 980.602f, 251.827f, Brand.Red),
    OvalSpec(1885.82f, 318.56f, 400.745f, 207.020f, Brand.Red),
)

private const val ANGLE = -18.44f          // поворот в Compose (против часовой стрелки)
private val THETA = Math.toRadians(18.44)  // то же самое в радианах
private const val PAD = 48f                // запас под тень вокруг спрайта

/** Овал с тенями из Figma, отрисованный один раз в картинку. */
internal class OvalSprite(
    val image: ImageBitmap,
    val width: Float,      // размеры спрайта в dp
    val height: Float,
    val cx: Float,         // центр овала в макете
    val cy: Float,
    val ux: Float,         // направление дрейфа (вдоль овала, вправо-вверх)
    val uy: Float,
    val mid: Float,        // где на «дорожке» находится центр окна
    val halfLength: Float,
)

private fun DrawScope.drawPill(spec: OvalSpec, center: Offset) {
    val half = spec.thickness / 2f
    val left = center.x - spec.length / 2f
    val top = center.y - half
    val right = center.x + spec.length / 2f
    val bottom = center.y + half

    // 1) внешняя тень: смещение (-3, 14) в экранных осях, blur 8.3, spread 4, чёрный 25%
    withTransform({ translate(-3f, 14f); rotate(ANGLE, pivot = center) }) {
        drawBlurredRoundRect(
            topLeft = Offset(left - 4f, top - 4f),
            size = Size(spec.length + 8f, spec.thickness + 8f),
            corner = half + 4f,
            color = Color.Black.copy(alpha = 0.25f),
            sigma = 4.15f,
        )
    }

    withTransform({ rotate(ANGLE, pivot = center) }) {
        // 2) заливка
        drawRoundRect(spec.color, Offset(left, top), Size(spec.length, spec.thickness), CornerRadius(half))

        // 3) внутренняя тень: смещение (0, -6) в экранных осях ≈ (1.9, -5.7) в осях овала, blur 9.9, чёрный 25%
        val pill = Path().apply { addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(half))) }
        clipPath(pill) {
            drawBlurredFrame(
                outer = spec.length, cx = center.x, cy = center.y,
                left = left + 1.9f, top = top - 5.7f, right = right + 1.9f, bottom = bottom - 5.7f,
                corner = half,
                color = Color.Black.copy(alpha = 0.25f),
                sigma = 4.95f,
            )
        }
    }
}

private fun renderSprite(spec: OvalSpec, density: Density): OvalSprite {
    val c = cos(THETA).toFloat()
    val s = sin(THETA).toFloat()
    val boundsW = spec.length * c + spec.thickness * s
    val boundsH = spec.length * s + spec.thickness * c
    val wDp = boundsW + 2 * PAD
    val hDp = boundsH + 2 * PAD
    val d = density.density
    val wPx = ceil(wDp * d).toInt().coerceAtLeast(1)
    val hPx = ceil(hDp * d).toInt().coerceAtLeast(1)

    val bitmap = ImageBitmap(wPx, hPx)
    CanvasDrawScope().draw(
        density, LayoutDirection.Ltr, GraphicsCanvas(bitmap), Size(wPx.toFloat(), hPx.toFloat()),
    ) {
        scale(d, d, pivot = Offset.Zero) {      // рисуем в dp, получаем чёткую картинку в пикселях
            drawPill(spec, Offset(wDp / 2f, hDp / 2f))
        }
    }

    val ux = c
    val uy = -s
    val mid = (960f - spec.cx) * ux + (540f - spec.cy) * uy
    return OvalSprite(bitmap, wDp, hDp, spec.cx, spec.cy, ux, uy, mid, spec.length / 2f + PAD)
}

// «Стеклянная» версия спрайта: та же капсула, но размытая один раз при создании
// и вчетверо меньшего разрешения. Стекло больше не делает blur на каждый кадр
// (это была главная причина тормозов), а просто рисует готовые мягкие пятна.
private const val GLASS_BITMAP_SCALE = 0.25f   // разрешение стеклянных спрайтов
private const val GLASS_SIGMA = 20f            // сила размытия стекла (≈ прежний blur 34 dp), dp
private const val GLASS_PAD = 84f              // запас под расплывание (≥ 3·sigma), dp

private fun renderGlassSprite(spec: OvalSpec, density: Density): OvalSprite {
    val c = cos(THETA).toFloat()
    val s = sin(THETA).toFloat()
    val boundsW = spec.length * c + spec.thickness * s
    val boundsH = spec.length * s + spec.thickness * c
    val wDp = boundsW + 2 * GLASS_PAD
    val hDp = boundsH + 2 * GLASS_PAD
    val pxPerDp = density.density * GLASS_BITMAP_SCALE
    val wPx = ceil(wDp * pxPerDp).toInt().coerceAtLeast(1)
    val hPx = ceil(hDp * pxPerDp).toInt().coerceAtLeast(1)

    val bitmap = ImageBitmap(wPx, hPx)
    CanvasDrawScope().draw(
        density, LayoutDirection.Ltr, GraphicsCanvas(bitmap), Size(wPx.toFloat(), hPx.toFloat()),
    ) {
        scale(pxPerDp, pxPerDp, pivot = Offset.Zero) {
            val center = Offset(wDp / 2f, hDp / 2f)
            withTransform({ rotate(ANGLE, pivot = center) }) {
                drawBlurredRoundRect(
                    topLeft = Offset(center.x - spec.length / 2f, center.y - spec.thickness / 2f),
                    size = Size(spec.length, spec.thickness),
                    corner = spec.thickness / 2f,
                    color = spec.color,
                    sigma = GLASS_SIGMA,
                )
            }
        }
    }

    val ux = c
    val uy = -s
    val mid = (960f - spec.cx) * ux + (540f - spec.cy) * uy
    return OvalSprite(bitmap, wDp, hDp, spec.cx, spec.cy, ux, uy, mid, spec.length / 2f + GLASS_PAD)
}

// ───────────────────────── Анимация дрейфа ─────────────────────────

/**
 * Общее состояние анимации: спрайты овалов и часы.
 * Один экземпляр рисуется и на фоне окна, и (размытым) внутри стеклянной карточки,
 * поэтому обе копии всегда движутся синхронно.
 */
@Stable
class OvalsAnimation internal constructor(
    internal val sprites: List<OvalSprite>,
    internal val glassSprites: List<OvalSprite>,
    internal val clock: LongState,
    internal val speed: Float,
    internal val viewDiagonal: Float,
    internal val period: Float,
)

/**
 * @param speed скорость дрейфа, dp/сек вдоль овалов (вправо-вверх). Отрицательное значение: в обратную сторону.
 * @param period расстояние, через которое узор повторяется. Цикл анимации = period / speed секунд.
 *               Больше значение: реже овалы дублируются и композиция ближе к макету.
 */
@Composable
fun rememberOvalsAnimation(
    specs: List<OvalSpec> = MenuOvals,
    speed: Float = 60f,
    period: Float = 2400f,
): OvalsAnimation {
    val density = LocalDensity.current
    val stage = LocalStage.current
    val clock = remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var lastShown = start
        while (true) {
            withFrameNanos { now ->
                // Обновляем картинку ~30 раз в секунду: движение остаётся плавным,
                // а нагрузка на GPU падает вдвое (на мониторах 120/144 Гц — в 4–5 раз).
                // Позиции считаются от абсолютного времени, поэтому ход анимации не меняется.
                if (now - lastShown >= 33_333_333L) {
                    clock.longValue = now - start
                    lastShown = now
                }
            }
        }
    }

    val sprites = remember(specs, density.density) { specs.map { renderSprite(it, density) } }
    val glassSprites = remember(specs, density.density) { specs.map { renderGlassSprite(it, density) } }
    val diagonal = hypot(stage.windowWidth.value, stage.windowHeight.value)
    return remember(sprites, glassSprites, speed, diagonal, period) {
        OvalsAnimation(sprites, glassSprites, clock, speed, diagonal, period)
    }
}

/**
 * Рисует овалы. [designTopLeft]: какой точке макета (в dp) соответствует левый верхний угол этого Canvas.
 * Для фона окна это (-originX, -originY), для карточки: её позиция в макете.
 * Анимированное значение читается только на этапе отрисовки: рекомпозиций на каждый кадр нет.
 */
/**
 * @param glass true — рисовать заранее размытые «стеклянные» спрайты (для карточки):
 *              мягкие цветовые пятна без blur-эффекта на каждый кадр.
 */
@Composable
fun OvalsCanvas(
    anim: OvalsAnimation,
    designTopLeft: Offset,
    modifier: Modifier = Modifier,
    glass: Boolean = false,
) {
    val sprites = if (glass) anim.glassSprites else anim.sprites
    Canvas(modifier) {
        val d = density
        val period = anim.period.toDouble()
        val travelled = anim.clock.longValue / 1e9 * anim.speed
        val phase = ((travelled % period) + period) % period

        for (sp in sprites) {
            // какие копии овала (сдвинутые на k·period) сейчас попадают в окно
            val span = anim.viewDiagonal / 2.0 + sp.halfLength
            val kMin = ceil((sp.mid - span - phase) / period).toInt()
            val kMax = floor((sp.mid + span - phase) / period).toInt()
            for (k in kMin..kMax) {
                val delta = phase + k * period
                val x = sp.cx + sp.ux * delta - sp.width / 2.0 - designTopLeft.x
                val y = sp.cy + sp.uy * delta - sp.height / 2.0 - designTopLeft.y
                // dstSize: обычные спрайты рисуются 1:1, а стеклянные (¼ разрешения) растягиваются.
                drawImage(
                    image = sp.image,
                    dstOffset = IntOffset((x * d).roundToInt(), (y * d).roundToInt()),
                    dstSize = IntSize((sp.width * d).roundToInt(), (sp.height * d).roundToInt()),
                )
            }
        }
    }
}

// ───────────────────────── Стеклянная карточка ─────────────────────────

/**
 * «Матовое стекло»: внутри карточки — та же анимация овалов, но из заранее размытых
 * спрайтов (GLASS_SIGMA), сверху полупрозрачная заливка и обводка из Figma.
 * Живого blur-эффекта на каждый кадр нет — так заметно быстрее.
 * Размер и позицию задаёт [modifier].
 *
 * @param designTopLeft позиция левого верхнего угла карточки в координатах макета (dp).
 */
@Composable
fun GlassCard(
    anim: OvalsAnimation,
    designTopLeft: Offset,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 39.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = ScoreKeeperTheme.colors
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier.drawBehind {
            // тень (-5, 13), blur 17.8, чёрный 25%, только снаружи карточки (сквозь стекло не просвечивает)
            val r = cornerRadius.toPx()
            val card = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
            }
            clipPath(card, ClipOp.Difference) {
                drawBlurredRoundRect(
                    topLeft = Offset(-5.dp.toPx(), 13.dp.toPx()),
                    size = size,
                    corner = r,
                    color = Color.Black.copy(alpha = 0.25f),
                    sigma = 8.9f.dp.toPx(),
                )
            }
        },
    ) {
        OvalsCanvas(anim, designTopLeft, Modifier.matchParentSize().clip(shape), glass = true)
        Box(
            Modifier
                .matchParentSize()
                .background(colors.glassFill, shape)
                .border(2.dp, colors.glassBorder, shape),
        )
        content()
    }
}
