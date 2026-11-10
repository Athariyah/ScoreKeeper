package ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ui.theme.AppLanguage
import ui.theme.ScoreKeeperTheme

// ───────────────────────── Переключатели из Figma (ваши SVG) ─────────────────────────
//
// Геометрия и пути взяты один в один из присланных SVG (viewBox 94×50 и 94×81):
//  • внешняя рамка #E2EEF7, дорожка #535E66;
//  • тема: круг-ручка (слева чёрная с луной — тёмная тема, справа белая с солнцем — светлая);
//  • язык: белая плашка выбора (сверху — РУ, снизу — EN), буквы #2070F5 / #BCBCBC.
// Ручка и плашка не «прыгают», а плавно едут на пружине, иконки и цвета перетекают.

// Цвета берём из темы: на тёмной карточке светлая рамка #E2EEF7 выглядела бы «дыркой».
private val SwitchIcon = Color(0xFFFFD95D)
private val LangUnselected = Color(0xFFBCBCBC)
private val LangUnselectedDark = Color(0xFF6E93B8)

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * Пружина может «перелетать» за пределы 0…1 (у неё есть отскок), а `lerp(Color, Color, t)`
 * в Compose смешивает цвета через Oklab: при t < 0 или t > 1 получается цвет вне диапазона
 * Oklab/sRGB и Skia выбрасывает IllegalArgumentException. Поэтому t всегда зажимаем.
 */
internal fun safeT(t: Float): Float = if (t.isNaN()) 0f else t.coerceIn(0f, 1f)

/** Смешивание цветов, устойчивое к «перелёту» пружины. */
internal fun lerpSafe(a: Color, b: Color, t: Float): Color = lerp(a, b, safeT(t))

// Солнце (центр иконки в (66, 25)) и луна со звёздами (центр ручки в (28, 25)) — пути из SVG.
private const val SUN_PATH =
    "M65.0938 15.7256C64.8495 15.4813 64.7273 15.1788 64.7273 14.8182V12.2727C64.7273 11.9121 64.8495 11.6101 65.0938 11.3665C65.3382 11.123 65.6402 11.0009 66 11C66.3598 10.9992 66.6622 11.1213 66.9075 11.3665C67.1527 11.6118 67.2744 11.9138 67.2727 12.2727V14.8182C67.2727 15.1788 67.1505 15.4813 66.9062 15.7256C66.6618 15.97 66.3598 16.0918 66 16.0909C65.6402 16.0901 65.3382 15.9692 65.0938 15.7256Z" +
    "M72.3 18.7C72.0667 18.4667 71.95 18.1752 71.95 17.8256C71.95 17.4761 72.0667 17.1736 72.3 16.9182L74.0818 15.1045C74.3364 14.85 74.6384 14.7227 74.988 14.7227C75.3376 14.7227 75.6401 14.85 75.8954 15.1045C76.1288 15.3379 76.2454 15.6349 76.2454 15.9955C76.2454 16.3561 76.1288 16.653 75.8954 16.8864L74.0818 18.7C73.8485 18.9333 73.5515 19.05 73.1909 19.05C72.8303 19.05 72.5333 18.9333 72.3 18.7Z" +
    "M76.1818 26.2727C75.8212 26.2727 75.5187 26.1505 75.2744 25.9062C75.03 25.6618 74.9082 25.3598 74.9091 25C74.9099 24.6402 75.0321 24.3382 75.2756 24.0938C75.5191 23.8495 75.8212 23.7273 76.1818 23.7273H78.7273C79.0879 23.7273 79.3904 23.8495 79.6347 24.0938C79.8791 24.3382 80.0008 24.6402 80 25C79.9991 25.3598 79.877 25.6622 79.6334 25.9075C79.3899 26.1527 79.0879 26.2744 78.7273 26.2727H76.1818Z" +
    "M65.0938 38.6347C64.8495 38.3904 64.7273 38.0879 64.7273 37.7273V35.1818C64.7273 34.8212 64.8495 34.5191 65.0938 34.2756C65.3382 34.0321 65.6402 33.9099 66 33.9091C66.3598 33.9082 66.6622 34.0304 66.9075 34.2756C67.1527 34.5208 67.2744 34.8229 67.2727 35.1818V37.7273C67.2727 38.0879 67.1505 38.3904 66.9062 38.6347C66.6618 38.8791 66.3598 39.0008 66 39C65.6402 38.9991 65.3382 38.8782 65.0938 38.6347Z" +
    "M57.9182 18.7L56.1045 16.9182C55.85 16.6636 55.7227 16.3561 55.7227 15.9955C55.7227 15.6349 55.85 15.3379 56.1045 15.1045C56.3379 14.8712 56.6349 14.7545 56.9955 14.7545C57.3561 14.7545 57.653 14.8712 57.8864 15.1045L59.7 16.9182C59.9333 17.1515 60.05 17.4485 60.05 17.8091C60.05 18.1697 59.9333 18.4667 59.7 18.7C59.4455 18.9333 59.1485 19.05 58.8091 19.05C58.4697 19.05 58.1727 18.9333 57.9182 18.7Z" +
    "M74.0818 34.8954L72.3 33.0818C72.0667 32.8273 71.95 32.5252 71.95 32.1756C71.95 31.8261 72.0667 31.5342 72.3 31.3C72.5333 31.0658 72.8252 30.9491 73.1756 30.95C73.5261 30.9508 73.8281 31.0675 74.0818 31.3L75.8954 33.0818C76.15 33.3151 76.2722 33.6121 76.262 33.9727C76.2518 34.3333 76.1296 34.6409 75.8954 34.8954C75.6409 35.15 75.3333 35.2773 74.9727 35.2773C74.6121 35.2773 74.3151 35.15 74.0818 34.8954Z" +
    "M53.2727 26.2727C52.9121 26.2727 52.6101 26.1505 52.3665 25.9062C52.123 25.6618 52.0009 25.3598 52 25C51.9992 24.6402 52.1213 24.3382 52.3665 24.0938C52.6118 23.8495 52.9138 23.7273 53.2727 23.7273H55.8182C56.1788 23.7273 56.4813 23.8495 56.7256 24.0938C56.97 24.3382 57.0918 24.6402 57.0909 25C57.0901 25.3598 56.9679 25.6622 56.7244 25.9075C56.4809 26.1527 56.1788 26.2744 55.8182 26.2727H53.2727Z" +
    "M56.1045 34.8954C55.8712 34.6621 55.7545 34.3651 55.7545 34.0045C55.7545 33.6439 55.8712 33.347 56.1045 33.1136L57.9182 31.3C58.1515 31.0667 58.443 30.95 58.7925 30.95C59.1421 30.95 59.4446 31.0667 59.7 31.3C59.9545 31.5545 60.0818 31.857 60.0818 32.2075C60.0818 32.5579 59.9545 32.8599 59.7 33.1136L57.9182 34.8954C57.6636 35.15 57.3561 35.2773 56.9955 35.2773C56.6349 35.2773 56.3379 35.15 56.1045 34.8954Z" +
    "M60.5909 30.4091C59.1061 28.9242 58.3636 27.1212 58.3636 25C58.3636 22.8788 59.1061 21.0758 60.5909 19.5909C62.0758 18.1061 63.8788 17.3636 66 17.3636C68.1212 17.3636 69.9242 18.1061 71.4091 19.5909C72.8939 21.0758 73.6364 22.8788 73.6364 25C73.6364 27.1212 72.8939 28.9242 71.4091 30.4091C69.9242 31.8939 68.1212 32.6364 66 32.6364C63.8788 32.6364 62.0758 31.8939 60.5909 30.4091Z"

