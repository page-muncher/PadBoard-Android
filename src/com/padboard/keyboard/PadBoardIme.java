package com.padboard.keyboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.input.InputManager;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.SystemClock;
import android.text.InputType;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import static android.view.KeyEvent.*;

public final class PadBoardIme extends InputMethodService implements InputManager.InputDeviceListener {
    static PadBoardIme active;
    final BoardState state = new BoardState();
    final ControllerInput controller = new ControllerInput(this, state);
    private final KeyGate gate = new KeyGate();
    private final EditorOutput output = new EditorOutput(this, state);
    private BoardView board;
    private boolean shown;
    private boolean swapButtons;
    private AudioManager audio;
    private InputManager devices;

    @Override
    public void onCreate() {
        setTheme(R.style.PadBoardImeTheme);
        super.onCreate();
        audio = getSystemService(AudioManager.class);
        devices = getSystemService(InputManager.class);
        if (devices != null) devices.registerInputDeviceListener(this, null);
        active = this;
    }

    @Override
    public View onCreateInputView() {
        board = new BoardView(this, this);
        return board;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public boolean onEvaluateInputViewShown() {
        return true;
    }

    @Override
    public void onConfigureWindow(Window window, boolean fullscreen, boolean candidatesOnly) {
        WindowManager.LayoutParams layout = window.getAttributes();
        layout.width = WindowManager.LayoutParams.MATCH_PARENT;
        layout.height = WindowManager.LayoutParams.MATCH_PARENT;
        if (Build.VERSION.SDK_INT >= 30) {
            // Android normally lets an IME draw behind the bottom system navigation bar.
            layout.setFitInsetsSides(WindowInsets.Side.all());
        }
        window.setAttributes(layout);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    @Override
    public void onComputeInsets(Insets insets) {
        int height = getWindow().getWindow().getDecorView().getHeight();
        insets.contentTopInsets = height;
        insets.visibleTopInsets = height;
        insets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION;
        insets.touchableRegion.setEmpty();
        if (board != null && board.isShown()) {
            board.addTouchableRegion(insets.touchableRegion);
        }
    }

    @Override
    public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        if (!restarting) {
            controller.clear();
            state.resetModifiers();
        }
        swapButtons = getSharedPreferences("padboard", Context.MODE_PRIVATE).getBoolean("swap_buttons", false);
        refresh();
    }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        setPanelOpen(isInputViewShown());
    }

    @Override
    public void onWindowHidden() {
        setPanelOpen(false);
        super.onWindowHidden();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        setPanelOpen(false);
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (Build.VERSION.SDK_INT >= 35) {
            // The IME caption bar supplies a second Hide and Switch Keyboard pair.
            WindowInsetsController insets = getWindow().getWindow().getInsetsController();
            if (insets != null) insets.hide(WindowInsets.Type.captionBar());
        }
        setPanelOpen(true);
    }

    private void setPanelOpen(boolean open) {
        if (shown == open) return;
        shown = open;
        if (!open) {
            savePanel();
            if (board != null) board.hideControls();
            controller.clear();
            state.resetModifiers();
            refresh();
        }
        ControllerService bridge = ControllerService.connected;
        if (bridge != null) bridge.setAxes(open);
    }

    boolean panelOpen() {
        return shown && getCurrentInputStarted();
    }

    boolean wantsControllerKey(KeyEvent event) {
        if (!ControllerInput.isPad(event)) return false;
        int code = event.getKeyCode();
        if (panelOpen()) return ControllerInput.uses(code);
        EditorInfo editor = getCurrentInputEditorInfo();
        return code == KEYCODE_BUTTON_THUMBR && getCurrentInputStarted() && editor != null
                && editor.inputType != InputType.TYPE_NULL && getCurrentInputConnection() != null;
    }

    void controllerKey(KeyEvent event) {
        if (event.getKeyCode() == KEYCODE_BUTTON_THUMBR) {
            if (event.getAction() == ACTION_DOWN) {
                if (panelOpen()) hidePanel();
                else requestShowSelf(0);
            }
        } else if (panelOpen()) {
            controller.key(event, swapButtons);
        }
    }

    boolean controllerMotion(MotionEvent event) {
        if (!panelOpen() || !event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                || event.getActionMasked() != MotionEvent.ACTION_MOVE) return false;
        controller.motion(event);
        return true;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent event) {
        return gate.route(this, event) || super.onKeyDown(code, event);
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent event) {
        return gate.route(this, event) || super.onKeyUp(code, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return controllerMotion(event) || super.onGenericMotionEvent(event);
    }

    void clearController() {
        controller.clear();
        refresh();
    }

    @Override
    public void onInputDeviceRemoved(int id) {
        gate.removeDevice(id);
        ControllerService bridge = ControllerService.connected;
        if (bridge != null) bridge.removeDevice(id);
        clearController();
    }

    @Override public void onInputDeviceAdded(int id) {}
    @Override public void onInputDeviceChanged(int id) {}

    void activate(BoardState.Face face, long now) {
        if (!panelOpen() || face.label.isEmpty()) return;
        if (state.toggleModifier(face.code, now)) {
            playModifierClick();
            refresh();
        } else {
            emit(face, false);
        }
    }

    void emit(BoardState.Face face, boolean selecting) {
        if (!panelOpen()) return;
        if (output.send(face, selecting)) {
            state.emitted();
            boolean backspace = face.code == KEYCODE_DEL || face.code == BoardState.DELETE_WORD;
            click(backspace ? AudioManager.FX_KEYPRESS_DELETE : AudioManager.FX_KEYPRESS_STANDARD);
        }
        refresh();
    }

    private void click(int effect) {
        if (audio != null) audio.playSoundEffect(effect);
    }

    void playModifierClick() {
        click(AudioManager.FX_KEYPRESS_RETURN);
    }

    void refresh() {
        if (board != null) board.refresh();
    }

    void switchLayout() {
        controller.cancelCross();
        state.switchLayout();
        click(AudioManager.FX_KEYPRESS_RETURN);
        refresh();
    }

    void hidePanel() {
        setPanelOpen(false);
        requestHideSelf(0);
    }

    void showPicker() {
        getSystemService(InputMethodManager.class).showInputMethodPicker();
    }

    void movePanel(float dx, float dy) {
        if (board != null) board.moveByStick(dx, dy);
    }

    void adjustSize(int delta) {
        if (board != null) board.adjustSize(delta);
    }

    void adjustOpacity(int delta) {
        if (board != null) board.adjustOpacity(delta);
    }

    void resetPanel() {
        if (board != null) board.reset();
    }

    void savePanel() {
        if (board != null) board.save();
    }

    @Override
    public void onDestroy() {
        setPanelOpen(false);
        if (active == this) active = null;
        if (devices != null) devices.unregisterInputDeviceListener(this);
        super.onDestroy();
    }
}
