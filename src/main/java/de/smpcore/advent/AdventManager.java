package de.smpcore.advent;

import de.smpcore.SMPCore;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import de.smpcore.util.Persist;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Adventskalender: startet automatisch am 1. Dezember, 24 Türchen (1.-24.12.).
 * Ein Türchen kann NUR am eigenen Tag abgeholt werden – wer den Tag verpasst, geht leer aus.
 * Wer alle 24 Türchen geholt hat, bekommt am 24.12. zusätzlich den Treue-Bonus.
 */
public final class AdventManager {
    public enum Phase { BEFORE, RUNNING, ENDED, OFF }

    /** Eine einzelne Belohnung eines Türchens. */
    public record Reward(String type, String id, long amount, int model, String name) {
        public String label() {
            return switch (type) {
                case "money" -> "<gold>$" + Num.format(amount);
                case "crystals" -> "<light_purple>" + Num.format(amount) + " Stars";
                case "gems" -> "<aqua>" + Num.format(amount) + " Gems";
                case "key" -> "<yellow>" + amount + "x Key <gray>(" + id + ")";
                default -> "<white>" + amount + "x " + (name == null || name.isBlank() ? Items.pretty(Material.matchMaterial(id) == null ? Material.CHEST : Material.matchMaterial(id)) : name);
            };
        }
    }

    private final SMPCore plugin;
    private final File file;
    private YamlConfiguration data = new YamlConfiguration();
    private final Map<Integer, List<Reward>> days = new HashMap<>();
    private final List<Reward> perfect = new ArrayList<>();
    private LocalDate lastDay;
    private final Set<UUID> greeted = new HashSet<>();

