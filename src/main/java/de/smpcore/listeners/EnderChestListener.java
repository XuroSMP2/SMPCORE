package de.smpcore.listeners;

import de.smpcore.SMPCore;
import de.smpcore.enderchest.EnderChestManager;
import de.smpcore.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class EnderChestListener implements Listener {
    private final SMPCore plugin;

    public EnderChestListener(SMPCore plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.ENDER_CHEST) return;
        Player p = e.getPlayer();
        if (p.isSneaking() && !p.getInventory().getItemInMainHand().getType().isAir()) return;
        e.setCancelled(true);
        Msg.use("ec");
        try { plugin.enderChests().open(p, p.getUniqueId(), plugin.enderChests().rows(p)); } finally { Msg.reset(); }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder(false) instanceof EnderChestManager.Holder h) plugin.enderChests().close(h);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { plugin.enderChests().importVanilla(e.getPlayer()); }
}
