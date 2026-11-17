package com.padboard.keyboard;

import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import static android.view.KeyEvent.*;

final class EditorOutput {
    private static final int[] MODIFIER_KEYS = {KEYCODE_CTRL_LEFT, KEYCODE_SHIFT_LEFT, KEYCODE_ALT_LEFT};
    private static final int[] MODIFIER_BITS = {META_CTRL_ON, META_SHIFT_ON, META_ALT_ON};
    private final PadBoardIme ime;
    private final BoardState state;

    EditorOutput(PadBoardIme ime, BoardState state) {
        this.ime = ime;
        this.state = state;
    }

    boolean send(BoardState.Face face, boolean selecting) {
        InputConnection editor = ime.getCurrentInputConnection();
        EditorInfo info = ime.getCurrentInputEditorInfo();
        if (editor == null || info == null) return false;
        if (face.code == BoardState.DELETE_WORD) return sendKey(editor, KEYCODE_DEL, META_CTRL_ON);

        int meta = 0;
        if (state.ctrl.active()) meta |= META_CTRL_ON;
        if (state.alt.active()) meta |= META_ALT_ON;
        if (state.shifted() || selecting) meta |= META_SHIFT_ON;
        boolean shortcut = (meta & (META_CTRL_ON | META_ALT_ON)) != 0;
        boolean raw = info.inputType == InputType.TYPE_NULL;
        boolean text = face.code == 0;

        if (face.code == BoardState.SELECT_ALL) {
            return (!raw && editor.performContextMenuAction(android.R.id.selectAll))
                    || sendKey(editor, KEYCODE_A, META_CTRL_ON);
        }
        if (face.code == BoardState.COPY || face.code == BoardState.PASTE) {
            int action = face.code == BoardState.COPY ? android.R.id.copy : android.R.id.paste;
            if (!raw && editor.performContextMenuAction(action)) return true;
            int code = face.code == BoardState.COPY ? KEYCODE_C : KEYCODE_V;
            return sendKey(editor, code, meta | META_CTRL_ON);
        }
        if (text && !shortcut && !raw) {
            return editor.commitText(face.text(state.shifted()), 1);
        }
        if (face.code == KEYCODE_SPACE && meta == 0 && !raw) {
            return editor.commitText(" ", 1);
        }
        if (face.code == KEYCODE_ENTER && meta == 0 && !raw) {
            if (ime.sendDefaultEditorAction(true)) return true;
            if ((info.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
                    && (info.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) {
                return editor.commitText("\n", 1);
            }
        }
        if (text) {
            if (meta == META_CTRL_ON && !raw) {
                int action = contextAction(face.label);
                if (action != 0 && editor.performContextMenuAction(action)) return true;
            }
            // Map the displayed character, including symbols that require Shift on a US key map.
            KeyEvent[] events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                    .getEvents(face.text(state.shifted()).toCharArray());
            if (events == null) return false;
            for (KeyEvent event : events) {
                if (event.getAction() == ACTION_DOWN && !KeyEvent.isModifierKey(event.getKeyCode())) {
                    return sendKey(editor, event.getKeyCode(), meta | event.getMetaState());
                }
            }
            return false;
        }
        return sendKey(editor, face.code, meta);
    }

    private static int contextAction(String label) {
        switch (label) {
            case "a": return android.R.id.selectAll;
            case "c": return android.R.id.copy;
            case "v": return android.R.id.paste;
            case "x": return android.R.id.cut;
            default: return 0;
        }
    }

    private static boolean sendKey(InputConnection editor, int code, int meta) {
        long now = SystemClock.uptimeMillis();
        int active = 0;
        // Android's native caret movement uses the editor's Shift state as well as event metadata.
        for (int i = 0; i < MODIFIER_KEYS.length; i++) {
            if ((meta & MODIFIER_BITS[i]) == 0) continue;
            active |= MODIFIER_BITS[i];
            sendEvent(editor, now, ACTION_DOWN, MODIFIER_KEYS[i], active);
        }
        boolean down = sendEvent(editor, now, ACTION_DOWN, code, meta);
        boolean up = sendEvent(editor, now, ACTION_UP, code, meta);
        for (int i = MODIFIER_KEYS.length - 1; i >= 0; i--) {
            if ((active & MODIFIER_BITS[i]) == 0) continue;
            active &= ~MODIFIER_BITS[i];
            sendEvent(editor, now, ACTION_UP, MODIFIER_KEYS[i], active);
        }
        return down || up;
    }

    private static boolean sendEvent(InputConnection editor, long now, int action, int code, int meta) {
        int flags = FLAG_SOFT_KEYBOARD | FLAG_KEEP_TOUCH_MODE;
        return editor.sendKeyEvent(new KeyEvent(now, now, action, code, 0, meta,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags));
    }
}
