package de.smpcore.market;

import de.smpcore.SMPCore;
import de.smpcore.auction.AuctionManager;
import de.smpcore.clan.ClanManager;
import de.smpcore.data.PlayerData;
import de.smpcore.order.OrderManager;
import de.smpcore.shop.ShopManager;
import de.smpcore.util.Items;
import de.smpcore.util.Persist;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

/**
 * Markt-Statistik für die Website XuroMarket: Handelsverlauf (Shop, Auktionshaus, Orders), Durchschnittspreise,
 * Käufe/Verkäufe pro Spieler und ein RTP-Tracker (nur Anzahl/Dimension/Zeit, KEINE Koordinaten).
 * Alle Methoden laufen im Server-Thread.
 */
public final class MarketManager {
    public enum Type { SHOP_BUY, SHOP_SELL, AH, ORDER }

    private record Trade(long time, Type type, UUID buyer, String buyerName, UUID seller, String sellerName,
                         Material item, int amount, double total, double net) { }

    private static final class PStats {
        String name;
        double boughtMoney, soldMoney;
        long boughtItems, soldItems;
    }

    private static final class IStats {
        double sumPrice;
        long sumAmount, count;
        final TreeMap<Long, double[]> days = new TreeMap<>(); // epochDay -> {summe preis, summe menge}
    }

    private record Rtp(long time, UUID id, String name, String dim) { }

    private static final int KEEP_DAYS = 30, KEEP_TRADES = 500, KEEP_RTP = 100;

    private final SMPCore plugin;
    private final File file;
    private final ArrayDeque<Trade> trades = new ArrayDeque<>();
    private final Map<UUID, PStats> players = new HashMap<>();
    private final Map<Material, IStats> items = new EnumMap<>(Material.class);
    private final TreeMap<Long, double[]> volume = new TreeMap<>(); // epochDay -> {Anzahl, Umsatz}
    private long rtpTotal;
    private final Map<String, Long> rtpDims = new LinkedHashMap<>();
    private final TreeMap<Long, int[]> rtpDays = new TreeMap<>(); // epochDay -> {overworld, nether, end}
    private final Map<UUID, Object[]> rtpPlayers = new HashMap<>(); // uuid -> {name, count}
    private final ArrayDeque<Rtp> rtpRecent = new ArrayDeque<>();

