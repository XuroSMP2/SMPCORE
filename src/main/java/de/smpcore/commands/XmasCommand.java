package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.advent.AdventManager;
import de.smpcore.gui.AdventMenu;
import de.smpcore.util.Msg;
import de.smpcore.util.Players;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /advent (Adventskalender), /xmas (Weihnachts-Infos, Schnee an/aus), /gift (Geschenk an Spieler). */
public final class XmasCommand implements TabExecutor {
    private final SMPCore plugin;
    private final Map<UUID, Long> giftCd = new HashMap<>();

    public XmasCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String n = cmd.getName().toLowerCase();
        if (n.equals("advent")) return advent(s, a);
        if (!(s instanceof Player p)) { Msg.error(s, "Nur im Spiel."); return true; }
        if (n.equals("gift")) return gift(p, a);
        // /xmas
        if (a.length > 0 && a[0].equalsIgnoreCase("snow")) {
            boolean on = plugin.xmas().toggleSnow(p);
            Msg.ok(p, "Schneefall um dich herum: " + (on ? "an" : "aus") + ".");
            return true;
        }
        long left = plugin.advent().daysUntilChristmasEve();
        Msg.send(p, "<red>Weihnachten auf XuroSMP");
        p.sendMessage(Msg.c("<dark_gray>  • <gray>Heiligabend: " + (left > 0 ? "in <white>" + left + " Tagen" : left == 0 ? "<white>heute!" : "<white>vorbei")));
        p.sendMessage(Msg.c("<dark_gray>  • <white>/advent <gray>– Adventskalender (1.–24. Dezember, jeden Tag ein Türchen)"));
        p.sendMessage(Msg.c("<dark_gray>  • <white>/gift <Spieler> <gray>– verschenke den Gegenstand in deiner Hand"));
        p.sendMessage(Msg.c("<dark_gray>  • <white>/xmas snow <gray>– Schneefall um dich an/aus"));
        p.sendMessage(Msg.c("<dark_gray>  • <gray>Heiligabend, 18 Uhr: Bescherung für alle, die online sind"));
        return true;
    }

    private boolean advent(CommandSender s, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("admin")) {
            if (!s.hasPermission("smpcore.admin")) { Msg.error(s, "Keine Berechtigung."); return true; }
            return admin(s, a);
        }
        if (!(s instanceof Player p)) { Msg.error(s, "Nur im Spiel."); return true; }
        if (!plugin.advent().enabled()) { Msg.error(p, "Der Adventskalender ist ausgeschaltet."); return true; }
        new AdventMenu(p).open();
        return true;
    }

    private boolean admin(CommandSender s, String[] a) {
        if (a.length >= 3 && a[1].equalsIgnoreCase("reset")) {
            Player t = Bukkit.getPlayerExact(a[2]);
            if (t == null) { Msg.error(s, "Spieler nicht online."); return true; }
            plugin.advent().reset(t.getUniqueId());
            Msg.ok(s, "Kalender von " + t.getName() + " zurückgesetzt.");
            return true;
        }
        if (a.length >= 3 && a[1].equalsIgnoreCase("date")) {
            String v = a[2].equalsIgnoreCase("off") ? "" : a[2];
            plugin.getConfig().set("advent.test-date", v);
            plugin.saveConfig();
            Msg.ok(s, v.isEmpty() ? "Testdatum aus – es gilt das echte Datum." : "Testdatum gesetzt: " + v + " (Format JJJJ-MM-TT).");
            return true;
        }
        Msg.send(s, "<gray>/advent admin reset <Spieler>  |  /advent admin date <JJJJ-MM-TT|off>");
        return true;
    }

    private boolean gift(Player p, String[] a) {
        if (!plugin.getConfig().getBoolean("xmas.gift-enabled", true)) { Msg.error(p, "Geschenke sind ausgeschaltet."); return true; }
        if (a.length < 1) { Msg.error(p, "Benutzung: /gift <Spieler> (Gegenstand in die Hand nehmen)"); return true; }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null || !t.isOnline()) { Msg.error(p, "Dieser Spieler ist nicht online."); return true; }
        if (t.equals(p)) { Msg.error(p, "Du kannst dich nicht selbst beschenken."); return true; }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) { Msg.error(p, "Nimm den Gegenstand, den du verschenken willst, in die Hand."); return true; }
        long now = System.currentTimeMillis();
        if (now - giftCd.getOrDefault(p.getUniqueId(), 0L) < 5000) { Msg.error(p, "Langsam – warte kurz."); return true; }
        giftCd.put(p.getUniqueId(), now);
        ItemStack give = hand.clone();
        p.getInventory().setItemInMainHand(null);
        t.getInventory().addItem(give).values().forEach(l -> t.getWorld().dropItemNaturally(t.getLocation(), l));
        Msg.ok(p, "Du hast " + t.getName() + " ein Geschenk gemacht. Frohes Fest!");
        Msg.send(t, "<red>🎁 <white>" + Msg.esc(p.getName()) + " <gray>schenkt dir <white>" + give.getAmount() + "x " + de.smpcore.util.Items.pretty(give.getType()) + "<gray>!");
        t.playSound(t.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        t.spawnParticle(Particle.HEART, t.getLocation().add(0, 2, 0), 8, 0.5, 0.3, 0.5);
        p.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1.5, 0), 20, 0.6, 0.6, 0.6, 0.02);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        String n = cmd.getName().toLowerCase();
        if (a.length == 1 && n.equals("gift")) return Players.names(a[0]);
        if (a.length == 1 && n.equals("xmas")) return Players.complete(a[0], List.of("snow"));
        if (n.equals("advent") && s.hasPermission("smpcore.admin")) {
            if (a.length == 1) return Players.complete(a[0], List.of("admin"));
            if (a.length == 2) return Players.complete(a[1], List.of("reset", "date"));
            if (a.length == 3 && a[1].equalsIgnoreCase("reset")) return Players.names(a[2]);
        }
        return List.of();
    }
}
