package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.gui.SettingsMenu;
import de.smpcore.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /settings: öffnet das Einstellungs-Menü. */
public final class SettingsCommand implements CommandExecutor {
    private final SMPCore plugin;
    public SettingsCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        if (!plugin.feature("settings")) { Msg.error(s, "Einstellungen sind derzeit deaktiviert."); return true; }
        new SettingsMenu(p).open();
        return true;
    }
}
