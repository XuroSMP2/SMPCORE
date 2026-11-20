package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.gui.UpgradesMenu;
import de.smpcore.upgrade.UpgradeManager;
import de.smpcore.util.Msg;
import de.smpcore.util.Players;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /upgrades [set <Spieler> <id> <Stufe>] */
public final class UpgradesCommand implements TabExecutor {
    private final SMPCore plugin;

    public UpgradesCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!plugin.feature("upgrades")) { Msg.error(s, "Upgrades sind derzeit deaktiviert."); return true; }
        if (a.length > 0 && a[0].equalsIgnoreCase("set")) {
            if (!s.hasPermission("smpcore.admin")) { Msg.error(s, "Keine Rechte."); return true; }
            if (a.length < 4) { Msg.error(s, "Nutzung: /upgrades set <Spieler> <id> <Stufe>"); return true; }
            PlayerData d = plugin.data().find(a[1]);
            UpgradeManager.Def def = plugin.upgrades().get(a[2]);
            if (d == null || def == null) { Msg.error(s, "Spieler oder Upgrade unbekannt."); return true; }
            int lvl;
            try { lvl = Math.max(0, Math.min(def.maxLevel(), Integer.parseInt(a[3]))); } catch (NumberFormatException e) { Msg.error(s, "Ungültige Stufe."); return true; }
            if (lvl == 0) d.upgrades.remove(def.id()); else d.upgrades.put(def.id(), lvl);
            Msg.ok(s, Msg.esc(d.name) + ": " + def.id() + " = Stufe " + lvl);
            return true;
        }
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        new UpgradesMenu(p).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("smpcore.admin")) return List.of();
        if (a.length == 1) return Players.complete(a[0], List.of("set"));
        if (a.length == 2) return Players.names(a[1]);
        if (a.length == 3) return Players.complete(a[2], plugin.upgrades().all().stream().map(UpgradeManager.Def::id).toList());
        return List.of();
    }
}
