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

    /** Separate settings tabs enter the same editor, with only their own rectangle editable. */
    public void editHud(org.bukkit.entity.Player p, int selected) {
        if (selected < 0 || selected > 2 || !p.isOnline()) return;
        int[][] boxes = {{475, 85, 156, 120}, {10,10,104,104}, {235,5,170,14}};
        String[] names = {"AxonScoreboard","AxonMap","AxonMap"};
        for (int i=0; i<boxes.length; i++) {
            var source = getServer().getPluginManager().getPlugin(names[i]);
            if (source == null || !source.isEnabled()) continue;
            try {
                boxes[i] = (int[])source.getClass().getMethod("hudBounds", org.bukkit.entity.Player.class, int.class).invoke(source,p,i);
            } catch (ReflectiveOperationException ex) {
                if (i == selected) { p.sendMessage("Zaktualizuj plugin " + names[i] + "."); return; }
            }
        }
        var selectedPlugin = getServer().getPluginManager().getPlugin(names[selected]);
        if (selectedPlugin == null || !selectedPlugin.isEnabled()) return;
        int[] defaults;
        try {
            defaults = (int[]) selectedPlugin.getClass()
                    .getMethod("defaultHudBounds", org.bukkit.entity.Player.class, int.class)
                    .invoke(selectedPlugin, p, selected);
        } catch (ReflectiveOperationException ex) {
            p.sendMessage("Zaktualizuj plugin " + names[selected] + ".");
            return;
        }
        var session = manager.open(p, OverlaySpec.builder("axon:hud_layout").density(1).build());
        if (session == null) { p.sendMessage("Nie można teraz otworzyć edytora HUD."); return; }
        new HudEditor(session, selected, boxes, defaults, bounds -> {
            var source = getServer().getPluginManager().getPlugin(names[selected]);
            if (source == null) return;
            try {
                if (bounds == null) {
                    source.getClass().getMethod("resetHudBounds", org.bukkit.entity.Player.class, int.class)
                            .invoke(source, p, selected);
                } else {
                    source.getClass().getMethod("saveHudBounds", org.bukkit.entity.Player.class, int.class, int[].class)
                            .invoke(source, p, selected, bounds);
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
