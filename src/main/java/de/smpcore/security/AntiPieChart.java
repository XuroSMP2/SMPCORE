package de.smpcore.security;

import de.smpcore.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Abmilderung gegen Base-Finding per F3-Piechart (Shift+F3) und F3-Koordinaten.
 * Das Piechart läuft im Client und zeigt Daten der Chunks, die der Client geladen hat.
 * Serverseitig gehen nur: reducedDebugInfo (keine Koordinaten/Chunk-Infos in F3) und eine begrenzte Sichtweite
 * (je weniger Chunks der Client bekommt, desto weniger Block-Entities kann er auswerten).
 */
public final class AntiPieChart implements Listener {
    private final SMPCore plugin;
    public AntiPieChart(SMPCore plugin) { this.plugin = plugin; }

    private boolean on() { return plugin.getConfig().getBoolean("anti-piechart.enabled", true); }

    /** Beim Start und bei /reload aufrufen. */
    public void apply() {
        for (World w : Bukkit.getWorlds()) world(w);
        for (Player p : Bukkit.getOnlinePlayers()) player(p);
    }

    private void world(World w) {
        // Das Plugin verwaltet die Regel komplett: an = F3 ohne Koordinaten, aus = F3 normal (auch nach dem Ausschalten, sonst bliebe sie in der Welt stehen).
        boolean hide = on() && plugin.getConfig().getBoolean("anti-piechart.reduced-debug-info", true);
        w.setGameRule(GameRule.REDUCED_DEBUG_INFO, hide);
    }

    private void player(Player p) {
        if (!on()) return;
        int cap = plugin.getConfig().getInt("anti-piechart.max-view-distance", 10);
        if (cap < 2 || p.hasPermission("smpcore.admin") || p.hasPermission("smpcore.antipiechart.bypass")) return;
        try {
            if (p.getSendViewDistance() > cap) p.setSendViewDistance(cap);
            if (p.getViewDistance() > cap) p.setViewDistance(cap);
        } catch (Throwable ignored) { /* ältere API */ }
    }

    @EventHandler public void onWorld(WorldLoadEvent e) { world(e.getWorld()); }
    @EventHandler public void onJoin(PlayerJoinEvent e) { Bukkit.getScheduler().runTaskLater(plugin, () -> player(e.getPlayer()), 20L); }
}
