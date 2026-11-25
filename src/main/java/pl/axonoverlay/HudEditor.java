package pl.axonoverlay;

import java.util.Arrays;
import org.bukkit.map.MinecraftFont;

/**
 * Edytor układu HUD (tablica, minimapa, kompas, kafel zdrowia) na pełnym ekranie AxonOverlay.
 *
 * <p>Współrzędne są wspólne z shaderami HUD: ekran odniesienia 640 × 384. Wszystkie dostępne
 * elementy da się edytować w jednym oknie: klik w element go wybiera i łapie, drugi klik
 * odkłada (Minecraft nie wysyła serwerowi puszczenia przycisku). Krawędzie i rogi zmieniają
 * rozmiar, rolka skaluje z zachowaniem proporcji, krawędzie przyklejają się do brzegów i środka
 * ekranu. Kursor rysuje shader (60+ fps), więc serwer rysuje tylko ramki.</p>
 *
 * <p>Każda klatka powstaje we własnym buforze i do płótna trafiają tylko zmienione piksele,
 * więc przy przeciąganiu wysyłane są wyłącznie kafle wokół ramki, a nie cały ekran.</p>
 */
final class HudEditor implements InputListener {

    /** Zapis jednego elementu: {@code bounds == null} przywraca domyślne miejsce. */
    interface Saver {
        void save(int part, int[] bounds);
    }

    private static final int W = HudGeometry.W, H = HudGeometry.H;
    private static final byte FRAME = 87, ACTIVE = 126, HOVER = 90, WHITE = 34, INK = 119, DIM = 116;
    private static final String[] LABELS = {"Tablica", "Minimapa", "Kompas", "Zdrowie"};
    private static final HudGeometry.Limits[] LIMITS = {
            new HudGeometry.Limits(100, 420, 75, 290),
            new HudGeometry.Limits(64, 256, 64, 256),
            new HudGeometry.Limits(96, 380, 21, 21),
            new HudGeometry.Limits(20, 300, 20, 300)
    };
    private static final int BAR_H = 20;
    /** Przyciski paska: {x, szerokość}. */
    private static final int SAVE_X = 404, SAVE_W = 64, RESET_X = 472, RESET_W = 82, CANCEL_X = 558, CANCEL_W = 74;

    private final OverlaySession session;
    private final int[][] boxes;
    private final int[][] start;
    private final double[] ratios;
    private final int[][] defaults;
    private final boolean[] available;
    private final boolean[] reset;
    private final Saver saver;
    private final byte[] base = new byte[W * H];
    private final byte[] frame = new byte[W * H];

    private int selected;
    private int x = W / 2, y = H / 2;
    private int handle, grabX, grabY;
    private int[] original;
    private int hoverBox = -1, hoverHandle;
    private long lastClick;
    private boolean closed;

    HudEditor(OverlaySession session, int selected, int[][] boxes, boolean[] available, int[][] defaults, Saver saver) {
        this.session = session;
        this.available = available.clone();
        this.boxes = new int[boxes.length][];
        this.defaults = new int[boxes.length][];
        this.start = new int[boxes.length][];
        this.ratios = new double[boxes.length];
        for (int i = 0; i < boxes.length; i++) {
            this.start[i] = LIMITS[i].fit(boxes[i]);
            this.defaults[i] = defaults[i] == null ? this.start[i].clone() : LIMITS[i].fit(defaults[i]);
            ratios[i] = (double) this.defaults[i][2] / this.defaults[i][3];
            this.boxes[i] = HudGeometry.scale(start[i], 1, LIMITS[i], ratios[i]);
        }
        this.reset = new boolean[boxes.length];
        this.selected = selected;
        this.saver = saver;
        paintBackground();
        // Cały ekran raz, potem już tylko różnice.
        session.canvas().clear(INK);
        session.onInput(this);
        render();
    }

    // ── wejście ────────────────────────────────────────────────────────────────────────────────

    @Override
    public void onCursor(int px, int py) {
        if (x == px && y == py) return;
        x = px;
        y = py;
        if (handle != 0) {
            boxes[selected] = HudGeometry.drag(original, handle, x - grabX, y - grabY, LIMITS[selected], ratios[selected]);
            reset[selected] = false;
        } else {
            updateHover();
        }
        render();
    }

