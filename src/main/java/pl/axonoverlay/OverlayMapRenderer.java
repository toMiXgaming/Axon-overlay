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
        if (pixels == null || pixels.length < OverlayLayout.WIDTH * OverlayLayout.HEIGHT) {
            return;
        }
        int offsetX = (tile % OverlayLayout.COLS) * OverlayLayout.TILE;
        int offsetY = (tile / OverlayLayout.COLS) * OverlayLayout.TILE;
        for (int y = 0; y < OverlayLayout.TILE; y++) {
            int row = (offsetY + y) * OverlayLayout.WIDTH + offsetX;
            for (int x = 0; x < OverlayLayout.TILE; x++) {
                canvas.setPixel(x, y, pixels[row + x]);
            }
        }
        writeMarker(canvas, tile);
    }

    /**
     * Znacznik, po którym shader rozpoznaje kafel i rozciąga go na pełny ekran.
     *
     * <p>Ten sam układ co w AxonMap: pierwszy wiersz to 8 pikseli na przemian
     * biały/czarny i 16 pikseli z numerem kafla (tyle białych, ile wynosi numer),
     * ostatni wiersz znowu 8 pikseli na przemian. Shader ukrywa te piksele.
     * Bez znacznika ramki są zwykłymi mapami zawieszonymi wokół gracza.</p>
     */
    static void writeMarker(MapCanvas canvas, int tile) {
        for (int x = 0; x < 8; x++) {
            canvas.setPixel(x, 0, x % 2 == 0 ? MARKER_A : MARKER_B);
            canvas.setPixel(x, OverlayLayout.TILE - 1, x % 2 == 0 ? MARKER_A : MARKER_B);
        }
        for (int x = 0; x < 16; x++) {
            canvas.setPixel(8 + x, 0, x < tile ? MARKER_A : MARKER_B);
        }
    }
}
