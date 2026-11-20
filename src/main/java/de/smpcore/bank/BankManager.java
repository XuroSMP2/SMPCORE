package de.smpcore.bank;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import de.smpcore.util.Persist;

import java.io.File;
import java.util.*;

/**
 * Bankkonten: Geld sicher einzahlen (geht beim Tod nicht verloren, kann nicht per /pay verschickt werden)
 * und regelmäßig Zinsen erhalten. Das Guthaben liegt in PlayerData.bank und wird mit players.yml gespeichert.
 */
public final class BankManager {
    /** Zusätzliches Konto (neben dem persönlichen Hauptkonto): kann mit anderen Spielern geteilt werden. */
    public static final class Account {
        public final String name;
        public final UUID owner;
        public double balance;
        public final Set<UUID> members = new LinkedHashSet<>();
        Account(String name, UUID owner) { this.name = name; this.owner = owner; }
        public boolean canUse(UUID id) { return owner.equals(id) || members.contains(id); }
    }

    private final SMPCore plugin;
    private final File file;
    private final Map<String, Account> accounts = new LinkedHashMap<>(); // key = Name in Kleinbuchstaben
    private long nextInterest;

    public BankManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bank-accounts.yml");
    }

    // ---------------------------------------------------------------- zusätzliche / geteilte Konten
    public void load() {
        accounts.clear();
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("accounts");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(k);
            if (c == null) continue;
            try {
                Account a = new Account(c.getString("name", k), UUID.fromString(c.getString("owner", "")));
                a.balance = c.getDouble("balance");
                for (String m : c.getStringList("members")) { try { a.members.add(UUID.fromString(m)); } catch (IllegalArgumentException ignored) { } }
                accounts.put(k.toLowerCase(Locale.ROOT), a);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Bankkonto " + k + " ungültig: " + ex.getMessage());
            }
        }
    }

    public void save(boolean async) {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Account> e : accounts.entrySet()) {
            String b = "accounts." + e.getKey() + ".";
            Account a = e.getValue();
            y.set(b + "name", a.name);
            y.set(b + "owner", a.owner.toString());
            y.set(b + "balance", a.balance);
            y.set(b + "members", a.members.stream().map(UUID::toString).toList());
        }
        Persist.write(plugin, file, y.saveToString(), async);
    }

    private UUID uid(Player p) { return plugin.spoof() == null ? p.getUniqueId() : plugin.spoof().resolve(p.getUniqueId()); }

    public Account account(String name) { return name == null ? null : accounts.get(name.toLowerCase(Locale.ROOT)); }

    /** Alle Konten, die der Spieler besitzt oder nutzen darf. */
    public List<Account> accessible(UUID id) {
        List<Account> out = new ArrayList<>();
        for (Account a : accounts.values()) if (a.canUse(id)) out.add(a);
        return out;
    }

    public int ownedCount(UUID id) {
        int n = 0;
        for (Account a : accounts.values()) if (a.owner.equals(id)) n++;
        return n;
    }

    /** Hauptkonto + alle eigenen Zusatzkonten (für Vermögen/Ranglisten). */
    public double totalOwned(PlayerData d) {
        double sum = d.bank;
        for (Account a : accounts.values()) if (a.owner.equals(d.uuid)) sum += a.balance;
        return r2(sum);
    }

    public boolean create(Player p, String name) {
        UUID id = uid(p);
        if (!name.matches("[A-Za-z0-9_]{3,16}")) { Msg.error(p, "Kontoname: 3-16 Zeichen (A-Z, 0-9, _)."); return false; }
        if (List.of("main", "haupt", "hauptkonto").contains(name.toLowerCase(Locale.ROOT)) || account(name) != null) { Msg.error(p, "Diesen Kontonamen gibt es schon."); return false; }
        int max = plugin.getConfig().getInt("bank.max-accounts", 3);
        if (ownedCount(id) >= max) { Msg.error(p, "Du kannst maximal " + max + " zusätzliche Konten besitzen."); return false; }
        double cost = plugin.getConfig().getDouble("bank.account-create-cost", 5000);
        if (cost > 0 && !plugin.economy().take(id, cost)) { Msg.error(p, "Ein neues Konto kostet " + EconomyManager.fmt(cost) + " Bargeld."); return false; }
        accounts.put(name.toLowerCase(Locale.ROOT), new Account(name, id));
        Msg.ok(p, "Konto <white>" + Msg.esc(name) + "<green> erstellt. <gray>Teilen mit <white>/bank share " + Msg.esc(name) + " <Spieler>");
        return true;
    }

    public boolean depositTo(Player p, Account a, double amount) {
        amount = r2(amount);
        if (!a.canUse(uid(p))) { Msg.error(p, "Du hast keinen Zugriff auf dieses Konto."); return false; }
        if (amount < 1) { Msg.error(p, "Mindestbetrag: $1"); return false; }
        double max = maxBalance();
        if (max > 0 && a.balance + amount > max + 1e-9) { Msg.error(p, "Das Konto fasst maximal " + EconomyManager.fmt(max) + "."); return false; }
        if (!plugin.economy().take(uid(p), amount)) { Msg.error(p, "Nicht genug Bargeld!"); return false; }
        a.balance = r2(a.balance + amount);
        Msg.ok(p, "Eingezahlt auf <white>" + Msg.esc(a.name) + "<green>: " + EconomyManager.fmt(amount) + " <gray>(Kontostand: <gold>" + EconomyManager.fmt(a.balance) + "<gray>)");
        notifyOwner(p, a, "hat " + EconomyManager.fmt(amount) + " eingezahlt");
        return true;
    }

    public boolean withdrawFrom(Player p, Account a, double amount) {
        amount = r2(amount);
        if (!a.canUse(uid(p))) { Msg.error(p, "Du hast keinen Zugriff auf dieses Konto."); return false; }
        if (amount < 1) { Msg.error(p, "Mindestbetrag: $1"); return false; }
        if (a.balance + 1e-9 < amount) { Msg.error(p, "Nicht genug Guthaben auf dem Konto!"); return false; }
        a.balance = r2(Math.max(0, a.balance - amount));
        plugin.economy().add(uid(p), amount);
        Msg.ok(p, "Abgehoben von <white>" + Msg.esc(a.name) + "<green>: " + EconomyManager.fmt(amount) + " <gray>(Kontostand: <gold>" + EconomyManager.fmt(a.balance) + "<gray>)");
        notifyOwner(p, a, "hat " + EconomyManager.fmt(amount) + " abgehoben");
        return true;
    }

    /** Der Besitzer erfährt, wenn ein anderer Teilnehmer ein-/auszahlt. */
    private void notifyOwner(Player actor, Account a, String what) {
        if (a.owner.equals(uid(actor))) return;
        Player o = Bukkit.getPlayer(a.owner);
        if (o != null) Msg.send(o, "<white>" + Msg.esc(actor.getName()) + " <gray>" + what + " <dark_gray>(Konto " + Msg.esc(a.name) + ")");
    }

    public boolean share(Player p, Account a, PlayerData target) {
        if (!a.owner.equals(uid(p))) { Msg.error(p, "Nur der Besitzer kann ein Konto teilen."); return false; }
        if (target.uuid.equals(a.owner)) { Msg.error(p, "Das bist du selbst."); return false; }
        int max = plugin.getConfig().getInt("bank.max-members", 5);
        if (!a.members.contains(target.uuid) && a.members.size() >= max) { Msg.error(p, "Maximal " + max + " Mitglieder pro Konto."); return false; }
        a.members.add(target.uuid);
        Msg.ok(p, "<white>" + Msg.esc(target.name) + "<green> darf jetzt auf <white>" + Msg.esc(a.name) + "<green> ein- und auszahlen.");
        Player t = Bukkit.getPlayer(target.uuid);
        if (t != null) Msg.send(t, "<white>" + Msg.esc(p.getName()) + " <gray>hat das Bankkonto <white>" + Msg.esc(a.name) + " <gray>mit dir geteilt: <white>/bank deposit|withdraw <Betrag> " + Msg.esc(a.name));
        return true;
    }

    public boolean unshare(Player p, Account a, PlayerData target) {
        if (!a.owner.equals(uid(p))) { Msg.error(p, "Nur der Besitzer kann Mitglieder entfernen."); return false; }
        if (!a.members.remove(target.uuid)) { Msg.error(p, Msg.esc(target.name) + " ist kein Mitglied."); return false; }
        Msg.ok(p, Msg.esc(target.name) + " hat keinen Zugriff mehr auf " + Msg.esc(a.name) + ".");
        return true;
    }

    public boolean leave(Player p, Account a) {
        if (a.owner.equals(uid(p))) { Msg.error(p, "Als Besitzer kannst du das Konto nur löschen: /bank delete " + Msg.esc(a.name)); return false; }
        if (!a.members.remove(uid(p))) { Msg.error(p, "Du bist kein Mitglied."); return false; }
        Msg.ok(p, "Du hast das Konto " + Msg.esc(a.name) + " verlassen.");
        return true;
    }

    /** Löscht das Konto, das Guthaben geht auf das Hauptkonto des Besitzers. */
    public boolean delete(Player p, Account a) {
        if (!a.owner.equals(uid(p))) { Msg.error(p, "Nur der Besitzer kann das Konto löschen."); return false; }
        PlayerData d = plugin.data().get(a.owner);
        if (d != null) d.bank = r2(d.bank + a.balance);
        accounts.remove(a.name.toLowerCase(Locale.ROOT));
        Msg.ok(p, "Konto gelöscht. " + EconomyManager.fmt(a.balance) + " wurden auf dein Hauptkonto überwiesen.");
        return true;
    }

    public String memberNames(Account a) {
        List<String> n = new ArrayList<>();
        for (UUID m : a.members) {
            PlayerData d = plugin.data().get(m);
            n.add(d == null ? m.toString().substring(0, 8) : d.name);
        }
        return n.isEmpty() ? "niemand" : String.join(", ", n);
    }

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
        for (Account a : accounts.values()) {
            if (a.balance < minBal) continue;
            PlayerData od = plugin.data().get(a.owner);
            Player op = Bukkit.getPlayer(a.owner);
            if (op == null && (od == null || now - od.lastSeen > activeMs)) continue;
            double pay = Math.min(a.balance * pct / 100.0, cap > 0 ? cap : Double.MAX_VALUE);
            if (max > 0) pay = Math.min(pay, Math.max(0, max - a.balance));
            pay = r2(pay);
            if (pay < 0.01) continue;
            a.balance = r2(a.balance + pay);
            if (op != null) {
                String prev = Msg.current();
                Msg.use("bank");
                try { Msg.send(op, "<green>Zinsen auf Konto <white>" + Msg.esc(a.name) + "<green>: <gold>+" + EconomyManager.fmt(pay)); } finally { Msg.use(prev); }
            }
        }
    }
}
