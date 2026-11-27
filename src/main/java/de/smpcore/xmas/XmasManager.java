package de.smpcore.xmas;

import de.smpcore.SMPCore;
import de.smpcore.crate.CrateManager;
import de.smpcore.data.PlayerData;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weihnachts-Extras: Schneefall um die Spieler, Willkommens-Gruß, Weihnachts-Crate,
 * Bescherung an Heiligabend (18 Uhr) und Feuerwerk beim Türchen-Öffnen.
 * Die Saison läuft automatisch (Standard: 27.11. bis 06.01.).
 */
public final class XmasManager implements Listener {
    public static final String SNOW_SETTING = "xmas-snow";
    private final SMPCore plugin;

    public XmasManager(SMPCore plugin) { this.plugin = plugin; }

    public boolean enabled() { return plugin.getConfig().getBoolean("xmas.enabled", true); }

    public boolean seasonActive() {
        if (!enabled()) return false;
        LocalDate d = plugin.advent().today();
        int m = d.getMonthValue(), t = d.getDayOfMonth();
        return (m == 11 && t >= 27) || m == 12 || (m == 1 && t <= 6);
    }

    public boolean snowOn(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        return d == null || d.settings.getOrDefault(SNOW_SETTING, true);
    }

    public boolean toggleSnow(Player p) {
        PlayerData d = plugin.data().getOrCreate(p.getUniqueId(), p.getName());
        boolean now = !d.settings.getOrDefault(SNOW_SETTING, true);
        d.settings.put(SNOW_SETTING, now);
        return now;
    }

    public void start() {
        ensureCrate();
        Bukkit.getScheduler().runTaskTimer(plugin, this::snowTick, 40L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::bescherungTick, 20L * 30, 20L * 30);
    }

