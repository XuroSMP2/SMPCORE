package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.manhunt.ManhuntManager;
import de.smpcore.tools.ToolManager;
import de.smpcore.util.Msg;
import de.smpcore.util.Players;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /manhunt <Spieler> <Preis> <Währung> <Zeit> | list | cancel <Spieler> */
public final class ManhuntCommand implements TabExecutor {
    private final SMPCore plugin;

    public ManhuntCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!plugin.feature("manhunt")) { Msg.error(s, "Manhunt ist derzeit deaktiviert."); return true; }
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("list")) {
            var all = plugin.manhunt().all();
            if (all.isEmpty()) { Msg.send(s, "<gray>Aktuell läuft kein Manhunt."); return true; }
            for (ManhuntManager.Hunt h : all) {
                Msg.send(s, "<red>" + Msg.esc(h.targetName) + " <dark_gray>» <green>" + ManhuntManager.fmt(h.currency, h.prize)
                        + " <gray>von <white>" + Msg.esc(h.creatorName) + " <dark_gray>(noch " + ToolManager.fmtDuration(h.endsAt - System.currentTimeMillis()) + ")");
            }
            return true;
        }
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        if (sub.equals("cancel")) {
            if (a.length < 2) { Msg.error(p, "Nutzung: /manhunt cancel <Spieler>"); return true; }
            if (!plugin.manhunt().cancel(p, a[1])) Msg.error(p, "Auf diesen Spieler läuft kein Manhunt.");
            return true;
        }
        if (a.length < 4) {
            Msg.error(p, "Nutzung: /manhunt <Spieler> <Preis> <Währung: money|stars|gems> <Zeit z. B. 30m, 2h>");
            return true;
        }
        plugin.manhunt().create(p, a[0], a[1], a[2], a[3]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) {
            java.util.List<String> o = new java.util.ArrayList<>(List.of("list", "cancel"));
            o.addAll(Players.names(""));
            return Players.complete(a[0], o);
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("cancel")) return Players.complete(a[1], plugin.manhunt().all().stream().map(h -> h.targetName).toList());
        if (a.length == 2) return List.of("1k", "10k", "100k", "1m");
        if (a.length == 3) return Players.complete(a[2], List.of("money", "stars", "gems"));
        if (a.length == 4) return Players.complete(a[3], List.of("10m", "30m", "1h", "2h", "12h", "1d"));
        return List.of();
    }
}
