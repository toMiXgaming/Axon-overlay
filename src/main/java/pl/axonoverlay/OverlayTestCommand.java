package pl.axonoverlay;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * {@code /overlay test} — ekran testowy dla admina.
 *
 * <p>Pokazuje pusty ekran z kursorem, przyciskiem i licznikiem KB/s. Służy do
 * sprawdzenia, czy biblioteka działa na danym kliencie i ile waży ruch sieciowy.</p>
 */
final class OverlayTestCommand implements CommandExecutor, TabCompleter {

    private final OverlayManager manager;

    OverlayTestCommand(OverlayManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cTa komenda działa tylko dla gracza.");
            return true;
        }
        if (!player.hasPermission("axonoverlay.admin")) {
            player.sendMessage("§cBrak uprawnień.");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("test")) {
            player.sendMessage("§7Użycie: §f/" + label + " test");
            return true;
        }
        if (manager.isOpen(player)) {
            manager.close(player);
            player.sendMessage("§7Ekran testowy zamknięty.");
            return true;
        }
        OverlayAvailability.Reason reason = manager.availability(player);
        if (!reason.available()) {
            player.sendMessage("§cPełny ekran niedostępny: §f" + reason.key());
            return true;
        }
        OverlaySpec spec = OverlaySpec.builder("test")
                .lockMovement(true)
                .hideHud(true)
                .initialScale(1)
                .build();
        OverlaySession session = manager.open(player, spec);
        if (session == null) {
            player.sendMessage("§cNie udało się otworzyć ekranu testowego.");
            return true;
        }
        TestScreen.attach(session);
        player.sendMessage("§aEkran testowy otwarty. §7Shift = zamknij, LPM = przycisk.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if ("test".startsWith(prefix)) {
                return List.of("test");
            }
        }
        return List.of();
    }
}