    /** Legt die Weihnachts-Crate an, falls sie in der crates.yml des Servers noch fehlt. */
    public void ensureCrate() {
        String id = plugin.getConfig().getString("xmas.crate", "weihnachten");
        if (plugin.crates().get(id) != null) return;
        CrateManager.Crate c = plugin.crates().create(id);
        if (c == null) return;
        c.name = "<red><bold>Weihnachts-Crate";
        c.keyName = "<red>Weihnachts-Key";
        c.icon = Material.CHEST;
        c.rewards.clear();
        c.rewards.add(CrateManager.money(5000, 28));
        c.rewards.add(CrateManager.money(25000, 10));
        c.rewards.add(CrateManager.crystals(5, 18));
        c.rewards.add(CrateManager.item(cookie(32), 14));
        c.rewards.add(CrateManager.item(new ItemStack(Material.DIAMOND, 6), 12));
        c.rewards.add(CrateManager.item(new ItemStack(Material.GOLDEN_APPLE, 6), 10));
        c.rewards.add(CrateManager.item(new ItemStack(Material.EMERALD, 24), 10));
        c.rewards.add(CrateManager.item(new ItemStack(Material.TOTEM_OF_UNDYING, 1), 4));
        c.rewards.add(CrateManager.item(new ItemStack(Material.NETHERITE_INGOT, 1), 3));
        c.rewards.add(CrateManager.item(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), 1.5));
        c.recalc();
        plugin.crates().save();
        plugin.getLogger().info("Weihnachts-Crate '" + id + "' wurde angelegt (Platzieren: /crate set " + id + ").");
    }

    private ItemStack cookie(int n) {
        ItemStack it = new ItemStack(Material.COOKIE, n);
        ItemMeta m = it.getItemMeta();
        if (m != null) { m.setCustomModelData(22); m.displayName(Msg.item("<gold>Weihnachtsplätzchen")); it.setItemMeta(m); }
        return it;
    }

    // ---------- Schnee ----------
    private void snowTick() {
        if (!seasonActive() || !plugin.getConfig().getBoolean("xmas.snow", true)) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!snowOn(p)) continue;
            Location l = p.getLocation();
            for (int i = 0; i < 10; i++) {
                double x = l.getX() + r.nextDouble(-8, 8), z = l.getZ() + r.nextDouble(-8, 8), y = l.getY() + r.nextDouble(1, 7);
                p.spawnParticle(Particle.SNOWFLAKE, x, y, z, 0, 0, -0.05, 0, 1.0);
            }
        }
    }

    // ---------- Join ----------
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!seasonActive()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            Msg.use("xmas");
            try {
                long left = plugin.advent().daysUntilChristmasEve();
                if (left > 0 && left <= 31) Msg.send(p, "<red>Frohe Weihnachtszeit! <gray>Noch <white>" + left + (left == 1 ? " Tag" : " Tage") + " <gray>bis Heiligabend. " + "<dark_gray>(/xmas)");
                else if (left == 0) Msg.send(p, "<red>Heiligabend! <gray>Um <white>18 Uhr <gray>gibt es die Bescherung für alle, die online sind.");
                else Msg.send(p, "<red>Frohe Weihnachten! <gray>Schön, dass du da bist.");
            } finally { Msg.reset(); }
            plugin.advent().joinGreeting(p);
        }, 100L);
    }

    // ---------- Bescherung ----------
    private void bescherungTick() {
        if (!seasonActive()) return;
        LocalDate d = plugin.advent().today();
        if (d.getMonthValue() != 12 || d.getDayOfMonth() != 24) return;
        LocalTime now = java.time.LocalTime.now(plugin.advent().zone());
        String t = plugin.getConfig().getString("xmas.bescherung-time", "18:00");
        LocalTime at;
        try { at = LocalTime.parse(t); } catch (Exception ex) { at = LocalTime.of(18, 0); }
        boolean test = !plugin.getConfig().getString("advent.test-date", "").isBlank();
        if (!test && now.isBefore(at)) return;
        if (plugin.advent().flag("bescherung")) return;
        plugin.advent().setFlag("bescherung");
        String crate = plugin.getConfig().getString("xmas.crate", "weihnachten");
        int keys = Math.max(1, plugin.getConfig().getInt("xmas.bescherung-keys", 3));
        long gems = plugin.getConfig().getLong("xmas.bescherung-gems", 25);
        Bukkit.broadcast(Msg.c("<red><bold>🎄 BESCHERUNG! <gray>Frohe Weihnachten, XuroSMP!"));
        Msg.use("xmas");
        try {
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.crates().addKeys(p.getUniqueId(), crate, keys);
                if (gems > 0) plugin.economy().addGems(p.getUniqueId(), gems);
                Msg.send(p, "<gray>Unterm Baum lag für dich: <white>" + keys + "x Weihnachts-Key" + (gems > 0 ? " <gray>und <aqua>" + gems + " Gems" : "") + "<gray>!");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
                firework(p.getLocation());
            }
        } finally { Msg.reset(); }
    }

    // ---------- Effekte ----------
    /** Kleines Feuerwerk in Weihnachtsfarben (ohne Schaden). */
    public void celebrate(Player p) {
        firework(p.getLocation().add(0, 1, 0));
        p.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1.5, 0), 40, 0.8, 0.8, 0.8, 0.02);
    }

    /** Partikel-Feuerwerk in Weihnachtsfarben (ohne Explosion, also ohne Schaden). */
    private void firework(Location l) {
        if (l.getWorld() == null) return;
        l.getWorld().spawnParticle(Particle.FIREWORK, l, 50, 1.0, 1.0, 1.0, 0.12);
        l.getWorld().spawnParticle(Particle.DUST, l, 30, 1.0, 1.0, 1.0, new Particle.DustOptions(Color.RED, 1.5f));
        l.getWorld().spawnParticle(Particle.DUST, l, 30, 1.0, 1.0, 1.0, new Particle.DustOptions(Color.fromRGB(40, 180, 60), 1.5f));
        l.getWorld().playSound(l, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1f);
        l.getWorld().playSound(l, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1f);
    }
}
