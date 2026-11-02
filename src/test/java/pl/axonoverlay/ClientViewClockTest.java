package pl.axonoverlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientViewClockTest {

    @Test
    void mapOpenTimeStaysInTheHudWindowAndKeepsTheServerPhase() {
        for (long age = 0; age < 100_000; age += 7) {
            long open = ClientView.mapOpenTime(age);
            long rest = Math.floorMod(open, 24000L);
            assertTrue(rest >= OverlayLayout.HUD_TIME, "age " + age);
            assertTrue(rest < OverlayLayout.HUD_TIME + OverlayLayout.HUD_TIME_WINDOW - 20, "age " + age);
            assertEquals(Math.floorMod(age, 40L), rest - OverlayLayout.HUD_TIME, "age " + age);
        }
    }
}
