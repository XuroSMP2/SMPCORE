package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
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
                double pct = plugin.bank().interestPercent();
                if (pct > 0) Msg.send(p, "<gray>Zinsen: <white>" + pct + " %<gray> alle <white>" + Num.mmss(plugin.bank().secondsToInterest()) + "<gray> (nächste Auszahlung)");
            }
            case "deposit", "einzahlen", "dep" -> amount(p, a, true, d);
            case "withdraw", "abheben", "wd" -> amount(p, a, false, d);
            case "top" -> {
                List<PlayerData> l = new ArrayList<>(plugin.data().all());
                l.sort(Comparator.comparingDouble((PlayerData x) -> x.bank).reversed());
                Msg.send(p, "<gold><bold>Größte Bankkonten");
                for (int i = 0; i < Math.min(10, l.size()); i++) {
                    if (l.get(i).bank <= 0) break;
                    p.sendMessage(Msg.c("<gray>" + (i + 1) + ". <white>" + Msg.esc(l.get(i).name) + " <dark_gray>» <gold>" + EconomyManager.fmt(l.get(i).bank)));
                }
            }
            default -> Msg.error(p, "Nutzung: /bank [balance|deposit <Betrag|all|half>|withdraw <Betrag|all|half>|top]");
        }
        return true;
    }

    private void amount(Player p, String[] a, boolean deposit, PlayerData d) {
        if (a.length < 2) { Msg.error(p, "Nutzung: /bank " + (deposit ? "deposit" : "withdraw") + " <Betrag|all|half>"); return; }
        double have = deposit ? d.money : d.bank, amt;
        String in = a[1].toLowerCase(Locale.ROOT);
        if (in.equals("all") || in.equals("alles")) amt = Math.floor(have * 100) / 100.0;
        else if (in.equals("half") || in.equals("halb")) amt = Math.floor(have / 2 * 100) / 100.0;
        else {
            try { amt = Num.parse(a[1]); } catch (NumberFormatException ex) { Msg.error(p, "Ungültiger Betrag."); return; }
        }
        if (deposit) plugin.bank().deposit(p, amt); else plugin.bank().withdraw(p, amt);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) return List.of("balance", "deposit", "withdraw", "top").stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        if (a.length == 2 && !a[0].equalsIgnoreCase("balance") && !a[0].equalsIgnoreCase("top")) return List.of("all", "half", "1k", "10k", "100k", "1m");
        return List.of();
    }
}