    @Override
    public void onLeftClick(boolean down, int px, int py) {
        if (!down || System.nanoTime() - lastClick < 150_000_000L) return;
        lastClick = System.nanoTime();
        x = px;
        y = py;
        if (handle != 0) {
            // drugi klik odkłada element tam, gdzie jest
            boxes[selected] = HudGeometry.drag(original, handle, x - grabX, y - grabY, LIMITS[selected], ratios[selected]);
            handle = 0;
            updateHover();
            render();
            return;
        }
        int bar = barY();
        if (y >= bar && y < bar + BAR_H) {
            if (x >= SAVE_X && x < SAVE_X + SAVE_W) {
                commit();
                return;
            }
            if (x >= RESET_X && x < RESET_X + RESET_W) {
                boxes[selected] = defaults[selected].clone();
                reset[selected] = true;
                render();
                return;
            }
            if (x >= CANCEL_X && x < CANCEL_X + CANCEL_W) {
                close();
                return;
            }
        }
        updateHover();
        if (hoverBox >= 0) {
            selected = hoverBox;
            handle = hoverHandle;
            grabX = x;
            grabY = y;
            original = boxes[selected].clone();
        }
        render();
    }

    /** PPM: puszcza element bez zmian (cofa bieżące przeciąganie). */
    @Override
    public void onRightClick(boolean down, int px, int py) {
        if (!down || handle == 0) return;
        boxes[selected] = original;
        handle = 0;
        render();
    }

    @Override
    public void onScroll(int direction) {
        if (direction == 0) return;
        int[] next = HudGeometry.scale(boxes[selected], direction > 0 ? 1.08 : 1 / 1.08, LIMITS[selected], ratios[selected]);
        if (Arrays.equals(next, boxes[selected])) return;
        boxes[selected] = next;
        reset[selected] = false;
        if (handle != 0) {
            original = next.clone();
            grabX = x;
            grabY = y;
        }
        render();
    }

    @Override
    public void onSneak(boolean down) {
        if (down) close();
    }

    @Override
    public void onJump(boolean down) {
        if (down) commit();
    }

    private void close() {
        closed = true;
        session.close();
    }

    private void commit() {
        if (closed) return;
        handle = 0;
        closed = true;
        session.close();
        for (int i = 0; i < boxes.length; i++) {
            if (!available[i]) continue;
            if (reset[i]) {
                saver.save(i, null);
            } else if (!Arrays.equals(boxes[i], start[i])) {
                saver.save(i, boxes[i].clone());
            }
        }
    }

    private void updateHover() {
        hoverBox = -1;
        hoverHandle = 0;
        // wybrany ma pierwszeństwo, potem reszta od góry
        int h = available[selected] ? HudGeometry.handleAt(boxes[selected], x, y, LIMITS[selected]) : 0;
        if (h != 0) {
            hoverBox = selected;
            hoverHandle = h;
            return;
        }
        for (int i = boxes.length - 1; i >= 0; i--) {
            if (i == selected || !available[i]) continue;
            h = HudGeometry.handleAt(boxes[i], x, y, LIMITS[i]);
            if (h != 0) {
                hoverBox = i;
                // klik w inny element zawsze go przesuwa; krawędzie dopiero po wybraniu
                hoverHandle = HudGeometry.MOVE;
                return;
            }
        }
    }

    // ── rysowanie ──────────────────────────────────────────────────────────────────────────────

    private int barY() {
        int[] b = boxes[selected];
        return b[1] + b[3] >= H - BAR_H - 14 ? 4 : H - BAR_H - 4;
    }

    private void render() {
        if (closed) return;
        System.arraycopy(base, 0, frame, 0, frame.length);
        for (int i = 0; i < boxes.length; i++) {
            if (available[i] && i != selected) box(i);
        }
        if (available[selected]) box(selected);
        bar();
        flush();
    }

