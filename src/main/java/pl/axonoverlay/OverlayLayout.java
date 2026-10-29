package pl.axonoverlay;

/**
 * Rozmiar pełnego ekranu nakładki. Musi zgadzać się z AXON_COLS / AXON_ROWS w shaderze
 * z paczki zasobów — inaczej obraz rozjedzie się o pół kafla.
 *
 * <p>Klasa jest czysta (bez Bukkita), więc testy jednostkowe liczą geometrię bez serwera.</p>
 */
public final class OverlayLayout {

    public static final int COLS = 5;
    public static final int ROWS = 3;
    public static final int TILES = COLS * ROWS;
    public static final int TILE = 128;
    public static final int WIDTH = COLS * TILE;
    public static final int HEIGHT = ROWS * TILE;

    /** Pierwsze 24 piksele pierwszego rzędu każdego kafla niosą znacznik, którego szuka shader. */
    public static final int MARKER_WIDTH = 24;

    /**
     * Stopnie obrotu głowy, które przesuwają kursor przez cały ekran. Widok gracza jest ustawiany
     * na yaw 0 / pitch 0 przy otwarciu. Musi zgadzać się z AXON_YAW_RANGE / AXON_PITCH_RANGE
     * w shaderze.
     */
    public static final double CURSOR_YAW_RANGE = 180.0;
    public static final double CURSOR_PITCH_RANGE = 100.0;

    /**
     * Gdy ekran jest otwarty, klient dostaje czas gry, którego reszta z dzielenia przez 24000 leży
     * w [HUD_TIME, HUD_TIME + HUD_TIME_WINDOW). Shadery GUI widzą go w uniformie GameTime i chowają
     * pasek szybkiego wyboru, serca i celownik. Musi zgadzać się z AXON_HUD_TIME / AXON_HUD_WINDOW.
     */
    public static final long HUD_TIME = 12000;
    public static final long HUD_TIME_WINDOW = 60;

    private OverlayLayout() {
    }
}
