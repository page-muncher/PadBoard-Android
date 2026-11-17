package com.padboard.keyboard;

import android.view.KeyEvent;

import java.util.HashSet;
import java.util.Set;

final class KeyGate {
    private final Set<Long> consumed = new HashSet<>();

    boolean route(PadBoardIme ime, KeyEvent event) {
        long id = ((long) event.getDeviceId() << 32) | event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (!consumed.remove(id)) return false;
            if (ime != null) ime.controllerKey(event);
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (event.getRepeatCount() != 0) return consumed.contains(id);
        if (ime != null && ime.wantsControllerKey(event)) {
            // A corresponding key up stays consumed even if this down closes the IME.
            consumed.add(id);
            ime.controllerKey(event);
            return true;
        }
        return false;
    }

    void removeDevice(int deviceId) {
        consumed.removeIf(id -> (int) (id >> 32) == deviceId);
    }
}
