package de.smpcore.bank;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Bankkonten: Geld sicher einzahlen (geht beim Tod nicht verloren, kann nicht per /pay verschickt werden)
 * und regelmäßig Zinsen erhalten. Das Guthaben liegt in PlayerData.bank und wird mit players.yml gespeichert.
 */
public final class BankManager {
    private final SMPCore plugin;
    private long nextInterest;

    public BankManager(SMPCore plugin) { this.plugin = plugin; }

    public double balance(UUID id) {
        PlayerData d = plugin.data().get(id);
        return d == null ? 0 : d.bank;
    }

    private static double r2(double v) { return Math.round(v * 100) / 100.0; }

    public double maxBalance() { return plugin.getConfig().getDouble("bank.max-balance", 0); }
    public double interestPercent() { return plugin.getConfig().getDouble("bank.interest-percent", 1.0); }
    public long secondsToInterest() { return Math.max(0, (nextInterest - System.currentTimeMillis()) / 1000); }

    public void start() {
        long minutes = Math.max(1, plugin.getConfig().getLong("bank.interest-minutes", 60));
        long ticks = 20L * 60 * minutes;
        nextInterest = System.currentTimeMillis() + minutes * 60_000L;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            payInterest();
            nextInterest = System.currentTimeMillis() + minutes * 60_000L;
        }, ticks, ticks);
    }

    /** Einzahlen. Meldet das Ergebnis direkt an den Spieler. */
    public boolean deposit(Player p, double amount) {
        amount = r2(amount);
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return false;
        if (amount < 1) { Msg.error(p, "Mindestbetrag: $1"); return false; }
        double max = maxBalance();
        if (max > 0 && d.bank + amount > max + 1e-9) {
            double room = Math.max(0, max - d.bank);
            Msg.error(p, "Dein Konto fasst maximal " + EconomyManager.fmt(max) + ". Du kannst noch " + EconomyManager.fmt(room) + " einzahlen.");
            return false;
        }
        if (!plugin.economy().take(p.getUniqueId(), amount)) { Msg.error(p, "Nicht genug Bargeld!"); return false; }
        d.bank = r2(d.bank + amount);
        Msg.ok(p, "Eingezahlt: " + EconomyManager.fmt(amount) + " <gray>(Kontostand: <gold>" + EconomyManager.fmt(d.bank) + "<gray>)");
        return true;
    }

    public boolean withdraw(Player p, double amount) {
        amount = r2(amount);
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return false;
        if (amount < 1) { Msg.error(p, "Mindestbetrag: $1"); return false; }
        if (d.bank + 1e-9 < amount) { Msg.error(p, "Nicht genug Guthaben auf dem Konto!"); return false; }
        d.bank = r2(Math.max(0, d.bank - amount));
        plugin.economy().add(p.getUniqueId(), amount);
        Msg.ok(p, "Abgehoben: " + EconomyManager.fmt(amount) + " <gray>(Kontostand: <gold>" + EconomyManager.fmt(d.bank) + "<gray>)");
        return true;
    }

    /** Zinsen für alle aktiven Spieler (online oder in den letzten X Tagen gesehen). */
    private void payInterest() {
        var cfg = plugin.getConfig();
        double pct = interestPercent();
        if (pct <= 0) return;
        double cap = cfg.getDouble("bank.interest-max", 25000);
        double minBal = cfg.getDouble("bank.interest-min-balance", 100);
        long activeMs = cfg.getLong("bank.interest-active-days", 7) * 86_400_000L;
        double max = maxBalance();
        long now = System.currentTimeMillis();
        for (PlayerData d : plugin.data().all()) {
            if (d.bank < minBal) continue;
            Player p = Bukkit.getPlayer(d.uuid);
            if (p == null && now - d.lastSeen > activeMs) continue;
            double pay = Math.min(d.bank * pct / 100.0, cap > 0 ? cap : Double.MAX_VALUE);
            if (max > 0) pay = Math.min(pay, Math.max(0, max - d.bank));
            pay = r2(pay);
            if (pay < 0.01) continue;
            d.bank = r2(d.bank + pay);
            if (p != null) {
                String prev = Msg.current();
                Msg.use("bank");
                try { Msg.send(p, "<green>Zinsen erhalten: <gold>+" + EconomyManager.fmt(pay) + " <gray>(" + pct + " %)"); } finally { Msg.use(prev); }
            }
        }
    }
}
