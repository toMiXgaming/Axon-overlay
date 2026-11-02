package pl.axonoverlay;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TilePatchesTest {

    @Test
    void identicalTilesNeedNothing() {
        assertTrue(TilePatches.changed(new byte[128 * 128], new byte[128 * 128], 128).isEmpty());
    }

    @Test
    void distantRowsBecomeSeparatePatches() {
        byte[] before = new byte[128 * 128];
        byte[] after = before.clone();
        after[60 * 128 + 40] = 1;
        after[61 * 128 + 50] = 1;
        for (int x = 8; x < 128; x++) {
            after[127 * 128 + x] = 2;
        }
        List<int[]> rects = TilePatches.changed(before, after, 128);
        assertEquals(2, rects.size());
        assertArrayEquals(new int[]{40, 60, 11, 2}, rects.get(0));
        assertArrayEquals(new int[]{8, 127, 120, 1}, rects.get(1));
        assertEquals(22 + 120, TilePatches.area(rects));
    }

    @Test
    void closeRowsMergeIntoOnePatch() {
        byte[] before = new byte[128 * 128];
        byte[] after = before.clone();
        after[10 * 128 + 5] = 1;
        after[14 * 128 + 9] = 1;
        List<int[]> rects = TilePatches.changed(before, after, 128);
        assertEquals(1, rects.size());
        assertArrayEquals(new int[]{5, 10, 5, 5}, rects.get(0));
    }
}