    public MarketManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "market.yml");
    }

    private static long today() { return LocalDate.now().toEpochDay(); }

    // ------------------------------------------------------------------ Aufzeichnen

    public void shopBuy(org.bukkit.entity.Player p, Material m, int amount, double total) {
        record(new Trade(System.currentTimeMillis(), Type.SHOP_BUY, p.getUniqueId(), p.getName(), null, "Shop", m, amount, total, total));
    }

    public void shopSell(org.bukkit.entity.Player p, Material m, int amount, double total) {
        record(new Trade(System.currentTimeMillis(), Type.SHOP_SELL, null, "Shop", p.getUniqueId(), p.getName(), m, amount, total, total));
    }

    public void auction(org.bukkit.entity.Player buyer, UUID seller, String sellerName, Material m, int amount, double price, double payout) {
        record(new Trade(System.currentTimeMillis(), Type.AH, buyer.getUniqueId(), buyer.getName(), seller, sellerName, m, amount, price, payout));
    }

    public void orderDelivery(org.bukkit.entity.Player seller, UUID owner, String ownerName, Material m, int amount, double pay) {
        record(new Trade(System.currentTimeMillis(), Type.ORDER, owner, ownerName, seller.getUniqueId(), seller.getName(), m, amount, pay, pay));
    }

    private void record(Trade t) {
        if (t.amount <= 0 || t.total < 0) return;
        trades.addFirst(t);
        while (trades.size() > KEEP_TRADES) trades.removeLast();
        if (t.buyer != null) {
            PStats s = players.computeIfAbsent(t.buyer, k -> new PStats());
            s.name = t.buyerName;
            s.boughtMoney += t.total;
            s.boughtItems += t.amount;
        }
        if (t.seller != null) {
            PStats s = players.computeIfAbsent(t.seller, k -> new PStats());
            s.name = t.sellerName;
            s.soldMoney += t.net;
            s.soldItems += t.amount;
        }
        long day = today();
        double[] v = volume.computeIfAbsent(day, k -> new double[2]);
        v[0]++;
        v[1] += t.total;
        if (t.type == Type.AH || t.type == Type.ORDER) { // nur Spieler-Preise zählen für den Durchschnitt
            IStats is = items.computeIfAbsent(t.item, k -> new IStats());
            is.sumPrice += t.total;
            is.sumAmount += t.amount;
            is.count++;
            double[] d = is.days.computeIfAbsent(day, k -> new double[2]);
            d[0] += t.total;
            d[1] += t.amount;
        }
        prune();
    }

    public void rtp(org.bukkit.entity.Player p, String dim) {
        rtpTotal++;
        rtpDims.merge(dim, 1L, Long::sum);
        int[] d = rtpDays.computeIfAbsent(today(), k -> new int[3]);
        d[dim.equals("nether") ? 1 : dim.equals("end") ? 2 : 0]++;
        Object[] pl = rtpPlayers.computeIfAbsent(p.getUniqueId(), k -> new Object[]{p.getName(), 0});
        pl[0] = p.getName();
        pl[1] = (Integer) pl[1] + 1;
        rtpRecent.addFirst(new Rtp(System.currentTimeMillis(), p.getUniqueId(), p.getName(), dim));
        while (rtpRecent.size() > KEEP_RTP) rtpRecent.removeLast();
        prune();
    }

    private void prune() {
        long min = today() - KEEP_DAYS;
        volume.headMap(min).clear();
        rtpDays.headMap(min).clear();
        for (IStats is : items.values()) is.days.headMap(min).clear();
    }

    // ------------------------------------------------------------------ Laden / Speichern

    public void load() {
        trades.clear(); players.clear(); items.clear(); volume.clear();
        rtpTotal = 0; rtpDims.clear(); rtpDays.clear(); rtpPlayers.clear(); rtpRecent.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        try {
            for (String line : y.getStringList("trades")) {
                String[] a = line.split("\\|", -1);
                Material m = Material.matchMaterial(a[6]);
                if (a.length < 10 || m == null) continue;
                trades.addLast(new Trade(Long.parseLong(a[0]), Type.valueOf(a[1]), a[2].isEmpty() ? null : UUID.fromString(a[2]), a[3],
                        a[4].isEmpty() ? null : UUID.fromString(a[4]), a[5], m, Integer.parseInt(a[7]), Double.parseDouble(a[8]), Double.parseDouble(a[9])));
            }
            ConfigurationSection ps = y.getConfigurationSection("players");
            if (ps != null) for (String k : ps.getKeys(false)) {
                PStats s = new PStats();
                s.name = ps.getString(k + ".name", "?");
                s.boughtMoney = ps.getDouble(k + ".bm"); s.soldMoney = ps.getDouble(k + ".sm");
                s.boughtItems = ps.getLong(k + ".bi"); s.soldItems = ps.getLong(k + ".si");
                players.put(UUID.fromString(k), s);
            }
            ConfigurationSection is = y.getConfigurationSection("items");
            if (is != null) for (String k : is.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m == null) continue;
                IStats s = new IStats();
                s.sumPrice = is.getDouble(k + ".sp"); s.sumAmount = is.getLong(k + ".sa"); s.count = is.getLong(k + ".c");
                ConfigurationSection ds = is.getConfigurationSection(k + ".days");
                if (ds != null) for (String d : ds.getKeys(false)) {
                    String[] v = ds.getString(d, "0,0").split(",");
                    s.days.put(Long.parseLong(d), new double[]{Double.parseDouble(v[0]), Double.parseDouble(v[1])});
                }
                items.put(m, s);
            }
            ConfigurationSection vs = y.getConfigurationSection("volume");
            if (vs != null) for (String d : vs.getKeys(false)) {
                String[] v = vs.getString(d, "0,0").split(",");
                volume.put(Long.parseLong(d), new double[]{Double.parseDouble(v[0]), Double.parseDouble(v[1])});
            }
            rtpTotal = y.getLong("rtp.total");
            ConfigurationSection dims = y.getConfigurationSection("rtp.dims");
            if (dims != null) for (String k : dims.getKeys(false)) rtpDims.put(k, dims.getLong(k));
            ConfigurationSection rd = y.getConfigurationSection("rtp.days");
            if (rd != null) for (String d : rd.getKeys(false)) {
                String[] v = rd.getString(d, "0,0,0").split(",");
                rtpDays.put(Long.parseLong(d), new int[]{Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2])});
            }
            ConfigurationSection rp = y.getConfigurationSection("rtp.players");
            if (rp != null) for (String k : rp.getKeys(false)) rtpPlayers.put(UUID.fromString(k), new Object[]{rp.getString(k + ".name", "?"), rp.getInt(k + ".count")});
            for (String line : y.getStringList("rtp.recent")) {
                String[] a = line.split("\\|", -1);
                if (a.length >= 4) rtpRecent.addLast(new Rtp(Long.parseLong(a[0]), UUID.fromString(a[1]), a[2], a[3]));
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("market.yml konnte nicht vollständig geladen werden: " + ex.getMessage());
        }
    }

    public void save(boolean async) {
        YamlConfiguration y = new YamlConfiguration();
        List<String> tl = new ArrayList<>();
        for (Trade t : trades) tl.add(t.time + "|" + t.type + "|" + (t.buyer == null ? "" : t.buyer) + "|" + clean(t.buyerName) + "|"
                + (t.seller == null ? "" : t.seller) + "|" + clean(t.sellerName) + "|" + t.item.name() + "|" + t.amount + "|" + t.total + "|" + t.net);
        y.set("trades", tl);
        players.forEach((id, s) -> {
            String b = "players." + id + ".";
            y.set(b + "name", s.name); y.set(b + "bm", s.boughtMoney); y.set(b + "sm", s.soldMoney);
            y.set(b + "bi", s.boughtItems); y.set(b + "si", s.soldItems);
        });
        items.forEach((m, s) -> {
            String b = "items." + m.name() + ".";
            y.set(b + "sp", s.sumPrice); y.set(b + "sa", s.sumAmount); y.set(b + "c", s.count);
            s.days.forEach((d, v) -> y.set(b + "days." + d, v[0] + "," + v[1]));
        });
        volume.forEach((d, v) -> y.set("volume." + d, v[0] + "," + v[1]));
        y.set("rtp.total", rtpTotal);
        rtpDims.forEach((k, v) -> y.set("rtp.dims." + k, v));
        rtpDays.forEach((d, v) -> y.set("rtp.days." + d, v[0] + "," + v[1] + "," + v[2]));
        rtpPlayers.forEach((id, v) -> { y.set("rtp.players." + id + ".name", v[0]); y.set("rtp.players." + id + ".count", v[1]); });
        List<String> rl = new ArrayList<>();
        for (Rtp r : rtpRecent) rl.add(r.time + "|" + r.id + "|" + clean(r.name) + "|" + r.dim);
        y.set("rtp.recent", rl);
        Persist.write(plugin, file, y.saveToString(), async);
    }

    private static String clean(String s) { return s == null ? "" : s.replace('|', '_').replace('\n', ' '); }

    // ------------------------------------------------------------------ Export für die Website

    private static double r2(double d) { return Math.round(d * 100) / 100.0; }

    private static String meta(ItemStack it) {
        if (!it.hasItemMeta()) return "";
        ItemMeta m = it.getItemMeta();
        List<String> parts = new ArrayList<>();
        if (m.hasDisplayName() && m.displayName() != null) parts.add(PlainTextComponentSerializer.plainText().serialize(m.displayName()));
        for (Map.Entry<Enchantment, Integer> e : it.getEnchantments().entrySet()) parts.add(e.getKey().getKey().getKey().replace('_', ' ') + " " + e.getValue());
        return String.join(", ", parts);
    }

    public Map<String, Object> export() {
        long now = System.currentTimeMillis();
        long today = today();
        Map<String, Object> out = new LinkedHashMap<>();

        // Auktionshaus
        List<Map<String, Object>> ah = new ArrayList<>();
        double ahValue = 0;
        Map<Material, Double> cheapest = new EnumMap<>(Material.class);
        List<AuctionManager.Listing> act = plugin.auctions().active(null, AuctionManager.Sort.NEWEST);
        for (AuctionManager.Listing l : act) {
            ahValue += l.price;
            int n = Math.max(1, l.item.getAmount());
            Material m = l.item.getType();
            if (!l.item.hasItemMeta()) cheapest.merge(m, l.price / n, Math::min);
            if (ah.size() >= 500) continue;
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("seller", l.sellerName);
            e.put("item", m.name());
            e.put("name", Items.pretty(m));
            e.put("amount", n);
            e.put("price", r2(l.price));
            e.put("unit", r2(l.price / n));
            e.put("meta", meta(l.item));
            e.put("created", l.created);
            e.put("expires", l.expires);
            ah.add(e);
        }
        out.put("ah", ah);

        // Orders
        List<Map<String, Object>> orders = new ArrayList<>();
        double orderValue = 0;
        int openOrders = 0;
        Map<Material, Double> bestOrder = new EnumMap<>(Material.class);
        for (OrderManager.Order o : plugin.orders().open(null, OrderManager.Sort.NEWEST)) {
            openOrders++;
            orderValue += o.price * o.remaining();
            bestOrder.merge(o.material, o.price, Math::max);
            if (orders.size() >= 500) continue;
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("owner", o.ownerName);
            e.put("item", o.material.name());
            e.put("name", Items.pretty(o.material));
            e.put("amount", o.amount);
            e.put("filled", o.filled);
            e.put("remaining", o.remaining());
            e.put("price", r2(o.price));
            e.put("total", r2(o.price * o.remaining()));
            e.put("created", o.created);
            e.put("expires", o.expires);
            orders.add(e);
        }
        out.put("orders", orders);

        // Letzte Trades
        List<Map<String, Object>> tr = new ArrayList<>();
        for (Trade t : trades) {
            if (tr.size() >= 200) break;
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("time", t.time);
            e.put("type", t.type.name());
            e.put("buyer", t.buyerName);
            e.put("seller", t.sellerName);
            e.put("item", t.item.name());
            e.put("name", Items.pretty(t.item));
            e.put("amount", t.amount);
            e.put("total", r2(t.total));
            tr.add(e);
        }
        out.put("trades", tr);

        // Umsatz pro Tag (14 Tage)
        List<Map<String, Object>> vol = new ArrayList<>();
        for (long d = today - 13; d <= today; d++) {
            double[] v = volume.get(d);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("day", LocalDate.ofEpochDay(d).toString());
            e.put("trades", v == null ? 0 : (long) v[0]);
            e.put("volume", v == null ? 0 : r2(v[1]));
            vol.add(e);
        }
        out.put("volume", vol);

        // Preise: Spieler-Durchschnitt (AH + Orders) und Shop-Preise
        Map<Material, double[]> shop = new EnumMap<>(Material.class);
        for (ShopManager.Category c : plugin.shop().categories())
            for (ShopManager.Item i : c.items()) shop.merge(i.material(), new double[]{i.buy(), i.sell()}, (a, b) -> a);
        Set<Material> all = EnumSet.noneOf(Material.class);
        all.addAll(items.keySet());
        all.addAll(cheapest.keySet());
        all.addAll(bestOrder.keySet());
        all.addAll(shop.keySet());
        List<Map<String, Object>> prices = new ArrayList<>();
        for (Material m : all) {
            IStats s = items.get(m);
            double[] sh = shop.get(m);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("item", m.name());
            e.put("name", Items.pretty(m));
            e.put("avg", s == null || s.sumAmount == 0 ? null : r2(s.sumPrice / s.sumAmount));
            double sp = 0, sa = 0;
            if (s != null) for (Map.Entry<Long, double[]> d : s.days.tailMap(today - 6).entrySet()) { sp += d.getValue()[0]; sa += d.getValue()[1]; }
            e.put("avg7d", sa == 0 ? null : r2(sp / sa));
            e.put("trades", s == null ? 0 : s.count);
            e.put("units", s == null ? 0 : s.sumAmount);
            e.put("ahMin", cheapest.containsKey(m) ? r2(cheapest.get(m)) : null);
            e.put("orderMax", bestOrder.containsKey(m) ? r2(bestOrder.get(m)) : null);
            e.put("shopBuy", sh == null || sh[0] <= 0 ? null : r2(sh[0]));
            e.put("shopSell", sh == null || sh[1] <= 0 ? null : r2(sh[1]));
            List<Object> hist = new ArrayList<>();
            for (long d = today - 13; d <= today; d++) {
                double[] v = s == null ? null : s.days.get(d);
                hist.add(v == null || v[1] == 0 ? null : r2(v[0] / v[1]));
            }
            e.put("history", hist);
            prices.add(e);
        }
        out.put("prices", prices);

        // Vermögen + Handel pro Spieler
        List<PlayerData> pd = new ArrayList<>(plugin.data().all());
        double circulation = 0;
        for (PlayerData d : pd) circulation += d.money + plugin.bank().totalOwned(d); // Bargeld + Bankguthaben (inkl. eigener Zusatzkonten)
        pd.sort((a, b) -> Double.compare(b.money + plugin.bank().totalOwned(b), a.money + plugin.bank().totalOwned(a)));
        List<Map<String, Object>> wealth = new ArrayList<>();
        ClanManager cm = plugin.clans();
        for (PlayerData d : pd) {
            if (wealth.size() >= 3000) break;
            PStats s = players.get(d.uuid);
            ClanManager.Clan c = cm.of(d.uuid);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("name", d.name);
            e.put("rank", d.rank == null ? "default" : d.rank);
            e.put("clan", c == null ? null : c.name);
            e.put("money", r2(d.money));
            double bankTotal = plugin.bank().totalOwned(d);
            e.put("bank", r2(bankTotal));
            e.put("total", r2(d.money + bankTotal));
            e.put("crystals", d.crystals);
            e.put("gems", d.gems);
            e.put("boughtMoney", s == null ? 0 : r2(s.boughtMoney));
            e.put("boughtItems", s == null ? 0 : s.boughtItems);
            e.put("soldMoney", s == null ? 0 : r2(s.soldMoney));
            e.put("soldItems", s == null ? 0 : s.soldItems);
            wealth.add(e);
        }
        out.put("wealth", wealth);

        // RTP-Tracker (ohne Koordinaten)
        Map<String, Object> rtp = new LinkedHashMap<>();
        rtp.put("total", rtpTotal);
        rtp.put("dims", rtpDims);
        List<Map<String, Object>> rd = new ArrayList<>();
        for (long d = today - 13; d <= today; d++) {
            int[] v = rtpDays.get(d);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("day", LocalDate.ofEpochDay(d).toString());
            e.put("overworld", v == null ? 0 : v[0]);
            e.put("nether", v == null ? 0 : v[1]);
            e.put("end", v == null ? 0 : v[2]);
            rd.add(e);
        }
        rtp.put("days", rd);
        List<Object[]> top = new ArrayList<>(rtpPlayers.values());
        top.sort((a, b) -> Integer.compare((Integer) b[1], (Integer) a[1]));
        List<Map<String, Object>> tl = new ArrayList<>();
        for (Object[] t : top.subList(0, Math.min(25, top.size()))) tl.add(Map.of("name", t[0], "count", t[1]));
        rtp.put("top", tl);
        List<Map<String, Object>> rr = new ArrayList<>();
        for (Rtp r : rtpRecent) {
            if (rr.size() >= 50) break;
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("time", r.time);
            e.put("name", r.name);
            e.put("dim", r.dim);
            rr.add(e);
        }
        rtp.put("recent", rr);
        out.put("rtp", rtp);

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("ahListings", act.size());
        totals.put("ahValue", r2(ahValue));
        totals.put("openOrders", openOrders);
        totals.put("orderValue", r2(orderValue));
        totals.put("circulation", r2(circulation));
        totals.put("players", pd.size());
        long dayAgo = now - 86_400_000L;
        long t24 = 0;
        double v24 = 0;
        for (Trade t : trades) if (t.time >= dayAgo) { t24++; v24 += t.total; }
        totals.put("trades24h", t24);
        totals.put("volume24h", r2(v24));
        out.put("totals", totals);
        return out;
    }
}
