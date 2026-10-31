package pl.axonoverlay;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTimeUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nasłuch pakietów: zamienia surowe pakiety klienta na zdarzenia {@link InputListener}.
 *
 * <p>Port z AxonMap. Cztery zadania:</p>
 * <ol>
 *   <li>kliknięcia w fałszywe ramki (atak = LPM, interakcja = PPM z blokadą powtórzeń),</li>
 *   <li>machnięcie ręką jako LPM (klient wysyła animację zamiast ataku, gdy nie ma celu),</li>
 *   <li>ukrywanie HUD przez przepisywanie pakietów czasu,</li>
 *   <li>ukrywanie ręki i hotbara przez zerowanie slotów w pakietach ekwipunku.</li>
 * </ol>
 *
 * <p>Nasłuch działa na wątkach sieciowych, więc nie dotyka API Bukkit poza
 * bezpiecznym {@link Bukkit#getScheduler()} i {@link Bukkit#getPlayer(UUID)}.</p>
 */
final class OverlayPackets extends PacketListenerAbstract {

    /** Machnięcie w ciągu tylu nanosekund po kliknięciu jest uznawane za to samo kliknięcie. */
    private static final long SWING_AFTER_CLICK_NANOS = 250_000_000L;

    /** Minimalny odstęp między powtórzeniami PPM (trzymanie przycisku). */
    private static final long RIGHT_CLICK_REPEAT_NANOS = 400_000_000L;

    /** Indeks slotu drugiej ręki w pakiecie set-player-inventory. */
    private static final int OFF_HAND_INDEX = 40;

    /** Stan jednej otwartej sesji widziany przez nasłuch. */
    static final class State {
        final int playerEntityId;
        final Set<Integer> frameIds = ConcurrentHashMap.newKeySet();
        final boolean hideHud;
        volatile boolean handHidden;
        volatile long lastClickNanos;
        volatile long lastRightClickNanos;

        State(int playerEntityId, int[] frameIds, boolean hideHud) {
            this.playerEntityId = playerEntityId;
            this.hideHud = hideHud;
            if (frameIds != null) {
                for (int id : frameIds) {
                    this.frameIds.add(id);
                }
            }
        }

        /** Czy podany identyfikator encji to ramka tej sesji. */
        boolean isFrame(int entityId) {
            return frameIds.contains(entityId);
        }
    }

    private final Plugin plugin;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final Map<UUID, OverlaySessionImpl> sessions = new ConcurrentHashMap<>();

    OverlayPackets(Plugin plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    /** Rejestruje sesję do nasłuchu. */
    void register(OverlaySessionImpl session) {
        UUID id = session.player().getUniqueId();
        sessions.put(id, session);
        states.put(id, session.packetState());
    }

    /** Wyrejestrowuje sesję. */
    void unregister(UUID playerId) {
        sessions.remove(playerId);
        states.remove(playerId);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        UUID playerId = event.getUser().getUUID();
        State state = playerId == null ? null : states.get(playerId);
        if (state == null) {
            return;
        }
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (!state.isFrame(interact.getEntityId())) {
                return;
            }
            event.setCancelled(true);
            state.lastClickNanos = System.nanoTime();
            switch (interact.getAction()) {
                case ATTACK -> click(playerId, false);
                // Klient wysyła INTERACT_AT i INTERACT dla jednego kliknięcia PPM, potem
                // powtarza oba dla drugiej ręki: reagujemy tylko na główną rękę i z blokadą.
                case INTERACT -> {
                    long now = System.nanoTime();
                    if (interact.getHand() == InteractionHand.MAIN_HAND
                            && now - state.lastRightClickNanos > RIGHT_CLICK_REPEAT_NANOS) {
                        click(playerId, true);
                    }
                    if (interact.getHand() == InteractionHand.MAIN_HAND) {
                        state.lastRightClickNanos = now;
                    }
                }
                default -> {
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            event.setCancelled(true);
            // Machnięcie, które nie trafiło w ramkę (patrzenie obok), też liczy się jako LPM.
            long now = System.nanoTime();
            if (now - state.lastClickNanos > SWING_AFTER_CLICK_NANOS) {
                state.lastClickNanos = now;
                click(playerId, false);
            }
        }
    }

    private void click(UUID playerId, boolean right) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            OverlaySessionImpl session = sessions.get(playerId);
            if (session != null) {
                session.handleClick(right);
            }
        });
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        UUID playerId = event.getUser().getUUID();
        State state = playerId == null ? null : states.get(playerId);
        boolean hidden = state != null && state.handHidden;
        if (event.getPacketType() == PacketType.Play.Server.TIME_UPDATE) {
            if (!hidden && busyElsewhere(playerId)) {
                // Gracz ma pełny ekran z drugiego pluginu (AxonMap albo AxonOverlay) — tamten
                // ustawia czas chowający HUD, więc nie wypychamy go z okna.
                return;
            }
            WrapperPlayServerTimeUpdate time = new WrapperPlayServerTimeUpdate(event);
            long age = hidden
                    ? ClientView.mapOpenTime(time.getWorldAge())
                    : ClientView.outsideMapTime(time.getWorldAge());
            if (age != time.getWorldAge()) {
                time.setWorldAge(age);
                event.markForReEncode(true);
            }
            return;
        }
        if (!hidden) {
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata metadata = new WrapperPlayServerEntityMetadata(event);
            if (metadata.getEntityId() != state.playerEntityId) {
                return;
            }
            for (EntityData data : metadata.getEntityMetadata()) {
                if (data.getIndex() == 0 && data.getValue() instanceof Byte flags) {
                    data.setValue((byte) (flags | ClientView.INVISIBLE));
                    event.markForReEncode(true);
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot slot = new WrapperPlayServerSetSlot(event);
            if (slot.getWindowId() == 0 && ClientView.isHiddenSlot(slot.getSlot())) {
                slot.setItem(ItemStack.EMPTY);
                event.markForReEncode(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems window = new WrapperPlayServerWindowItems(event);
            List<ItemStack> items = window.getItems();
            if (window.getWindowId() == 0 && items.size() > ClientView.OFF_HAND_SLOT) {
                List<ItemStack> emptied = new ArrayList<>(items);
                for (int slot = ClientView.FIRST_HOTBAR_SLOT; slot <= ClientView.OFF_HAND_SLOT; slot++) {
                    emptied.set(slot, ItemStack.EMPTY);
                }
                window.setItems(emptied);
                event.markForReEncode(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
            WrapperPlayServerSetPlayerInventory inventory = new WrapperPlayServerSetPlayerInventory(event);
            if (inventory.getSlot() < 9 || inventory.getSlot() == OFF_HAND_INDEX) {
                inventory.setStack(ItemStack.EMPTY);
                event.markForReEncode(true);
            }
        }
    }

    /** Czy gracz siedzi na pełnym ekranie innego pluginu (metadata {@code axonoverlay.busy}). */
    private static boolean busyElsewhere(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(playerId);
        if (player == null) {
            return false;
        }
        for (org.bukkit.metadata.MetadataValue value : player.getMetadata("axonoverlay.busy")) {
            org.bukkit.plugin.Plugin owning = value.getOwningPlugin();
            if (owning != null && !owning.getName().equals("AxonOverlay")) {
                return true;
            }
        }
        return false;
    }
}