    /** Kopiuje do płótna tylko zmienione piksele; płótno samo oznacza brudne kafle. */
    private void flush() {
        var canvas = session.canvas();
        byte[] now = canvas.pixels();
        for (int row = 0; row < H; row++) {
            int offset = row * W;
            int first = -1, last = -1;
            for (int col = 0; col < W; col++) {
                if (now[offset + col] != frame[offset + col]) {
                    if (first < 0) first = col;
                    last = col;
                }
            }
            if (first >= 0) {
                System.arraycopy(frame, offset + first, now, offset + first, last - first + 1);
                canvas.markDirty(first, row, last - first + 1, 1);
            }
        }
    }

    private void box(int i) {
        int[] b = boxes[i];
        int l = b[0], t = b[1], w = b[2], h = b[3];
        boolean sel = i == selected;
        boolean hover = i == hoverBox && handle == 0;
        byte color = sel ? ACTIVE : hover ? HOVER : FRAME;
        // lekko przyciemnione wnętrze, żeby było widać, co zajmuje miejsce
        shade(l + 2, t + 2, w - 4, h - 4);
        int thick = sel ? 2 : 1;
        fill(l, t, w, thick, color);
        fill(l, t + h - thick, w, thick, color);
        fill(l, t, thick, h, color);
        fill(l + w - thick, t, thick, h, color);

        String name = LABELS[i];
        int labelW = textWidth(name) + 6;
        int labelY = t >= 11 ? t - 11 : t + h + 1 <= H - 11 ? t + h + 1 : t + 2;
        fill(l, labelY, labelW, 11, sel ? ACTIVE : INK);
        text(l + 3, labelY + 2, name, WHITE);
        if (!sel) return;

        // uchwyty: rogi i środki krawędzi
        boolean fixed = LIMITS[i].fixedHeight();
        int[][] points = fixed
                ? new int[][]{{l, t + h / 2, HudGeometry.LEFT}, {l + w, t + h / 2, HudGeometry.RIGHT}}
                : new int[][]{
                {l, t, HudGeometry.LEFT | HudGeometry.TOP}, {l + w, t, HudGeometry.RIGHT | HudGeometry.TOP},
                {l, t + h, HudGeometry.LEFT | HudGeometry.BOTTOM}, {l + w, t + h, HudGeometry.RIGHT | HudGeometry.BOTTOM},
                {l + w / 2, t, HudGeometry.TOP}, {l + w / 2, t + h, HudGeometry.BOTTOM},
                {l, t + h / 2, HudGeometry.LEFT}, {l + w, t + h / 2, HudGeometry.RIGHT}};
        int active = handle != 0 ? handle : (hoverBox == i ? hoverHandle : 0);
        for (int[] p : points) {
            boolean on = active == p[2];
            int s = on ? 4 : 3;
            fill(p[0] - s, p[1] - s, 2 * s + 1, 2 * s + 1, INK);
            fill(p[0] - s + 1, p[1] - s + 1, 2 * s - 1, 2 * s - 1, on ? ACTIVE : WHITE);
        }

        // rozmiar przy przeciąganiu albo skalowaniu
        String size = w + " x " + h;
        int sw = textWidth(size) + 6;
        int sx = Math.clamp(l + w - sw, 0, W - sw);
        int sy = t + h + 2 <= H - 11 ? t + h + 2 : Math.max(0, t - 24);
        if (labelY == sy) sx = Math.clamp(l + labelW + 4, 0, W - sw);
        fill(sx, sy, sw, 11, INK);
        text(sx + 3, sy + 2, size, WHITE);
    }

    private void bar() {
        int by = barY();
        String hint;
        if (handle == HudGeometry.MOVE) {
            hint = "Przesuwasz: " + LABELS[selected] + "  LPM: odloz  PPM: cofnij";
        } else if (handle != 0) {
            hint = "Zmieniasz rozmiar  LPM: odloz  Rolka: skala";
        } else {
            hint = "LPM: chwyc  Krawedz/rog: rozmiar  Spacja: zapisz  Shift: wyjdz";
        }
        fill(8, by, 390, BAR_H, INK);
        fill(8, by, 3, BAR_H, ACTIVE);
        text(16, by + 6, hint, WHITE);
        button(SAVE_X, SAVE_W, by, "Zapisz", ACTIVE);
        button(RESET_X, RESET_W, by, "Domyslne", reset[selected] ? HOVER : INK);
        button(CANCEL_X, CANCEL_W, by, "Anuluj", INK);
    }

