package de.smpcore.listeners;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.List;

/** Mute (Chat + private Nachrichten) und Freeze. */
public final class StaffListener implements Listener {
    private final SMPCore plugin;

    public StaffListener(SMPCore plugin) { this.plugin = plugin; }

    private void tell(Player p) {
        long left = plugin.staff().left(p.getUniqueId());
        p.sendMessage(Msg.c("<red>Du bist gemutet" + (left > 0 ? " (noch " + Num.duration(left) + ")" : " (dauerhaft)") + "."));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        if (!plugin.staff().isMuted(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
        tell(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage().toLowerCase();
        for (String c : List.of("/msg ", "/r ", "/w ", "/tell ", "/m ", "/pm ", "/reply ")) {
            if (m.startsWith(c) && plugin.staff().isMuted(e.getPlayer().getUniqueId())) {
                e.setCancelled(true);
                tell(e.getPlayer());
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.staff().frozen().contains(e.getPlayer().getUniqueId())) return;
        Location f = e.getFrom(), t = e.getTo();
        if (t == null || (f.getX() == t.getX() && f.getY() == t.getY() && f.getZ() == t.getZ())) return;
        Location back = f.clone();
        back.setYaw(t.getYaw());
        back.setPitch(t.getPitch());
        e.setTo(back);
    }
}
