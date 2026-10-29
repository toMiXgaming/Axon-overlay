package pl.axonoverlay;

/**
 * Odbiornik wejścia z pełnoekranowej nakładki.
 *
 * <p>Wszystkie metody są wołane z wątku głównego serwera, w kolejności zdarzeń.
 * Domyślne implementacje nic nie robią, więc nakładka nadpisuje tylko te, które
 * jej potrzebują.</p>
 *
 * <p>Kursor jest podawany w pikselach płótna: {@code (0,0)} to lewy górny róg,
 * {@code (WIDTH-1, HEIGHT-1)} prawy dolny. Wartości są już przycięte do obszaru
 * płótna, więc implementacja nie musi sprawdzać granic.</p>
 */
public interface InputListener {

    /**
     * Kursor przesunął się (gracz obrócił głowę).
     *
     * @param x współrzędna X w pikselach płótna
     * @param y współrzędna Y w pikselach płótna
     */
    default void onCursor(int x, int y) {
    }

    /**
     * Zmienił się zestaw wciśniętych klawiszy ruchu.
     *
     * @param forward czy wciśnięte W
     * @param back    czy wciśnięte S
     * @param left    czy wciśnięte A
     * @param right   czy wciśnięte D
     */
    default void onMove(boolean forward, boolean back, boolean left, boolean right) {
    }

    /** Gracz wcisnął lub puścił spację (skok). */
    default void onJump(boolean pressed) {
    }

    /** Gracz wcisnął lub puścił shift (kucanie / sprint w nakładce). */
    default void onSneak(boolean pressed) {
    }

    /**
     * Kliknięcie lewym przyciskiem myszy.
     *
     * @param pressed {@code true} przy wciśnięciu, {@code false} przy puszczeniu
     * @param x       współrzędna X kursora w chwili zdarzenia
     * @param y       współrzędna Y kursora w chwili zdarzenia
     */
    default void onLeftClick(boolean pressed, int x, int y) {
    }

    /**
     * Kliknięcie prawym przyciskiem myszy.
     *
     * @param pressed {@code true} przy wciśnięciu, {@code false} przy puszczeniu
     * @param x       współrzędna X kursora w chwili zdarzenia
     * @param y       współrzędna Y kursora w chwili zdarzenia
     */
    default void onRightClick(boolean pressed, int x, int y) {
    }

    /** Gracz przewinął kółko myszy. */
    default void onScroll(int direction) {
    }

    /** Nakładka została zamknięta — tu zwalnia się zasoby i zapisuje stan. */
    default void onClose() {
    }
}