    private void button(int bx, int bw, int by, String label, byte color) {
        boolean hover = handle == 0 && x >= bx && x < bx + bw && y >= by && y < by + BAR_H;
        fill(bx, by, bw, BAR_H, hover ? HOVER : color);
        text(bx + (bw - textWidth(label)) / 2, by + 6, label, WHITE);
    }

    private void paintBackground() {
        Arrays.fill(base, (byte) 119);
        fillBase(0, 263, W, H - 263, (byte) 117);
        fillBase(0, 263, W, 3, (byte) 116);
        // siatka pomocnicza: środek ekranu
        for (int py = 0; py < H; py += 4) base[py * W + W / 2] = DIM;
        for (int px = 0; px < W; px += 4) base[(H / 2) * W + px] = DIM;

        // Autorskie pikselowe sylwetki kurczaka i zombie na tle edytora (dla skali).
        fillBase(66, 236, 7, 27, (byte) 62);
        fillBase(86, 236, 7, 27, (byte) 62);
        fillBase(51, 208, 53, 33, (byte) 21);
        fillBase(77, 180, 33, 34, (byte) 33);
        fillBase(104, 197, 18, 9, (byte) 62);
        fillBase(104, 206, 6, 8, (byte) 17);
        fillBase(96, 189, 5, 5, INK);
        fillBase(59, 216, 28, 13, (byte) 20);

        fillBase(549, 242, 12, 21, (byte) 101);
        fillBase(568, 242, 12, 21, (byte) 101);
        fillBase(544, 211, 42, 34, (byte) 93);
        fillBase(527, 211, 20, 12, (byte) 29);
        fillBase(583, 211, 20, 12, (byte) 29);
        fillBase(548, 173, 34, 38, (byte) 29);
        fillBase(553, 187, 6, 5, INK);
        fillBase(569, 187, 6, 5, INK);
        fillBase(559, 197, 10, 3, INK);
    }

    // ── prymitywy na buforze ───────────────────────────────────────────────────────────────────

    private void fillBase(int fx, int fy, int fw, int fh, byte c) {
        fillInto(base, fx, fy, fw, fh, c);
    }

    private void fill(int fx, int fy, int fw, int fh, byte c) {
        fillInto(frame, fx, fy, fw, fh, c);
    }

    private static void fillInto(byte[] buffer, int fx, int fy, int fw, int fh, byte c) {
        int left = Math.max(0, fx), top = Math.max(0, fy);
        int right = Math.min(W, fx + fw), bottom = Math.min(H, fy + fh);
        for (int row = top; row < bottom; row++) {
            Arrays.fill(buffer, row * W + left, row * W + right, c);
        }
    }

    /** Wnętrze ramki w szachownicę, żeby tło było widać, a obszar się wyróżniał. */
    private void shade(int fx, int fy, int fw, int fh) {
        int left = Math.max(0, fx), top = Math.max(0, fy);
        int right = Math.min(W, fx + fw), bottom = Math.min(H, fy + fh);
        for (int row = top; row < bottom; row++) {
            for (int col = left + ((row + left) & 1); col < right; col += 2) {
                frame[row * W + col] = DIM;
            }
        }
    }

    private static int textWidth(String s) {
        int width = 0;
        for (char ch : s.toCharArray()) {
            var glyph = MinecraftFont.Font.getChar(ch);
            width += glyph == null ? 4 : glyph.getWidth() + 1;
        }
        return Math.max(0, width - 1);
    }

    private void text(int tx, int ty, String s, byte color) {
        for (char ch : s.toCharArray()) {
            var glyph = MinecraftFont.Font.getChar(ch);
            if (glyph == null) {
                tx += 4;
                continue;
            }
            for (int j = 0; j < glyph.getHeight(); j++) {
                for (int i = 0; i < glyph.getWidth(); i++) {
                    int px = tx + i, py = ty + j;
                    if (glyph.get(j, i) && px >= 0 && py >= 0 && px < W && py < H) frame[py * W + px] = color;
                }
            }
            tx += glyph.getWidth() + 1;
        }
    }
}
