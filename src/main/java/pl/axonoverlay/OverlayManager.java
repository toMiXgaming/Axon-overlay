package pl.axonoverlay;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Menedżer sesji: jedno miejsce, w którym żyją wszystkie otwarte nakładki.
 *
 * <p>Dzięki temu gracz nie otworzy mapy i warsztatu naraz — druga próba dostaje
 * {@link OverlayAvailability.Reason#ALREADY_OPEN}. Menedżer sprząta też sesje po
 * wylogowaniu i przy wyłączaniu pluginu.</p>
 */
final class OverlayManager implements OverlayApi, Listener {

    /**
     * Znacznik „gracz ma otwarty pełny ekran”, wspólny dla wszystkich pluginów Axon.
     *
     * <p>AxonMap ma jeszcze własną sesję pełnego ekranu, więc menedżer nie widzi jej
     * w {@link #sessions}. Oba pluginy ustawiają ten metadata na graczu przy otwarciu
     * i zdejmują przy zamknięciu, dzięki czemu gracz nie otworzy mapy i warsztatu naraz.</p>
     */
    static final String BUSY_METADATA = "axonoverlay.busy";

    private final Plugin plugin;
    private final OverlayPackets packets;
    private final Map<UUID, OverlaySessionImpl> sessions = new ConcurrentHashMap<>();
    private boolean packetEventsReady;

    OverlayManager(Plugin plugin) {
        this.plugin = plugin;
        this.packets = new OverlayPackets(plugin);
        this.packetEventsReady = Bukkit.getPluginManager().isPluginEnabled("packetevents");
    }

    /** Rejestruje nasłuch pakietów; wołane raz przy starcie pluginu. */
    void enable() {
        if (packetEventsReady) {
            com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager().registerListener(packets);
        } else {
            plugin.getLogger().warning("Brak PacketEvents — pełny ekran jest wyłączony.");
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Wyłącza menedżer: zamyka sesje i odpina nasłuch. */
    void disable() {
        closeAll();
        if (packetEventsReady) {
            try {
                com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager().unregisterListener(packets);
            } catch (RuntimeException exception) {
                plugin.getLogger().fine("Nie udało się odpiąć nasłuchu: " + exception.getMessage());
            }
        }
    }

    @Override
    public OverlaySession open(Player player, OverlaySpec spec) {
        if (player == null || spec == null) {
            return null;
        }
        if (!packetEventsReady) {
            return null;
        }
        if (sessions.containsKey(player.getUniqueId()) || busyElsewhere(player)) {
            return null;
        }
        OverlayAvailability.Reason reason = OverlayAvailability.checkPlayer(player, false);
        if (!reason.available()) {
            return null;
        }
        OverlaySessionImpl session = new OverlaySessionImpl(plugin, player, spec);
        sessions.put(player.getUniqueId(), session);
        packets.register(session);
        // Sesja może zamknąć się sama (wylogowanie w ticku, przycisk na ekranie) — wtedy też
        // zwalniamy gracza, inaczej zostałby na zawsze „z otwartą nakładką”.
        session.onClosed(() -> release(player, session));
        player.setMetadata(BUSY_METADATA, new FixedMetadataValue(plugin, spec.id()));
        session.start();
        if (!session.isOpen()) {
            // Fotel się nie udał (inny plugin anulował spawn) — sprzątamy od razu.
            close(player);
            return null;
        }
        return session;
    }

    @Override
    public boolean isOpen(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    @Override
    public void close(Player player) {
        if (player == null) {
            return;
        }
        OverlaySessionImpl session = sessions.remove(player.getUniqueId());
        if (session != null) {
            packets.unregister(player.getUniqueId());
            player.removeMetadata(BUSY_METADATA, plugin);
            session.close();
        }
    }

    @Override
    public void closeAll() {
        for (UUID id : new java.util.ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            OverlaySessionImpl session = sessions.remove(id);
            packets.unregister(id);
            if (player != null) {
                player.removeMetadata(BUSY_METADATA, plugin);
            }
            if (session != null) {
                session.close();
            }
        }
    }

    @Override
    public OverlayAvailability.Reason availability(Player player) {
        if (!packetEventsReady) {
            return OverlayAvailability.Reason.NO_PACKET_EVENTS;
        }
        return OverlayAvailability.checkPlayer(player,
                sessions.containsKey(player.getUniqueId()) || busyElsewhere(player));
    }

    private void release(Player player, OverlaySessionImpl session) {
        if (sessions.remove(player.getUniqueId(), session)) {
            packets.unregister(player.getUniqueId());
        }
        player.removeMetadata(BUSY_METADATA, plugin);
    }

    /** Czy inny plugin (np. AxonMap) trzyma gracza na swoim pełnym ekranie. */
    private boolean busyElsewhere(Player player) {
        for (MetadataValue value : player.getMetadata(BUSY_METADATA)) {
            if (value.getOwningPlugin() != plugin) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean ready() {
        return packetEventsReady;
    }

    /** Sesja gracza albo {@code null}. */
    OverlaySessionImpl session(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    /** Zejście z fotela (Shift u klienta, inny plugin) zamyka ekran, jak w AxonMap. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        OverlaySessionImpl session = sessions.get(player.getUniqueId());
        if (session != null && event.getDismounted().equals(session.seat())) {
            close(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        close(event.getPlayer());
    }
}
