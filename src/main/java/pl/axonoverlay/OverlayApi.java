package pl.axonoverlay;

import org.bukkit.entity.Player;

/**
 * Wejście do biblioteki nakładek.
 *
 * <p>Plugin pobiera instancję przez {@code AxonOverlayPlugin.api()} albo przez
 * {@link org.bukkit.plugin.ServicesManager}. Typowe użycie:</p>
 *
 * <pre>{@code
 * OverlaySpec spec = OverlaySpec.builder("warsztat").initialScale(1).build();
 * OverlaySession session = api.open(player, spec);
 * if (session == null) {
 *     // warunki zapasowe nie pozwalają na pełny ekran — pokaż okno skrzyni
 *     return;
 * }
 * session.onInput(new InputListener() {
 *     @Override public void onLeftClick(boolean pressed, int x, int y) { ... }
 * });
 * }</pre>
 */
public interface OverlayApi {

    /**
     * Otwiera pełnoekranową nakładkę dla gracza.
     *
     * @param player gracz; musi być online
     * @param spec   opis nakładki
     * @return sesja albo {@code null}, gdy warunki zapasowe nie są spełnione
     *         (szczegóły w {@link #availability(Player)})
     */
    OverlaySession open(Player player, OverlaySpec spec);

    /** Czy gracz ma otwartą jakąkolwiek nakładkę. */
    boolean isOpen(Player player);

    /** Zamyka nakładkę gracza, jeśli jest otwarta. */
    void close(Player player);

    /** Zamyka wszystkie nakładki (przeładowanie pluginu, wyłączenie serwera). */
    void closeAll();

    /** Sprawdza warunki zapasowe dla gracza. */
    OverlayAvailability.Reason availability(Player player);

    /** Czy biblioteka jest gotowa do pracy (PacketEvents obecny). */
    boolean ready();
}
