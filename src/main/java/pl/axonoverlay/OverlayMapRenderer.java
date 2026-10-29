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
    }
}
