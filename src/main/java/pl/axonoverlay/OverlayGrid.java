package pl.axonoverlay;

/**
 * Siatka kafli jednej sesji: ile map tworzy ekran i ile pikseli ma płótno.
 *
 * <p>Gęstość 1 to klasyczny ekran 5×3 mapy (640×384 px, tak jak AxonMap). Gęstość 2 to
 * ten sam ekran z 10×6 map (1280×768 px): każdy piksel ekranu dostaje cztery razy więcej
 * pikseli obrazu, więc tekst i krawędzie nie są już schodkowe. Kursor i kliknięcia nadal
 * przychodzą w pikselach logicznych 640×384 ({@link OverlayLayout#WIDTH}), żeby układ
 * przycisków nie zależał od gęstości.</p>
 *
 * <p>Klasa jest czysta (bez Bukkita), więc testy liczą ją bez serwera.</p>
 */
public final class OverlayGrid {

    /** Największa obsługiwana gęstość. Shader rozpoznaje siatkę 10×6 po znaczniku kafla. */
    public static final int MAX_DENSITY = 2;

    public static final OverlayGrid STANDARD = new OverlayGrid(1);
    public static final OverlayGrid SHARP = new OverlayGrid(2);

    private final int density;

    private OverlayGrid(int density) {
        this.density = density;
    }

    /** Siatka dla gęstości 1 albo 2 (inne wartości są przycinane). */
    public static OverlayGrid of(int density) {
        return density >= 2 ? SHARP : STANDARD;
    }

    /** Ile pikseli płótna przypada na piksel logiczny w każdej osi. */
    public int density() {
        return density;
    }

    public int cols() {
        return OverlayLayout.COLS * density;
    }

    public int rows() {
        return OverlayLayout.ROWS * density;
    }

    public int tiles() {
        return cols() * rows();
    }

    /** Szerokość płótna w pikselach. */
    public int width() {
        return cols() * OverlayLayout.TILE;
    }

    /** Wysokość płótna w pikselach. */
    public int height() {
        return rows() * OverlayLayout.TILE;
    }

    @Override
    public String toString() {
        return "OverlayGrid[" + cols() + "x" + rows() + ", " + width() + "x" + height() + "]";
    }
}
