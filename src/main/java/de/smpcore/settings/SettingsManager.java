package de.smpcore.settings;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;

/** Persönliche Einstellungen (/settings). Gespeichert in PlayerData.settings. */
public final class SettingsManager implements Listener {
    public record Def(String id, Material icon, String name, List<String> lore, boolean def, String permission) {}

    private final SMPCore plugin;
    private final List<Def> defs = List.of(
            new Def("mobspawns", Material.ZOMBIE_HEAD, "Monster-Spawns",
                    List.of("<gray>Aus: In deiner Nähe spawnen keine", "<gray>feindlichen Mobs (nur wenn alle Spieler", "<gray>dort das ausgeschaltet haben)."), true, null),
            new Def("scoreboard", Material.OAK_SIGN, "Scoreboard",
                    List.of("<gray>Die Seitenleiste mit deinen Stats."), true, null),
            new Def("balance_show", Material.GOLD_INGOT, "Kontostand unter meinem Namen",
                    List.of("<gray>Andere sehen dein Geld unter deinem Namen."), true, null),
            new Def("balance_see", Material.SPYGLASS, "Kontostände anderer sehen",
                    List.of("<gray>Zeigt das Geld anderer Spieler", "<gray>unter ihren Namen."), true, null),
            new Def("msg", Material.WRITABLE_BOOK, "Private Nachrichten",
                    List.of("<gray>Aus: niemand kann dir per /msg schreiben", "<gray>(das Team schon)."), true, null),
            new Def("tpa", Material.ENDER_PEARL, "TP-Anfragen",
                    List.of("<gray>Aus: niemand kann dir /tpa oder", "<gray>/tpahere schicken."), true, null),
            new Def("pay", Material.EMERALD, "Geld empfangen",
                    List.of("<gray>Aus: niemand kann dir per /pay", "<gray>Geld oder Stars senden (auch nicht /pay *)."), true, null),
            new Def("nightvision", Material.ENDER_EYE, "Nachtsicht",
                    List.of("<gray>Dauerhafte Nachtsicht.", "<dark_gray>Nur mit Berechtigung."), false, "smpcore.settings.nightvision")
    );

    public SettingsManager(SMPCore plugin) { this.plugin = plugin; }

    public List<Def> all() { return defs; }

    public Def get(String id) { for (Def d : defs) if (d.id().equals(id)) return d; return null; }

    public boolean on(UUID id, String key) {
        PlayerData d = plugin.data().get(id);
        if (d == null) return true;
        if (key.equals("scoreboard")) return !d.scoreboardOff;
        Def def = get(key);
        Boolean v = d.settings.get(key);
        return v != null ? v : (def == null || def.def());
    }

    public void set(Player p, String key, boolean value) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return;
        if (key.equals("scoreboard")) d.scoreboardOff = !value;
        else d.settings.put(key, value);
        if (key.equals("nightvision")) applyNightVision(p);
    }

    public boolean toggle(Player p, String key) {
        boolean n = !on(p.getUniqueId(), key);
        set(p, key, n);
        return n;
    }

    public void applyNightVision(Player p) {
        boolean want = on(p.getUniqueId(), "nightvision") && p.hasPermission("smpcore.settings.nightvision");
        if (want) p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        else {
            PotionEffect e = p.getPotionEffect(PotionEffectType.NIGHT_VISION);
            if (e != null && e.getDuration() == PotionEffect.INFINITE_DURATION) p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.feature("settings")) return;
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline() && on(e.getPlayer().getUniqueId(), "nightvision")) applyNightVision(e.getPlayer());
        }, 20L);
    }

    /** Feindliche Mobs spawnen nicht, wenn alle Spieler in der Nähe "Monster-Spawns" ausgeschaltet haben. */
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!plugin.feature("settings") || !(e.getEntity() instanceof Enemy)) return;
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r != CreatureSpawnEvent.SpawnReason.NATURAL && r != CreatureSpawnEvent.SpawnReason.REINFORCEMENTS) return;
        double radius = plugin.getConfig().getDouble("settings.mobspawn-radius", 64);
        boolean any = false;
        for (Player p : e.getLocation().getNearbyPlayers(radius)) {
            any = true;
            if (on(p.getUniqueId(), "mobspawns")) return;
        }
        if (any) e.setCancelled(true);
    }
}
