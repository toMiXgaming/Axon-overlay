package pl.axonoverlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testy {@link DirtyTiles}: mapowanie pikseli na kafle, granice kafli i limit wysyłki.
 */
class DirtyTilesTest {

    private static final int TILE = OverlayLayout.TILE;

    @Test
    void singlePixelMarksOneTile() {
        DirtyTiles tiles = new DirtyTiles();
        tiles.markRect(10, 10, 1, 1);
        assertEquals(1, tiles.dirtyCount());
    }

    @Test
    void pixelInSecondTileMarksSecondTile() {
        DirtyTiles tiles = new DirtyTiles();
        tiles.markRect(TILE + 5, 5, 1, 1);
        assertEquals(1, tiles.dirtyCount());
        int[] drained = tiles.drain(System.nanoTime());
        assertEquals(1, drained.length);
        assertEquals(1, drained[0]);
    }

    @Test
    void rectAcrossTileBoundaryMarksTwoTiles() {
        DirtyTiles tiles = new DirtyTiles();
        // Prostokąt 4 px szeroki, zaczyna się 2 px przed granicą kafla.
        tiles.markRect(TILE - 2, 10, 4, 1);
        assertEquals(2, tiles.dirtyCount());
    }

    @Test
    void rectAcrossBothBoundariesMarksFourTiles() {
        DirtyTiles tiles = new DirtyTiles();
        tiles.markRect(TILE - 1, TILE - 1, 2, 2);
        assertEquals(4, tiles.dirtyCount());
    }

    @Test
    void exactTileRectMarksOneTile() {
        DirtyTiles tiles = new DirtyTiles();
        tiles.markRect(0, 0, TILE, TILE);
        assertEquals(1, tiles.dirtyCount());
    }

    @Test
    void rectIsClippedToCanvas() {
        DirtyTiles tiles = new DirtyTiles();
        tiles.markRect(-100, -100, 50, 50);
        assertEquals(0, tiles.dirtyCount());
    }

    @Test
    void zeroSizedRectDoesNothing() {
        DirtyTiles tiles = new DirtyTiles();
        tiles.markRect(10, 10, 0, 5);
        tiles.markRect(10, 10, 5, 0);
        assertFalse(tiles.hasDirty());
    }

    @Test
    void markAllMarksEveryTile() {
        DirtyTiles tiles = new DirtyTiles();
        tiles.markAll();
        assertEquals(OverlayLayout.TILES, tiles.dirtyCount());
    }

    @Test
    void drainRespectsPerSecondLimit() {
        DirtyTiles tiles = new DirtyTiles(3);
        tiles.markAll();
        long now = 1_000_000_000L;
        int[] first = tiles.drain(now);
        assertEquals(3, first.length);
        // Ten sam tick: budżet wyczerpany.
        assertEquals(0, tiles.drain(now).length);
        // Po sekundzie budżet wraca.
        assertEquals(3, tiles.drain(now + 1_000_000_000L).length);
    }

    @Test
    void drainReturnsEmptyWhenNothingDirty() {
        DirtyTiles tiles = new DirtyTiles();
        assertEquals(0, tiles.drain(System.nanoTime()).length);
    }

    @Test
    void drainClearsDrainedTiles() {
        DirtyTiles tiles = new DirtyTiles(10);
        tiles.markAll();
        tiles.drain(System.nanoTime());
        assertEquals(OverlayLayout.TILES - 10, tiles.dirtyCount());
    }

    @Test
    void bytesCounterAccumulates() {
        DirtyTiles tiles = new DirtyTiles();
        tiles.addBytes(1024);
        tiles.addBytes(2048);
        assertEquals(3072, tiles.bytesSent());
    }

    @Test
    void negativeBytesAreIgnored() {
        DirtyTiles tiles = new DirtyTiles();
        tiles.addBytes(-5);
        assertEquals(0, tiles.bytesSent());
    }

    @Test
    void tilesSentCounterTracksDrain() {
        DirtyTiles tiles = new DirtyTiles(5);
        tiles.markAll();
        tiles.drain(System.nanoTime());
        assertEquals(5, tiles.tilesSent());
    }

    @Test
    void resetCountersClearsBytesAndTiles() {
        DirtyTiles tiles = new DirtyTiles(5);
        tiles.markAll();
        tiles.drain(System.nanoTime());
        tiles.addBytes(500);
        tiles.resetCounters();
        assertEquals(0, tiles.bytesSent());
        assertEquals(0, tiles.tilesSent());
    }

    @Test
    void limitBelowOneIsRaisedToOne() {
        DirtyTiles tiles = new DirtyTiles(0);
        assertEquals(1, tiles.tilesPerSecond());
    }

    @Test
    void markTileIgnoresOutOfRangeIndex() {
        DirtyTiles tiles = new DirtyTiles();
        tiles.markTile(-1);
        tiles.markTile(OverlayLayout.TILES);
        assertFalse(tiles.hasDirty());
        tiles.markTile(0);
        assertTrue(tiles.hasDirty());
    }

    @Test
    void lastTileIndexIsValid() {
        DirtyTiles tiles = new DirtyTiles();
        tiles.markTile(OverlayLayout.TILES - 1);
        int[] drained = tiles.drain(System.nanoTime());
        assertEquals(1, drained.length);
        assertEquals(OverlayLayout.TILES - 1, drained[0]);
    }
}
