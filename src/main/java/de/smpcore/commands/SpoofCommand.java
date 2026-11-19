package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /spoof <Spieler> | off | list | release <Spieler> */
public final class SpoofCommand implements TabExecutor {
    private final SMPCore plugin;

    public SpoofCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!plugin.feature("spoof")) { Msg.error(s, "Dieses Feature ist deaktiviert."); return true; }
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);

        // /spoof off darf jeder aktive Spoofer, auch wenn ihm durch den fremden Rang die Permission fehlt
        if (sub.equals("off") || sub.equals("back")) {
            if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler. Als Konsole: /spoof release <Spieler>"); return true; }
            if (!plugin.spoof().finish(p, false)) Msg.error(p, "Du spoofst gerade niemanden.");
            return true;
        }
        boolean console = !(s instanceof Player);
        if (!console && !s.hasPermission("smpcore.spoof")) { Msg.error(s, "Keine Berechtigung."); return true; }

        switch (sub) {
            case "list" -> {
                List<String> l = plugin.spoof().describe();
                if (l.isEmpty()) Msg.send(s, "<gray>Keine aktiven Spoofs.");
                else l.forEach(x -> Msg.send(s, "<white>" + Msg.esc(x)));
            }
            case "release" -> {
                if (a.length < 2) { Msg.error(s, "Nutzung: /spoof release <Spieler>"); return true; }
                String n = plugin.spoof().release(a[1]);
                if (n == null) Msg.error(s, "Dieser Spieler wird nicht gespooft.");
                else Msg.ok(s, Msg.esc(n) + " ist wieder freigegeben.");
            }
            case "" -> Msg.send(s, "<gray>Nutzung: <white>/spoof <Spieler> <gray>| <white>/spoof off <gray>| <white>/spoof list <gray>| <white>/spoof release <Spieler>");
            default -> {
                if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
                plugin.spoof().start(p, a[0]);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) {
            List<String> o = new ArrayList<>(List.of("off", "list", "release"));
            Bukkit.getOnlinePlayers().forEach(p -> o.add(p.getName()));
            return o.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
