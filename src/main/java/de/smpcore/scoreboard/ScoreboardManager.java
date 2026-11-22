package de.smpcore.scoreboard;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.util.Msg;
import de.smpcore.util.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScoreboardManager {
    private static final class Board {
        final Scoreboard sb; final int lines;
        final Map<String, String> shown = new HashMap<>(); // Name -> angezeigter Kontostand
        Board(Scoreboard sb, int lines) { this.sb = sb; this.lines = lines; }
        Scoreboard sb() { return sb; }
        int lines() { return lines; }
    }
    private int tickCount;

    private final SMPCore plugin;
    private final Map<UUID, Board> boards = new HashMap<>();
    private BukkitTask task;

    public ScoreboardManager(SMPCore plugin) { this.plugin = plugin; }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(), 20L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) remove(p);
    }

    /** Alle aktiven Spieler-Scoreboards (für die TAB-Sortierung). */
    public Collection<Scoreboard> boards() {
        List<Scoreboard> l = new ArrayList<>();
        for (Board b : boards.values()) l.add(b.sb());
        return l;
    }

    public void remove(Player p) {
        if (boards.remove(p.getUniqueId()) != null && p.isOnline()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private Board create(Player p, int lines) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("smp", Criteria.DUMMY, Msg.c("SMP"));
        if (lines > 0) o.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < lines; i++) {
            String entry = "\u00a7" + "0123456789abcdef".charAt(i) + "\u00a7r";
            Team t = sb.registerNewTeam("l" + i);
            t.addEntry(entry);
            o.getScore(entry).setScore(lines - i);
        }
        p.setScoreboard(sb);
        return new Board(sb, lines);
    }

    private void tick() {
        boolean on = plugin.feature("scoreboard");
        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        int n = Math.min(lines.size(), 15);
        String title = plugin.getConfig().getString("scoreboard.title", "SMP");
        boolean balOn = plugin.feature("settings") && plugin.getConfig().getBoolean("settings.balance-below-name", true);
        boolean doBal = balOn && (tickCount++ % 3 == 0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data().get(p.getUniqueId());
            boolean side = on && d != null && !d.scoreboardOff && n > 0;
            boolean see = balOn && plugin.settings().on(p.getUniqueId(), "balance_see");
            if (d == null || (!side && !see)) { remove(p); continue; }
            int want = side ? n : 0;
            Board b = boards.get(p.getUniqueId());
            if (b == null || b.lines() != want) {
                b = create(p, want);
                boards.put(p.getUniqueId(), b);
            } else if (p.getScoreboard() != b.sb()) {
                p.setScoreboard(b.sb());
            }
            if (side) {
                Objective o = b.sb().getObjective("smp");
                if (o != null) o.displayName(Msg.c(Placeholders.apply(title, p)));
                for (int i = 0; i < n; i++) {
                    Team t = b.sb().getTeam("l" + i);
                    if (t != null) t.prefix(Msg.c(Placeholders.apply(lines.get(i), p)));
                }
            }
            if (see && (doBal || b.shown.isEmpty())) balances(b);
        }
    }

    /** Kontostand unter den Namen aller Spieler (BELOW_NAME), mit festem Text statt Zahl. */
    private void balances(Board b) {
        Scoreboard sb = b.sb();
        Objective bo = sb.getObjective("bal");
        if (bo == null) {
            bo = sb.registerNewObjective("bal", Criteria.DUMMY, net.kyori.adventure.text.Component.empty());
            bo.setDisplaySlot(DisplaySlot.BELOW_NAME);
            b.shown.clear();
        }
        java.util.Set<String> online = new java.util.HashSet<>();
        for (Player t : Bukkit.getOnlinePlayers()) {
            String name = t.getName();
            online.add(name);
            PlayerData td = plugin.data().get(t.getUniqueId());
            boolean vis = td != null && plugin.settings().on(t.getUniqueId(), "balance_show") && !plugin.vanished().contains(t.getUniqueId());
            if (!vis) { if (b.shown.remove(name) != null) sb.resetScores(name); continue; }
            String txt = de.smpcore.economy.EconomyManager.fmt(td.money);
            if (!txt.equals(b.shown.get(name))) {
                Score sc = bo.getScore(name);
                sc.setScore(0);
                sc.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.fixed(Msg.c("<gold>" + Msg.esc(txt))));
                b.shown.put(name, txt);
            }
        }
        b.shown.keySet().removeIf(k -> { if (online.contains(k)) return false; sb.resetScores(k); return true; });
    }
}
