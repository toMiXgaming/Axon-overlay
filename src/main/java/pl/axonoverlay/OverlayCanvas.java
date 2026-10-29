package pl.axonoverlay;

/**
 * Bufor pikseli płótna nakładki.
 *
 * <p>Płótno ma {@link OverlayLayout#WIDTH} × {@link OverlayLayout#HEIGHT} pikseli
 * w formacie mapy Minecrafta: jeden bajt na piksel, paleta 256 kolorów. Nakładka
 * rysuje bezpośrednio do {@link #pixels()}, a potem woła {@link #markDirty(int, int, int, int)}
 * albo {@link #markAllDirty()}, żeby biblioteka wiedziała, co wysłać.</p>
 *
 * <p>Rysowanie odbywa się w wątku asynchronicznym (render), ale znaczniki brudnych
 * kafli i wysyłka pakietów zawsze wracają na wątek główny. Klasa nie jest
 * synchronizowana — nakładka odpowiada za to, żeby nie rysować w trakcie wysyłki.</p>
 */
public final class OverlayCanvas {

    private final byte[] pixels = new byte[OverlayLayout.WIDTH * OverlayLayout.HEIGHT];
    private final DirtyTiles dirty;

    OverlayCanvas(DirtyTiles dirty) {
        this.dirty = dirty;
    }

    /** Szerokość płótna w pikselach. */
    public int width() {
        return OverlayLayout.WIDTH;
    }

    /** Wysokość płótna w pikselach. */
    public int height() {
        return OverlayLayout.HEIGHT;
    }

    /** Surowy bufor pikseli — zapis bezpośredni, bez kopii. */
    public byte[] pixels() {
        return pixels;
    }

    /** Czyści całe płótno do koloru {@code color}. */
    public void clear(byte color) {
        java.util.Arrays.fill(pixels, color);
        dirty.markAll();
    }

    /** Ustawia pojedynczy piksel i oznacza jego kafel jako brudny. */
    public void set(int x, int y, byte color) {
        if (x < 0 || y < 0 || x >= OverlayLayout.WIDTH || y >= OverlayLayout.HEIGHT) {
            return;
        }
        pixels[y * OverlayLayout.WIDTH + x] = color;
        dirty.markRect(x, y, 1, 1);
    }

    /** Odczytuje piksel; poza płótnem zwraca 0. */
    public byte get(int x, int y) {
        if (x < 0 || y < 0 || x >= OverlayLayout.WIDTH || y >= OverlayLayout.HEIGHT) {
            return 0;
        }
        return pixels[y * OverlayLayout.WIDTH + x];
    }

    /** Wypełnia prostokąt kolorem i oznacza pokryte kafle jako brudne. */
    public void fillRect(int x, int y, int width, int height, byte color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int left = Math.max(0, x);
        int top = Math.max(0, y);
        int right = Math.min(OverlayLayout.WIDTH, x + width);
        int bottom = Math.min(OverlayLayout.HEIGHT, y + height);
        if (left >= right || top >= bottom) {
            return;
        }
        for (int py = top; py < bottom; py++) {
            int rowStart = py * OverlayLayout.WIDTH;
            java.util.Arrays.fill(pixels, rowStart + left, rowStart + right, color);
        }
        dirty.markRect(left, top, right - left, bottom - top);
    }

    /** Oznacza prostokąt jako brudny bez zmiany pikseli (np. po rysowaniu w {@link #pixels()}). */
    public void markDirty(int x, int y, int width, int height) {
        dirty.markRect(x, y, width, height);
    }

    /** Oznacza całe płótno jako brudne. */
    public void markAllDirty() {
        dirty.markAll();
    }

    /** Liczba kafli czekających na wysyłkę. */
    public int dirtyCount() {
        return dirty.dirtyCount();
    }

    /** Łączna liczba bajtów wysłanych w tej sesji. */
    public long bytesSent() {
        return dirty.bytesSent();
    }

    /** Średni ruch sesji w kilobajtach na sekundę. */
    public double kilobytesPerSecond() {
        return dirty.kilobytesPerSecond(System.nanoTime());
    }
}
