package pl.axonoverlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class OverlayGridTest {

    @Test
    void standardGridMatchesClassicLayout() {
        OverlayGrid grid = OverlayGrid.of(1);
        assertEquals(OverlayLayout.COLS, grid.cols());
        assertEquals(OverlayLayout.ROWS, grid.rows());
        assertEquals(OverlayLayout.TILES, grid.tiles());
        assertEquals(OverlayLayout.WIDTH, grid.width());
        assertEquals(OverlayLayout.HEIGHT, grid.height());
    }

    @Test
    void sharpGridHasFourTimesThePixels() {
        OverlayGrid grid = OverlayGrid.of(2);
        assertEquals(10, grid.cols());
        assertEquals(6, grid.rows());
        assertEquals(60, grid.tiles());
        assertEquals(1280, grid.width());
        assertEquals(768, grid.height());
    }

    @Test
    void densityIsClamped() {
        assertSame(OverlayGrid.STANDARD, OverlayGrid.of(0));
        assertSame(OverlayGrid.SHARP, OverlayGrid.of(7));
    }

    @Test
    void specDensityDefaultsToOneAndClamps() {
        assertEquals(1, OverlaySpec.builder("t").build().density());
        assertEquals(2, OverlaySpec.builder("t").density(2).build().density());
        assertEquals(2, OverlaySpec.builder("t").density(9).build().grid().density());
    }

    @Test
    void dirtyTilesOnSharpGridUseTenColumns() {
        DirtyTiles tiles = new DirtyTiles(100, OverlayGrid.SHARP);
        tiles.markRect(OverlayLayout.TILE * 9 + 5, OverlayLayout.TILE * 5 + 5, 1, 1);
        int[] drained = tiles.drain(System.nanoTime());
        assertEquals(1, drained.length);
        assertEquals(59, drained[0]);
        tiles.markAll();
        assertEquals(60, tiles.dirtyCount());
    }

    @Test
    void canvasSizeFollowsGrid() {
        OverlayCanvas canvas = new OverlayCanvas(new DirtyTiles(10, OverlayGrid.SHARP));
        assertEquals(1280, canvas.width());
        assertEquals(768, canvas.height());
        assertEquals(2, canvas.density());
        assertEquals(1280 * 768, canvas.pixels().length);
        canvas.fillRect(1270, 760, 50, 50, (byte) 7);
        assertEquals(7, canvas.get(1279, 767));
    }
}