private const val MOON_PATH =
    "M28.3809 38C26.5261 38 24.7875 37.6463 23.165 36.9388C21.5425 36.2313 20.1293 35.2753 18.9255 34.0708C17.7217 32.8663 16.7665 31.4514 16.0599 29.8259C15.3533 28.2005 15 26.4592 15 24.602C15 21.8605 15.7565 19.3733 17.2695 17.1403C18.7824 14.9073 20.8081 13.2713 23.3465 12.2321C23.6556 12.1216 23.9758 12.0827 24.307 12.1154C24.6382 12.1481 24.9142 12.2534 25.135 12.4311C25.3117 12.5859 25.4499 12.8017 25.5497 13.0785C25.6495 13.3553 25.699 13.7143 25.6981 14.1556C25.7423 15.9022 26.0955 17.5661 26.758 19.1473C27.4204 20.7286 28.3699 22.1378 29.6064 23.375C30.8429 24.6122 32.2561 25.5629 33.8459 26.227C35.4357 26.8912 37.1028 27.2449 38.8471 27.2883C39.3108 27.2883 39.6641 27.3272 39.907 27.405C40.1499 27.4828 40.3486 27.6097 40.5032 27.7857C40.6798 28.0068 40.796 28.2942 40.8516 28.648C40.9073 29.0017 40.8794 29.3223 40.7682 29.6097C39.7524 32.1522 38.1185 34.1862 35.8662 35.7117C33.614 37.2372 31.1189 38 28.3809 38Z" +
    "M31.3287 18.5L29.2089 16.3776C28.9439 16.1122 28.8115 15.8027 28.8115 15.449C28.8115 15.0952 28.9439 14.7857 29.2089 14.5204L31.3287 12.398C31.5936 12.1327 31.9028 12 32.2561 12C32.6093 12 32.9185 12.1327 33.1834 12.398L35.3032 14.5204C35.5682 14.7857 35.7006 15.0952 35.7006 15.449C35.7006 15.8027 35.5682 16.1122 35.3032 16.3776L33.1834 18.5C32.9185 18.7653 32.6093 18.898 32.2561 18.898C31.9028 18.898 31.5936 18.7653 31.3287 18.5Z" +
    "M37.9529 22.4796L37.158 21.6837C36.893 21.4184 36.7605 21.1088 36.7605 20.7551C36.7605 20.4014 36.893 20.0918 37.158 19.8265L37.9529 19.0306C38.2178 18.7653 38.527 18.6327 38.8803 18.6327C39.2335 18.6327 39.5427 18.7653 39.8076 19.0306L40.6025 19.8265C40.8675 20.0918 41 20.4014 41 20.7551C41 21.1088 40.8675 21.4184 40.6025 21.6837L39.8076 22.4796C39.5427 22.7449 39.2335 22.8776 38.8803 22.8776C38.527 22.8776 38.2178 22.7449 37.9529 22.4796Z"

