package com.padboard.keyboard;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Build;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;

public final class ControllerService extends AccessibilityService {
    static ControllerService connected;
    private final KeyGate gate = new KeyGate();
    private boolean capturingAxes;

    @Override
    protected void onServiceConnected() {
        connected = this;
        PadBoardIme ime = PadBoardIme.active;
        setAxes(ime != null && ime.panelOpen());
    }

    void setAxes(boolean shown) {
        if (Build.VERSION.SDK_INT < 34 || capturingAxes == shown) return;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        info.setMotionEventSources(shown ? InputDevice.SOURCE_JOYSTICK : 0);
        setServiceInfo(info);
        capturingAxes = shown;
    }

    void removeDevice(int deviceId) {
        gate.removeDevice(deviceId);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        return gate.route(PadBoardIme.active, event);
    }

    @Override
    public void onMotionEvent(MotionEvent event) {
        PadBoardIme ime = PadBoardIme.active;
        if (ime != null) ime.controllerMotion(event);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (connected == this) connected = null;
        PadBoardIme ime = PadBoardIme.active;
        if (ime != null) ime.clearController();
        super.onDestroy();
    }
}
