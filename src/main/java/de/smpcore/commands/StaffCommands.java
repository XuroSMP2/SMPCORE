package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.gui.SmpToolsMenu;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import de.smpcore.util.Players;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/** invsee, ecsee, gm, freeze, mute, unmute, clearchat, skull, rename, ptime, smptools */
public final class StaffCommands implements TabExecutor {
    private static final MiniMessage SAFE = MiniMessage.builder().tags(TagResolver.resolver(
            StandardTags.color(), StandardTags.decorations(), StandardTags.gradient(), StandardTags.rainbow(), StandardTags.reset())).build();

    private final SMPCore plugin;

    public StaffCommands(SMPCore plugin) { this.plugin = plugin; }

    private boolean can(CommandSender s, String node) {
        return s.hasPermission("smpcore.cmd." + node) || s.hasPermission("smpcore.admin");
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase(Locale.ROOT);
        switch (n) {
            case "mute" -> { mute(s, a); return true; }
            case "unmute" -> {
                if (!can(s, "mute")) { Msg.error(s, "Keine Rechte."); return true; }
                PlayerData d = a.length > 0 ? plugin.data().find(a[0]) : null;
                if (d == null) { Msg.error(s, "Nutzung: /unmute <Spieler>"); return true; }
                Msg.send(s, plugin.staff().unmute(d.uuid) ? "<green>" + Msg.esc(d.name) + " ist nicht mehr gemutet." : "<red>Der Spieler ist nicht gemutet.");
                return true;
            }
            case "clearchat" -> {
                if (!can(s, "clearchat")) { Msg.error(s, "Keine Rechte."); return true; }
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (can(o, "clearchat")) continue;
                    for (int i = 0; i < 100; i++) o.sendMessage(Component.empty());
                }
                Bukkit.broadcast(Msg.c("<gray>Der Chat wurde vom Team geleert."));
                return true;
            }
            case "smptools" -> {
                if (a.length > 0 && a[0].equalsIgnoreCase("give")) { giveTool(s, a); return true; }
            }
            default -> { }
        }
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        switch (n) {
            case "smptools" -> {
                if (!can(p, "smptools")) { Msg.error(p, "Keine Rechte."); return true; }
                new SmpToolsMenu.Main(p).open();
            }
            case "invsee" -> {
                if (!can(p, "invsee")) { Msg.error(p, "Keine Rechte."); return true; }
                Player t = a.length > 0 ? Players.online(a[0]) : null;
                if (t == null || t.equals(p)) { Msg.error(p, "Nutzung: /invsee <Spieler> (muss online sein)"); return true; }
                p.openInventory(t.getInventory());
            }
            case "ecsee" -> {
                if (!can(p, "ecsee")) { Msg.error(p, "Keine Rechte."); return true; }
                PlayerData d = a.length > 0 ? plugin.data().find(a[0]) : null;
                if (d == null) { Msg.error(p, "Nutzung: /ecsee <Spieler>"); return true; }
                plugin.enderChests().open(p, d.uuid, 6);
            }
            case "gm" -> gm(p, a);
            case "freeze" -> {
                if (!can(p, "freeze")) { Msg.error(p, "Keine Rechte."); return true; }
                Player t = a.length > 0 ? Players.online(a[0]) : null;
                if (t == null) { Msg.error(p, "Nutzung: /freeze <Spieler>"); return true; }
                boolean on = plugin.staff().frozen().add(t.getUniqueId());
                if (!on) plugin.staff().frozen().remove(t.getUniqueId());
                Msg.send(p, "<white>" + Msg.esc(t.getName()) + " <gray>ist jetzt " + (on ? "<red>eingefroren" : "<green>wieder frei") + "<gray>.");
                Msg.send(t, on ? "<red>Du wurdest vom Team eingefroren! Bleib bitte stehen." : "<green>Du bist wieder frei.");
            }
            case "skull" -> {
                if (!can(p, "skull")) { Msg.error(p, "Dafür brauchst du einen höheren Rang."); return true; }
                if (a.length == 0) { Msg.error(p, "Nutzung: /skull <Spielername>"); return true; }
                PlayerData d = plugin.data().find(a[0]);
                UUID id = d != null ? d.uuid : Bukkit.getOfflinePlayer(a[0]).getUniqueId();
                String name = d != null ? d.name : a[0];
                p.getInventory().addItem(Items.head(id, "<yellow>Kopf von " + Msg.esc(name), List.of())).values()
                        .forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
                Msg.send(p, "<green>Kopf von " + Msg.esc(name) + " erhalten.");
            }
            case "rename" -> {
                if (!can(p, "rename")) { Msg.error(p, "Dafür brauchst du einen höheren Rang."); return true; }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir() || a.length == 0) { Msg.error(p, "Nutzung: /rename <Name> (Item in der Hand, Farben mit <red> usw.)"); return true; }
                String text = String.join(" ", a);
                if (text.replaceAll("<[^>]*>", "").length() > 40) { Msg.error(p, "Der Name darf maximal 40 Zeichen lang sein."); return true; }
                ItemMeta m = hand.getItemMeta();
                m.displayName(Component.empty().decoration(TextDecoration.ITALIC, false).append(SAFE.deserialize(text)));
                hand.setItemMeta(m);
                Msg.send(p, "<green>Item umbenannt!");
            }
            case "ptime" -> {
                if (!can(p, "ptime")) { Msg.error(p, "Dafür brauchst du einen höheren Rang."); return true; }
                String v = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "";
                switch (v) {
                    case "day" -> p.setPlayerTime(1000, false);
                    case "noon" -> p.setPlayerTime(6000, false);
                    case "night" -> p.setPlayerTime(13000, false);
                    case "midnight" -> p.setPlayerTime(18000, false);
                    case "reset" -> p.resetPlayerTime();
                    default -> { Msg.error(p, "Nutzung: /ptime <day|noon|night|midnight|reset>"); return true; }
                }
                Msg.send(p, "<green>Deine Zeit: " + v);
            }
            default -> { }
        }
        return true;
    }

    private void gm(Player p, String[] a) {
        if (!can(p, "gm")) { Msg.error(p, "Keine Rechte."); return; }
        if (a.length == 0) { Msg.error(p, "Nutzung: /gm <0|1|2|3> [Spieler]"); return; }
        GameMode mode = switch (a[0].toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
        if (mode == null) { Msg.error(p, "Modus: 0 (Survival), 1 (Creative), 2 (Adventure), 3 (Spectator)"); return; }
        Player t = a.length > 1 ? Players.online(a[1]) : p;
        if (t == null) { Msg.error(p, "Spieler nicht gefunden."); return; }
        t.setGameMode(mode);
        Msg.send(p, "<white>" + Msg.esc(t.getName()) + " <gray>ist jetzt im Modus <green>" + mode.name().toLowerCase(Locale.ROOT) + "<gray>.");
    }

    private void mute(CommandSender s, String[] a) {
        if (!can(s, "mute")) { Msg.error(s, "Keine Rechte."); return; }
        PlayerData d = a.length > 0 ? plugin.data().find(a[0]) : null;
        if (d == null) { Msg.error(s, "Nutzung: /mute <Spieler> [Minuten|perm] [Grund]"); return; }
        long until = 0;
        int ri = 1;
        if (a.length > 1) {
            try {
                long m = Long.parseLong(a[1]);
                until = m > 0 ? System.currentTimeMillis() + m * 60_000L : 0;
                ri = 2;
            } catch (NumberFormatException ex) {
                if (a[1].equalsIgnoreCase("perm")) ri = 2;
            }
        }
        String reason = a.length > ri ? String.join(" ", Arrays.copyOfRange(a, ri, a.length)) : "kein Grund angegeben";
        plugin.staff().mute(d.uuid, until);
        plugin.punish().log(d.uuid, "MUTE", s instanceof Player mp ? mp.getName() : "Konsole", reason, until);
        String dur = until == 0 ? "dauerhaft" : Num.duration(until - System.currentTimeMillis());
        Msg.send(s, "<white>" + Msg.esc(d.name) + " <gray>wurde gemutet (<white>" + dur + "<gray>): " + Msg.esc(reason));
        Player t = Bukkit.getPlayer(d.uuid);
        if (t != null) t.sendMessage(Msg.c("<red>Du wurdest gemutet (" + dur + "). Grund: " + Msg.esc(reason)));
    }

    /** /smptools give <Spieler> <Tool> [Zeit] [Anzahl]  (Zeit z. B. 3d, 12h, 1d12h, perm; Standard: tools.default-duration) */
    private void giveTool(CommandSender s, String[] a) {
        if (!can(s, "smptools")) { Msg.error(s, "Keine Rechte."); return; }
        String usage = "Nutzung: /smptools give <Spieler> <Tool> [Zeit z. B. 3d|12h|perm] [Anzahl]";
        if (a.length < 3) { Msg.error(s, usage); return; }
        Player t = Players.online(a[1]);
        if (t == null) { Msg.error(s, "Spieler muss online sein."); return; }
        var tool = plugin.tools().get(a[2]);
        if (tool == null) { Msg.error(s, "Unbekanntes Tool. Verfügbar: " + String.join(", ", plugin.tools().all().stream().map(x -> x.id()).toList())); return; }
        long ms = plugin.tools().defaultDurationMs();
        if (a.length > 3) {
            ms = de.smpcore.tools.ToolManager.parseDuration(a[3]);
            if (ms < 0) { Msg.error(s, "Ungültige Zeit. Beispiele: 3d, 12h, 1d12h, 30m oder perm. " + usage); return; }
        }
        int amount = 1;
        if (a.length > 4) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(a[4]))); } catch (NumberFormatException ex) { Msg.error(s, "Ungültige Anzahl."); return; }
        }
        long exp = ms > 0 ? System.currentTimeMillis() + ms : 0L;
        for (int i = 0; i < amount; i++) {
            t.getInventory().addItem(plugin.tools().create(tool, 1, exp)).values().forEach(it -> t.getWorld().dropItemNaturally(t.getLocation(), it));
        }
        String dur = ms > 0 ? "läuft in " + de.smpcore.tools.ToolManager.fmtDuration(ms) + " ab" : "unbegrenzt";
        Msg.ok(s, amount + "x " + tool.id() + " an " + Msg.esc(t.getName()) + " gegeben (" + dur + ").");
        if (!s.equals(t)) Msg.send(t, "<gray>Du hast <white>" + tool.name() + " <gray>erhalten – " + (ms > 0 ? "<red>" + dur : "<green>unbegrenzt") + "<gray>.");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase(Locale.ROOT);
        if (a.length == 1 && List.of("invsee", "ecsee", "freeze", "mute", "unmute", "skull").contains(n)) return Players.names(a[0]);
        if (n.equals("smptools")) {
            if (a.length == 1) return Players.complete(a[0], List.of("give"));
            if (a.length == 2) return Players.names(a[1]);
            if (a.length == 3) return Players.complete(a[2], plugin.tools().all().stream().map(x -> x.id()).toList());
            if (a.length == 4) return Players.complete(a[3], List.of("3d", "1d", "7d", "12h", "perm"));
        }
        if (a.length == 1 && n.equals("gm")) return Players.complete(a[0], List.of("0", "1", "2", "3"));
        if (a.length == 2 && n.equals("gm")) return Players.names(a[1]);
        if (a.length == 1 && n.equals("ptime")) return Players.complete(a[0], List.of("day", "noon", "night", "midnight", "reset"));
        return List.of();
    }
}
