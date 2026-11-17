package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.gui.BattlePassMenus;
import de.smpcore.util.Msg;
import de.smpcore.util.Players;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/** /battlepass | /bp quests | /bp premium | /bp give <Spieler> | /bp revoke <Spieler> */
public final class BattlePassCommand implements TabExecutor {
    private final SMPCore plugin;

    public BattlePassCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length > 0 && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("revoke"))) {
            if (!s.hasPermission("smpcore.admin")) { Msg.error(s, "Keine Rechte."); return true; }
            PlayerData d = a.length > 1 ? plugin.data().find(a[1]) : null;
            if (d == null) { Msg.error(s, "Nutzung: /bp " + a[0].toLowerCase() + " <Spieler>"); return true; }
            boolean on = a[0].equalsIgnoreCase("give");
            plugin.battlePass().setPremium(d.uuid, on);
            Msg.ok(s, "Premium-Pass für " + Msg.esc(d.name) + (on ? " vergeben." : " entzogen."));
            Player t = Bukkit.getPlayer(d.uuid);
            if (t != null && on) Msg.send(t, "<gold><bold>Du hast den Premium-Battle-Pass erhalten! <gray>Öffne ihn mit <white>/bp");
            return true;
        }
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        if (!plugin.battlePass().enabled()) { Msg.error(p, "Der Battle Pass ist derzeit deaktiviert."); return true; }
        if (a.length > 0 && a[0].equalsIgnoreCase("quests")) new BattlePassMenus.Quests(p).open();
        else if (a.length > 0 && a[0].equalsIgnoreCase("premium")) plugin.battlePass().buyPremium(p);
        else new BattlePassMenus.Main(p).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) return Players.complete(a[0], s.hasPermission("smpcore.admin") ? List.of("quests", "premium", "give", "revoke") : List.of("quests", "premium"));
        if (a.length == 2 && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("revoke"))) return Players.names(a[1]);
        return List.of();
    }
}
