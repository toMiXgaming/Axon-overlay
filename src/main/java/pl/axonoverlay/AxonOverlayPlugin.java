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

    /** Instancja API; nigdy {@code null} po {@code onEnable}. */
    public OverlayApi api() {
        return manager;
    }
}
