package pl.axonoverlay;

import java.util.Arrays;
import java.util.function.Consumer;
import org.bukkit.map.MinecraftFont;

/** Coordinates shared with the HUD shaders: a reference viewport of 640 by 384. */
final class HudEditor implements InputListener {
    private static final int W = 640, H = 384;
    private static final byte FRAME = 87, ACTIVE = 126, WHITE = 34, INK = 119;
    private static final int SAVE_X = 372, SAVE_W = 60;
    private static final int RESET_X = 448, RESET_W = 174, TOOLBAR_H = 21;
    private static final Limits[] LIMITS = {
            new Limits(100, 420, 75, 290),
            new Limits(64, 256, 64, 256),
            new Limits(96, 380, 21, 21),
            new Limits(20, 300, 20, 300)
    };

    private record Limits(int minWidth, int maxWidth, int minHeight, int maxHeight) {
        int[] fit(int[] bounds) {
            int width = Math.clamp(bounds[2], minWidth, maxWidth);
            int height = Math.clamp(bounds[3], minHeight, maxHeight);
            return new int[]{Math.clamp(bounds[0], 0, W - width),
                    Math.clamp(bounds[1], 0, H - height), width, height};
        }
    }

    private final OverlaySession session;
    private final int[][] boxes;
    private final int selected;
    private final Consumer<int[]> save;
    private final int[] defaults;
    private final Limits limits;
    private boolean reset;
    private int x = 320, y = 192, grabX, grabY, handle;
    private int[] original;
    private long lastClick;
    private byte[] background;
    private static final String[] LABELS = {"Tablica", "Minimapa", "Kompas", "Zdrowie"};

    HudEditor(OverlaySession session, int selected, int[][] boxes, int[] defaults, Consumer<int[]> save) {
        this.session = session;
        this.selected = selected;
        this.boxes = Arrays.stream(boxes).map(int[]::clone).toArray(int[][]::new);
        this.limits = LIMITS[selected];
        this.boxes[selected] = limits.fit(boxes[selected]);
        this.defaults = limits.fit(defaults);
        this.save = save;
        session.onInput(this);
        render();
    }

    @Override public void onCursor(int px, int py) {
        if (x == px && y == py) return;
        int previousX = x, previousY = y;
        x = px; y = py;
        if (handle != 0) { move(); render(); return; }
        var canvas = session.canvas();
        for (int row=Math.max(0,previousY-4);row<=Math.min(H-1,previousY+4);row++) {
            int left=Math.max(0,previousX-4), right=Math.min(W,previousX+5);
            System.arraycopy(background,row*W+left,canvas.pixels(),row*W+left,right-left);
        }
        canvas.markDirty(previousX-4,previousY-4,9,9);
        cursor();
    }

    private void move() {
        int[] b = boxes[selected], a = original;
        int dx = x - grabX, dy = y - grabY;
        int right = a[0] + a[2], bottom = a[1] + a[3];
        switch (handle) {
            case 1 -> {
                b[0] = Math.clamp(a[0] + dx, 0, W - b[2]);
                b[1] = Math.clamp(a[1] + dy, 0, H - b[3]);
            }
            case 2 -> {
                b[0] = Math.clamp(a[0] + dx, Math.max(0, right - limits.maxWidth()), right - limits.minWidth());
                b[2] = right - b[0];
            }
            case 3 -> b[2] = Math.clamp(a[2] + dx, limits.minWidth(), Math.min(limits.maxWidth(), W - a[0]));
            case 4 -> {
                b[1] = Math.clamp(a[1] + dy, Math.max(0, bottom - limits.maxHeight()), bottom - limits.minHeight());
                b[3] = bottom - b[1];
            }
            case 5 -> b[3] = Math.clamp(a[3] + dy, limits.minHeight(), Math.min(limits.maxHeight(), H - a[1]));
            default -> { }
        }
        if (!Arrays.equals(b, a)) reset = false;
    }

    @Override public void onScroll(int direction) {
        if (handle != 0 || direction == 0) return;
        int[] b = boxes[selected];
        double factor = direction > 0 ? 1.08 : 1 / 1.08;
        int w = Math.clamp((int) Math.round(b[2] * factor), limits.minWidth(), limits.maxWidth());
        int h = Math.clamp((int) Math.round(b[3] * factor), limits.minHeight(), limits.maxHeight());
        if (w == b[2] && h == b[3]) return;
        b[0] = Math.clamp(b[0] + (b[2] - w) / 2, 0, W - w);
        b[1] = Math.clamp(b[1] + (b[3] - h) / 2, 0, H - h);
        b[2] = w;
        b[3] = h;
        reset = false;
        render();
    }

    private int toolbarY() {
        int[] b = boxes[selected];
        return b[1] + b[3] >= H - TOOLBAR_H - 10 ? 4 : H - TOOLBAR_H - 4;
    }

