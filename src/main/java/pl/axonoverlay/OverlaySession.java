package pl.axonoverlay;

import org.bukkit.entity.Player;

/**
 * Otwarta sesja pełnoekranowej nakładki.
 *
 * <p>Obiekt żyje od {@link OverlayApi#open(Player, OverlaySpec)} do
 * {@link #close()}. Nakładka rysuje do {@link #canvas()}, odbiera wejście przez
 * {@link #onInput(InputListener)} i zamyka ekran przez {@link #close()}.</p>
 *
 * <p>Wszystkie metody poza {@link #canvas()} i {@link #player()} muszą być wołane
 * z wątku głównego. {@link #canvas()} można czytać z wątku renderującego.</p>
 */
public interface OverlaySession {

    /** Gracz, do którego należy sesja. */
    Player player();

    /** Identyfikator nakładki z {@link OverlaySpec#id()}. */
    String id();

    /** Płótno, po którym rysuje nakładka. */
    OverlayCanvas canvas();

    /** Podpina odbiornik wejścia; poprzedni zostaje zastąpiony. */
    void onInput(InputListener listener);

    /** Aktualny zoom w blokach na piksel. */
    int scale();

    /** Ustawia zoom; wartość jest przycinana do sensownego zakresu. */
    void scale(int value);

    /** Środek widoku w blokach świata (współrzędna X). */
    double centerX();

    /** Środek widoku w blokach świata (współrzędna Z). */
    double centerZ();

    /** Przesuwa środek widoku o podaną liczbę bloków. */
    void center(double x, double z);

    /** Czy sesja jest jeszcze otwarta. */
    boolean isOpen();

    /** Zamyka ekran, przywraca gracza i woła {@link InputListener#onClose()}. */
    void close();

    /** Łączna liczba bajtów wysłanych w tej sesji. */
    long bytesSent();

    /** Średni ruch sesji w kilobajtach na sekundę. */
    double kilobytesPerSecond();
}
