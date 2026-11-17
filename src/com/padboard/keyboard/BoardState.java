package com.padboard.keyboard;

import java.util.Locale;

import static android.view.KeyEvent.*;

final class BoardState {
    static final int COPY = -1;
    static final int PASTE = -2;
    static final int SELECT_ALL = -3;
    static final int DELETE_WORD = -4;
    private static final int ROWS = 4;
    private static final int COLS = 12;

    static final class Face {
        final String label;
        final String shifted;
        final int code;
        final Face navigation;
        final Face alternate;

        Face(String label, String shifted, int code, Face navigation, Face alternate) {
            this.label = label;
            this.shifted = shifted;
            this.code = code;
            this.navigation = navigation;
            this.alternate = alternate;
        }

        String text(boolean shift) {
            if (!shift || code != 0) return label;
            return shifted != null ? shifted : label.toUpperCase(Locale.ROOT);
        }
    }

    private static Face ch(String label) {
        return ch(label, null);
    }

    private static Face ch(String label, String shift) {
        return new Face(label, shift, 0, null, null);
    }

    static Face key(String label, int code) {
        return new Face(label, null, code, null, null);
    }

    private static Face fn(String label, int n) {
        return new Face(label, null, 0, null, key("F" + n, KEYCODE_F1 + n - 1));
    }

    private static Face num(String label, String navigation, int code) {
        return new Face(label, null, 0, key(navigation, code), null);
    }

    private static final Face CTRL = key("Ctrl", KEYCODE_CTRL_LEFT);
    private static final Face ALT = key("Alt", KEYCODE_ALT_LEFT);
    static final Face SHIFT = key("Shift", KEYCODE_SHIFT_LEFT);
    static final Face SPACE = key("Space", KEYCODE_SPACE);
    static final Face BACKSPACE = key("⌫", KEYCODE_DEL);
    static final Face WORD_BACKSPACE = key("Ctrl+⌫", DELETE_WORD);
    static final Face ENTER = key("Enter", KEYCODE_ENTER);
    static final Face COPY_KEY = key("Copy", COPY);
    static final Face PASTE_KEY = key("Paste", PASTE);
    private static final Face ESC = key("Esc", KEYCODE_ESCAPE);
    private static final Face BLANK = ch("");

    private static final Face[][][] PAGES = {
        {
            {ch("`", "~"), ch("1", "!"), ch("2", "@"), ch("3", "#"), ch("4", "$"), ch("5", "%"),
                ch("6", "^"), ch("7", "&"), ch("8", "*"), ch("9", "("), ch("0", ")"), ch("-", "_")},
            {key("Tab", KEYCODE_TAB), ch("q"), ch("w"), ch("e"), ch("r"), ch("t"),
                ch("y"), ch("u"), ch("i"), ch("o"), ch("p"), ch("=", "+")},
            {ESC, ch("a"), ch("s"), ch("d"), ch("f"), ch("g"),
                ch("h"), ch("j"), ch("k"), ch("l"), ch(";", ":"), ch("'", "\"")},
            {CTRL, ALT, ch("z"), ch("x"), ch("c"), ch("v"),
                ch("b"), ch("n"), ch("m"), ch(",", "<"), ch(".", ">"), ch("/", "?")}
        }, {
            {fn("[", 1), fn("]", 2), fn("{", 3), fn("}", 4), fn("\\", 5), fn("|", 6),
                key("Num Lock", KEYCODE_NUM_LOCK), num("7", "Home", KEYCODE_MOVE_HOME), num("8", "↑", KEYCODE_DPAD_UP),
                num("9", "PgUp", KEYCODE_PAGE_UP), ch("/"), ch("*")},
            {fn("", 7), fn("", 8), fn("", 9), fn("", 10), fn("", 11), fn("", 12),
                BLANK, num("4", "←", KEYCODE_DPAD_LEFT), ch("5"), num("6", "→", KEYCODE_DPAD_RIGHT), ch("+"), ch("-")},
            {ESC, key("Delete", KEYCODE_FORWARD_DEL), COPY_KEY, PASTE_KEY, key("All", SELECT_ALL), BLANK,
                BLANK, num("1", "End", KEYCODE_MOVE_END), num("2", "↓", KEYCODE_DPAD_DOWN), num("3", "PgDn", KEYCODE_PAGE_DOWN),
                BLANK, BLANK},
            {CTRL, ALT, SHIFT, key("Ins", KEYCODE_INSERT), BLANK, BLANK,
                BLANK, BLANK, num("0", "Ins", KEYCODE_INSERT), num(".", "Del", KEYCODE_FORWARD_DEL), ENTER, BLANK}
        }
    };

