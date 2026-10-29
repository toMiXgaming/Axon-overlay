package pl.axonoverlay;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTimeUpdate;
import org.bukkit.GameRule;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Widok klienta: ukrywanie ręki, wymuszanie pory dnia i obracanie głowy.
 *
 * <p>Nic tutaj nie zmienia stanu serwera — gracz tylko widzi siebie jako
 * niewidzialnego (co ukrywa rękę w pierwszej osobie) i ma puste sloty paska
 * szybkiego wyboru oraz drugiej ręki (co ukrywa trzymany przedmiot).
 * Port z AxonMap bez zmian zachowania.</p>
 */
final class ClientView {

    /** Bit flagi encji odpowiadający za niewidzialność. */
    static final byte INVISIBLE = 0x20;

    /** Pierwszy slot paska szybkiego wyboru w oknie ekwipunku gracza. */
    static final int FIRST_HOTBAR_SLOT = 36;

    /** Slot drugiej ręki. */
    static final int OFF_HAND_SLOT = 45;

    private ClientView() {
    }

    /** Czy slot należy do paska szybkiego wyboru albo drugiej ręki. */
    static boolean isHiddenSlot(int slot) {
        return slot >= FIRST_HOTBAR_SLOT && slot <= OFF_HAND_SLOT;
    }

    /** Ukrywa rękę i trzymane przedmioty; {@link OverlayPackets} utrzymuje to do {@link #show}. */
    static void hideHand(Player player) {
        sendFlags(player, (byte) (flags(player) | INVISIBLE));
        for (int slot = FIRST_HOTBAR_SLOT; slot <= OFF_HAND_SLOT; slot++) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                    new WrapperPlayServerSetSlot(0, 0, slot, ItemStack.EMPTY));
        }
    }

    /** Wysyła prawdziwe flagi i odświeża ekwipunek. */
    static void show(Player player) {
        sendFlags(player, flags(player));
        player.updateInventory();
    }

    /**
     * Wysyła aktualny czas. {@link OverlayPackets} zamienia czas gry na wartość
     * „mapa otwarta” dla graczy z otwartą nakładką, więc to od razu przełącza HUD.
     */
    static void sendTime(Player player) {
        boolean ticking = !Boolean.FALSE.equals(player.getWorld().getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerTimeUpdate(player.getWorld().getGameTime(), player.getPlayerTime(), ticking));
    }

    /** Czas gry z tą samą liczbą dni, którego reszta mówi shaderom, że mapa jest otwarta. */
    static long mapOpenTime(long gameTime) {
        return gameTime - Math.floorMod(gameTime, 24000L) + OverlayLayout.HUD_TIME + 5;
    }

    /**
     * Wypycha innych graczy poza okno „mapa otwarta”, łącznie z tickami, które ich
     * klient liczy sam do następnej aktualizacji czasu.
     */
    static long outsideMapTime(long gameTime) {
        long rest = Math.floorMod(gameTime, 24000L);
        long from = OverlayLayout.HUD_TIME - 60;
        long to = OverlayLayout.HUD_TIME + OverlayLayout.HUD_TIME_WINDOW;
        return rest >= from && rest < to ? gameTime + (to - rest) : gameTime;
    }

    /** Obraca głowę gracza po stronie klienta (bez teleportu). */
    static void rotate(Player player, float yaw, float pitch) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerPlayerRotation(yaw, pitch));
    }

    /** Wysyła flagi encji gracza. */
    static void sendFlags(Player player, byte flags) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerEntityMetadata(
                player.getEntityId(), List.of(new EntityData(0, EntityDataTypes.BYTE, flags))));
    }

    /** Wspólny bajt flag encji, tak jak wysłałby go serwer. */
    static byte flags(Player player) {
        int flags = 0;
        if (player.getFireTicks() > 0) {
            flags |= 0x01;
        }
        if (player.isSneaking()) {
            flags |= 0x02;
        }
        if (player.isSprinting()) {
            flags |= 0x08;
        }
        if (player.isSwimming()) {
            flags |= 0x10;
        }
        if (player.isInvisible()) {
            flags |= INVISIBLE;
        }
        if (player.isGlowing()) {
            flags |= 0x40;
        }
        if (player.isGliding()) {
            flags |= 0x80;
        }
        return (byte) flags;
    }
}
