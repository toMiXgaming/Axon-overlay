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
    static int[] drag(int[] a, int handle, int dx, int dy, Limits limits) {
        if (handle == MOVE) {
            int x = snap(a[0] + dx, a[2], W);
            int y = snap(a[1] + dy, a[3], H);
            return new int[]{Math.clamp(x, 0, W - a[2]), Math.clamp(y, 0, H - a[3]), a[2], a[3]};
        }
        int left = a[0], top = a[1], right = a[0] + a[2], bottom = a[1] + a[3];
        if ((handle & LEFT) != 0) {
            left = Math.clamp(snapEdge(left + dx, W), Math.max(0, right - limits.maxWidth()), right - limits.minWidth());
        }
        if ((handle & RIGHT) != 0) {
            right = Math.clamp(snapEdge(right + dx, W), left + limits.minWidth(), Math.min(W, left + limits.maxWidth()));
        }
        if ((handle & TOP) != 0) {
            top = Math.clamp(snapEdge(top + dy, H), Math.max(0, bottom - limits.maxHeight()), bottom - limits.minHeight());
        }
        if ((handle & BOTTOM) != 0) {
            bottom = Math.clamp(snapEdge(bottom + dy, H), top + limits.minHeight(), Math.min(H, top + limits.maxHeight()));
        }
        return new int[]{left, top, right - left, bottom - top};
    }

    /** Skalowanie rolką wokół środka prostokąta. */
    static int[] scale(int[] b, double factor, Limits limits) {
        int w = Math.clamp((int) Math.round(b[2] * factor), limits.minWidth(), limits.maxWidth());
        int h = limits.fixedHeight() ? b[3]
                : Math.clamp((int) Math.round(b[3] * factor), limits.minHeight(), limits.maxHeight());
        // Gdy jeden wymiar oparł się o limit, drugi nie rośnie dalej (zachowujemy proporcje).
        if (!limits.fixedHeight() && b[2] > 0 && b[3] > 0) {
            double sw = (double) w / b[2], sh = (double) h / b[3];
            double s = factor >= 1 ? Math.min(sw, sh) : Math.max(sw, sh);
            w = Math.clamp((int) Math.round(b[2] * s), limits.minWidth(), limits.maxWidth());
            h = Math.clamp((int) Math.round(b[3] * s), limits.minHeight(), limits.maxHeight());
        }
        w = Math.min(w, W);
        h = Math.min(h, H);
        int x = Math.clamp(b[0] + (b[2] - w) / 2, 0, W - w);
        int y = Math.clamp(b[1] + (b[3] - h) / 2, 0, H - h);
        return new int[]{x, y, w, h};
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
