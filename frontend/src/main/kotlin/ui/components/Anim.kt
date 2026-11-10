package ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Пружина (`spring`) даёт «отскок» и выходит за пределы 0…1. Compose смешивает цвета
 * через Oklab: при t < 0 или t > 1 получается цвет вне диапазона Oklab/sRGB, и Skia
 * выбрасывает IllegalArgumentException. Отрицательная альфа падает так же.
 *
 * Здесь — два безопасных помощника, которыми пользуется всё приложение: параметр
 * анимации всегда зажимается в [0, 1], поэтому ни смешивание цветов, ни `copy(alpha=…)`
 * не могут выйти за допустимый диапазон.
 */

/** Зажимает параметр анимации в [0, 1] (NaN → 0). */
internal fun safeT(t: Float): Float = if (t.isNaN()) 0f else t.coerceIn(0f, 1f)

/** Смешивание цветов, устойчивое к «перелёту» пружины. */
internal fun lerpSafe(a: Color, b: Color, t: Float): Color = lerp(a, b, safeT(t))