// Буквы «РУ» (центр плашки сверху) и «EN» (снизу) — пути из SVG.
private const val RU_PATH =
    "M35.0869 23.84H40.1269C40.8309 23.84 41.4549 23.584 41.9989 23.072C42.5109 22.528 42.7669 21.904 42.7669 21.2C42.7669 20.464 42.5109 19.84 41.9989 19.328C41.4549 18.816 40.8309 18.56 40.1269 18.56H35.0869V23.84Z" +
    "M31.7269 16.88C31.7269 16.416 31.8949 16.016 32.2309 15.68C32.5509 15.36 32.9429 15.2 33.4069 15.2H40.1269C41.7909 15.2 43.2069 15.784 44.3749 16.952C45.5429 18.12 46.1269 19.536 46.1269 21.2C46.1269 22.864 45.5429 24.28 44.3749 25.448C43.2069 26.616 41.7909 27.2 40.1269 27.2H35.0869V30.32C35.0869 30.784 34.9269 31.176 34.6069 31.496C34.2709 31.832 33.8709 32 33.4069 32C32.9429 32 32.5509 31.832 32.2309 31.496C31.8949 31.176 31.7269 30.784 31.7269 30.32V16.88Z" +
    "M50.8116 28.64H51.3876C52.1716 28.64 52.7716 28.304 53.1876 27.632L47.4516 17.72C47.2116 17.32 47.1476 16.896 47.2596 16.448C47.3876 16 47.6516 15.656 48.0516 15.416C48.4516 15.192 48.8756 15.136 49.3236 15.248C49.7716 15.376 50.1156 15.64 50.3556 16.04L55.0836 24.176L59.4036 16.088C59.6276 15.672 59.9636 15.4 60.4116 15.272C60.8596 15.128 61.2836 15.168 61.6836 15.392C62.0996 15.616 62.3716 15.952 62.4996 16.4C62.6436 16.848 62.6036 17.272 62.3796 17.672L56.2596 29.096C55.7796 29.992 55.1076 30.696 54.2436 31.208C53.3636 31.736 52.4116 32 51.3876 32H50.8116C50.3476 32 49.9556 31.832 49.6356 31.496C49.2996 31.176 49.1316 30.784 49.1316 30.32C49.1316 29.856 49.2996 29.456 49.6356 29.12C49.9556 28.8 50.3476 28.64 50.8116 28.64Z"

private const val EN_PATH =
    "M44.1261 67H32.6061C32.1421 67 31.7421 66.84 31.4061 66.52C31.0861 66.184 30.9261 65.784 30.9261 65.32V51.88C30.9261 51.416 31.0861 51.016 31.4061 50.68C31.7421 50.36 32.1421 50.2 32.6061 50.2H44.1261C44.5901 50.2 44.9901 50.36 45.3261 50.68C45.6461 51.016 45.8061 51.416 45.8061 51.88C45.8061 52.344 45.6461 52.744 45.3261 53.08C44.9901 53.4 44.5901 53.56 44.1261 53.56H34.2861V56.92H43.1661C43.6301 56.92 44.0221 57.08 44.3421 57.4C44.6781 57.736 44.8461 58.136 44.8461 58.6C44.8461 59.064 44.6781 59.464 44.3421 59.8C44.0221 60.12 43.6301 60.28 43.1661 60.28H34.2861V63.64H44.1261C44.5901 63.64 44.9901 63.8 45.3261 64.12C45.6461 64.456 45.8061 64.856 45.8061 65.32C45.8061 65.784 45.6461 66.184 45.3261 66.52C44.9901 66.84 44.5901 67 44.1261 67Z" +
    "M48.1995 65.32V51.88C48.1995 51.096 48.5675 50.568 49.3035 50.296C50.0395 50.04 50.6555 50.2 51.1515 50.776L59.7195 60.784V51.88C59.7195 51.416 59.8795 51.024 60.1995 50.704C60.5355 50.368 60.9355 50.2 61.3995 50.2C61.8635 50.2 62.2635 50.368 62.5995 50.704C62.9195 51.024 63.0795 51.416 63.0795 51.88V65.32C63.0795 66.104 62.7115 66.632 61.9755 66.904C61.7995 66.968 61.6075 67 61.3995 67C60.8875 67 60.4635 66.8 60.1275 66.4L51.5595 56.416V65.32C51.5595 65.784 51.3995 66.176 51.0795 66.496C50.7435 66.832 50.3435 67 49.8795 67C49.4155 67 49.0155 66.832 48.6795 66.496C48.3595 66.176 48.1995 65.784 48.1995 65.32Z"

