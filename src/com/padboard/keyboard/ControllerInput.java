package com.padboard.keyboard;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import static android.view.KeyEvent.*;

final class ControllerInput {
    private static final int UP = 1, DOWN = 2, LEFT = 4, RIGHT = 8;
    private static final long REPEAT_DELAY = 310, REPEAT_INTERVAL = 75;
    private static final float TRIGGER_PRESS = .17f, TRIGGER_RELEASE = .10f;

    private final PadBoardIme ime;
    private final BoardState state;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable directionRepeat = this::repeatDirection;
    private final Runnable crossRepeat = this::repeatCross;
    private final Runnable squareRepeat = this::repeatSquare;
    private final Runnable stickTick = this::movePanel;
    private int dpad, hat, direction;
    private boolean r1, l1, circle, cross;
    private boolean l2, r2, l2Axis, r2Axis;
    private boolean adjustmentReady = true;
    private int pressedIndex = -1;
    private BoardState.Face heldFace;
    private float rightX, rightY, velocityX, velocityY;
    private long lastStick;
    private boolean moving;

    ControllerInput(PadBoardIme ime, BoardState state) {
        this.ime = ime;
        this.state = state;
    }

    static boolean isPad(KeyEvent event) {
        if (event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            return true;
        }
        if (KeyEvent.isGamepadButton(event.getKeyCode())) return true;
        InputDevice device = event.getDevice();
        return device != null && (device.supportsSource(InputDevice.SOURCE_GAMEPAD)
                || device.supportsSource(InputDevice.SOURCE_JOYSTICK));
    }

    static boolean uses(int code) {
        return directionBit(code) != 0 || code == KEYCODE_DPAD_CENTER || code == KEYCODE_BUTTON_A
                || code == KEYCODE_BUTTON_B || code == KEYCODE_BUTTON_X || code == KEYCODE_BUTTON_Y
                || code == KEYCODE_BUTTON_L1 || code == KEYCODE_BUTTON_R1 || code == KEYCODE_BUTTON_L2
                || code == KEYCODE_BUTTON_R2 || code == KEYCODE_BUTTON_THUMBL || code == KEYCODE_BUTTON_THUMBR;
    }

    int pressedIndex() {
        return pressedIndex;
    }

    boolean caretMode() {
        return r1;
    }