    public AdventManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "advent-data.yml");
    }

    // ---------- Laden ----------
    public void load() {
        days.clear();
        perfect.clear();
        File f = new File(plugin.getDataFolder(), "advent.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection s = cfg.getConfigurationSection("days");
        if (s != null) for (String k : s.getKeys(false)) {
            int d;
            try { d = Integer.parseInt(k); } catch (NumberFormatException e) { continue; }
            List<Reward> list = new ArrayList<>();
            for (String line : s.getStringList(k)) {
                Reward r = parse(line);
                if (r != null) list.add(r);
            }
            days.put(d, list);
        }
        for (String line : cfg.getStringList("perfect")) {
            Reward r = parse(line);
            if (r != null) perfect.add(r);
        }
        data = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        lastDay = today();
    }

    /** "money 2000" | "crystals 3" | "gems 5" | "key weihnachten 1" | "item COOKIE 8 [cmd=22] [Name]" */
    private Reward parse(String line) {
        String[] t = line.trim().split("\\s+");
        try {
            switch (t[0].toLowerCase(Locale.ROOT)) {
                case "money", "crystals", "gems":
                    return new Reward(t[0].toLowerCase(Locale.ROOT), "", Long.parseLong(t[1]), 0, null);
                case "key":
                    return new Reward("key", t[1].toLowerCase(Locale.ROOT), Long.parseLong(t[2]), 0, null);
                case "item": {
                    if (Material.matchMaterial(t[1]) == null) { plugin.getLogger().warning("Advent: unbekanntes Item '" + t[1] + "'"); return null; }
                    int amt = Integer.parseInt(t[2]);
                    int model = 0;
                    StringBuilder name = new StringBuilder();
                    for (int i = 3; i < t.length; i++) {
                        if (t[i].startsWith("cmd=")) model = Integer.parseInt(t[i].substring(4));
                        else name.append(name.length() > 0 ? " " : "").append(t[i]);
                    }
                    return new Reward("item", t[1].toUpperCase(Locale.ROOT), amt, model, name.length() == 0 ? null : name.toString());
                }
                default:
                    plugin.getLogger().warning("Advent: unbekannte Belohnung '" + line + "'");
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Advent: Zeile nicht lesbar: '" + line + "'");
        }
        return null;
    }

    // ---------- Datum ----------
    public boolean enabled() { return plugin.getConfig().getBoolean("advent.enabled", true); }

    public ZoneId zone() {
        try { return ZoneId.of(plugin.getConfig().getString("advent.timezone", "Europe/Berlin")); }
        catch (Exception e) { return ZoneId.of("Europe/Berlin"); }
    }

    /** Heutiges Datum (zum Testen per advent.test-date überschreibbar, z.B. "2026-12-05"). */
    public LocalDate today() {
        String t = plugin.getConfig().getString("advent.test-date", "");
        if (t != null && !t.isBlank()) {
            try { return LocalDate.parse(t.trim()); } catch (Exception ignored) {}
        }
        return LocalDate.now(zone());
    }

    /** Das Jahr, zu dem der Kalender gehört (Januar zählt noch zum Vorjahr). */
    public int year() {
        LocalDate d = today();
        return d.getMonthValue() == 1 ? d.getYear() - 1 : d.getYear();
    }

    public Phase phase() {
        if (!enabled()) return Phase.OFF;
        LocalDate d = today();
        if (d.getMonthValue() == 12) return d.getDayOfMonth() <= 24 ? Phase.RUNNING : Phase.ENDED;
        if (d.getMonthValue() == 1) return Phase.ENDED;
        return Phase.BEFORE;
    }

    /** Das heute offene Türchen (1-24), sonst 0. */
    public int doorToday() { return phase() == Phase.RUNNING ? today().getDayOfMonth() : 0; }

    public long daysUntilStart() {
        LocalDate d = today();
        LocalDate start = LocalDate.of(d.getYear(), 12, 1);
        return Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(d, start));
    }

    public long daysUntilChristmasEve() {
        LocalDate d = today();
        LocalDate eve = LocalDate.of(d.getYear(), 12, 24);
        return java.time.temporal.ChronoUnit.DAYS.between(d, eve);
    }

    // ---------- Status ----------
    public List<Integer> claimed(UUID id) {
        return data.getIntegerList("claimed." + year() + "." + id);
    }

    public boolean isClaimed(UUID id, int day) { return claimed(id).contains(day); }

    public boolean canClaimToday(UUID id) {
        int d = doorToday();
        return d > 0 && !isClaimed(id, d) && days.containsKey(d);
    }

    public List<Reward> rewards(int day) { return days.getOrDefault(day, List.of()); }
    public List<Reward> perfectRewards() { return perfect; }

    // ---------- Abholen ----------
    public boolean claim(Player p, int day) {
        int today = doorToday();
        if (phase() == Phase.BEFORE) { Msg.error(p, "Der Adventskalender startet am 1. Dezember."); return false; }
        if (phase() != Phase.RUNNING) { Msg.error(p, "Der Adventskalender ist für dieses Jahr vorbei."); return false; }
        if (day != today) {
            Msg.error(p, day > today ? "Türchen " + day + " öffnet sich erst am " + day + ". Dezember."
                    : "Türchen " + day + " ist vorbei – es kann nur am jeweiligen Tag geöffnet werden.");
            return false;
        }
        if (isClaimed(p.getUniqueId(), day)) { Msg.error(p, "Dieses Türchen hast du heute schon geöffnet."); return false; }
        List<Reward> rw = rewards(day);
        if (rw.isEmpty()) { Msg.error(p, "Für dieses Türchen ist keine Belohnung eingestellt."); return false; }

        List<Integer> list = new ArrayList<>(claimed(p.getUniqueId()));
        list.add(day);
        data.set("claimed." + year() + "." + p.getUniqueId(), list);
        save();

        for (Reward r : rw) give(p, r);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        Msg.send(p, "<red>Türchen " + day + " <gray>geöffnet! Du hast erhalten:");
        for (Reward r : rw) p.sendMessage(Msg.c("<dark_gray>  • " + r.label()));
        plugin.xmas().celebrate(p);

        if (day == 24 && list.size() >= 24 && !perfect.isEmpty()) {
            Msg.send(p, "<gold><bold>TREUE-BONUS! <gray>Du hast alle 24 Türchen geöffnet:");
            for (Reward r : perfect) { give(p, r); p.sendMessage(Msg.c("<dark_gray>  • " + r.label())); }
            Bukkit.broadcast(Msg.c("<dark_gray>[<red>Advent<dark_gray>] <gold>" + Msg.esc(p.getName()) + " <gray>hat alle 24 Türchen geöffnet!"));
        }
        return true;
    }

    public void give(Player p, Reward r) {
        switch (r.type()) {
            case "money" -> plugin.economy().add(p.getUniqueId(), r.amount());
            case "crystals" -> plugin.economy().addCrystals(p.getUniqueId(), r.amount());
            case "gems" -> plugin.economy().addGems(p.getUniqueId(), r.amount());
            case "key" -> plugin.crates().addKeys(p.getUniqueId(), r.id(), (int) r.amount());
            default -> {
                Material m = Material.matchMaterial(r.id());
                if (m == null) return;
                int left = (int) r.amount();
                while (left > 0) {
                    int n = Math.min(left, m.getMaxStackSize());
                    ItemStack it = new ItemStack(m, n);
                    ItemMeta meta = it.getItemMeta();
                    if (meta != null) {
                        if (r.model() > 0) meta.setCustomModelData(r.model());
                        if (r.name() != null) meta.displayName(Msg.item(r.name()));
                        it.setItemMeta(meta);
                    }
                    p.getInventory().addItem(it).values().forEach(l -> p.getWorld().dropItemNaturally(p.getLocation(), l));
                    left -= n;
                }
            }
        }
    }

    // ---------- Benachrichtigung ----------
    public void remind(Player p) {
        if (!canClaimToday(p.getUniqueId())) return;
        int d = doorToday();
        Component c = Msg.c("<red><bold>ADVENT ▸ <gray>Türchen <white>" + d + " <gray>wartet auf dich! ")
                .append(Msg.c("<green><bold>[Jetzt öffnen]</bold>")
                        .clickEvent(ClickEvent.runCommand("/advent"))
                        .hoverEvent(HoverEvent.showText(Msg.c("<gray>Öffnet den Adventskalender"))))
                .append(Msg.c(" <dark_gray>(nur heute möglich)"));
        p.sendMessage(c);
    }

    public void joinGreeting(Player p) {
        Phase ph = phase();
        if (ph == Phase.RUNNING && canClaimToday(p.getUniqueId())) {
            remind(p);
            LocalDate t = today();
            if (greeted.add(p.getUniqueId())) {
                p.showTitle(Title.title(Msg.c("<red>🎄 <white>Türchen " + t.getDayOfMonth()), Msg.c("<gray>Heute wartet eine Überraschung – /advent"),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
            }
        } else if (ph == Phase.BEFORE && plugin.xmas().seasonActive()) {
            long n = daysUntilStart();
            Msg.use("advent");
            try { Msg.send(p, "<gray>Der Adventskalender startet in <white>" + n + (n == 1 ? " Tag" : " Tagen") + " <gray>(1. Dezember)."); }
            finally { Msg.reset(); }
        }
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            LocalDate t = today();
            if (lastDay != null && !t.equals(lastDay)) {
                lastDay = t;
                greeted.clear();
                if (phase() == Phase.RUNNING) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);
                        remind(p);
                    }
                }
            }
            lastDay = t;
        }, 20L * 30, 20L * 30);
        // Erinnerung für alle, die heute noch nicht abgeholt haben (alle 45 Minuten, nur wenn nötig)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (phase() != Phase.RUNNING) return;
            for (Player p : Bukkit.getOnlinePlayers()) remind(p);
        }, 20L * 60 * 45, 20L * 60 * 45);
    }

    // ---------- Admin ----------
    public void reset(UUID id) {
        data.set("claimed." + year() + "." + id, null);
        save();
    }

    public boolean flag(String key) { return data.getBoolean("flags." + year() + "." + key, false); }
    public void setFlag(String key) { data.set("flags." + year() + "." + key, true); save(); }

    private void save() {
        Persist.write(plugin, file, data.saveToString(), true);
    }
}
