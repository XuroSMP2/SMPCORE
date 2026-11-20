package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.bank.BankManager;
import de.smpcore.gui.BankMenu;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** /bank [balance|deposit|withdraw|top] – Bankkonto. */
public final class BankCommand implements TabExecutor {
    private final SMPCore plugin;

    public BankCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!plugin.feature("bank")) { Msg.error(s, "Bankkonten sind derzeit deaktiviert."); return true; }
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return true;
        if (a.length == 0) { new BankMenu(p).open(); return true; }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "balance", "bal", "kontostand" -> {
                Msg.send(p, "<gray>Konto: <gold>" + EconomyManager.fmt(d.bank) + " <dark_gray>| <gray>Bargeld: <green>" + EconomyManager.fmt(d.money));
                for (BankManager.Account x : plugin.bank().accessible(p.getUniqueId()))
                    Msg.send(p, "<gray>Konto <white>" + Msg.esc(x.name) + "<gray>: <gold>" + EconomyManager.fmt(x.balance) + (x.owner.equals(p.getUniqueId()) ? "" : " <dark_gray>(geteilt)"));
                double pct = plugin.bank().interestPercent();
                if (pct > 0) Msg.send(p, "<gray>Zinsen: <white>" + pct + " %<gray> alle <white>" + Num.mmss(plugin.bank().secondsToInterest()) + "<gray> (nächste Auszahlung)");
            }
            case "deposit", "einzahlen", "dep" -> amount(p, a, true, d);
            case "withdraw", "abheben", "wd" -> amount(p, a, false, d);
            case "create", "erstellen" -> {
                if (a.length < 2) { Msg.error(p, "Nutzung: /bank create <Name>"); return true; }
                plugin.bank().create(p, a[1]);
            }
            case "accounts", "konten" -> {
                List<BankManager.Account> l = plugin.bank().accessible(p.getUniqueId());
                Msg.send(p, "<gray>Hauptkonto: <gold>" + EconomyManager.fmt(d.bank));
                if (l.isEmpty()) Msg.send(p, "<gray>Keine weiteren Konten. Erstellen: <white>/bank create <Name>");
                for (BankManager.Account x : l)
                    Msg.send(p, "<white>" + Msg.esc(x.name) + " <dark_gray>» <gold>" + EconomyManager.fmt(x.balance) + " <gray>(" + (x.owner.equals(p.getUniqueId()) ? "dein Konto" : "geteilt von " + Msg.esc(ownerName(x))) + ")");
            }
            case "info" -> account(p, a, 1, x -> {
                Msg.send(p, "<gray>Konto <white>" + Msg.esc(x.name) + " <dark_gray>| <gray>Guthaben: <gold>" + EconomyManager.fmt(x.balance));
                Msg.send(p, "<gray>Besitzer: <white>" + Msg.esc(ownerName(x)) + " <dark_gray>| <gray>Mitglieder: <white>" + Msg.esc(plugin.bank().memberNames(x)));
            });
            case "share", "teilen" -> {
                if (a.length < 3) { Msg.error(p, "Nutzung: /bank share <Konto> <Spieler>"); return true; }
                PlayerData t = plugin.data().find(a[2]);
                BankManager.Account x = plugin.bank().account(a[1]);
                if (x == null || !x.canUse(p.getUniqueId())) Msg.error(p, "Dieses Konto gibt es nicht.");
                else if (t == null) Msg.error(p, "Spieler nicht gefunden.");
                else plugin.bank().share(p, x, t);
            }
            case "unshare" -> {
                if (a.length < 3) { Msg.error(p, "Nutzung: /bank unshare <Konto> <Spieler>"); return true; }
                PlayerData t = plugin.data().find(a[2]);
                BankManager.Account x = plugin.bank().account(a[1]);
                if (x == null || !x.canUse(p.getUniqueId())) Msg.error(p, "Dieses Konto gibt es nicht.");
                else if (t == null) Msg.error(p, "Spieler nicht gefunden.");
                else plugin.bank().unshare(p, x, t);
            }
            case "leave", "verlassen" -> account(p, a, 1, x -> plugin.bank().leave(p, x));
            case "delete", "löschen" -> account(p, a, 1, x -> plugin.bank().delete(p, x));
            case "top" -> {
                List<PlayerData> l = new ArrayList<>(plugin.data().all());
                l.sort(Comparator.comparingDouble((PlayerData x) -> x.bank).reversed());
                Msg.send(p, "<gold><bold>Größte Bankkonten");
                for (int i = 0; i < Math.min(10, l.size()); i++) {
                    if (l.get(i).bank <= 0) break;
                    p.sendMessage(Msg.c("<gray>" + (i + 1) + ". <white>" + Msg.esc(l.get(i).name) + " <dark_gray>» <gold>" + EconomyManager.fmt(l.get(i).bank)));
                }
            }
            default -> {
                Msg.error(p, "Nutzung: /bank [balance|deposit <Betrag|all|half> [Konto]|withdraw <Betrag|all|half> [Konto]|top]");
                Msg.send(p, "<gray>Weitere Konten: <white>/bank create|accounts|info|share|unshare|leave|delete");
            }
        }
        return true;
    }

    private String ownerName(BankManager.Account x) {
        PlayerData o = plugin.data().get(x.owner);
        return o == null ? "?" : o.name;
    }

    private void account(Player p, String[] a, int idx, java.util.function.Consumer<BankManager.Account> then) {
        BankManager.Account x = a.length > idx ? plugin.bank().account(a[idx]) : null;
        if (a.length <= idx) Msg.error(p, "Nutzung: /bank " + a[0] + " <Konto>");
        else if (x == null || !x.canUse(p.getUniqueId())) Msg.error(p, "Dieses Konto gibt es nicht.");
        else then.accept(x);
    }

    private void amount(Player p, String[] a, boolean deposit, PlayerData d) {
        if (a.length < 2) { Msg.error(p, "Nutzung: /bank " + (deposit ? "deposit" : "withdraw") + " <Betrag|all|half>"); return; }
        BankManager.Account acc = null;
        if (a.length > 2) {
            acc = plugin.bank().account(a[2]);
            if (acc == null || !acc.canUse(p.getUniqueId())) { Msg.error(p, "Dieses Konto gibt es nicht."); return; }
        }
        double have = deposit ? d.money : (acc != null ? acc.balance : d.bank), amt;
        String in = a[1].toLowerCase(Locale.ROOT);
        if (in.equals("all") || in.equals("alles")) amt = Math.floor(have * 100) / 100.0;
        else if (in.equals("half") || in.equals("halb")) amt = Math.floor(have / 2 * 100) / 100.0;
        else {
            try { amt = Num.parse(a[1]); } catch (NumberFormatException ex) { Msg.error(p, "Ungültiger Betrag."); return; }
        }
        if (acc != null) { if (deposit) plugin.bank().depositTo(p, acc, amt); else plugin.bank().withdrawFrom(p, acc, amt); }
        else if (deposit) plugin.bank().deposit(p, amt); else plugin.bank().withdraw(p, amt);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> mine = s instanceof Player p ? plugin.bank().accessible(p.getUniqueId()).stream().map(x -> x.name).toList() : List.of();
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "";
        if (a.length == 1) return List.of("balance", "deposit", "withdraw", "top", "create", "accounts", "info", "share", "unshare", "leave", "delete").stream().filter(x -> x.startsWith(sub)).toList();
        if (a.length == 2 && (sub.equals("deposit") || sub.equals("withdraw"))) return List.of("all", "half", "1k", "10k", "100k", "1m");
        if (a.length == 2 && List.of("info", "share", "unshare", "leave", "delete").contains(sub)) return mine.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))).toList();
        if (a.length == 3 && (sub.equals("deposit") || sub.equals("withdraw"))) return mine.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(a[2].toLowerCase(Locale.ROOT))).toList();
        if (a.length == 3 && (sub.equals("share") || sub.equals("unshare"))) return de.smpcore.util.Players.names(a[2]);
        return List.of();
    }
}
