package pl.axonoverlay;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Główny plugin biblioteki nakładek.
 *
 * <p>Rejestruje {@link OverlayApi} w {@link org.bukkit.plugin.ServicesManager},
 * żeby AxonMap i Axon-items mogły pobrać je bez zależności kompilacyjnej:</p>
 *
 * <pre>{@code
 * OverlayApi api = Bukkit.getServicesManager().load(OverlayApi.class);
 * }</pre>
 */
public final class AxonOverlayPlugin extends JavaPlugin {

    private OverlayManager manager;

    @Override
    public void onEnable() {
        manager = new OverlayManager(this);
        manager.enable();
        getServer().getServicesManager().register(OverlayApi.class, manager, this, ServicePriority.Normal);

        PluginCommand command = getCommand("overlay");
        if (command != null) {
            OverlayTestCommand executor = new OverlayTestCommand(manager);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Brak komendy /overlay w plugin.yml.");
        }
        getLogger().info("AxonOverlay gotowy (PacketEvents: "
                + (manager.ready() ? "tak" : "nie") + ").");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            getServer().getServicesManager().unregister(OverlayApi.class, manager);
            manager.disable();
            manager = null;
        }
    }

    /**
     * Edytor układu HUD. Otwiera się z wybranym elementem ({@code selected}), ale w tym samym oknie
     * da się przesunąć każdy dostępny element; zapis trafia do pluginu, do którego element należy.
     */
    public void editHud(org.bukkit.entity.Player p, int selected) {
        if (selected < 0 || selected > 3 || !p.isOnline()) return;
        // 0 tablica, 1 minimapa, 2 kompas, 3 kafel zdrowia (AxonScoreboard, u niego część 1)
        int[][] boxes = {{475, 85, 156, 120}, {10, 10, 104, 104}, {235, 5, 170, 21}, {5, 177, 121, 77}};
        String[] names = {"AxonScoreboard", "AxonMap", "AxonMap", "AxonScoreboard"};
        int[] parts = {0, 1, 2, 1};
        boolean[] available = new boolean[boxes.length];
        int[][] defaults = new int[boxes.length][];
        for (int i = 0; i < boxes.length; i++) {
            var source = getServer().getPluginManager().getPlugin(names[i]);
            if (source == null || !source.isEnabled()) continue;
            try {
                boxes[i] = (int[]) source.getClass().getMethod("hudBounds", org.bukkit.entity.Player.class, int.class)
                        .invoke(source, p, parts[i]);
                defaults[i] = (int[]) source.getClass().getMethod("defaultHudBounds", org.bukkit.entity.Player.class, int.class)
                        .invoke(source, p, parts[i]);
                available[i] = boxes[i] != null && boxes[i].length == 4;
            } catch (ReflectiveOperationException | RuntimeException ex) {
                available[i] = false;
            }
        }
        if (!available[selected]) {
            p.sendMessage("Zaktualizuj plugin " + names[selected] + ".");
            return;
        }
        // Przy przeciąganiu zmieniają się tylko kafle wokół ramki, ale przy 20 ruchach na sekundę
        // domyślne 10 kafli na sekundę dławiło edytor.
        var session = manager.open(p, OverlaySpec.builder("axon:hud_layout").density(1).tilesPerSecond(120).build());
        if (session == null) { p.sendMessage("Nie można teraz otworzyć edytora HUD."); return; }
        new HudEditor(session, selected, boxes, available, defaults, (part, bounds) -> {
            var source = getServer().getPluginManager().getPlugin(names[part]);
            if (source == null) return;
            try {
                if (bounds == null) {
                    source.getClass().getMethod("resetHudBounds", org.bukkit.entity.Player.class, int.class)
                            .invoke(source, p, parts[part]);
                } else {
                    source.getClass().getMethod("saveHudBounds", org.bukkit.entity.Player.class, int.class, int[].class)
                            .invoke(source, p, parts[part], bounds);
                }
                p.sendActionBar(net.kyori.adventure.text.Component.text("Zapisano układ HUD."));
            } catch (ReflectiveOperationException ex) {
                getLogger().warning("Zapis HUD: " + ex);
                p.sendMessage("Nie udało się zapisać układu HUD.");
            }
        });
    }

    /** Instancja API; nigdy {@code null} po {@code onEnable}. */
    public OverlayApi api() {
        return manager;
    }
}
