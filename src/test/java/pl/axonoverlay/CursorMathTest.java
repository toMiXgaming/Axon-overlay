package pl.axonoverlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testy {@link CursorMath}: yaw/pitch na piksele, krawędzie ekranu i przejście przez ±180°.
 */
class CursorMathTest {

    private static final double EPS = 1e-6;

    @Test
    void centerOfScreenForZeroRotation() {
        double[] cursor = CursorMath.cursor(0, 0);
        assertEquals(OverlayLayout.WIDTH / 2.0, cursor[0], EPS);
        assertEquals(OverlayLayout.HEIGHT / 2.0, cursor[1], EPS);
    }

    @Test
    void yawMinusHalfRangeGoesToLeftEdge() {
        double[] cursor = CursorMath.cursor(-OverlayLayout.CURSOR_YAW_RANGE / 2, 0);
        assertEquals(0.0, cursor[0], EPS);
    }

    @Test
    void yawPlusHalfRangeGoesToRightEdge() {
        double[] cursor = CursorMath.cursor(OverlayLayout.CURSOR_YAW_RANGE / 2, 0);
        assertEquals(OverlayLayout.WIDTH, cursor[0], EPS);
    }

    @Test
    void pitchMinusHalfRangeGoesToTopEdge() {
        double[] cursor = CursorMath.cursor(0, -OverlayLayout.CURSOR_PITCH_RANGE / 2);
        assertEquals(0.0, cursor[1], EPS);
    }

    @Test
    void pitchPlusHalfRangeGoesToBottomEdge() {
        double[] cursor = CursorMath.cursor(0, OverlayLayout.CURSOR_PITCH_RANGE / 2);
        assertEquals(OverlayLayout.HEIGHT, cursor[1], EPS);
    }

    @Test
    void cursorIsClampedBeyondRange() {
        // 120° to więcej niż półzakres (90°), ale po normalizacji wciąż dodatnie —
        // kursor staje na prawej krawędzi. Pitch 400° przycina się do dołu.
        double[] cursor = CursorMath.cursor(120, 400);
        assertEquals(OverlayLayout.WIDTH, cursor[0], EPS);
        assertEquals(OverlayLayout.HEIGHT, cursor[1], EPS);
    }

    @Test
    void yawWrapsAcrossPlusMinus180() {
        // 190° to to samo co -170°.
        assertEquals(-170.0, CursorMath.normalizeYaw(190), EPS);
        assertEquals(170.0, CursorMath.normalizeYaw(-190), EPS);
        assertEquals(0.0, CursorMath.normalizeYaw(360), EPS);
        assertEquals(0.0, CursorMath.normalizeYaw(-360), EPS);
    }

    @Test
    void cursorStaysOnEdgeBeyondHalfRange() {
        // Poza półzakresem kursor stoi na krawędzi — nie odbija na drugą stronę.
        double[] right = CursorMath.cursor(179.9, 0);
        double[] left = CursorMath.cursor(-179.9, 0);
        assertEquals(OverlayLayout.WIDTH, right[0], EPS);
        assertEquals(0.0, left[0], EPS);
    }

    @Test
    void clampYawKeepsInsideRange() {
        assertEquals(OverlayLayout.CURSOR_YAW_RANGE / 2, CursorMath.clampYaw(500), EPS);
        assertEquals(-OverlayLayout.CURSOR_YAW_RANGE / 2, CursorMath.clampYaw(-500), EPS);
        assertEquals(45.0, CursorMath.clampYaw(45), EPS);
    }

    @Test
    void clampPitchKeepsInsideRange() {
        assertEquals(OverlayLayout.CURSOR_PITCH_RANGE / 2, CursorMath.clampPitch(500), EPS);
        assertEquals(-OverlayLayout.CURSOR_PITCH_RANGE / 2, CursorMath.clampPitch(-500), EPS);
    }

    @Test
    void outsideDetectsRotationBeyondTolerance() {
        assertFalse(CursorMath.outside(0, 0, 0));
        assertTrue(CursorMath.outside(OverlayLayout.CURSOR_YAW_RANGE, 0, 0));
        assertTrue(CursorMath.outside(0, OverlayLayout.CURSOR_PITCH_RANGE, 0));
        assertFalse(CursorMath.outside(OverlayLayout.CURSOR_YAW_RANGE / 2 - 1, 0, 0));
    }

    @Test
    void insideChecksCanvasBounds() {
        assertTrue(CursorMath.inside(0, 0));
        assertTrue(CursorMath.inside(OverlayLayout.WIDTH - 1, OverlayLayout.HEIGHT - 1));
        assertFalse(CursorMath.inside(-1, 0));
        assertFalse(CursorMath.inside(0, -1));
        assertFalse(CursorMath.inside(OverlayLayout.WIDTH, 0));
        assertFalse(CursorMath.inside(0, OverlayLayout.HEIGHT));
    }

    @Test
    void toWorldMapsCenterPixelToCenterBlock() {
        double[] world = CursorMath.toWorld(OverlayLayout.WIDTH / 2.0, OverlayLayout.HEIGHT / 2.0, 100, 200, 1);
        assertEquals(100.0, world[0], EPS);
        assertEquals(200.0, world[1], EPS);
    }

    @Test
    void toWorldScalesWithZoom() {
        double[] world = CursorMath.toWorld(OverlayLayout.WIDTH / 2.0 + 10, OverlayLayout.HEIGHT / 2.0, 0, 0, 4);
        assertEquals(40.0, world[0], EPS);
        assertEquals(0.0, world[1], EPS);
    }

    @Test
    void toPixelIsInverseOfToWorld() {
        double[] world = CursorMath.toWorld(123, 456, 500, -300, 3);
        double[] pixel = CursorMath.toPixel(world[0], world[1], 500, -300, 3);
        assertEquals(123.0, pixel[0], EPS);
        assertEquals(456.0, pixel[1], EPS);
    }
}
