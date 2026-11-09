package ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Маршрутизатор горячих клавиш.
 *
 * Клавиатуру слушает окно приложения (`Window(onPreviewKeyEvent = …)` в Main.kt), а действия
 * живут на экранах. Чтобы не протаскивать коллбэки через десять слоёв, используется этот
 * маленький маршрутизатор (по духу — как `ApiClient`):
 *
 *  1. [dialogHandler] — открыт модальный диалог: Enter = «готово/подтвердить», Esc = «назад»;
 *  2. [screenHandler] — экран матча: очки, откат, корректировка, сброс, старт/финиш, назад;
 *  3. дальше Main.kt обрабатывает общие действия окна: настройки, свернуть, закрыть.
 *
 * Обработчик возвращает `true`, если событие «съедено».
 */
object HotkeyRouter {
    /** Открыт диалог: обрабатываем только подтверждение и «назад». */
    var dialogHandler: ((HotkeyId) -> Boolean)? = null

    /** Экран (матч/меню): основные действия. */
    var screenHandler: ((HotkeyId) -> Boolean)? = null

    private val focusedFields = mutableSetOf<Any>()

    /** Курсор в текстовом поле — горячие клавиши не должны мешать печатать. */
    val textInputFocused: Boolean
        get() = synchronized(focusedFields) { focusedFields.isNotEmpty() }

    internal fun markTextFocus(key: Any, focused: Boolean) {
        synchronized(focusedFields) {
            if (focused) focusedFields.add(key) else focusedFields.remove(key)
        }
    }

    fun dispatchToDialog(id: HotkeyId): Boolean = dialogHandler?.invoke(id) ?: false

    fun dispatchToScreen(id: HotkeyId): Boolean = screenHandler?.invoke(id) ?: false
}

/**
 * Перевод нажатия клавиши из макета в [HotkeyId].
 *
 * Используем кросс-платформенное API Compose (`KeyEvent.key`, `isCtrlPressed`, …), а не
 * AWT-события: на desktop `nativeKeyEvent` — это внутренний класс Compose, а не
 * `java.awt.event.KeyEvent`, поэтому обращаться к `keyCode`/`isControlDown` нельзя.
 */
object HotkeyKeys {
    /** Переводит нажатие в [HotkeyId] (или null, если клавиша не наша). */
    fun toHotkey(event: KeyEvent): HotkeyId? {
        if (event.type != KeyEventType.KeyDown) return null

        val key = event.key
        val shift = event.isShiftPressed

        // F1/F2 разбираем до проверки модификаторов: на ноутбуках Fn+F1/F2 — обычное дело,
        // а Ctrl/Alt+F1..F4 системных сочетаний не занимают.
        when (key) {
            Key.F1 -> return HotkeyId.CLOSE_APP
            Key.F2 -> return HotkeyId.MINIMIZE
            else -> Unit
        }

        // Остальные сочетания с модификаторами оставляем системе (Ctrl+C, Alt+Tab и т.п.).
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return null

        return when (key) {
            Key.One, Key.NumPad1 -> if (shift) null else HotkeyId.ADD_POINT_1
            Key.Two, Key.NumPad2 -> if (shift) null else HotkeyId.ADD_POINT_2
            Key.Minus, Key.NumPadSubtract -> if (shift) null else HotkeyId.UNDO
            Key.Tab -> HotkeyId.CORRECTION
            Key.Backspace, Key.Delete -> HotkeyId.RESET
            Key.Enter, Key.NumPadEnter -> HotkeyId.DONE
            Key.Escape -> HotkeyId.BACK_ESC
            Key.H -> if (shift) null else HotkeyId.BACK_H
            Key.Grave -> HotkeyId.SETTINGS
            else -> null
        }
    }
}

/**
 * Помечает поле ввода: пока в нём курсор, горячие клавиши не перехватывают набор текста
 * (иначе буква «H» закрывала бы диалог вместо того, чтобы напечататься).
 */
fun Modifier.trackTextFocus(key: Any): Modifier =
    onFocusChanged { HotkeyRouter.markTextFocus(key, it.isFocused) }
