package de.smpcore.afk;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Platzieren/Abbauen der AFK-Blöcke (Upgrade "AFK_BLOCK"). */
public final class AfkBlockListener implements Listener {
    private final SMPCore plugin;
    public AfkBlockListener(SMPCore plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!plugin.afk().isBlockItem(e.getItemInHand())) return;
        var p = e.getPlayer();
        int allowed = plugin.upgrades().bonus(p.getUniqueId(), "AFK_BLOCK");
        if (p.hasPermission("smpcore.admin")) allowed = Math.max(allowed, 1000);
        if (plugin.afk().blocksOf(p.getUniqueId()) >= allowed) {
            e.setCancelled(true);
            Msg.error(p, "Du darfst nicht mehr AFK-Blöcke platzieren. Kaufe Stufen im <white>/upgrades<red>-Menü.");
            return;
        }
        plugin.afk().addBlock(e.getBlockPlaced().getLocation(), p.getUniqueId());
        Msg.ok(p, "AFK-Block platziert. Stell dich drauf, um Stars zu bekommen.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        var loc = e.getBlock().getLocation();
        if (!plugin.afk().isAfkBlock(loc)) return;
        plugin.afk().removeBlock(loc);
        e.setDropItems(false);
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) loc.getWorld().dropItemNaturally(loc.add(0.5, 0.5, 0.5), plugin.afk().blockItem());
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) { e.blockList().removeIf(b -> plugin.afk().isAfkBlock(b.getLocation())); }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) { e.blockList().removeIf(b -> plugin.afk().isAfkBlock(b.getLocation())); }
}
