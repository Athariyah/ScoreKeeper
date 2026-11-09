package ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.type
import java.awt.event.KeyEvent as AwtKeyEvent

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
 * Раскладка клавиш из макета «Хоткеи.pdf»:
 *
 * | клавиша          | действие                                  |
 * |------------------|-------------------------------------------|
 * | 1 / 2            | очко команде 1 / 2                        |
 * | − (минус)        | откат                                     |
 * | Tab      (→|)    | корректировка                             |
 * | Backspace / Del  | сброс                                     |
 * | Enter            | начать / продолжить / завершить, «готово» |
 * | H, Esc           | назад                                     |
 * | ~ (Backquote)    | настройки                                 |
 * | F1               | закрыть приложение                        |
 * | F2               | свернуть приложение                       |
 */
object HotkeyKeys {
    /** Переводит нажатие в [HotkeyId] (или null, если клавиша не наша). */
    fun toHotkey(event: KeyEvent): HotkeyId? {
        if (event.type != KeyEventType.KeyDown) return null
        val awt = event.nativeKeyEvent
        val code = awt.keyCode
        // Сочетания с модификаторами оставляем системе (Ctrl+C, Alt+Tab и т.п.),
        // кроме F1/F2 — их обрабатываем и с Ctrl/Alt.
        val functionKey = code == AwtKeyEvent.VK_F1 || code == AwtKeyEvent.VK_F2
        if (!functionKey && (awt.isControlDown || awt.isAltDown || awt.isMetaDown)) return null
        val shift = awt.isShiftDown
        return when (code) {
            AwtKeyEvent.VK_1, AwtKeyEvent.VK_NUMPAD1 -> if (shift) null else HotkeyId.ADD_POINT_1
            AwtKeyEvent.VK_2, AwtKeyEvent.VK_NUMPAD2 -> if (shift) null else HotkeyId.ADD_POINT_2
            AwtKeyEvent.VK_MINUS, AwtKeyEvent.VK_SUBTRACT ->
                if (shift) null else HotkeyId.UNDO
            AwtKeyEvent.VK_TAB -> HotkeyId.CORRECTION
            AwtKeyEvent.VK_BACK_SPACE, AwtKeyEvent.VK_DELETE -> HotkeyId.RESET
            AwtKeyEvent.VK_ENTER, AwtKeyEvent.VK_NUMPAD_ENTER -> HotkeyId.DONE
            AwtKeyEvent.VK_ESCAPE -> HotkeyId.BACK_ESC
            AwtKeyEvent.VK_H -> if (shift) null else HotkeyId.BACK_H
            AwtKeyEvent.VK_BACK_QUOTE -> HotkeyId.SETTINGS
            // F1/F2 работают с любыми модификаторами: на ноутбуках Fn+F1/F2 — обычное дело,
            // а Ctrl/Alt+F1..F4 системных сочетаний не занимают.
            AwtKeyEvent.VK_F1 -> HotkeyId.CLOSE_APP
            AwtKeyEvent.VK_F2 -> HotkeyId.MINIMIZE
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
