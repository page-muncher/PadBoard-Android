package com.padboard.keyboard;

final class ModifierLatch {
    static final long DOUBLE_TAP_MS = 300;
    private static final long ONE_SHOT_CANCEL_MS = 1_000;
    private boolean armed;
    private boolean locked;
    private long armedAt;

    boolean active() {
        return armed || locked;
    }

    boolean locked() {
        return locked;
    }

    void tap(long now) {
        if (locked) {
            locked = false;
            return;
        }
        if (armed) {
            long elapsed = now - armedAt;
            if (elapsed >= 0 && elapsed <= DOUBLE_TAP_MS) {
                armed = false;
                locked = true;
                return;
            }
            if (elapsed >= ONE_SHOT_CANCEL_MS) {
                armed = false;
                return;
            }
        }
        armed = true;
        armedAt = now;
    }

    void consume() {
        armed = false;
    }

    void reset() {
        armed = false;
        locked = false;
    }
}
