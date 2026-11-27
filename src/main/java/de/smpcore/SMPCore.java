package de.smpcore;

import de.smpcore.afk.AfkManager;
import de.smpcore.battlepass.BattlePassManager;
import de.smpcore.enderchest.EnderChestManager;
import de.smpcore.staff.StaffManager;
import de.smpcore.farm.FarmMode;
import de.smpcore.holo.HologramManager;
import de.smpcore.live.LiveManager;
import de.smpcore.announcer.Announcer;
import de.smpcore.auction.AuctionManager;
import de.smpcore.bounty.BountyManager;
import de.smpcore.clan.ClanManager;
import de.smpcore.commands.*;
import de.smpcore.crate.CrateManager;
import de.smpcore.data.DataManager;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.gui.MenuListener;
import de.smpcore.gui.SignInput;
import de.smpcore.kit.KitManager;
import de.smpcore.warp.WarpManager;
import de.smpcore.keyall.KeyallManager;
import de.smpcore.order.OrderManager;
import de.smpcore.pack.PackManager;
import de.smpcore.listeners.*;
import de.smpcore.rank.RankManager;
import de.smpcore.scoreboard.ScoreboardManager;
import de.smpcore.shop.CrystalShopManager;
import de.smpcore.shop.ShopManager;
import de.smpcore.spawner.SpawnerManager;
import de.smpcore.tab.TabManager;
import de.smpcore.teleport.TeleportManager;
import de.smpcore.tools.ToolManager;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public final class SMPCore extends JavaPlugin {
    private static SMPCore instance;
    private DataManager data;
    private EconomyManager economy;
    private ShopManager shop;
    private CrystalShopManager crystalShop;
    private CrystalShopManager gemShop;
    private ClanManager clans;
    private AfkManager afk;
    private de.smpcore.security.AntiPieChart antiPie;
    private TabManager tab;
    private OrderManager orders;
    private WarpManager warps;
    private KitManager kits;
    private Announcer announcer;
    private PackManager packs;
    private LiveManager live;
    private FarmMode farm;
    private HologramManager holograms;
    private BattlePassManager battlePass;
    private EnderChestManager enderChests;
    private StaffManager staff;
    private final java.util.Set<java.util.UUID> vanished = new java.util.HashSet<>();
    private AuctionManager auctions;
    private BountyManager bounties;
    private CrateManager crates;
    private ScoreboardManager scoreboards;
    private TeleportManager teleports;
    private RankManager ranks;
    private KeyallManager keyall;
    private de.smpcore.advent.AdventManager advent;
    private de.smpcore.xmas.XmasManager xmas;
    private SpawnerManager spawners;
    private ToolManager tools;
    private de.smpcore.web.WebApiManager webApi;
    private de.smpcore.market.MarketManager market;
    private de.smpcore.bank.BankManager bank;
    private de.smpcore.combat.CombatManager combat;
    private de.smpcore.link.LinkManager link;
    private de.smpcore.spoof.SpoofManager spoof;
    private de.smpcore.upgrade.UpgradeManager upgrades;
    private de.smpcore.settings.SettingsManager settings;
    private de.smpcore.manhunt.ManhuntManager manhunt;
    private de.smpcore.staff.PunishManager punish;

    public static SMPCore get() { return instance; }
    public DataManager data() { return data; }
    public EconomyManager economy() { return economy; }
    public ShopManager shop() { return shop; }
    public CrystalShopManager crystalShop() { return crystalShop; }
    public CrystalShopManager gemShop() { return gemShop; }
    public ClanManager clans() { return clans; }
    public AfkManager afk() { return afk; }
    public TabManager tab() { return tab; }
    public OrderManager orders() { return orders; }
    public WarpManager warps() { return warps; }
    public PackManager packs() { return packs; }
    public LiveManager live() { return live; }
    public FarmMode farm() { return farm; }
    public HologramManager holograms() { return holograms; }
    public BattlePassManager battlePass() { return battlePass; }
    public EnderChestManager enderChests() { return enderChests; }
    public StaffManager staff() { return staff; }
    public KitManager kits() { return kits; }
    public java.util.Set<java.util.UUID> vanished() { return vanished; }
    public AuctionManager auctions() { return auctions; }
    public BountyManager bounties() { return bounties; }
    public CrateManager crates() { return crates; }
    public ScoreboardManager scoreboards() { return scoreboards; }
    public TeleportManager teleports() { return teleports; }
    public RankManager ranks() { return ranks; }
    public KeyallManager keyall() { return keyall; }
    public de.smpcore.advent.AdventManager advent() { return advent; }
    public de.smpcore.xmas.XmasManager xmas() { return xmas; }
    public SpawnerManager spawners() { return spawners; }
    public ToolManager tools() { return tools; }
    public de.smpcore.web.WebApiManager webApi() { return webApi; }
    public de.smpcore.market.MarketManager market() { return market; }
    public de.smpcore.bank.BankManager bank() { return bank; }
    public de.smpcore.combat.CombatManager combat() { return combat; }
    public de.smpcore.link.LinkManager link() { return link; }
    public de.smpcore.spoof.SpoofManager spoof() { return spoof; }
    public de.smpcore.upgrade.UpgradeManager upgrades() { return upgrades; }
    public de.smpcore.settings.SettingsManager settings() { return settings; }
    public de.smpcore.manhunt.ManhuntManager manhunt() { return manhunt; }
    public de.smpcore.staff.PunishManager punish() { return punish; }

    public boolean feature(String name) { return getConfig().getBoolean("features." + name, true); }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        for (String f : new String[]{"shop.yml", "crates.yml", "crystalshop.yml", "ranks.yml", "spawners.yml", "tools.yml", "gemshop.yml", "kits.yml", "battlepass.yml", "upgrades.yml", "advent.yml"}) {
            if (!new File(getDataFolder(), f).exists()) saveResource(f, false);
        }
        Msg.configure(getConfig());
        Num.setComma(getConfig().getBoolean("number-comma", true));

        // Sicherung: Gibt es noch keine players.yml, werden die Daten aus players-seed.yml übernommen (nie überschreiben!)
        File playersFile = new File(getDataFolder(), "players.yml");
        if (!playersFile.exists()) {
            try {
                getDataFolder().mkdirs();
                File seed = new File(getDataFolder(), "players-seed.yml");
                if (!seed.exists() && getResource("players-seed.yml") != null) saveResource("players-seed.yml", false);
                if (seed.exists()) {
                    java.nio.file.Files.copy(seed.toPath(), playersFile.toPath());
                    getLogger().info("Spielerdaten aus players-seed.yml übernommen.");
                }
            } catch (java.io.IOException ex) {
                getLogger().warning("Seed-Daten konnten nicht übernommen werden: " + ex.getMessage());
            }
        }
        data = new DataManager(this);
        data.load();
        economy = new EconomyManager(data);
        market = new de.smpcore.market.MarketManager(this);
        market.load();
        shop = new ShopManager(this);
        shop.load();
        crystalShop = new CrystalShopManager(this, "crystalshop.yml", false);
        crystalShop.load();
        gemShop = new CrystalShopManager(this, "gemshop.yml", true);
        gemShop.load();
        clans = new ClanManager(this);
        clans.load();
        orders = new OrderManager(this);
        orders.load();
        orders.expire();
        warps = new WarpManager(this);
        warps.load();
        kits = new KitManager(this);
        kits.load();
        announcer = new Announcer(this);
        packs = new PackManager(this);
        live = new LiveManager(this);
        farm = new FarmMode(this);
        holograms = new HologramManager(this);
        holograms.load();
        battlePass = new BattlePassManager(this);
        battlePass.loadConfig();
        battlePass.loadData();
        enderChests = new EnderChestManager(this);
        enderChests.load();
        staff = new StaffManager(this);
        staff.load();
        afk = new AfkManager(this);
        tab = new TabManager(this);
        auctions = new AuctionManager(this);
        auctions.load();
        bounties = new BountyManager(this);
        bounties.load();
        crates = new CrateManager(this);
        crates.load();
        ranks = new RankManager(this);
        ranks.load();
        spawners = new SpawnerManager(this);
        spawners.loadConfig();
        spawners.loadData();
        tools = new ToolManager(this);
        tools.load();
        tools.start();
        keyall = new KeyallManager(this);
        advent = new de.smpcore.advent.AdventManager(this);
        advent.load();
        xmas = new de.smpcore.xmas.XmasManager(this);
        scoreboards = new ScoreboardManager(this);
        teleports = new TeleportManager(this);

        bank = new de.smpcore.bank.BankManager(this);
        bank.load();
        combat = new de.smpcore.combat.CombatManager(this);
        link = new de.smpcore.link.LinkManager(this);
        link.load();
        punish = new de.smpcore.staff.PunishManager(this);
        punish.load();
        manhunt = new de.smpcore.manhunt.ManhuntManager(this);
        manhunt.load();
        manhunt.start();
        settings = new de.smpcore.settings.SettingsManager(this);
        getServer().getPluginManager().registerEvents(settings, this);
        upgrades = new de.smpcore.upgrade.UpgradeManager(this);
        upgrades.load();
        new de.smpcore.recipe.EndCrystalRecipe(this).load();
        if (antiPie != null) antiPie.apply();
        spoof = new de.smpcore.spoof.SpoofManager(this);
        spoof.load();
        EconomyCommands eco = new EconomyCommands(this);
        for (String n : new String[]{"balance", "pay", "eco", "crystals", "gems", "top"}) reg(n, eco);
        ShopCommands sc = new ShopCommands(this);
        for (String n : new String[]{"shop", "sell", "crystalshop", "gemshop"}) reg(n, sc);
        reg("ah", new AuctionCommand(this));
        reg("bounty", new BountyCommand(this));
        reg("crate", new CrateCommand(this));
        AdminCommand ac = new AdminCommand(this);
        for (String n : new String[]{"adminpanel", "smpcore", "keyall"}) reg(n, ac);
        reg("rank", new RankCommand(this));
        reg("clan", new ClanCommand(this));
        reg("order", new OrderCommand(this));
        reg("bank", new de.smpcore.commands.BankCommand(this));
        reg("verify", new de.smpcore.commands.VerifyCommand(this));
        reg("spoof", new de.smpcore.commands.SpoofCommand(this));
        reg("upgrades", new de.smpcore.commands.UpgradesCommand(this));
        reg("settings", new de.smpcore.commands.SettingsCommand(this));
        reg("manhunt", new de.smpcore.commands.ManhuntCommand(this));
        de.smpcore.commands.PunishCommands pcmd = new de.smpcore.commands.PunishCommands(this);
        for (String n : new String[]{"ban", "tempban", "unban", "lookup", "banlist"}) reg(n, pcmd);
        reg("dimension", new DimensionCommand(this));
        FeatureCommands fc = new FeatureCommands(this);
        for (String n : new String[]{"live", "announce", "farmmode", "hat", "repair", "near", "seen", "rules", "wiki"}) reg(n, fc);
        reg("border", new BorderCommand(this));
        reg("holo", new HoloCommand(this));
        reg("battlepass", new BattlePassCommand(this));
        StaffCommands stc = new StaffCommands(this);
        for (String n : new String[]{"invsee", "ecsee", "gm", "freeze", "mute", "unmute", "clearchat", "skull", "rename", "ptime", "smptools"}) reg(n, stc);
        UtilityCommands uc = new UtilityCommands(this);
        for (String n : new String[]{"back", "warp", "setwarp", "delwarp", "kit", "msg", "r", "heal", "feed", "fly", "craft", "ec", "trash", "vanish", "sign"}) reg(n, uc);
        de.smpcore.commands.XmasCommand xc = new de.smpcore.commands.XmasCommand(this);
        for (String n : new String[]{"advent", "xmas", "gift"}) reg(n, xc);
        reg("afk", new AfkCommand(this));
        SpawnerCommand spc = new SpawnerCommand(this);
        reg("spawner", spc);
        reg("tool", spc);
        TeleportCommands tc = new TeleportCommands(this);
        for (String n : new String[]{"spawn", "setspawn", "sethome", "home", "delhome", "homes", "tpa", "tpaccept", "tpdeny", "tpahere", "rtp"}) reg(n, tc);
        MiscCommands mc = new MiscCommands(this);
        for (String n : new String[]{"daily", "stats", "scoreboard"}) reg(n, mc);

        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new CrateListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerListener(this), this);
        getServer().getPluginManager().registerEvents(new ToolListener(this), this);
        getServer().getPluginManager().registerEvents(new ClanListener(this), this);
        getServer().getPluginManager().registerEvents(new SignInput(), this);
        getServer().getPluginManager().registerEvents(new de.smpcore.gui.SellMenu(), this);
        getServer().getPluginManager().registerEvents(new PackListener(this), this);
        getServer().getPluginManager().registerEvents(new DimensionListener(this), this);
        getServer().getPluginManager().registerEvents(farm, this);
        getServer().getPluginManager().registerEvents(new BattlePassListener(this), this);
        getServer().getPluginManager().registerEvents(new EnderChestListener(this), this);
        getServer().getPluginManager().registerEvents(new StaffListener(this), this);
        getServer().getPluginManager().registerEvents(combat, this);
        getServer().getPluginManager().registerEvents(link, this);
        getServer().getPluginManager().registerEvents(spoof, this);
        getServer().getPluginManager().registerEvents(manhunt, this);
        getServer().getPluginManager().registerEvents(punish, this);
        getServer().getPluginManager().registerEvents(new de.smpcore.afk.AfkBlockListener(this), this);
        antiPie = new de.smpcore.security.AntiPieChart(this);
        getServer().getPluginManager().registerEvents(antiPie, this);
        antiPie.apply();

        webApi = new de.smpcore.web.WebApiManager(this);
        webApi.start();
        bank.start();
        combat.start();
        link.start();
        scoreboards.start();
        crates.startParticles();
        keyall.start();
        advent.start();
        xmas.start();
        getServer().getPluginManager().registerEvents(xmas, this);
        afk.start();
        tab.start();
        announcer.start();
        holograms.start();
        long ticks = 20L * spawners.intervalSeconds();
        getServer().getScheduler().runTaskTimer(this, () -> spawners.tick(), ticks, ticks);
        getServer().getScheduler().runTaskTimer(this, () -> saveAll(true), 20L * 300, 20L * 300);
        getServer().getScheduler().runTaskTimer(this, () -> orders.expire(), 20L * 60, 20L * 60 * 5);
        getServer().getScheduler().runTaskTimer(this, () -> playtimeTick(), 20L * 60, 20L * 60);
        for (Player p : getServer().getOnlinePlayers()) {
            data.getOrCreate(p.getUniqueId(), p.getName());
            ranks.apply(p);
        }
        getLogger().info("SMPCore aktiviert – " + data.all().size() + " Spieler, " + crates.all().size() + " Crates, "
                + ranks.all().size() + " Ränge geladen.");
    }

    @Override
    public void onDisable() {
        SignInput.cleanup();
        if (spoof != null) spoof.shutdown();
        if (webApi != null) webApi.stop();
        if (holograms != null) holograms.stop();
        if (scoreboards != null) scoreboards.shutdown();
        if (ranks != null) for (Player p : getServer().getOnlinePlayers()) ranks.remove(p);
        if (data != null) saveAll(false);
    }

    /** Registriert einen Befehl und setzt vor jedem Aufruf den passenden Chat-Prefix (AH ▸, HOME ▸ ...). */
    private void reg(String name, TabExecutor ex) {
        PluginCommand c = getCommand(name);
        if (c == null) return;
        TabExecutor wrapped = new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
                Msg.use(cmd.getName());
                try { return ex.onCommand(s, cmd, label, a); } finally { Msg.reset(); }
            }

            @Override
            public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
                return ex.onTabComplete(s, cmd, label, a);
            }
        };
        c.setExecutor(wrapped);
        c.setTabCompleter(wrapped);
    }

    public void saveAll(boolean async) {
        data.save(async);
        auctions.save(async);
        bounties.save(async);
        spawners.save(async);
        clans.save(async);
        orders.save(async);
        battlePass.save(async);
        enderChests.save(async);
        staff.save(async);
        market.save(async);
        bank.save(async);
        manhunt.save(async);
        punish.save(async);
    }

    public void reloadAll() {
        reloadConfig();
        Msg.configure(getConfig());
        Num.setComma(getConfig().getBoolean("number-comma", true));
        shop.load();
        crystalShop.load();
        gemShop.load();
        crates.load();
        ranks.load();
        spawners.loadConfig();
        tools.load();
        upgrades.load();
        new de.smpcore.recipe.EndCrystalRecipe(this).load();
        if (antiPie != null) antiPie.apply();
        kits.load();
        warps.load();
        battlePass.loadConfig();
        holograms.load();
        holograms.start();
        keyall.reset();
        advent.load();
        xmas.ensureCrate();
        webApi.start();
    }

    private void playtimeTick() {
        int every = getConfig().getInt("crystals.playtime-minutes", 30);
        long amount = getConfig().getLong("crystals.playtime-amount", 1);
        for (Player p : getServer().getOnlinePlayers()) {
            PlayerData d = data.get(p.getUniqueId());
            if (d == null) continue;
            d.playtime += 60;
            battlePass.addProgress(p, "PLAYTIME", null, 1);
            if (every > 0 && amount > 0 && (d.playtime / 60) % every == 0) {
                economy.addCrystals(p.getUniqueId(), amount);
                Msg.use("crystals");
                try { Msg.send(p, "<yellow>+" + amount + " Stars <gray>für deine Spielzeit!"); } finally { Msg.reset(); }
            }
        }
    }
}
