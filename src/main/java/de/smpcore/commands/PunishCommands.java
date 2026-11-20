package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.rank.RankManager;
import de.smpcore.staff.PunishManager;
import de.smpcore.tools.ToolManager;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import de.smpcore.util.Players;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;

/** /ban, /tempban, /unban, /lookup, /banlist */
public final class PunishCommands implements TabExecutor {
    private final SMPCore plugin;

    public PunishCommands(SMPCore plugin) { this.plugin = plugin; }

    private boolean can(CommandSender s, String node) {
        return s instanceof ConsoleCommandSender || s.hasPermission("smpcore.cmd." + node) || s.hasPermission("smpcore.admin");
    }

    private boolean unlimited(CommandSender s) {
        return s instanceof ConsoleCommandSender || s.hasPermission("smpcore.ban.permanent") || s.hasPermission("smpcore.admin");
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        switch (c.getName().toLowerCase(Locale.ROOT)) {
            case "ban" -> ban(s, a, false);
            case "tempban" -> ban(s, a, true);
            case "unban" -> unban(s, a);
            case "lookup" -> lookup(s, a);
            case "banlist" -> banlist(s, a);
            default -> { }
        }
        return true;
    }

    // ---------------------------------------------------------------- /ban, /tempban
    private void ban(CommandSender s, String[] a, boolean temp) {
        if (!can(s, "ban")) { Msg.error(s, "Keine Rechte."); return; }
        String usage = temp ? "Nutzung: /tempban <Spieler> <Dauer z. B. 1d, 12h> <Grund>" : "Nutzung: /ban <Spieler> [Dauer z. B. 1d, 12h, perm] <Grund>";
        if (a.length < 2) { Msg.error(s, usage); return; }
        PlayerData t = plugin.data().find(a[0]);
        if (t == null) { Msg.error(s, "Spieler <white>" + Msg.esc(a[0]) + "<red> ist nicht bekannt (war noch nie auf dem Server)."); return; }

        long dur = ToolManager.parseDuration(a[1]); // -1 = keine Dauer angegeben
        int ri = 1;
        if (dur >= 0) ri = 2;
        else if (temp) { Msg.error(s, "Ungültige Dauer. " + usage); return; }
        else dur = 0;
        String reason = a.length > ri ? String.join(" ", Arrays.copyOfRange(a, ri, a.length)) : "";
        if (reason.isBlank()) {
            if (plugin.getConfig().getBoolean("ban.require-reason", true)) { Msg.error(s, "Bitte gib einen Grund an. " + usage); return; }
            reason = "Verstoß gegen die Regeln";
        }
        if (temp && dur == 0) { Msg.error(s, "Für dauerhafte Bans nutze /ban."); return; }

        if (!unlimited(s)) {
            long max = Math.max(60_000L, ToolManager.parseDuration(plugin.getConfig().getString("ban.mod-max-duration", "7d")));
            if (dur == 0 || dur > max) {
                Msg.error(s, "Du darfst höchstens für " + ToolManager.fmtDuration(max) + " bannen. Dauerhafte Bans macht ein Admin.");
                return;
            }
        }
        if (s instanceof Player sp) {
            if (sp.getUniqueId().equals(t.uuid)) { Msg.error(s, "Du kannst dich nicht selbst bannen."); return; }
            if (protectedTarget(sp, t)) { Msg.error(s, "Diesen Spieler kannst du nicht bannen (Team / gleicher oder höherer Rang)."); return; }
        }
        String by = s instanceof Player sp2 ? sp2.getName() : "Konsole";
        UUID byId = s instanceof Player sp3 ? sp3.getUniqueId() : null;
        plugin.punish().ban(t.uuid, t.name, byId, by, reason, dur);

        String d = dur > 0 ? Num.duration(dur) : "dauerhaft";
        String msg = "<dark_gray>[<red>Ban<dark_gray>] <white>" + Msg.esc(by) + " <gray>hat <red>" + Msg.esc(t.name) + " <gray>gebannt <dark_gray>(<white>" + d + "<dark_gray>): <gray>" + Msg.esc(reason);
        if (plugin.getConfig().getBoolean("ban.broadcast", true)) Bukkit.broadcast(Msg.c(msg));
        else {
            Msg.send(s, msg);
            for (Player o : Bukkit.getOnlinePlayers()) if (o != s && can(o, "ban")) o.sendMessage(Msg.c(msg));
        }
    }