    final ModifierLatch shift = new ModifierLatch();
    final ModifierLatch ctrl = new ModifierLatch();
    final ModifierLatch alt = new ModifierLatch();
    final ModifierLatch alternate = new ModifierLatch();
    private final int[] row = {2, 0};
    private final int[] col = {3, 2};
    private int page;
    private boolean numLock = true;
    private boolean circleHeld;
    private boolean circleUsed;
    private long circleDownAt;

    boolean symbols() {
        return page == 1;
    }

    int selected() {
        return row[page] * COLS + col[page];
    }

    private Face position(int index) {
        return PAGES[page][index / COLS][index % COLS];
    }

    Face face(int index) {
        Face position = position(index);
        if (showAlternate() && position.alternate != null) return position.alternate;
        if (!digits() && position.navigation != null) return position.navigation;
        return position;
    }

    String corner(int index) {
        Face position = position(index);
        if (position.alternate != null) return showAlternate() ? position.label : position.alternate.label;
        if (position.navigation != null) return digits() ? position.navigation.label : position.label;
        if (position.shifted != null) return shifted() ? position.label : position.shifted;
        return "";
    }

    boolean shifted() {
        return shift.active() || (!symbols() && circleHeld);
    }

    private boolean showAlternate() {
        return symbols() && (alternate.active() || circleHeld);
    }

    private boolean digits() {
        return numLock && !showAlternate();
    }

    boolean latched(int code) {
        if (code == KEYCODE_NUM_LOCK) return numLock;
        if (code == KEYCODE_SHIFT_LEFT) return shifted();
        ModifierLatch latch = latchFor(code);
        return latch != null && latch.active();
    }

    boolean locked(int code) {
        ModifierLatch latch = latchFor(code);
        return latch != null && latch.locked();
    }

    private ModifierLatch latchFor(int code) {
        switch (code) {
            case KEYCODE_SHIFT_LEFT: return shift;
            case KEYCODE_CTRL_LEFT: return ctrl;
            case KEYCODE_ALT_LEFT: return alt;
            default: return null;
        }
    }

    boolean toggleModifier(int code, long now) {
        ModifierLatch latch = latchFor(code);
        if (latch != null) {
            latch.tap(now);
        } else if (code == KEYCODE_NUM_LOCK) {
            numLock = !numLock;
        } else {
            return false;
        }
        normalizeSelection();
        return true;
    }

    void circleDown(long now) {
        circleHeld = true;
        circleUsed = false;
        circleDownAt = now;
    }

    void circleUp(long now, boolean canceled) {
        if (circleHeld && !circleUsed && !canceled && now - circleDownAt < ModifierLatch.DOUBLE_TAP_MS) {
            (symbols() ? alternate : shift).tap(now);
        }
        circleHeld = false;
        normalizeSelection();
    }

    void emitted() {
        circleUsed |= circleHeld;
        shift.consume();
        ctrl.consume();
        alt.consume();
        alternate.consume();
        normalizeSelection();
    }

    void resetModifiers() {
        circleHeld = false;
        circleUsed = false;
        shift.reset();
        ctrl.reset();
        alt.reset();
        alternate.reset();
        normalizeSelection();
    }

    void switchLayout() {
        circleUsed |= circleHeld;
        page = 1 - page;
        normalizeSelection();
    }

    void jump(boolean right) {
        row[page] = symbols() ? (right ? 1 : 0) : 2;
        col[page] = right ? 8 : (symbols() ? 2 : 3);
    }

    void move(int dr, int dc) {
        for (int r = row[page] + dr, c = col[page] + dc;
                r >= 0 && r < ROWS && c >= 0 && c < COLS; r += dr, c += dc) {
            if (!face(r * COLS + c).label.isEmpty()) {
                row[page] = r;
                col[page] = c;
                return;
            }
        }
    }

    private void normalizeSelection() {
        if (!face(selected()).label.isEmpty()) return;
        row[page] = 0;
        if (face(selected()).label.isEmpty()) jump(false);
    }
}
