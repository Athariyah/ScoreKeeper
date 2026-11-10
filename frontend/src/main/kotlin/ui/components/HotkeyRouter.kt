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
 * Перевод нажатия клавиши в [HotkeyId].
 *
 * Используем кросс-платформенное API Compose (`KeyEvent.key`, `isCtrlPressed`, …), а не
 * AWT-события: на desktop `nativeKeyEvent` — внутренний класс Compose, а не
 * `java.awt.event.KeyEvent`.
 *
 * Важно: раскладка клавиатуры не должна влиять на хоткеи. Клавиши «~», «−», «1», «2» и
 * Enter на русской раскладке дают другие символы, поэтому ориентируемся на физическую
 * клавишу и, дополнительно, на её символ/код — так работают обе раскладки.
 */
object HotkeyKeys {
    /** Переводит нажатие в [HotkeyId] (или null, если клавиша не наша). */
    fun toHotkey(event: KeyEvent): HotkeyId? {
        if (event.type != KeyEventType.KeyDown) return null

        val key = event.key
        val shift = event.isShiftPressed

        // F1/F2/F11 разбираем до проверки модификаторов: на ноутбуках Fn+F-клавиши — обычное
        // дело, а Ctrl/Alt + F1…F4 системных сочетаний не занимают.
        when (key) {
            Key.F1 -> return HotkeyId.CLOSE_APP
            Key.F2 -> return HotkeyId.MINIMIZE
            Key.F11 -> return HotkeyId.BACK_H
            else -> Unit
        }

        // Остальные сочетания с модификаторами оставляем системе (Ctrl+C, Alt+Tab и т.п.).
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return null

        // Физические клавиши: работают в любой раскладке (RU/EN) одинаково.
        when (key) {
            Key.One, Key.NumPad1 -> return if (shift) null else HotkeyId.ADD_POINT_1
            Key.Two, Key.NumPad2 -> return if (shift) null else HotkeyId.ADD_POINT_2
            Key.Minus, Key.NumPadSubtract -> return if (shift) null else HotkeyId.UNDO
            Key.Tab -> return HotkeyId.CORRECTION
            Key.Backspace, Key.Delete -> return HotkeyId.RESET
            // Пробел — старт/продолжить/завершить матч (широкая клавиша макета).
            Key.Spacebar -> return if (shift) null else HotkeyId.START_MATCH
            Key.Enter, Key.NumPadEnter -> return HotkeyId.DONE
            Key.Escape -> return HotkeyId.BACK_ESC
            // H — окно «ХОТКЕИ» (эта клавиша нарисована в верхней полосе экрана матча).
            // Физическая клавиша, поэтому на русской раскладке («Р») работает так же.
            Key.H -> return if (shift) null else HotkeyId.BACK_H
            else -> Unit
        }

        // «~» живёт на клавише слева от «1» (в EN это `/~, на русской — та же клавиша «ё»):
        // Compose присылает физическую клавишу, поэтому раскладка здесь не важна.
        if (key == Key.Grave || key == Key.Apostrophe) return HotkeyId.SETTINGS

        return null
    }
}

/**
 * Помечает поле ввода: пока в нём курсор, горячие клавиши не перехватывают набор текста
 * (иначе «−» или Enter мешали бы печатать).
 */
fun Modifier.trackTextFocus(key: Any): Modifier =
    onFocusChanged { HotkeyRouter.markTextFocus(key, it.isFocused) }