    /** Teammitglieder und gleich-/höherrangige Spieler sind geschützt (Admins/Konsole dürfen alles). */
    private boolean protectedTarget(Player by, PlayerData t) {
        if (by.hasPermission("smpcore.admin")) return false;
        Player online = Bukkit.getPlayer(t.uuid);
        if (online != null && (online.hasPermission("smpcore.ban.bypass") || online.hasPermission("smpcore.admin"))) return true;
        RankManager.Rank tr = plugin.ranks().rankOf(t.uuid);
        RankManager.Rank sr = plugin.ranks().rankOf(by.getUniqueId());
        return tr != null && sr != null && tr.weight >= sr.weight;
    }

    // ---------------------------------------------------------------- /unban
    private void unban(CommandSender s, String[] a) {
        if (!can(s, "ban")) { Msg.error(s, "Keine Rechte."); return; }
        if (a.length < 1) { Msg.error(s, "Nutzung: /unban <Spieler> [Grund]"); return; }
        PlayerData t = plugin.data().find(a[0]);
        PunishManager.Ban b = t == null ? null : plugin.punish().get(t.uuid);
        if (b == null) { Msg.error(s, "Dieser Spieler ist nicht gebannt."); return; }
        boolean own = s instanceof Player p && p.getUniqueId().equals(b.byId);
        if (!own && !unlimited(s)) { Msg.error(s, "Du kannst nur deine eigenen Bans aufheben."); return; }
        String by = s instanceof Player p2 ? p2.getName() : "Konsole";
        String reason = a.length > 1 ? String.join(" ", Arrays.copyOfRange(a, 1, a.length)) : "aufgehoben";
        plugin.punish().unban(t.uuid, by, reason);
        String msg = "<dark_gray>[<green>Unban<dark_gray>] <white>" + Msg.esc(by) + " <gray>hat <green>" + Msg.esc(t.name) + " <gray>entbannt.";
        Msg.send(s, msg);
        for (Player o : Bukkit.getOnlinePlayers()) if (o != s && can(o, "ban")) o.sendMessage(Msg.c(msg));
    }

