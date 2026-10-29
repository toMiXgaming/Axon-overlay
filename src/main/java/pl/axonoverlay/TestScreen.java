package pl.axonoverlay;

import org.bukkit.Bukkit;

/**
 * Ekran testowy: pusty obraz, kursor, przycisk i licznik KB/s.
 *
 * <p>Rysuje prosto do płótna sesji. Kursor to mały krzyż, przycisk to prostokąt,
 * który zmienia kolor po kliknięciu. Licznik odświeża się co sekundę i pokazuje
 * realny ruch sieciowy sesji — specyfikacja wymaga poniżej 40 KB/s.</p>
 */
final class TestScreen {

    private static final byte BACKGROUND = 0;
    private static final byte GRID = 8;
    private static final byte CURSOR = 56;
    private static final byte BUTTON = 28;
    private static final byte BUTTON_HOVER = 44;
    private static final byte BUTTON_PRESSED = 60;
    private static final byte TEXT = 56;

    private static final int BUTTON_X = 240;
    private static final int BUTTON_Y = 160;
    private static final int BUTTON_W = 160;
    private static final int BUTTON_H = 48;

    private TestScreen() {
    }

    /** Podpina ekran testowy do sesji. */
    static void attach(OverlaySession session) {
        OverlayCanvas canvas = session.canvas();
        drawBase(canvas);
        session.onInput(new InputListener() {
            private int cursorX = OverlayLayout.WIDTH / 2;
            private int cursorY = OverlayLayout.HEIGHT / 2;
            private boolean pressed;

            @Override
            public void onCursor(int x, int y) {
                clearCursor(canvas, cursorX, cursorY);
                cursorX = x;
                cursorY = y;
                drawCursor(canvas, x, y);
                boolean hover = insideButton(x, y);
                drawButton(canvas, hover ? BUTTON_HOVER : BUTTON);
            }

            @Override
            public void onLeftClick(boolean down, int x, int y) {
                if (!down) {
                    pressed = false;
                    drawButton(canvas, insideButton(x, y) ? BUTTON_HOVER : BUTTON);
                    return;
                }
                if (insideButton(x, y)) {
                    pressed = true;
                    drawButton(canvas, BUTTON_PRESSED);
                }
            }

            @Override
            public void onSneak(boolean down) {
                if (down) {
                    session.close();
                }
            }

            @Override
            public void onClose() {
                // nic do zwolnienia
            }
        });
        drawCursor(canvas, OverlayLayout.WIDTH / 2, OverlayLayout.HEIGHT / 2);
        drawButton(canvas, BUTTON);
        startCounter(session);
    }

    private static void drawBase(OverlayCanvas canvas) {
        canvas.clear(BACKGROUND);
        // Delikatna siatka co 32 piksele, żeby było widać ruch kursora.
        for (int x = 0; x < OverlayLayout.WIDTH; x += 32) {
            canvas.fillRect(x, 0, 1, OverlayLayout.HEIGHT, GRID);
        }
        for (int y = 0; y < OverlayLayout.HEIGHT; y += 32) {
            canvas.fillRect(0, y, OverlayLayout.WIDTH, 1, GRID);
        }
    }

    private static void drawCursor(OverlayCanvas canvas, int x, int y) {
        canvas.fillRect(x - 6, y, 13, 1, CURSOR);
        canvas.fillRect(x, y - 6, 1, 13, CURSOR);
        canvas.fillRect(x - 1, y - 1, 3, 3, CURSOR);
    }

    private static void clearCursor(OverlayCanvas canvas, int x, int y) {
        canvas.fillRect(x - 6, y, 13, 1, BACKGROUND);
        canvas.fillRect(x, y - 6, 1, 13, BACKGROUND);
        canvas.fillRect(x - 1, y - 1, 3, 3, BACKGROUND);
        // Odtwórz siatkę, jeśli kursor ją przeciął.
        if (x % 32 == 0) {
            canvas.fillRect(x, y - 6, 1, 13, GRID);
        }
        if (y % 32 == 0) {
            canvas.fillRect(x - 6, y, 13, 1, GRID);
        }
    }

    private static void drawButton(OverlayCanvas canvas, byte color) {
        canvas.fillRect(BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H, color);
        canvas.fillRect(BUTTON_X, BUTTON_Y, BUTTON_W, 2, TEXT);
        canvas.fillRect(BUTTON_X, BUTTON_Y + BUTTON_H - 2, BUTTON_W, 2, TEXT);
        canvas.fillRect(BUTTON_X, BUTTON_Y, 2, BUTTON_H, TEXT);
        canvas.fillRect(BUTTON_X + BUTTON_W - 2, BUTTON_Y, 2, BUTTON_H, TEXT);
    }

    private static boolean insideButton(int x, int y) {
        return x >= BUTTON_X && x < BUTTON_X + BUTTON_W && y >= BUTTON_Y && y < BUTTON_Y + BUTTON_H;
    }

    /** Odświeża licznik KB/s co sekundę, dopóki sesja żyje. */
    private static void startCounter(OverlaySession session) {
        Bukkit.getScheduler().runTaskTimer(
                Bukkit.getPluginManager().getPlugin("AxonOverlay"),
                () -> {
                    if (!session.isOpen()) {
                        return;
                    }
                    double kbps = session.kilobytesPerSecond();
                    // Pasek wypełnienia: 40 KB/s to pełna szerokość.
                    int width = (int) Math.min(OverlayLayout.WIDTH - 40, Math.round(kbps / 40.0 * (OverlayLayout.WIDTH - 40)));
                    session.canvas().fillRect(20, OverlayLayout.HEIGHT - 24, OverlayLayout.WIDTH - 40, 8, BACKGROUND);
                    session.canvas().fillRect(20, OverlayLayout.HEIGHT - 24, width, 8, kbps > 40 ? BUTTON_PRESSED : CURSOR);
                },
                20L, 20L);
    }
}