    void key(KeyEvent event, boolean swap) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == ACTION_DOWN;
        long now = event.getEventTime();
        int bit = directionBit(code);
        if (bit != 0) {
            dpad = down ? dpad | bit : dpad & ~bit;
            updateDirection(down ? bit : 0);
            return;
        }
        if (swap) {
            if (code == KEYCODE_BUTTON_A) code = KEYCODE_BUTTON_B;
            else if (code == KEYCODE_BUTTON_B) code = KEYCODE_BUTTON_A;
            else if (code == KEYCODE_BUTTON_X) code = KEYCODE_BUTTON_Y;
            else if (code == KEYCODE_BUTTON_Y) code = KEYCODE_BUTTON_X;
        }
        switch (code) {
            case KEYCODE_BUTTON_B:
                circle = down;
                if (down) ime.playModifierClick();
                if (down && r1) {
                    ime.hidePanel();
                } else {
                    if (down) state.circleDown(now);
                    else state.circleUp(now, event.isCanceled());
                    ime.refresh();
                }
                break;
            case KEYCODE_BUTTON_R1:
                r1 = down;
                cancelCross();
                if (down && circle) {
                    ime.hidePanel();
                    break;
                }
                if (down) handler.removeCallbacks(squareRepeat);
                ime.refresh();
                break;
            case KEYCODE_DPAD_CENTER:
            case KEYCODE_BUTTON_A:
                cross(down, now);
                break;
            case KEYCODE_BUTTON_X:
                if (down) backspace(now);
                else handler.removeCallbacks(squareRepeat);
                break;
            case KEYCODE_BUTTON_Y:
                if (!down) break;
                if (r1) ime.activate(l1 ? BoardState.PASTE_KEY : BoardState.COPY_KEY, now);
                else ime.activate(BoardState.SPACE, now);
                break;
            case KEYCODE_BUTTON_L1:
                l1 = down;
                if (down && !r1) ime.switchLayout();
                break;
            case KEYCODE_BUTTON_L2:
                if (down && !l2Axis && !l2) jump(false);
                l2 = down;
                break;
            case KEYCODE_BUTTON_R2:
                if (down && !r2Axis && !r2) jump(true);
                r2 = down;
                break;
            case KEYCODE_BUTTON_THUMBL:
                if (down) ime.resetPanel();
                break;
            default:
                break;
        }
    }

    void cancelCross() {
        handler.removeCallbacks(crossRepeat);
        cross = false;
        heldFace = null;
        pressedIndex = -1;
    }

    private void cross(boolean down, long now) {
        if (!down) {
            cancelCross();
            ime.refresh();
            return;
        }
        if (r1) {
            ime.activate(BoardState.ENTER, now);
            return;
        }
        cross = true;
        pressedIndex = state.selected();
        heldFace = state.face(pressedIndex);
        ime.activate(heldFace, now);
        if (repeatable(heldFace)) handler.postDelayed(crossRepeat, REPEAT_DELAY);
    }

    private static boolean repeatable(BoardState.Face face) {
        switch (face.code) {
            case BoardState.COPY:
            case BoardState.PASTE:
            case BoardState.SELECT_ALL:
            case KEYCODE_SHIFT_LEFT:
            case KEYCODE_CTRL_LEFT:
            case KEYCODE_ALT_LEFT:
            case KEYCODE_NUM_LOCK:
            case KEYCODE_ESCAPE:
            case KEYCODE_INSERT:
                return false;
            default:
                return !face.label.isEmpty();
        }
    }

    private void repeatCross() {
        if (!cross || pressedIndex != state.selected() || heldFace != state.face(pressedIndex)) return;
        ime.activate(heldFace, SystemClock.uptimeMillis());
        handler.postDelayed(crossRepeat, REPEAT_INTERVAL);
    }

    private void backspace(long now) {
        ime.activate(r1 ? BoardState.WORD_BACKSPACE : BoardState.BACKSPACE, now);
        if (!r1) handler.postDelayed(squareRepeat, REPEAT_DELAY);
    }

    private void repeatSquare() {
        ime.activate(BoardState.BACKSPACE, SystemClock.uptimeMillis());
        handler.postDelayed(squareRepeat, REPEAT_INTERVAL);
    }

    private static int directionBit(int code) {
        switch (code) {
            case KEYCODE_DPAD_UP: return UP;
            case KEYCODE_DPAD_DOWN: return DOWN;
            case KEYCODE_DPAD_LEFT: return LEFT;
            case KEYCODE_DPAD_RIGHT: return RIGHT;
            default: return 0;
        }
    }

    private void updateDirection(int fresh) {
        int directions = dpad | hat;
        int next = (fresh & directions) != 0 ? Integer.lowestOneBit(fresh & directions)
                : (direction & directions) != 0 ? direction : Integer.lowestOneBit(directions);
        if (next == direction) return;
        direction = next;
        handler.removeCallbacks(directionRepeat);
        if (direction == 0) return;
        moveDirection();
        handler.postDelayed(directionRepeat, REPEAT_DELAY);
    }

    private void repeatDirection() {
        if (direction == 0) return;
        moveDirection();
        handler.postDelayed(directionRepeat, REPEAT_INTERVAL);
    }

    private void moveDirection() {
        int dr = direction == UP ? -1 : direction == DOWN ? 1 : 0;
        int dc = direction == LEFT ? -1 : direction == RIGHT ? 1 : 0;
        if (r1) {
            int code = direction == UP ? KEYCODE_DPAD_UP : direction == DOWN ? KEYCODE_DPAD_DOWN
                    : direction == LEFT ? KEYCODE_DPAD_LEFT : KEYCODE_DPAD_RIGHT;
            ime.emit(BoardState.key("", code), l1);
        } else {
            int previous = state.selected();
            state.move(dr, dc);
            if (state.selected() != previous) cancelCross();
            ime.refresh();
        }
    }

    private void jump(boolean right) {
        state.jump(right);
        cancelCross();
        ime.refresh();
    }

    void motion(MotionEvent event) {
        int oldHat = hat;
        float hx = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        hat = (hx < -.5f ? LEFT : hx > .5f ? RIGHT : 0) | (hy < -.5f ? UP : hy > .5f ? DOWN : 0);
        if (hat != oldHat) updateDirection(hat & ~oldHat);

        float leftTrigger = Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE));
        float rightTrigger = Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_GAS));
        boolean leftPressed = l2Axis ? leftTrigger > TRIGGER_RELEASE : leftTrigger >= TRIGGER_PRESS;
        boolean rightPressed = r2Axis ? rightTrigger > TRIGGER_RELEASE : rightTrigger >= TRIGGER_PRESS;
        if (leftPressed && !l2Axis && !l2) jump(false);
        if (rightPressed && !r2Axis && !r2) jump(true);
        l2Axis = leftPressed;
        r2Axis = rightPressed;

        float lx = event.getAxisValue(MotionEvent.AXIS_X);
        float ly = event.getAxisValue(MotionEvent.AXIS_Y);
        float tilt = Math.max(Math.abs(lx), Math.abs(ly));
        if (tilt <= .34f) adjustmentReady = true;
        if (adjustmentReady && tilt >= .60f) {
            adjustmentReady = false;
            if (Math.abs(lx) > Math.abs(ly)) ime.adjustOpacity(lx > 0 ? 5 : -5);
            else ime.adjustSize(ly < 0 ? 5 : -5);
        }

        InputDevice device = event.getDevice();
        boolean usesRx = device != null && device.getMotionRange(MotionEvent.AXIS_RX, event.getSource()) != null
                && device.getMotionRange(MotionEvent.AXIS_RY, event.getSource()) != null;
        rightX = event.getAxisValue(usesRx ? MotionEvent.AXIS_RX : MotionEvent.AXIS_Z);
        rightY = event.getAxisValue(usesRx ? MotionEvent.AXIS_RY : MotionEvent.AXIS_RZ);
        if (!moving && Math.hypot(rightX, rightY) > .25) {
            moving = true;
            lastStick = SystemClock.uptimeMillis();
            handler.postDelayed(stickTick, 16);
        }
    }

    private void movePanel() {
        float length = (float) Math.hypot(rightX, rightY);
        if (length <= .25f || !ime.panelOpen()) {
            velocityX = velocityY = 0;
            moving = false;
            ime.savePanel();
            return;
        }
        long now = SystemClock.uptimeMillis();
        long elapsed = Math.min(50, now - lastStick);
        float tilt = (Math.min(length, 1) - .25f) / .75f;
        float speed = 920 * tilt * (.28f + .72f * tilt);
        float blend = (float) (1 - Math.exp(-elapsed / 20.0));
        velocityX += (rightX / length * speed - velocityX) * blend;
        velocityY += (rightY / length * speed - velocityY) * blend;
        ime.movePanel(velocityX * elapsed / 1000, velocityY * elapsed / 1000);
        lastStick = now;
        handler.postDelayed(stickTick, 16);
    }

    void clear() {
        handler.removeCallbacksAndMessages(null);
        dpad = hat = direction = 0;
        r1 = l1 = circle = cross = false;
        l2 = r2 = l2Axis = r2Axis = false;
        pressedIndex = -1;
        heldFace = null;
        adjustmentReady = true;
        rightX = rightY = velocityX = velocityY = 0;
        moving = false;
        state.circleUp(SystemClock.uptimeMillis(), true);
    }
}
