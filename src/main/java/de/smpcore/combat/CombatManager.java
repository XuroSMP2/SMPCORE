package de.smpcore.combat;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

/**
 * Combat-Tag: Wer einen Spieler angreift oder angegriffen wird, ist für X Sekunden im Kampf.
 * Im Kampf sind Befehle (und damit Teleports/Menüs) gesperrt, wer sich ausloggt, stirbt und verliert seine Items.
 */
public final class CombatManager implements Listener {
    private final SMPCore plugin;
    private final Map<UUID, Long> until = new HashMap<>();
    private final Map<UUID, UUID> opponent = new HashMap<>();

    public CombatManager(SMPCore plugin) { this.plugin = plugin; }

    private boolean on() { return plugin.feature("combat"); }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public boolean isTagged(Player p) {
        Long t = until.get(p.getUniqueId());
        return t != null && t > System.currentTimeMillis();
    }

    public int secondsLeft(Player p) {
        Long t = until.get(p.getUniqueId());
        return t == null ? 0 : (int) Math.max(0, (t - System.currentTimeMillis() + 999) / 1000);
    }

    public void clear(Player p) {
        until.remove(p.getUniqueId());
        opponent.remove(p.getUniqueId());
    }

    private boolean exempt(Player p) {
        return p.hasPermission("smpcore.combat.bypass") || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR
                || plugin.vanished().contains(p.getUniqueId())
                || plugin.getConfig().getStringList("combat.disabled-worlds").contains(p.getWorld().getName());
    }

    private void tag(Player p, Player other) {
        if (exempt(p)) return;
        boolean was = isTagged(p);
        long ms = Math.max(3, plugin.getConfig().getInt("combat.duration-seconds", 15)) * 1000L;
        until.put(p.getUniqueId(), System.currentTimeMillis() + ms);
        opponent.put(p.getUniqueId(), other.getUniqueId());
        if (!was) {
            String prev = Msg.current();
            Msg.use("combat");
            try { Msg.send(p, "<red>Du bist im Kampf! <gray>Nicht ausloggen, Befehle sind gesperrt."); } finally { Msg.use(prev); }
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = until.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) { it.remove(); opponent.remove(e.getKey()); continue; }
            if (e.getValue() <= now) {
                it.remove();
                opponent.remove(e.getKey());
                p.sendActionBar(Msg.c("<green>Du bist nicht mehr im Kampf."));
            } else {
                p.sendActionBar(Msg.c("<red><bold>⚔ Im Kampf <white>" + (int) ((e.getValue() - now + 999) / 1000) + "s <gray>– nicht ausloggen!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!on() || !(e.getEntity() instanceof Player victim)) return;
        Entity d = e.getDamager();
        Player attacker = d instanceof Player pl ? pl : (d instanceof Projectile pr && pr.getShooter() instanceof Player sp ? sp : null);
        if (attacker == null || attacker.equals(victim) || e.getFinalDamage() <= 0) return;
        tag(victim, attacker);
        tag(attacker, victim);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!on() || !isTagged(p) || p.hasPermission("smpcore.combat.bypass")) return;
        String cmd = e.getMessage().substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
        int colon = cmd.indexOf(':');
        if (colon >= 0) cmd = cmd.substring(colon + 1);
        for (String ok : plugin.getConfig().getStringList("combat.allowed-commands")) if (ok.equalsIgnoreCase(cmd)) return;
        e.setCancelled(true);
        String prev = Msg.current();
        Msg.use("combat");
        try { Msg.error(p, "Im Kampf gesperrt! Noch " + secondsLeft(p) + "s."); } finally { Msg.use(prev); }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        boolean tagged = isTagged(p);
        UUID opp = opponent.get(p.getUniqueId());
        clear(p);
        if (!on() || !tagged || Bukkit.isStopping() || exempt(p)) return; // beim Serverstop niemanden bestrafen
        if (!plugin.getConfig().getBoolean("combat.kill-on-logout", true)) return;
        Player o = opp == null ? null : Bukkit.getPlayer(opp);
        Bukkit.broadcast(Msg.c("<dark_gray>[<red>⚔<dark_gray>] <red>" + Msg.esc(p.getName()) + " <gray>hat sich im Kampf ausgeloggt"
                + (o != null ? " <gray>(Gegner: <white>" + Msg.esc(o.getName()) + "<gray>)" : "") + "<gray>!"));
        p.setHealth(0); // Items droppen, Totems helfen nicht
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        until.remove(id);
        opponent.remove(id);
        // Wer nur gegen den Toten gekämpft hat, ist sofort wieder frei.
        for (Iterator<Map.Entry<UUID, UUID>> it = opponent.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, UUID> en = it.next();
            if (en.getValue().equals(id)) { until.remove(en.getKey()); it.remove(); }
        }
    }
}
