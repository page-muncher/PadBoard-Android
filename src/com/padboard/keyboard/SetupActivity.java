package com.padboard.keyboard;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public final class SetupActivity extends Activity {
    private TextView status;
    private final static String PREFS = "padboard";

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BoardView.BACKGROUND);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(22));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int width = Math.min(dp(680), getResources().getDisplayMetrics().widthPixels - dp(52));
        root.addView(column, new LinearLayout.LayoutParams(Math.max(dp(220), width), -2));
        column.setFocusableInTouchMode(true);

        TextView title = label("PadBoard", 29, BoardView.TEXT);
        title.setTypeface(null, Typeface.BOLD);
        column.addView(title);
        status = label("", 14, BoardView.SECONDARY);
        add(column, status, 3);

        LinearLayout actions = new LinearLayout(this);
        action(actions, "Enable PadBoard", () -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        action(actions, "Select keyboard", () -> getSystemService(InputMethodManager.class).showInputMethodPicker());
        add(column, actions, 9);
        add(column, testField(), 7);

        TextView controllerTitle = label("Controller", 19, BoardView.TEXT);
        controllerTitle.setTypeface(null, Typeface.BOLD);
        add(column, controllerTitle, 12);
        add(column, label("For controller typing, R3 and stick controls, enable PadBoard Controller in Accessibility. "
                + "R3 opens PadBoard when an app has a focused text field.", 14, BoardView.SECONDARY), 3);
        LinearLayout access = new LinearLayout(this);
        action(access, "Controller settings", () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        add(column, access, 4);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Switch swap = new Switch(this);
        swap.setText("Swap A/B and X/Y");
        swap.setTextColor(BoardView.TEXT);
        swap.setMinHeight(dp(42));
        swap.setChecked(prefs.getBoolean("swap_buttons", false));
        swap.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean("swap_buttons", checked).apply());
        add(column, swap, 3);

        setContentView(scroll);
        column.requestFocus();
    }

    private EditText testField() {
        EditText test = new EditText(this);
        test.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        test.setHint("Tap to focus, then type with the controller");
        test.setSaveEnabled(false);
        test.setTextColor(BoardView.TEXT);
        test.setHintTextColor(BoardView.SECONDARY);
        test.setTextSize(16);
        test.setGravity(Gravity.TOP);
        test.setMinLines(2);
        test.setPadding(dp(13), dp(11), dp(13), dp(11));
        GradientDrawable field = new GradientDrawable();
        field.setColor(0xff191e20);
        field.setCornerRadius(dp(10));
        field.setStroke(dp(1), 0xff384142);
        test.setBackground(field);
        return test;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshSelection();
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) refreshSelection();
    }

    private void refreshSelection() {
        if (status == null) return;
        ComponentName service = new ComponentName(this, PadBoardIme.class);
        boolean enabled = false;
        for (InputMethodInfo info : getSystemService(InputMethodManager.class).getEnabledInputMethodList()) {
            if (service.equals(info.getComponent())) enabled = true;
        }
        String current = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        status.setText(service.equals(ComponentName.unflattenFromString(current == null ? "" : current))
                ? "Selected as your Android keyboard" : enabled ? "Enabled in Android" : "Enable and select PadBoard to get started");
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(view, params);
    }

    private void action(LinearLayout row, String text, Runnable click) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(0xff272e30));
        button.setTextColor(BoardView.SELECTED);
        button.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.rightMargin = dp(6);
        row.addView(button, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
