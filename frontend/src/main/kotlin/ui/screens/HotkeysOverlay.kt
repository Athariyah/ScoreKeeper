package ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.components.HotkeyColumnHeader
import ui.components.HotkeyId
import ui.components.HotkeyPanel
import ui.components.HotkeyPulse
import ui.components.HotkeyRow
import ui.components.HotkeyRowView
import ui.components.KeyGlyph
import ui.components.rememberHotkeyFlash
import ui.theme.AppLanguage
import ui.theme.AppStrings
import ui.theme.ScoreKeeperTheme
import ui.theme.colorsFor
import ui.theme.stringsFor

// ───────────────────────── Окно «ХОТКЕИ» ─────────────────────────
//
// Карточка 794×564 по центру экрана — как в макете «Хоткеи.pdf»:
// заголовок (Aqum, 46) цветом акцента, две колонки клавиш с подписями,
// вертикальный разделитель между ними и круглая кнопка «назад» в правом верхнем углу.
//
// Клавиши в списке работают как кнопки: вызывают те же действия, что и горячие клавиши
// (их присылает экран), и вспыхивают той же анимацией, что и при нажатии на клавиатуре.

/** Левая колонка — «КНОПКИ МАТЧА». */
private fun matchRows(): List<HotkeyRow> = listOf(
    HotkeyRow(HotkeyId.ADD_POINT_1, KeyGlyph.Digit1) { it.hotkeyAddPoint1 },
    HotkeyRow(HotkeyId.ADD_POINT_2, KeyGlyph.Digit2) { it.hotkeyAddPoint2 },
    HotkeyRow(HotkeyId.UNDO, KeyGlyph.Minus) { it.hotkeyUndo },
    HotkeyRow(HotkeyId.CORRECTION, KeyGlyph.Correction, keyWidth = 62f) { it.hotkeyCorrection },
    HotkeyRow(HotkeyId.RESET, KeyGlyph.Reset, keyWidth = 62f) { it.hotkeyReset },
    HotkeyRow(HotkeyId.START_MATCH, KeyGlyph.EnterWide, keyWidth = 154f) { it.hotkeyStartMatch },
)

/** Правая колонка — «ОСНОВНЫЕ». */
private fun mainRows(): List<HotkeyRow> = listOf(
    HotkeyRow(HotkeyId.BACK_H, KeyGlyph.LetterH) { it.hotkeyBack },
    HotkeyRow(HotkeyId.BACK_ESC, KeyGlyph.Esc) { it.hotkeyBack },
    HotkeyRow(HotkeyId.SETTINGS, KeyGlyph.Tilde) { it.hotkeySettings },
    HotkeyRow(HotkeyId.CLOSE_APP, KeyGlyph.F1) { it.hotkeyCloseApp },
    HotkeyRow(HotkeyId.MINIMIZE, KeyGlyph.F2) { it.hotkeyMinimize },
    HotkeyRow(HotkeyId.DONE, KeyGlyph.EnterSmall, keyWidth = 62f) { it.hotkeyDone },
)

/**
 * @param onHotkey действие по клику на «клавишу» в списке — те же обработчики, что у клавиатуры.
 * @param pulse текущая вспышка нажатой клавиши (общая для клавиатуры и кликов).
 */
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

        OverlayScaffold(
            cardWidth = HotkeyPanel.WIDTH.dp,
            cardHeight = HotkeyPanel.HEIGHT.dp,
            onBack = onBack,
        ) {
            Box(
                Modifier.fillMaxWidth().offset(y = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                FigmaText(strings.hotkeys, type.panelTitle, colors.accentText)
            }

            Row(Modifier.offset(34.dp, 100.dp)) {
                HotkeyColumn(
                    header = strings.hotkeysMatchButtons,
                    rows = matchRows(),
                    strings = strings,
                    pulse = pulse,
                    dark = dark,
                    onHotkey = onHotkey,
                    modifier = Modifier.width(344.dp),
                )
                // разделитель колонок, как в макете (2 px, от подписей до низа списка)
                Box(
                    Modifier.offset(x = 13.dp, y = 18.dp)
                        .size(2.dp, 404.dp)
                        .background(colors.divider),
                )
                HotkeyColumn(
                    header = strings.hotkeysMain,
                    rows = mainRows(),
                    strings = strings,
                    pulse = pulse,
                    dark = dark,
                    onHotkey = onHotkey,
                    modifier = Modifier.offset(x = 30.dp).width(344.dp),
                )
            }
        }
    }
}

@Composable
private fun HotkeyColumn(
    header: String,
    rows: List<HotkeyRow>,
    strings: AppStrings,
    pulse: HotkeyPulse,
    dark: Boolean,
    onHotkey: (HotkeyId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tick = pulse.tick
    val activeId = pulse.lastId

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().offset(y = (-6).dp), contentAlignment = Alignment.Center) {
            HotkeyColumnHeader(header, dark = dark)
        }
        rows.forEach { row ->
            HotkeyRowClickable(
                row = row,
                label = row.label(strings),
                tick = tick,
                active = activeId == row.id,
                dark = dark,
                onClick = { onHotkey(row.id) },
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HotkeyRowClickable(
    row: HotkeyRow,
    label: String,
    tick: Long,
    active: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // Вспышка только у той строки, чья клавиша нажата: остальным отдаём «пустой» тик,
    // иначе при любом нажатии мигали бы все шесть клавиш сразу.
    val flash = rememberHotkeyFlash(if (active) tick else 0L)
    val hover by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 320f),
        label = "hotkeyRowHover",
    )
    val glow = if (dark) Color.White else Color(0xFF2070F5)

    Box(
        Modifier
            .offset(y = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(glow.copy(alpha = (if (dark) 0.035f else 0.05f) * hover))
            .graphicsLayer {
                val s = 1f + 0.012f * hover
                scaleX = s
                scaleY = s
            }
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(Modifier.height(44.dp), contentAlignment = Alignment.CenterStart) {
            HotkeyRowView(
                row = row,
                label = label,
                pulse = flash,
                dark = dark,
                modifier = Modifier.offset(x = 10.dp),
            )
        }
    }
}
