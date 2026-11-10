package ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import ui.components.CardBackButton
import ui.components.HotkeyId
import ui.components.HotkeyPanel
import ui.components.HotkeyPulse
import ui.components.HotkeyRow
import ui.components.blockTopForBaseline
import ui.components.drawKeycap
import ui.components.mainRows
import ui.components.matchRows
import ui.components.rememberHotkeyFlash
import ui.components.safeT
import ui.theme.AppLanguage
import ui.theme.AppStrings
import ui.theme.ScoreKeeperTypography
import ui.theme.DesignStage
import ui.theme.ScoreKeeperTheme
import ui.theme.colorsFor
import ui.theme.stringsFor

// ───────────────────────── Окно «ХОТКЕИ» ─────────────────────────
//
// Карточка 794×564 по центру экрана — ровно как в макете «Frame 10.pdf»:
// заголовок, две колонки клавиш с подписями по координатам макета, вертикальный
// разделитель и круглая кнопка «назад» в правом верхнем углу.
//
// Клавиши в списке работают как кнопки: вызывают те же действия, что и горячие клавиши
// (их присылает экран), и вспыхивают той же анимацией, что и при нажатии на клавиатуре.

@Composable
fun HotkeysOverlay(
    darkTheme: Boolean,
    language: AppLanguage,
    pulse: HotkeyPulse,
    onBack: () -> Unit,
    onHotkey: (HotkeyId) -> Unit,
) {
    ScoreKeeperTheme(colors = colorsFor(darkTheme), strings = stringsFor(language)) {
        val colors = ScoreKeeperTheme.colors
        val type = ScoreKeeperTheme.typography
        val strings = ScoreKeeperTheme.strings
        val dark = colors.dark
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, tween(170)) }
        val measurer = rememberTextMeasurer()

        // вспышки нажатых клавиш — считаются в композиции, не в draw-лямбде
        val tick = pulse.tick
        val activeId = pulse.lastId
        val match = matchRows()
        val main = mainRows()
        // Вспышки считаем в композиции: у остальных строк тик 0, поэтому анимация не идёт.
        val flashes = HashMap<HotkeyId, Float>()
        for (row in match + main) {
            flashes[row.id] = if (activeId == row.id) rememberHotkeyFlash(tick) else 0f
        }

        DesignStage {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f * safeT(appear.value)))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* клики по затемнению не проходят в меню */ },
            ) {
                Box(Modifier.align(Alignment.Center).requiredSize(1920.dp, 1080.dp)) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(HotkeyPanel.WIDTH.dp, HotkeyPanel.HEIGHT.dp)
                            .graphicsLayer {
                                val s = 0.975f + 0.025f * safeT(appear.value)
                                scaleX = s
                                scaleY = s
                                alpha = safeT(appear.value)
                            }
                            .clip(RoundedCornerShape(HotkeyPanel.RADIUS.dp))
                            .background(colors.cardSurface),
                    ) {
                        // Всё содержимое карточки — один Canvas строго по координатам макета.
                        Canvas(Modifier.fillMaxSize()) {
                            val u = 1.dp.toPx()
                            drawHotkeyCardContent(
                                measurer = measurer,
                                strings = strings,
                                type = type,
                                dark = dark,
                                accent = if (dark) colors.accent else Color(0xFF2070F5),
                                stroke = if (dark) Color(0xFF141F31) else Color(0xFFE2EEF7),
                                divider = if (dark) colors.divider else Color(0xFFE2EEF7),
                                labelColor = if (dark) colors.accent else Color(0xFF101010),
                                rows = match + main,
                                flashes = flashes,
                                unit = u,
                            )
                        }

                        // Прозрачные зоны нажатия по всей строке (клик = действие хоткея).
                        (match + main).forEach { row ->
                            HotkeyRowHitArea(row = row, onClick = { onHotkey(row.id) })
                        }

                        // кнопка «назад» в правом верхнем углу
                        CardBackButton(
                            onClick = onBack,
                            modifier = Modifier.align(Alignment.TopStart).graphicsLayer {
                                translationX = HotkeyPanel.BACK_X.dp.toPx()
                                translationY = HotkeyPanel.BACK_Y.dp.toPx()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Всё содержимое карточки: разделитель, заголовки, клавиши и подписи. */
private fun DrawScope.drawHotkeyCardContent(
    measurer: TextMeasurer,
    strings: AppStrings,
    type: ScoreKeeperTypography,
    dark: Boolean,
    accent: Color,
    stroke: Color,
    divider: Color,
    labelColor: Color,
    rows: List<HotkeyRow>,
    flashes: Map<HotkeyId, Float>,
    unit: Float,
) {
    // 1. разделитель колонок 2 dp
    val dx = HotkeyPanel.DIVIDER_X * unit
    drawLine(
        color = divider,
        start = Offset(dx, HotkeyPanel.DIVIDER_TOP * unit),
        end = Offset(dx, HotkeyPanel.DIVIDER_BOTTOM * unit),
        strokeWidth = 2f * unit,
    )

    // 2. «ХОТКЕИ» и заголовки колонок: заливка + внешний контур 2 px
    outlinedText(
        measurer, strings.hotkeys, type.panelTitle,
        x = HotkeyPanel.TITLE_X * unit,
        capTop = HotkeyPanel.TITLE_CAP_TOP * unit,
        fill = accent, stroke = stroke, strokeWidth = 2f * unit,
    )
    outlinedText(
        measurer, strings.hotkeysMatchButtons, type.panelHeader,
        x = HotkeyPanel.LEFT_HEADER_X * unit,
        capTop = HotkeyPanel.HEADER_CAP_TOP * unit,
        fill = accent, stroke = stroke, strokeWidth = 2f * unit,
    )
    outlinedText(
        measurer, strings.hotkeysMain, type.panelHeader,
        x = HotkeyPanel.RIGHT_HEADER_X * unit,
        capTop = HotkeyPanel.HEADER_CAP_TOP * unit,
        fill = accent, stroke = stroke, strokeWidth = 2f * unit,
    )

    // 3. подписи строк — по базовым линиям макета, многострочные
    val labelStyle = type.hotkeyLabel.copy(color = labelColor)
    rows.forEach { row ->
        val layout = measurer.measure(AnnotatedString(row.label(strings)), labelStyle)
        val top = blockTopForBaseline(
            baseline = row.textY,
            size = labelStyle.fontSize.value,
            lineHeight = labelStyle.lineHeight.value * u,
        )
        drawText(
            textLayoutResult = layout,
            color = labelColor,
            topLeft = Offset(row.textX * unit, top),
        )
    }

    // 4. клавиши
    rows.forEach { row ->
        withTransform({ translate(row.keyX * unit, row.keyY * unit) }) {
            drawKeycap(
                glyph = row.glyph,
                keyW = row.keyW,
                pulse = flashes[row.id] ?: 0f,
                dark = dark,
                measurer = measurer,
                keyStyle = type.hotkeyKey,
                scale = unit,
            )
        }
    }
}

/**
 * Текст с внешним контуром. [capTop] — верх заглавных букв (как в макете), поэтому блок
 * поднимается на высоту подъёма шрифта сверх высоты заглавной.
 */
private fun DrawScope.outlinedText(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    capTop: Float,
    fill: Color,
    stroke: Color,
    strokeWidth: Float,
) {
    val layout = measurer.measure(AnnotatedString(text), style)
    val sizePx = style.fontSize.value * density
    // верх блока = capTop − (базовая линия первой строки − высота заглавной)
    val top = capTop - (layout.firstBaseline - sizePx * 0.702f)
    val topLeft = Offset(x, top)
    drawText(layout, color = stroke, topLeft = topLeft, drawStyle = Stroke(strokeWidth, join = StrokeJoin.Round))
    drawText(layout, color = fill, topLeft = topLeft)
}

/** Прозрачная зона нажатия на строку — подсветка при наведении и клик. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BoxScope.HotkeyRowHitArea(row: HotkeyRow, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val hover by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 320f),
        label = "hotkeyRowHover",
    )
    val colors = ScoreKeeperTheme.colors
    val glow = if (colors.dark) Color.White else Color(0xFF2070F5)
    val h = if (row.id == HotkeyId.START_MATCH) 64f else 50f

    Box(
        Modifier
            .graphicsLayer {
                translationX = (row.keyX - 8f).dp.toPx()
                translationY = (row.keyY - 4f).dp.toPx()
            }
            .size(400.dp, h.dp)
            .clip(RoundedCornerShape(14.dp))
            // hover приходит с пружиной и может «перелетать» в минус: alpha < 0 → краш Skia
            .background(glow.copy(alpha = (if (colors.dark) 0.05f else 0.06f) * safeT(hover)))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    )
}
