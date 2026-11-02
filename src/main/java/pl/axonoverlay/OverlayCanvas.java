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

    private final OverlayGrid grid;
    private final int width;
    private final int height;
    private final byte[] pixels;
    private final DirtyTiles dirty;

    OverlayCanvas(DirtyTiles dirty) {
        this.dirty = dirty;
        this.grid = dirty.grid();
        this.width = grid.width();
        this.height = grid.height();
        this.pixels = new byte[width * height];
    }

    /** Szerokość płótna w pikselach (640 albo 1280 przy gęstości 2). */
    public int width() {
        return width;
    }

    /** Wysokość płótna w pikselach (384 albo 768 przy gęstości 2). */
    public int height() {
        return height;
    }

    /**
     * Pikseli płótna na piksel logiczny (1 albo 2). Kursor i kliknięcia są w pikselach logicznych
     * 640×384, więc nakładka rysująca w gęstości 2 mnoży współrzędne przez tę wartość.
     */
    public int density() {
        return grid.density();
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
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        pixels[y * width + x] = color;
        dirty.markRect(x, y, 1, 1);
    }

    /** Odczytuje piksel; poza płótnem zwraca 0. */
    public byte get(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }
        return pixels[y * width + x];
    }

    /** Wypełnia prostokąt kolorem i oznacza pokryte kafle jako brudne. */
    public void fillRect(int x, int y, int width, int height, byte color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int left = Math.max(0, x);
        int top = Math.max(0, y);
        int right = Math.min(this.width, x + width);
        int bottom = Math.min(this.height, y + height);
        if (left >= right || top >= bottom) {
            return;
        }
        for (int py = top; py < bottom; py++) {
            int rowStart = py * this.width;
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
