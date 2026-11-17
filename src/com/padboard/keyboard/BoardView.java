package com.padboard.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Region;
import android.graphics.Typeface;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

final class BoardView extends ViewGroup {
    static final int TEXT = 0xffeef1eb;
    static final int SECONDARY = 0xffb0bab5;
    static final int SELECTED = 0xffd9f6b5;
    static final int BACKGROUND = 0xff111416;
    private static final int WIDTH = 780, HEIGHT = 196;
    private static final float PANEL_RADIUS = 12;
    private static final int MIN_SIZE = 60, MAX_SIZE = 115, MIN_OPACITY = 25;

    private final SharedPreferences prefs;
    private final Panel panel;
    private final CornerButton hide, picker;
    private final int buttonSize, buttonInset;
    private final int[] touchLocation = new int[2];
    private final Path touchOutline = new Path();
    private final Region touchBounds = new Region();
    private final Region touchRegion = new Region();
    private float fractionX, fractionY;
    private int size, opacity;

    BoardView(Context context, PadBoardIme ime) {
        super(context);
        prefs = context.getSharedPreferences("padboard", Context.MODE_PRIVATE);
        fractionX = prefs.getFloat("x", .5f);
        fractionY = prefs.getFloat("y", 1f);
        size = prefs.getInt("size", 100);
        opacity = prefs.getInt("opacity", 100);
        float density = getResources().getDisplayMetrics().density;
        buttonSize = Math.round(48 * density);
        buttonInset = Math.round(8 * density);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        panel = new Panel(context, ime.state, ime.controller);
        addView(panel);
        hide = new CornerButton(context, false, ime::hidePanel);
        picker = new CornerButton(context, true, ime::showPicker);
        hide.setVisibility(INVISIBLE);
        picker.setVisibility(INVISIBLE);
        addView(hide);
        addView(picker);
        panel.setOnClickListener(v -> setControlsVisible(true));
    }

    void hideControls() {
        setControlsVisible(false);
    }

    private void setControlsVisible(boolean visible) {
        int visibility = visible ? VISIBLE : INVISIBLE;
        if (hide.getVisibility() == visibility) return;
        hide.setVisibility(visibility);
        picker.setVisibility(visibility);
        requestLayout();
    }

    void addTouchableRegion(Region touchable) {
        addViewRegion(touchable, panel, panel.getWidth() * PANEL_RADIUS / WIDTH,
                panel.getHeight() * PANEL_RADIUS / HEIGHT);
        if (hide.getVisibility() == VISIBLE) {
            addViewRegion(touchable, hide, hide.getWidth() / 2f, hide.getHeight() / 2f);
            addViewRegion(touchable, picker, picker.getWidth() / 2f, picker.getHeight() / 2f);
        }
    }

    private void addViewRegion(Region touchable, View view, float radiusX, float radiusY) {
        view.getLocationInWindow(touchLocation);
        int left = touchLocation[0], top = touchLocation[1];
        int right = left + view.getWidth(), bottom = top + view.getHeight();
        touchOutline.rewind();
        touchOutline.addRoundRect(left, top, right, bottom, radiusX, radiusY, Path.Direction.CW);
        touchBounds.set(left, top, right, bottom);
        touchRegion.setPath(touchOutline, touchBounds);
        touchable.op(touchRegion, Region.Op.UNION);
    }

    void refresh() {
        panel.invalidate();
    }

    private int horizontalRoom() {
        return Math.max(0, getMeasuredWidth() - panel.getMeasuredWidth());
    }

    private int verticalRoom() {
        return Math.max(0, getMeasuredHeight() - panel.getMeasuredHeight());
    }

    void moveByStick(float dx, float dy) {
        float density = getResources().getDisplayMetrics().density;
        int x = horizontalRoom(), y = verticalRoom();
        if (x > 0) fractionX = clamp(fractionX + dx * density / x, 0, 1);
        if (y > 0) fractionY = clamp(fractionY + dy * density / y, 0, 1);
        requestLayout();
    }

    void adjustSize(int delta) {
        size = Math.round(clamp(size + delta, MIN_SIZE, MAX_SIZE));
        requestLayout();
        save();
    }

    void adjustOpacity(int delta) {
        opacity = Math.round(clamp(opacity + delta, MIN_OPACITY, 100));
        refresh();
        save();
    }

    void reset() {
        fractionX = .5f;
        fractionY = 1;
        size = opacity = 100;
        requestLayout();
        refresh();
        save();
    }

    void save() {
        prefs.edit().putFloat("x", fractionX).putFloat("y", fractionY)
                .putInt("size", size).putInt("opacity", opacity).apply();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        float base = Math.min(.86f * width, height * WIDTH / (1.15f * HEIGHT));
        int panelWidth = Math.min(width, Math.round(base * size / 100f));
        int panelHeight = Math.min(height, Math.round(panelWidth * HEIGHT / (float) WIDTH));
        panel.measure(exact(panelWidth), exact(panelHeight));
        int controlSize = exact(Math.min(buttonSize, Math.min(width, height)));
        hide.measure(controlSize, controlSize);
        picker.measure(controlSize, controlSize);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int x = Math.round(horizontalRoom() * fractionX), y = Math.round(verticalRoom() * fractionY);
        panel.layout(x, y, x + panel.getMeasuredWidth(), y + panel.getMeasuredHeight());

        int diameter = hide.getMeasuredWidth();
        int bottom = Math.max(diameter, getHeight() - buttonInset);
        int left = Math.min(buttonInset, getWidth() - diameter);
        int right = Math.max(diameter, getWidth() - buttonInset);
        hide.layout(left, bottom - diameter, left + diameter, bottom);
        picker.layout(right - diameter, bottom - diameter, right, bottom);
    }