    @Override public void onLeftClick(boolean down, int px, int py) {
        if (!down || System.nanoTime()-lastClick < 160_000_000L) return;
        lastClick = System.nanoTime();
        x = px; y = py;
        if (handle != 0) { move(); handle = 0; render(); return; }
        int toolbar = toolbarY();
        if (x >= SAVE_X && x < SAVE_X + SAVE_W && y >= toolbar && y < toolbar + TOOLBAR_H) {
            commit();
            return;
        }
        if (x >= RESET_X && x < RESET_X + RESET_W && y >= toolbar && y < toolbar + TOOLBAR_H) {
            boxes[selected] = defaults.clone();
            reset = true;
            render();
            return;
        }
        int[] b = boxes[selected];
        if (x >= b[0]-4 && x <= b[0]+b[2]+4 && y >= b[1]-4 && y <= b[1]+b[3]+4) {
            grabX = x; grabY = y; original = b.clone();
            if (Math.abs(x-b[0]) <= 6) handle = 2;
            else if (Math.abs(x-b[0]-b[2]) <= 6) handle = 3;
            else if (selected != 2 && Math.abs(y-b[1]) <= 6) handle = 4;
            else if (selected != 2 && Math.abs(y-b[1]-b[3]) <= 6) handle = 5;
            else handle = 1;
        }
        render();
    }

    @Override public void onSneak(boolean down) { if (down) session.close(); }
    @Override public void onJump(boolean down) {
        if (down) commit();
    }

    private void commit() {
        if (handle != 0) move();
        int[] value = reset ? null : boxes[selected].clone();
        session.close();
        save.accept(value);
    }

    private void label(int tx, int ty, String text) {
        var c = session.canvas();
        for (char ch : text.toCharArray()) {
            var glyph = MinecraftFont.Font.getChar(ch);
            if (glyph == null) {
                tx += 4;
                continue;
            }
            for (int j = 0; j < glyph.getHeight(); j++) for (int i = 0; i < glyph.getWidth(); i++)
                if (glyph.get(j,i)) c.set(tx+i,ty+j,WHITE);
            tx += glyph.getWidth()+1;
        }
    }

    private void render() {
        var c = session.canvas();
        paintBackground(c);
        for (int i = 0; i < boxes.length; i++) if (i != selected) frame(i);
        frame(selected);
        if (handle == 0) {
            int toolbar = toolbarY();
            c.fillRect(18, toolbar, 338, TOOLBAR_H, INK);
            label(27, toolbar + 6, "LPM: chwyc / odloz   Rolka: rozmiar   Shift: anuluj");
            c.fillRect(SAVE_X, toolbar, SAVE_W, TOOLBAR_H, INK);
            label(SAVE_X + 9, toolbar + 6, "Zapisz");
            c.fillRect(RESET_X, toolbar, RESET_W, TOOLBAR_H, reset ? ACTIVE : INK);
            label(RESET_X + 9, toolbar + 6, reset ? "Domyslny uklad wybrany" : "Przywroc domyslne ustawienia");
        }
        if (background == null) background = new byte[W*H];
        System.arraycopy(c.pixels(),0,background,0,W*H);
        cursor();
    }

    private static void paintBackground(OverlayCanvas c) {
        c.clear((byte) 119);
        c.fillRect(0, 263, W, H - 263, (byte) 117);
        c.fillRect(0, 263, W, 3, (byte) 116);

        // Autorskie pikselowe sylwetki kurczaka i zombie na tle edytora.
        c.fillRect(66, 236, 7, 27, (byte) 62);
        c.fillRect(86, 236, 7, 27, (byte) 62);
        c.fillRect(51, 208, 53, 33, (byte) 21);
        c.fillRect(77, 180, 33, 34, (byte) 33);
        c.fillRect(104, 197, 18, 9, (byte) 62);
        c.fillRect(104, 206, 6, 8, (byte) 17);
        c.fillRect(96, 189, 5, 5, INK);
        c.fillRect(59, 216, 28, 13, (byte) 20);

        c.fillRect(549, 242, 12, 21, (byte) 101);
        c.fillRect(568, 242, 12, 21, (byte) 101);
        c.fillRect(544, 211, 42, 34, (byte) 93);
        c.fillRect(527, 211, 20, 12, (byte) 29);
        c.fillRect(583, 211, 20, 12, (byte) 29);
        c.fillRect(548, 173, 34, 38, (byte) 29);
        c.fillRect(553, 187, 6, 5, INK);
        c.fillRect(569, 187, 6, 5, INK);
        c.fillRect(559, 197, 10, 3, INK);
    }

    private void cursor() {
        var c = session.canvas();
        c.fillRect(x-4,y,9,1,WHITE); c.fillRect(x,y-4,1,9,WHITE);
    }

    private void frame(int i) {
        int[] b = boxes[i]; int l=b[0], t=b[1], w=b[2], h=b[3];
        byte color = i == selected ? ACTIVE : FRAME;
        var c = session.canvas();
        c.fillRect(l,t,w,2,color); c.fillRect(l,t+h-2,w,2,color);
        c.fillRect(l,t,2,h,color); c.fillRect(l+w-2,t,2,h,color);
        c.fillRect(l+2,t+2,Math.min(w-4,52),11,INK); label(l+4,t+3,LABELS[i]);
        if (i == selected) {
            c.fillRect(l,t+h/2-3,5,7,WHITE); c.fillRect(l+w-5,t+h/2-3,5,7,WHITE);
            if (i != 2) { c.fillRect(l+w/2-3,t,7,5,WHITE); c.fillRect(l+w/2-3,t+h-5,7,5,WHITE); }
        }
    }
}
