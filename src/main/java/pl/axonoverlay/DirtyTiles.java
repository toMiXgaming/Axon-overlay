package pl.axonoverlay;

import java.util.BitSet;

/**
 * Śledzenie kafli, które trzeba przesłać do klienta.
 *
 * <p>Płótno ma {@link OverlayLayout#COLS} × {@link OverlayLayout#ROWS} kafli po
 * {@link OverlayLayout#TILE} pikseli. Kiedy nakładka przerysowuje fragment obrazu,
 * woła {@link #markRect(int, int, int, int)} — klasa wylicza, które kafle są brudne.
 * Następnie {@link #drain(int)} zwraca najwyżej N kafli na tick, pilnując limitu
 * wysyłki (domyślnie 10 kafli na sekundę), a {@link #bytesSent()} liczy realny
 * ruch sieciowy sesji.</p>
 *
 * <p>Klasa jest czysto logiczna (bez Bukkita), więc testy działają w milisekundach.
 * Nie jest bezpieczna wątkowo — sesja woła ją wyłącznie z wątku głównego.</p>
 */
public final class DirtyTiles {

    /** Domyślny limit kafli wysyłanych na sekundę (specyfikacja etapu 5). */
    public static final int DEFAULT_TILES_PER_SECOND = 10;

    /** Ile kafli mieści się w jednym wierszu płótna. */
    private static final int COLS = OverlayLayout.COLS;

    /** Łączna liczba kafli. */
    private static final int TOTAL = OverlayLayout.TILES;

    private final BitSet dirty = new BitSet(TOTAL);
    private final int tilesPerSecond;

    private long windowStartNanos;
    private int sentInWindow;
    private long bytesSent;
    private long tilesSent;

    /** Tworzy tracker z domyślnym limitem {@value #DEFAULT_TILES_PER_SECOND} kafli na sekundę. */
    public DirtyTiles() {
        this(DEFAULT_TILES_PER_SECOND);
    }

    /**
     * @param tilesPerSecond limit kafli wysyłanych w ciągu sekundy; wartości poniżej 1
     *                       są podnoszone do 1, żeby sesja nigdy nie zawisła bez odświeżania
     */
    public DirtyTiles(int tilesPerSecond) {
        this.tilesPerSecond = Math.max(1, tilesPerSecond);
        this.windowStartNanos = System.nanoTime();
    }

    /** Oznacza wszystkie kafle jako brudne — używane przy pierwszym otwarciu ekranu. */
    public void markAll() {
        dirty.set(0, TOTAL);
    }

    /**
     * Oznacza kafle pokrywające prostokąt w pikselach płótna.
     *
     * <p>Prostokąt jest przycinany do obszaru płótna, więc wywołujący nie musi
     * sprawdzać granic. Prostokąt o zerowej szerokości lub wysokości nie robi nic.</p>
     *
     * @param x      lewa krawędź w pikselach
     * @param y      górna krawędź w pikselach
     * @param width  szerokość w pikselach
     * @param height wysokość w pikselach
     */
    public void markRect(int x, int y, int width, int height) {
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
        // Prawa i dolna krawędź są wyłączne: piksel 128 należy już do drugiego kafla.
        int firstCol = left / OverlayLayout.TILE;
        int lastCol = (right - 1) / OverlayLayout.TILE;
        int firstRow = top / OverlayLayout.TILE;
        int lastRow = (bottom - 1) / OverlayLayout.TILE;
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = firstCol; col <= lastCol; col++) {
                dirty.set(row * COLS + col);
            }
        }
    }

    /** Oznacza pojedynczy kafel indeksem 0..{@code TILES-1}. */
    public void markTile(int index) {
        if (index >= 0 && index < TOTAL) {
            dirty.set(index);
        }
    }

    /** Czy jakikolwiek kafel czeka na wysyłkę. */
    public boolean hasDirty() {
        return !dirty.isEmpty();
    }

    /** Liczba kafli czekających na wysyłkę. */
    public int dirtyCount() {
        return dirty.cardinality();
    }

    /**
     * Zwraca kafle do wysłania w tym ticku, respektując limit na sekundę.
     *
     * <p>Limit działa w oknie przesuwnym: gdy od początku okna minęła sekunda,
     * licznik jest zerowany. Dzięki temu krótkie zrywy (np. przewinięcie mapy)
     * nie przekraczają średniej, a pojedynczy tick może wysłać więcej niż
     * {@code tilesPerSecond / 20}, jeśli budżet się nazbierał.</p>
     *
     * @param nowNanos znacznik czasu w nanosekundach (wstrzykiwany w testach)
     * @return tablica indeksów kafli do wysłania; pusta, gdy nie ma nic brudnego
     *         albo budżet na tę sekundę się wyczerpał
     */
    public int[] drain(long nowNanos) {
        long elapsed = nowNanos - windowStartNanos;
        // Ujemne elapsed oznacza, że zegar cofnięto (albo test podał własny, mniejszy
        // znacznik) — wtedy okno i tak trzeba przestawić, inaczej budżet nigdy nie wróci.
        if (elapsed >= 1_000_000_000L || elapsed < 0) {
            windowStartNanos = nowNanos;
            sentInWindow = 0;
        }
        int budget = tilesPerSecond - sentInWindow;
        if (budget <= 0 || dirty.isEmpty()) {
            return new int[0];
        }
        int count = Math.min(budget, dirty.cardinality());
        int[] result = new int[count];
        int filled = 0;
        int index = dirty.nextSetBit(0);
        while (filled < count && index >= 0) {
            result[filled++] = index;
            dirty.clear(index);
            index = dirty.nextSetBit(index + 1);
        }
        sentInWindow += filled;
        tilesSent += filled;
        return result;
    }

    /** Rejestruje realnie wysłane bajty (np. rozmiar pakietu mapy). */
    public void addBytes(long bytes) {
        if (bytes > 0) {
            bytesSent += bytes;
        }
    }

    /** Łączna liczba bajtów wysłanych w tej sesji. */
    public long bytesSent() {
        return bytesSent;
    }

    /** Łączna liczba kafli wysłanych w tej sesji. */
    public long tilesSent() {
        return tilesSent;
    }

    /** Średni ruch w kilobajtach na sekundę od początku sesji. */
    public double kilobytesPerSecond(long nowNanos) {
        long elapsed = nowNanos - windowStartNanos;
        // Okno jest zerowane co sekundę, więc do średniej bierzemy czas od startu sesji.
        long sinceStart = Math.max(1L, elapsed);
        return bytesSent / 1024.0 / (sinceStart / 1_000_000_000.0);
    }

    /** Zeruje licznik bajtów i kafli — używane przy ponownym otwarciu ekranu. */
    public void resetCounters() {
        bytesSent = 0;
        tilesSent = 0;
        sentInWindow = 0;
        windowStartNanos = System.nanoTime();
    }

    /** Limit kafli na sekundę, z którym tracker został utworzony. */
    public int tilesPerSecond() {
        return tilesPerSecond;
    }
}