    private static int exact(int value) {
        return MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int fade(int color, int percent) {
        return (color & 0x00ffffff) | (Math.round(255 * percent / 100f) << 24);
    }

    private static final class CornerButton extends Button {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path chevron = new Path();
        private final boolean keyboardPicker;

        CornerButton(Context context, boolean keyboardPicker, Runnable action) {
            super(context);
            this.keyboardPicker = keyboardPicker;
            setBackground(null);
            setStateListAnimator(null);
            setPadding(0, 0, 0, 0);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setContentDescription(keyboardPicker ? "Switch Android keyboard" : "Hide PadBoard");
            setOnClickListener(v -> action.run());
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / 48f, getHeight() / 48f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xbf111416);
            canvas.drawCircle(24, 24, 24, paint);
            if (isPressed()) canvas.scale(.88f, .88f, 24, 24);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(2);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            if (keyboardPicker) {
                canvas.drawRoundRect(13, 16, 35, 32, 2.5f, 2.5f, paint);
                for (int x = 17; x <= 31; x += 3) {
                    canvas.drawPoint(x, 21, paint);
                    canvas.drawPoint(x, 24, paint);
                }
                canvas.drawLine(20, 28, 28, 28, paint);
            } else {
                chevron.rewind();
                chevron.moveTo(15, 20);
                chevron.lineTo(24, 29);
                chevron.lineTo(33, 20);
                canvas.drawPath(chevron, paint);
            }
            canvas.restore();
        }
    }

    private final class Panel extends View {
        private static final float CELL_WIDTH = (WIDTH - 20 - 13) / 12f;
        private static final float KEY_WIDTH = CELL_WIDTH - 5.2f;
        private static final float KEY_HEIGHT = 38.8f;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Typeface keyTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        private final BoardState state;
        private final ControllerInput controller;
        private final GestureDetector taps;

        Panel(Context context, BoardState state, ControllerInput controller) {
            super(context);
            this.state = state;
            this.controller = controller;
            setContentDescription("Show keyboard controls");
            setSoundEffectsEnabled(false);
            taps = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent event) {
                    return true;
                }

                @Override public boolean onSingleTapUp(MotionEvent event) {
                    return performClick();
                }
            });
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            taps.onTouchEvent(event);
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / (float) WIDTH, getHeight() / (float) HEIGHT);
            paint.setColor(fade(0xff191e20, opacity));
            canvas.drawRoundRect(0, 0, WIDTH, HEIGHT, PANEL_RADIUS, PANEL_RADIUS, paint);
            paint.setColor(fade(0xff384142, opacity));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);
            canvas.drawRoundRect(.5f, .5f, WIDTH - .5f, HEIGHT - .5f, PANEL_RADIUS, PANEL_RADIUS, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(WIDTH / 2f - .5f, 12, WIDTH / 2f + .5f, HEIGHT - 12, paint);

            boolean shifted = state.shifted(), caret = controller.caretMode();
            for (int i = 0; i < 48; i++) {
                BoardState.Face face = state.face(i);
                String label = face.text(shifted), corner = state.corner(i);
                if (label.isEmpty() && corner.isEmpty()) continue;
                int col = i % 12, row = i / 12;
                float x = 12.6f + col * CELL_WIDTH + (col >= 6 ? 13 : 0);
                float y = 12.6f + row * 44;
                canvas.save();
                canvas.translate(x, y);
                if (i == controller.pressedIndex()) {
                    canvas.scale(.87f, .87f, KEY_WIDTH / 2, KEY_HEIGHT / 2);
                }
                drawKey(canvas, face.code, label, corner, i == state.selected(), caret);
                canvas.restore();
            }
            canvas.restore();
        }

        private void drawKey(Canvas canvas, int code, String label, String corner, boolean highlighted, boolean caret) {
            int alpha = highlighted ? 100 : opacity;
            int ink = highlighted ? 0xff192610 : TEXT;
            boolean latched = state.latched(code);
            if (!label.isEmpty()) {
                int fill = highlighted ? (caret ? 0xff809273 : SELECTED) : latched ? 0xff405334 : 0xff272e30;
                int line = highlighted ? fill : latched ? 0xffa6cd80 : 0xff3c4547;
                paint.setColor(fade(fill, alpha));
                canvas.drawRoundRect(0, 0, KEY_WIDTH, KEY_HEIGHT, 8, 8, paint);
                paint.setColor(fade(line, alpha));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1);
                canvas.drawRoundRect(.5f, .5f, KEY_WIDTH - .5f, KEY_HEIGHT - .5f, 8, 8, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(fade(ink, alpha));
                paint.setTypeface(keyTypeface);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(label.length() > 2 ? 17 : 22);
                float labelWidth = paint.measureText(label);
                if (labelWidth > KEY_WIDTH - 7) paint.setTextSize(paint.getTextSize() * (KEY_WIDTH - 7) / labelWidth);
                float y = KEY_HEIGHT / 2 - (paint.ascent() + paint.descent()) / 2;
                if (!corner.isEmpty()) y += 2;
                canvas.drawText(label, KEY_WIDTH / 2, y, paint);
                if (latched) {
                    canvas.drawCircle(5.5f, KEY_HEIGHT - 5, 2.1f, paint);
                    if (state.locked(code)) canvas.drawCircle(11.5f, KEY_HEIGHT - 5, 2.1f, paint);
                }
            }
            if (!corner.isEmpty()) {
                paint.setColor(fade(highlighted ? ink : SECONDARY, alpha));
                paint.setTypeface(Typeface.DEFAULT);
                paint.setTextSize(corner.length() > 2 ? 9 : 11);
                paint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(corner, KEY_WIDTH - 4, 10.5f, paint);
            }
        }
    }
}
