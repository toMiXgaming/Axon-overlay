package pl.axonoverlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientViewClockTest {

    @Test
    void mapOpenTimeStaysInTheHudWindowAndKeepsTheServerPhase() {
        for (long age = 0; age < 100_000; age += 7) {
            long open = ClientView.mapOpenTime(age, true);
            long rest = Math.floorMod(open, 24000L);
            assertTrue(rest >= OverlayLayout.HUD_TIME, "age " + age);
            // zapas 20 ticków na czas liczony przez klienta, wciąż w oknie zamrażania
            assertTrue(rest < OverlayLayout.HUD_TIME + OverlayLayout.FREEZE_WINDOW - 20, "age " + age);
            assertEquals(Math.floorMod(age, 40L), rest - OverlayLayout.HUD_TIME, "age " + age);
        }
    }

    @Test
    void withoutFreezeTimeLandsInTheSecondHalfOfTheHudWindow() {
        for (long age = 0; age < 100_000; age += 7) {
            long rest = Math.floorMod(ClientView.mapOpenTime(age, false), 24000L);
            assertTrue(rest >= OverlayLayout.HUD_TIME + OverlayLayout.NO_FREEZE_OFFSET, "age " + age);
            assertTrue(rest < OverlayLayout.HUD_TIME + OverlayLayout.HUD_TIME_WINDOW - 20, "age " + age);
            // ta sama faza zegara shadera: mod(reszta - HUD_TIME, 40) = czas serwera mod 40
            assertEquals(Math.floorMod(age, 40L), Math.floorMod(rest - OverlayLayout.HUD_TIME, 40L), "age " + age);
        }
    }
}
