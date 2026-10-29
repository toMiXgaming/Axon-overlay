package pl.axonoverlay;

import org.bukkit.entity.Player;

/**
 * Warunki zapasowe: czy pełny ekran jest w ogóle możliwy dla danego gracza.
 *
 * <p>Axon-items użyje tego w etapie 7, żeby wybrać między pełnym ekranem warsztatu
 * a zwykłym oknem skrzyni. Biblioteka sprawdza wszystko, co może zawieść po
 * stronie serwera i klienta:</p>
 *
 * <ul>
 *   <li>PacketEvents jest zainstalowany i włączony (bez niego nie ma fałszywych ramek),</li>
 *   <li>gracz zaakceptował paczkę zasobów (shader i model kursora),</li>
 *   <li>gracz stoi na ziemi — w powietrzu fotel teleportowałby go w dziwne miejsce,</li>
 *   <li>gracz nie jest w pojeździe — pasażer nie może dostać kolejnego pasażera,</li>
 *   <li>gracz nie kuca — kucanie zmienia wysokość oczu i psuje rzut kursora.</li>
 * </ul>
 */
public final class OverlayAvailability {

    /** Powód, dla którego pełny ekran nie jest dostępny. */
    public enum Reason {
        OK("ok"),
        NO_PACKET_EVENTS("brak-packetevents"),
        NO_RESOURCE_PACK("brak-paczki"),
        NOT_ON_GROUND("nie-na-ziemi"),
        IN_VEHICLE("w-pojezdzie"),
        SNEAKING("kuca"),
        ALREADY_OPEN("juz-otwarta");

        private final String key;

        Reason(String key) {
            this.key = key;
        }

        /** Klucz konfiguracyjny, np. {@code "brak-paczki"}. */
        public String key() {
            return key;
        }

        /** Czy pełny ekran jest możliwy. */
        public boolean available() {
            return this == OK;
        }
    }

    private OverlayAvailability() {
    }

    /**
     * Sprawdza warunki zapasowe.
     *
     * @param player          gracz do sprawdzenia
     * @param packetEvents    czy PacketEvents jest obecny i włączony
     * @param resourcePackOk  czy gracz zaakceptował paczkę zasobów
     * @param alreadyOpen     czy gracz ma już otwartą inną nakładkę
     * @return pierwszy niespełniony warunek albo {@link Reason#OK}
     */
    public static Reason check(Player player, boolean packetEvents, boolean resourcePackOk, boolean alreadyOpen) {
        if (player == null || !player.isOnline()) {
            return Reason.NOT_ON_GROUND;
        }
        if (!packetEvents) {
            return Reason.NO_PACKET_EVENTS;
        }
        if (!resourcePackOk) {
            return Reason.NO_RESOURCE_PACK;
        }
        if (alreadyOpen) {
            return Reason.ALREADY_OPEN;
        }
        if (player.isInsideVehicle()) {
            return Reason.IN_VEHICLE;
        }
        if (player.isSneaking()) {
            return Reason.SNEAKING;
        }
        if (!player.isOnGround()) {
            return Reason.NOT_ON_GROUND;
        }
        return Reason.OK;
    }

    /** Skrót: sprawdza tylko warunki zależne od gracza (bez PacketEvents i paczki). */
    public static Reason checkPlayer(Player player, boolean alreadyOpen) {
        return check(player, true, true, alreadyOpen);
    }
}
