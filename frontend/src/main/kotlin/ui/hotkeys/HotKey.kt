package ui.hotkeys

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint
import kotlinx.coroutines.delay

// ───────────────────────── Горячие клавиши приложения ─────────────────────────
//
// Список клавиш — из экрана «ХОТКЕИ»:
//   1 / 2  — очко команде 1 / 2          ~ — настройки
//   -      — откат                       F1 — закрыть приложение
//   Tab    — корректировка               F2 — свернуть приложение
//   ⌫      — сброс                       Enter — готово / подтвердить
//   Space  — начать / продолжить / завершить матч
//   H      — открыть хоткеи (только открыть; закрыть — стрелкой или Esc)
//   Esc    — назад

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

    /** Клавиши, физически зажатые прямо сейчас (между KeyDown и KeyUp). */
    val held = mutableStateMapOf<HotKey, Boolean>()

    /** Счётчик срабатываний каждой клавиши (растёт на каждый KeyDown) — нужен, чтобы даже
     *  короткий тап успевал отыграть полную визуальную анимацию нажатия. */
    val pressCount = mutableStateMapOf<HotKey, Int>()

    fun onKeyDown(hk: HotKey) {
        held[hk] = true
        pressCount[hk] = (pressCount[hk] ?: 0) + 1
    }

    fun onKeyUp(hk: HotKey) {
        held.remove(hk)
    }

    fun clearPressed() {
        held.clear()
    }
}

/**
 * Анимированный прогресс нажатия горячей клавиши (0f — отпущена, 1f — нажата):
 *  • при коротком нажатии держит импульс ~145 мс, чтобы ход клавиши вниз всегда был заметен;
 *  • при удержании остаётся нажатой до KeyUp, затем пружинно возвращается.
 */
@Composable
fun rememberHotkeyPress(hotKey: HotKey?, enabled: Boolean = true): Float {
    if (hotKey == null) return 0f
    val isHeld = enabled && Hotkeys.held[hotKey] == true
    val count = Hotkeys.pressCount[hotKey] ?: 0
    var lastSeen by remember(hotKey) { mutableStateOf(count) }
    var pulse by remember(hotKey) { mutableStateOf(false) }

    LaunchedEffect(count, enabled) {
        if (!enabled) {
            lastSeen = count
            pulse = false
            return@LaunchedEffect
        }
        if (count != lastSeen) {
            lastSeen = count
            pulse = false
            withFrameNanos { }
            pulse = true
            delay(145)
            pulse = false
        }
    }

    val active = isHeld || pulse
    val spec = if (active) tween<Float>(60) else spring(dampingRatio = 0.55f, stiffness = 480f)
    return animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spec,
        label = "hkPress_$hotKey",
    ).value
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