/**
 * Переключатель темы 94×50 из Figma.
 * [dark]=true — ручка слева (чёрная, луна); [dark]=false — справа (белая, солнце).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ThemeSwitch(dark: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val sunPath = remember { PathParser().parsePathString(SUN_PATH).toPath() }
    val moonPath = remember { PathParser().parsePathString(MOON_PATH).toPath() }
    val colors = ScoreKeeperTheme.colors
    val t by animateFloatAsState(
        targetValue = if (dark) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "themeKnob",
    )

    Canvas(
        modifier
            .size(94.dp, 50.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            ),
    ) {
        val s = size.width / 94f
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawRoundRect(colors.cardBorder, Offset.Zero, Size(94f, 50f), CornerRadius(25f))
            drawRoundRect(colors.fieldBg, Offset(4f, 4f), Size(86f, 42f), CornerRadius(21f))
            val cx = mix(28f, 66f, safeT(t))
            drawCircle(lerpSafe(Color.Black, Color.White, t), radius = 18f, center = Offset(cx, 25f))
            if (t < 0.98f) {
                withTransform({ translate(cx - 28f, 0f) }) {
                    drawPath(moonPath, SwitchIcon, alpha = (1f - t).coerceIn(0f, 1f))
                }
            }
            if (t > 0.02f) {
                withTransform({ translate(cx - 66f, 0f) }) {
                    drawPath(sunPath, SwitchIcon, alpha = t.coerceIn(0f, 1f))
                }
            }
        }
    }
}

/**
 * Выбор языка 94×81 из Figma: белая плашка едет между «РУ» (сверху) и «EN» (снизу).
 * Нажатие на верхнюю половину выбирает RU, на нижнюю — EN.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LanguageSwitch(
    language: AppLanguage,
    onChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ruPath = remember { PathParser().parsePathString(RU_PATH).toPath() }
    val enPath = remember { PathParser().parsePathString(EN_PATH).toPath() }
    val colors = ScoreKeeperTheme.colors
    val t by animateFloatAsState(
        targetValue = if (language == AppLanguage.RU) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "langPill",
    )

    Canvas(
        modifier
            .size(94.dp, 81.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(onChange) {
                detectTapGestures { offset ->
                    onChange(if (offset.y < size.height / 2f) AppLanguage.RU else AppLanguage.EN)
                }
            },
    ) {
        val s = size.width / 94f
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawRoundRect(colors.cardBorder, Offset.Zero, Size(94f, 81f), CornerRadius(25f))
            drawRoundRect(colors.fieldBg, Offset(4f, 4f), Size(86f, 74f), CornerRadius(25f))
            // Белая плашка: в покое совпадает с путями из SVG (y 7→41, высота 34,
            // «внешние» углы круглые 24.3, «внутренние» 9.7 — при езде углы перетекают).
            val py = mix(7f, 41f, safeT(t))
            val topR = mix(24.3f, 9.7f, safeT(t))
            val botR = mix(9.7f, 24.3f, safeT(t))
            val pill = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 8f, top = py, right = 85f, bottom = py + 34f,
                        topLeftCornerRadius = CornerRadius(topR),
                        topRightCornerRadius = CornerRadius(topR),
                        bottomRightCornerRadius = CornerRadius(botR),
                        bottomLeftCornerRadius = CornerRadius(botR),
                    ),
                )
            }
            drawPath(pill, if (colors.dark) colors.tabFill else Color.White)
            val selected = if (colors.dark) colors.accent else Color(0xFF2070F5)
            val unselected = if (colors.dark) LangUnselectedDark else LangUnselected
            drawPath(ruPath, lerpSafe(selected, unselected, t))
            drawPath(enPath, lerpSafe(unselected, selected, t))
        }
    }
}
