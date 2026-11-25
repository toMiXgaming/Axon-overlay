package pl.axonoverlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HudGeometryTest {

    private static final HudGeometry.Limits BOARD = new HudGeometry.Limits(100, 420, 75, 290);
    private static final HudGeometry.Limits COMPASS = new HudGeometry.Limits(96, 380, 21, 21);

    @Test
    void handleInsideMovesAndEdgesResize() {
        int[] b = {100, 100, 200, 100};
        assertEquals(HudGeometry.MOVE, HudGeometry.handleAt(b, 200, 150, BOARD));
        assertEquals(HudGeometry.LEFT, HudGeometry.handleAt(b, 101, 150, BOARD));
        assertEquals(HudGeometry.RIGHT | HudGeometry.BOTTOM, HudGeometry.handleAt(b, 300, 200, BOARD));
        assertEquals(0, HudGeometry.handleAt(b, 20, 20, BOARD));
    }

    @Test
    void fixedHeightHasNoTopBottomHandles() {
        int[] b = {235, 5, 170, 21};
        assertEquals(HudGeometry.MOVE, HudGeometry.handleAt(b, 300, 5, COMPASS));
    }

    @Test
    void moveSnapsToScreenEdgesAndCenter() {
        int[] a = {100, 100, 200, 100};
        assertArrayEquals(new int[]{0, 100, 200, 100}, HudGeometry.drag(a, HudGeometry.MOVE, -96, 0, BOARD, 2));
        assertArrayEquals(new int[]{440, 100, 200, 100}, HudGeometry.drag(a, HudGeometry.MOVE, 336, 0, BOARD, 2));
        assertArrayEquals(new int[]{220, 100, 200, 100}, HudGeometry.drag(a, HudGeometry.MOVE, 118, 0, BOARD, 2));
    }

    @Test
    void moveNeverLeavesScreen() {
        int[] a = {100, 100, 200, 100};
        assertArrayEquals(new int[]{440, 284, 200, 100}, HudGeometry.drag(a, HudGeometry.MOVE, 900, 900, BOARD, 2));
    }

    @Test
    void resizeRespectsLimits() {
        int[] a = {100, 100, 200, 100};
        int[] r = HudGeometry.drag(a, HudGeometry.RIGHT, -500, 0, BOARD, 2);
        assertArrayEquals(new int[]{100, 112, 149, 75}, r);
        int[] l = HudGeometry.drag(a, HudGeometry.LEFT | HudGeometry.TOP, 50, 10, BOARD, 2);
        assertArrayEquals(new int[]{144, 122, 156, 78}, l);
    }

    @Test
    void scaleKeepsProportionsAtLimit() {
        int[] b = {100, 100, 400, 100};
        int[] s = HudGeometry.scale(b, 1.5, BOARD, 4);
        assertEquals(420, s[2]);
        assertEquals(105, s[3]);
    }

    @Test
    void topEdgeKeepsBottomAnchoredAndMapSquare() {
        var map = new HudGeometry.Limits(64, 256, 64, 256);
        int[] box = {10, 10, 104, 104};
        assertArrayEquals(new int[]{5, 0, 114, 114},
                HudGeometry.drag(box, HudGeometry.TOP, 90, -100, map, 1));
    }

    @Test
    void previousDistortionIsCorrectedEvenAtScreenEdge() {
        var map = new HudGeometry.Limits(64, 256, 64, 256);
        assertArrayEquals(new int[]{384, 128, 256, 256},
                HudGeometry.scale(new int[]{384, 320, 256, 64}, 1, map, 1));
    }

    @Test
    void compassOnlyChangesWidth() {
        assertArrayEquals(new int[]{235, 5, 210, 21}, HudGeometry.drag(
                new int[]{235, 5, 170, 21}, HudGeometry.RIGHT, 40, 100, COMPASS, 170.0 / 21));
    }
}
