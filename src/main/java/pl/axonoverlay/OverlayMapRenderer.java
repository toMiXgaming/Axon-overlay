package pl.axonoverlay;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

/**
 * Kopiuje jeden kafel 128×128 z bufora sesji do płótna mapy.
 *
 * <p>Renderer jest bezstanowy poza numerem kafla — czyta zawsze aktualny bufor
 * sesji, więc {@link Player#sendMap(MapView)} wystarcza do odświeżenia obrazu.
 * Dzięki temu nie budujemy ręcznie pakietów mapy (ich układ zmienia się między
 * wersjami protokołu).</p>
 */
final class OverlayMapRenderer extends MapRenderer {

    /** Kolory znacznika kafla — te same co w AxonMap, bo shader jest wspólny. */
    @SuppressWarnings("deprecation")
    private static final byte MARKER_A = org.bukkit.map.MapPalette.matchColor(255, 255, 255);
    @SuppressWarnings("deprecation")
    private static final byte MARKER_B = org.bukkit.map.MapPalette.matchColor(0, 0, 0);

    private final OverlaySessionImpl session;
    private final int tile;

    OverlayMapRenderer(OverlaySessionImpl session, int tile) {
        super(true);
        this.session = session;
        this.tile = tile;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
        byte[] pixels = session.pixels();
        OverlayGrid grid = session.grid();
        if (pixels == null || pixels.length < grid.width() * grid.height()) {
            return;
        }
        byte[] view = tileView(pixels, tile, grid);
        int size = OverlayLayout.TILE;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                canvas.setPixel(x, y, view[y * size + x]);
            }
        }
    }

    /** Kafel siatki 5×3 tak, jak widzi go klient (zgodność ze starszym kodem i testami). */
    static byte[] tileView(byte[] pixels, int tile) {
        return tileView(pixels, tile, OverlayGrid.STANDARD);
    }

    /**
     * Kafel tak, jak widzi go klient: piksele sesji ze znacznikiem (do wysyłania samych zmian).
     *
     * <p>Znacznik, po którym shader rozpoznaje kafel i rozciąga go na pełny ekran. Siatka 5×3 ma ten
     * sam układ co w AxonMap: pierwszy wiersz to 8 pikseli na przemian biały/czarny i 16 pikseli
     * z numerem kafla (tyle białych, ile wynosi numer), ostatni wiersz znowu 8 pikseli na przemian.
     * Siatka 10×6 (gęstość 2) ma w ostatnim wierszu odwrócony wzór (czarny/biały), w pikselach 8–23
     * pierwszego wiersza resztę z dzielenia numeru przez 16, a w pikselach 24–31 numer / 16.
     * Shader ukrywa te piksele. Bez znacznika ramki są zwykłymi mapami zawieszonymi wokół gracza.</p>
     */
    static byte[] tileView(byte[] pixels, int tile, OverlayGrid grid) {
        int size = OverlayLayout.TILE;
        int width = grid.width();
        byte[] view = new byte[size * size];
        int offsetX = (tile % grid.cols()) * size;
        int offsetY = (tile / grid.cols()) * size;
        for (int y = 0; y < size; y++) {
            System.arraycopy(pixels, (offsetY + y) * width + offsetX, view, y * size, size);
        }
        boolean sharp = grid.density() > 1;
        for (int x = 0; x < 8; x++) {
            boolean even = x % 2 == 0;
            view[x] = even ? MARKER_A : MARKER_B;
            view[(size - 1) * size + x] = even != sharp ? MARKER_A : MARKER_B;
        }
        int low = sharp ? tile % 16 : tile;
        for (int x = 0; x < 16; x++) {
            view[8 + x] = x < low ? MARKER_A : MARKER_B;
        }
        if (sharp) {
            int high = tile / 16;
            for (int x = 0; x < 8; x++) {
                view[24 + x] = x < high ? MARKER_A : MARKER_B;
            }
        }
        return view;
    }
}
