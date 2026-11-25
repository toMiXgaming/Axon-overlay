package pl.axonoverlay;

/**
 * Czysta matematyka edytora HUD (bez Bukkita): który uchwyt jest pod kursorem, przesuwanie
 * i zmiana rozmiaru z przyciąganiem do krawędzi ekranu i środka.
 *
 * <p>Prostokąt to {x, y, szerokość, wysokość} w pikselach ekranu odniesienia 640 × 384.
 * Uchwyt to zestaw bitów krawędzi: {@link #LEFT}, {@link #RIGHT}, {@link #TOP}, {@link #BOTTOM};
 * {@link #MOVE} przesuwa cały prostokąt.</p>
 */
final class HudGeometry {

    static final int W = 640, H = 384;
    static final int LEFT = 1, RIGHT = 2, TOP = 4, BOTTOM = 8, MOVE = 16;

    /** Z jakiej odległości (px) łapie się krawędź. */
    static final int GRAB = 7;
    /** Z jakiej odległości (px) prostokąt przykleja się do krawędzi ekranu albo środka. */
    static final int SNAP = 6;

    record Limits(int minWidth, int maxWidth, int minHeight, int maxHeight) {
        boolean fixedHeight() {
            return minHeight == maxHeight;
        }

        int[] fit(int[] b) {
            int width = Math.clamp(b[2], minWidth, maxWidth);
            int height = Math.clamp(b[3], minHeight, maxHeight);
            return new int[]{Math.clamp(b[0], 0, W - width), Math.clamp(b[1], 0, H - height), width, height};
        }
    }

    private HudGeometry() {
    }

    /** Uchwyt pod kursorem albo 0, gdy kursor jest poza prostokątem (z marginesem {@link #GRAB}). */
    static int handleAt(int[] b, int x, int y, Limits limits) {
        if (x < b[0] - GRAB || x > b[0] + b[2] + GRAB || y < b[1] - GRAB || y > b[1] + b[3] + GRAB) {
            return 0;
        }
        // Przy małym prostokącie strefy krawędzi nie mogą zjeść całego środka.
        int grabX = Math.min(GRAB, Math.max(2, b[2] / 4));
        int grabY = Math.min(GRAB, Math.max(2, b[3] / 4));
        int handle = 0;
        if (Math.abs(x - b[0]) <= grabX) handle |= LEFT;
        else if (Math.abs(x - (b[0] + b[2])) <= grabX) handle |= RIGHT;
        if (!limits.fixedHeight()) {
            if (Math.abs(y - b[1]) <= grabY) handle |= TOP;
            else if (Math.abs(y - (b[1] + b[3])) <= grabY) handle |= BOTTOM;
        }
        if (handle == 0) {
            boolean inside = x >= b[0] && x <= b[0] + b[2] && y >= b[1] && y <= b[1] + b[3];
            return inside ? MOVE : 0;
        }
        return handle;
    }

    /**
     * Nowy prostokąt po przeciągnięciu uchwytu o (dx, dy) od stanu {@code a}.
     * Wynik zawsze mieści się w ekranie i w limitach.
     */
    static int[] drag(int[] a, int handle, int dx, int dy, Limits limits, double ratio) {
        if (handle == MOVE) {
            int x = snap(a[0] + dx, a[2], W);
            int y = snap(a[1] + dy, a[3], H);
            return new int[]{Math.clamp(x, 0, W - a[2]), Math.clamp(y, 0, H - a[3]), a[2], a[3]};
        }
        boolean left = (handle & LEFT) != 0, right = (handle & RIGHT) != 0;
        boolean top = (handle & TOP) != 0, bottom = (handle & BOTTOM) != 0;
        int xAnchor = left ? a[0] + a[2] : a[0];
        int yAnchor = top ? a[1] + a[3] : a[1];
        int w = a[2], h = a[3];
        if (dx != 0 && left) w = xAnchor - snapEdge(a[0] + dx, W);
        if (dx != 0 && right) w = snapEdge(a[0] + a[2] + dx, W) - xAnchor;
        if (dy != 0 && top) h = yAnchor - snapEdge(a[1] + dy, H);
        if (dy != 0 && bottom) h = snapEdge(a[1] + a[3] + dy, H) - yAnchor;
        int roomW = left ? xAnchor : right ? W - xAnchor : W;
        int roomH = top ? yAnchor : bottom ? H - yAnchor : H;
        double desired = w;
        if (top || bottom) {
            // Rzut ruchu rogu na przekątną. Górna i dolna krawędź sterują wysokością.
            if (left || right) desired += ((h - a[3]) - (w - a[2]) / ratio) / (ratio + 1 / ratio);
            else desired = a[2] + (h - a[3]) * ratio;
        }
        int[] size = size(desired, ratio, limits, roomW, roomH);
        int x = left ? xAnchor - size[0] : right ? xAnchor : a[0] + (a[2] - size[0]) / 2;
        int y = top ? yAnchor - size[1] : bottom ? yAnchor : a[1] + (a[3] - size[1]) / 2;
        return new int[]{Math.clamp(x, 0, W - size[0]), Math.clamp(y, 0, H - size[1]), size[0], size[1]};
    }

    /** Jedna skala okna, ograniczona jednocześnie przez obie osie. */
    private static int[] size(double targetWidth, double ratio, Limits limits, int roomW, int roomH) {
        int maxW = Math.min(roomW, limits.maxWidth());
        if (limits.fixedHeight()) {
            return new int[]{Math.clamp(Math.round(targetWidth), limits.minWidth(), maxW), limits.minHeight()};
        }
        int maxH = Math.min(roomH, limits.maxHeight());
        int min = Math.max(limits.minWidth(), (int) Math.ceil((limits.minHeight() - 0.5) * ratio));
        int max = Math.min(maxW, (int) Math.ceil((maxH + 0.5) * ratio) - 1);
        int width = Math.clamp(Math.round(targetWidth), min, max);
        return new int[]{width, (int) Math.round(width / ratio)};
    }

    /** Skalowanie rolką wokół środka prostokąta. */
    static int[] scale(int[] b, double factor, Limits limits, double ratio) {
        int[] size = size(b[2] * factor, ratio, limits, W, H);
        int x = Math.clamp(b[0] + (b[2] - size[0]) / 2, 0, W - size[0]);
        int y = Math.clamp(b[1] + (b[3] - size[1]) / 2, 0, H - size[1]);
        return new int[]{x, y, size[0], size[1]};
    }

    /** Pozycja początku odcinka o długości {@code size}, przyciągnięta do 0, środka i końca ekranu. */
    static int snap(int start, int size, int screen) {
        if (Math.abs(start) <= SNAP) return 0;
        if (Math.abs(start + size - screen) <= SNAP) return screen - size;
        int center = screen / 2 - size / 2;
        if (Math.abs(start - center) <= SNAP) return center;
        return start;
    }

    /** Pojedyncza krawędź przyciągnięta do 0, środka i końca ekranu. */
    static int snapEdge(int edge, int screen) {
        if (Math.abs(edge) <= SNAP) return 0;
        if (Math.abs(edge - screen) <= SNAP) return screen;
        if (Math.abs(edge - screen / 2) <= SNAP) return screen / 2;
        return edge;
    }
}
