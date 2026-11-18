package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /verify <Code> verknüpft den Account mit XuroMarket. /verify logout löst die Verknüpfung. */
public final class VerifyCommand implements TabExecutor {
    private final SMPCore plugin;

    public VerifyCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        if (a.length == 0) {
            plugin.link().status(p);
            Msg.send(p, "<gray>Nutzung: <white>/verify <Code> <gray>oder <white>/verify logout");
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "logout", "unlink", "abmelden" -> plugin.link().unlink(p);
            case "status" -> plugin.link().status(p);
            default -> {
                Msg.send(p, "<red>Achtung: <gray>Gib nur Codes ein, die du <white>selbst<gray> auf XuroMarket gesehen hast. Wer dich bittet, einen Code von ihm einzugeben, will an dein Inventar!");
                plugin.link().verify(p, a[0]);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        return a.length == 1 ? List.of("logout", "status") : List.of();
    }
}
