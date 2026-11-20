package de.smpcore.listeners;

import de.smpcore.SMPCore;
import de.smpcore.tools.ToolManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ToolListener implements Listener {
    private final SMPCore plugin;
    private final Set<UUID> processing = new HashSet<>();

    public ToolListener(SMPCore plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (processing.contains(p.getUniqueId()) || p.isSneaking()) return; // Sneaken = nur 1 Block
        ItemStack hand = p.getInventory().getItemInMainHand();
        ToolManager.Tool tool = plugin.tools().of(hand);
        if (tool != null && tool.tree()) { fell(e, p, hand, tool); return; }
        if (tool == null || tool.size() <= 1) return;

        Block origin = e.getBlock();
        BlockFace face = BlockFace.UP;
        RayTraceResult rt = p.rayTraceBlocks(6.0);
        if (rt != null && rt.getHitBlockFace() != null) face = rt.getHitBlockFace();

        int extra = 0;
        processing.add(p.getUniqueId());
        try {
            for (Block b : plugin.tools().area(origin, face, tool.size())) {
                if (b.equals(origin) || !plugin.tools().canBreak(b, hand)) continue;
                BlockBreakEvent ev = new BlockBreakEvent(b, p); // damit Schutz-Plugins mitreden können
                Bukkit.getPluginManager().callEvent(ev);
                if (ev.isCancelled()) continue;
                if (ev.isDropItems()) b.breakNaturally(hand);
                else b.setType(Material.AIR);
                extra++;
            }
        } finally {
            processing.remove(p.getUniqueId());
        }
        if (extra > 0) damage(p, hand, extra);
    }

    /** Holzfäller-Axt: fällt den ganzen Baum (zusammenhängende Stämme + optional natürliche Blätter). */
    private void fell(BlockBreakEvent e, Player p, ItemStack hand, ToolManager.Tool tool) {
        Block origin = e.getBlock();
        if (!Tag.LOGS.isTagged(origin.getType())) return;
        int cap = tool.maxBlocks();
        if (hand.getItemMeta() instanceof Damageable dm) cap = Math.min(cap, Math.max(1, hand.getType().getMaxDurability() - dm.getDamage()));

        Set<Block> logs = new LinkedHashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        logs.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && logs.size() < cap) {
            Block b = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                Block n = b.getRelative(dx, dy, dz);
                if (logs.size() >= cap || logs.contains(n) || !Tag.LOGS.isTagged(n.getType())) continue;
                if (Math.abs(n.getX() - origin.getX()) > 10 || Math.abs(n.getZ() - origin.getZ()) > 10) continue;
                if (n.getY() < origin.getY() || n.getY() - origin.getY() > 60) continue; // nur nach oben, nie Nachbarbäume unterhalb
                logs.add(n);
                queue.add(n);
            }
        }
        // Natürliche Blätter (nicht vom Spieler gesetzt) in der Nähe der Stämme
        Set<Block> leaves = new LinkedHashSet<>();
        for (Block b : logs) {
            for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
                Block n = b.getRelative(dx, dy, dz);
                if (leaves.size() < 400 && Tag.LEAVES.isTagged(n.getType()) && n.getBlockData() instanceof Leaves lv && !lv.isPersistent()) leaves.add(n);
            }
        }
        if (tool.requireLeaves() && leaves.isEmpty()) return; // kein Baum (z. B. Holzhaus): normal 1 Block abbauen

        int broken = 0;
        processing.add(p.getUniqueId());
        try {
            for (Block b : logs) {
                if (b.equals(origin)) continue;
                BlockBreakEvent ev = new BlockBreakEvent(b, p);
                Bukkit.getPluginManager().callEvent(ev);
                if (ev.isCancelled()) continue;
                if (ev.isDropItems()) b.breakNaturally(hand); else b.setType(Material.AIR);
                broken++;
            }
            if (tool.leaves()) {
                for (Block b : leaves) {
                    BlockBreakEvent ev = new BlockBreakEvent(b, p);
                    Bukkit.getPluginManager().callEvent(ev);
                    if (ev.isCancelled()) continue;
                    if (ev.isDropItems()) b.breakNaturally(hand); else b.setType(Material.AIR);
                }
            }
        } finally {
            processing.remove(p.getUniqueId());
        }
        if (broken > 0) damage(p, hand, broken);
    }

    private void damage(Player p, ItemStack hand, int blocks) {
        ItemMeta meta = hand.getItemMeta();
        if (!(meta instanceof Damageable d)) return;
        int unb = hand.getEnchantmentLevel(Enchantment.UNBREAKING);
        int dmg = 0;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < blocks; i++) if (unb == 0 || rnd.nextInt(unb + 1) == 0) dmg++;
        d.setDamage(d.getDamage() + dmg);
        if (d.getDamage() >= hand.getType().getMaxDurability()) {
            p.getInventory().setItemInMainHand(null);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        } else {
            hand.setItemMeta(d);
        }
    }
}
