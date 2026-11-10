package ui.hotkeys

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint

// ───────────────────────── Горячие клавиши приложения ─────────────────────────
//
// Список клавиш — из экрана «ХОТКЕИ»:
//   1 / 2  — очко команде 1 / 2          ~ — настройки
//   -      — откат                       F1 — закрыть приложение
//   Tab    — корректировка               F2 — свернуть приложение
//   ⌫      — сброс                       Enter — готово / подтвердить
//   Space  — начать / продолжить / завершить матч
//   H      — открыть хоткеи (а внутри них — «назад»)      Esc — назад

/** Логические клавиши приложения (не зависят от раскладки клавиатуры). */
enum class HotKey { ONE, TWO, MINUS, TAB, BACKSPACE, SPACE, H, ESC, TILDE, F1, F2, ENTER }

/**
 * Определяет логическую клавишу. Для H и ~ дополнительно смотрим на введённый символ,
 * чтобы работало и в русской раскладке (там физическая H даёт «р», а ~ — «ё»).
 */
fun KeyEvent.toHotKey(): HotKey? {
    when (key) {
        Key.One, Key.NumPad1 -> return HotKey.ONE
        Key.Two, Key.NumPad2 -> return HotKey.TWO
        Key.Minus, Key.NumPadSubtract -> return HotKey.MINUS
        Key.Tab -> return HotKey.TAB
        Key.Backspace -> return HotKey.BACKSPACE
        Key.Spacebar -> return HotKey.SPACE
        Key.Escape -> return HotKey.ESC
        Key.Enter, Key.NumPadEnter -> return HotKey.ENTER
        Key.F1 -> return HotKey.F1
        Key.F2 -> return HotKey.F2
        Key.H -> return HotKey.H
        Key.Grave -> return HotKey.TILDE
        else -> Unit
    }
    return when (utf16CodePoint) {
        'h'.code, 'H'.code, 'р'.code, 'Р'.code -> HotKey.H
        '`'.code, '~'.code, 'ё'.code, 'Ё'.code -> HotKey.TILDE
        '-'.code -> HotKey.MINUS
        else -> null
    }
}

/** Один «слой» клавиатуры: открытый оверлей, который сам обрабатывает Esc и Enter. */
class HotkeyLayer {
    var onBack: (() -> Unit)? = null
    var onConfirm: (() -> Unit)? = null
}

/** Действия экрана матча, доступные с клавиатуры. null — действие сейчас недоступно. */
class MatchHotkeyActions(
    val point1: (() -> Unit)?,
    val point2: (() -> Unit)?,
    val undo: (() -> Unit)?,
    val correction: (() -> Unit)?,
    val reset: (() -> Unit)?,
    val mainAction: () -> Unit,
    val back: () -> Unit,
)

object Hotkeys {
    /** Стек открытых оверлеев: верхний (последний) получает Esc / Enter. */
    val layers = mutableStateListOf<HotkeyLayer>()

    /** Действия экрана матча (регистрирует сам экран, пока он на экране). */
    var matchActions: MatchHotkeyActions? by mutableStateOf(null)

    /** true, пока курсор в текстовом поле: тогда 1, 2, -, Space, Tab и т.д. — обычный ввод. */
    var typing: Boolean by mutableStateOf(false)
}

/** Регистрирует оверлей в стеке клавиатуры на время, пока он на экране. */
@Composable
fun RegisterHotkeyLayer(onBack: (() -> Unit)?, onConfirm: (() -> Unit)?) {
    val layer = remember { HotkeyLayer() }
    SideEffect {
        layer.onBack = onBack
        layer.onConfirm = onConfirm
    }
    DisposableEffect(layer) {
        Hotkeys.layers.add(layer)
        onDispose { Hotkeys.layers.remove(layer) }
    }
}

/** Регистрирует действия экрана матча. */
@Composable
fun RegisterMatchHotkeys(actions: MatchHotkeyActions) {
    SideEffect { Hotkeys.matchActions = actions }
    DisposableEffect(Unit) {
        onDispose { Hotkeys.matchActions = null }
    }
}

/** Помечает текстовое поле: пока в нём фокус, игровые хоткеи отключены. */
fun Modifier.trackTyping(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { if (focused) Hotkeys.typing = false }
    }
    onFocusChanged {
        focused = it.isFocused
        Hotkeys.typing = it.isFocused
    }
}