    // ---------------------------------------------------------------- /lookup
    private void lookup(CommandSender s, String[] a) {
        if (!can(s, "lookup")) { Msg.error(s, "Keine Rechte."); return; }
        if (a.length < 1) { Msg.error(s, "Nutzung: /lookup <Spieler>"); return; }
        PlayerData d = plugin.data().find(a[0]);
        if (d == null) { Msg.error(s, "Spieler nicht bekannt."); return; }
        Player on = Bukkit.getPlayer(d.uuid);
        boolean hidden = on != null && plugin.vanished().contains(d.uuid) && !s.hasPermission("smpcore.admin") && !(s instanceof ConsoleCommandSender);
        RankManager.Rank rk = plugin.ranks().rankOf(d.uuid);
        String status = on != null && !hidden ? "<green>online" : "<gray>offline <dark_gray>(zuletzt " + (d.lastSeen > 0 ? PunishManager.when(d.lastSeen) : "unbekannt") + ")";

        Msg.send(s, "<dark_gray>━━━━ <aqua>Lookup: <white>" + Msg.esc(d.name) + " <dark_gray>━━━━");
        Msg.send(s, "<gray>UUID: <white>" + d.uuid + " <dark_gray>| <gray>Status: " + status);
        Msg.send(s, "<gray>Rang: <white>" + Msg.esc(rk == null ? "-" : rk.id) + " <dark_gray>| <gray>Spielzeit: <white>" + Num.duration(d.playtime * 1000L)
                + " <dark_gray>| <gray>K/D: <white>" + d.kills + "/" + d.deaths);
        Msg.send(s, "<gray>Geld: <gold>" + de.smpcore.economy.EconomyManager.fmt(d.money) + " <dark_gray>| <gray>Bank: <gold>" + de.smpcore.economy.EconomyManager.fmt(plugin.bank().totalOwned(d)));

        PunishManager.Ban b = plugin.punish().get(d.uuid);
        Msg.send(s, b == null ? "<gray>Ban: <green>nicht gebannt"
                : "<gray>Ban: <red>gebannt <dark_gray>(" + plugin.punish().remaining(b) + ") <gray>von <white>" + Msg.esc(b.by) + "<gray>: " + Msg.esc(b.reason));
        Msg.send(s, plugin.staff().isMuted(d.uuid) ? "<gray>Mute: <red>gemutet <dark_gray>(" + (plugin.staff().left(d.uuid) <= 0 ? "dauerhaft" : "noch " + Num.duration(plugin.staff().left(d.uuid))) + ")" : "<gray>Mute: <green>nicht gemutet");

        List<PunishManager.Entry> h = plugin.punish().history(d.uuid);
        if (h.isEmpty()) { Msg.send(s, "<gray>Verlauf: <green>keine Strafen"); return; }
        Msg.send(s, "<gray>Verlauf <dark_gray>(letzte " + Math.min(8, h.size()) + " von " + h.size() + "):");
        for (int i = h.size() - 1; i >= Math.max(0, h.size() - 8); i--) {
            PunishManager.Entry e = h.get(i);
            String col = e.type().contains("UNBAN") || e.type().equals("UNMUTE") ? "<green>" : "<red>";
            Msg.send(s, "<dark_gray>" + PunishManager.when(e.time()) + " " + col + e.type() + " <gray>von <white>" + Msg.esc(e.by())
                    + (e.until() > 0 ? " <dark_gray>(bis " + PunishManager.when(e.until()) + ")" : "") + "<gray>: " + Msg.esc(e.reason()));
        }
    }

    // ---------------------------------------------------------------- /banlist
    private void banlist(CommandSender s, String[] a) {
        if (!can(s, "ban")) { Msg.error(s, "Keine Rechte."); return; }
        List<PunishManager.Ban> l = new ArrayList<>(plugin.punish().active());
        if (l.isEmpty()) { Msg.send(s, "<gray>Aktuell ist niemand gebannt."); return; }
        int page = 1;
        if (a.length > 0) try { page = Math.max(1, Integer.parseInt(a[0])); } catch (NumberFormatException ignored) { }
        int pages = (l.size() + 9) / 10;
        page = Math.min(page, pages);
        Msg.send(s, "<dark_gray>━━ <aqua>Banliste <gray>(Seite " + page + "/" + pages + ") <dark_gray>━━");
        for (int i = (page - 1) * 10; i < Math.min(l.size(), page * 10); i++) {
            PunishManager.Ban b = l.get(i);
            Msg.send(s, "<red>" + Msg.esc(b.name) + " <dark_gray>(" + plugin.punish().remaining(b) + ") <gray>von <white>" + Msg.esc(b.by) + "<gray>: " + Msg.esc(b.reason));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase(Locale.ROOT);
        if (a.length == 1 && n.equals("unban")) return Players.complete(a[0], plugin.punish().active().stream().map(b -> b.name).toList());
        if (a.length == 1 && !n.equals("banlist")) return Players.names(a[0]);
        if (a.length == 2 && (n.equals("ban") || n.equals("tempban"))) return Players.complete(a[1], List.of("1h", "12h", "1d", "3d", "7d", "30d", "perm"));
        return List.of();
    }
}
