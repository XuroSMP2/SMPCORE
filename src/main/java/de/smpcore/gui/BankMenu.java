package de.smpcore.gui;

import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Items;
import de.smpcore.util.Num;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Bankkonto-Menü: Ein- und Auszahlen mit einem Tap (Bedrock-tauglich). */
public final class BankMenu extends Menu {
    private static final double[] STEPS = {1_000, 10_000, 100_000, 1_000_000};

    public BankMenu(Player v) { super(v, 36, "<gold><bold>Bankkonto"); }

    @Override
    protected void build() {
        PlayerData d = plugin.data().get(viewer.getUniqueId());
        if (d == null) return;
        double pct = plugin.bank().interestPercent();
        double max = plugin.bank().maxBalance();
        set(4, Items.make(Material.GOLD_BLOCK, "<gold><bold>Dein Konto",
                "<gray>Guthaben: <gold>" + EconomyManager.fmt(d.bank),
                "<gray>Bargeld: <green>" + EconomyManager.fmt(d.money),
                max > 0 ? "<gray>Maximum: <white>" + EconomyManager.fmt(max) : "<gray>Maximum: <white>unbegrenzt",
                pct > 0 ? "<gray>Zinsen: <white>" + pct + " %<gray> – nächste in <white>" + Num.mmss(plugin.bank().secondsToInterest()) : "<gray>Zinsen: <white>aus",
                "<dark_gray>Sicher vor Tod und /pay"));
        set(9, Items.make(Material.HOPPER, "<green><bold>Einzahlen", "<gray>Bargeld -> Konto"));
        set(18, Items.make(Material.DISPENSER, "<red><bold>Abheben", "<gray>Konto -> Bargeld"));
        for (int i = 0; i < STEPS.length; i++) {
            double amt = STEPS[i];
            set(11 + i, Items.make(Material.LIME_CONCRETE, "<green>+ " + Num.format(amt), "<gray>Zahlt " + EconomyManager.fmt(amt) + " ein"), e -> act(true, amt));
            set(20 + i, Items.make(Material.RED_CONCRETE, "<red>- " + Num.format(amt), "<gray>Hebt " + EconomyManager.fmt(amt) + " ab"), e -> act(false, amt));
        }
        set(15, Items.make(Material.EMERALD_BLOCK, "<green><bold>Alles einzahlen", "<gray>Dein ganzes Bargeld"), e -> act(true, Math.floor(d.money * 100) / 100.0));
        set(24, Items.make(Material.REDSTONE_BLOCK, "<red><bold>Alles abheben", "<gray>Das ganze Guthaben"), e -> act(false, Math.floor(d.bank * 100) / 100.0));
        set(31, Items.make(Material.BARRIER, "<gray>Schließen"), e -> closeLater());
        fill(Items.filler());
    }

    private void act(boolean deposit, double amount) {
        if (deposit) plugin.bank().deposit(viewer, amount); else plugin.bank().withdraw(viewer, amount);
        refresh();
    }
}
