package de.smpcore.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.smpcore.SMPCore;
import de.smpcore.clan.ClanManager;
import de.smpcore.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.ToDoubleFunction;

/**
 * Eingebaute, schreibgeschützte HTTP/JSON-API für Websites (z. B. Statistiken, Bestenlisten, Online-Spieler).
 * Die HTTP-Threads lesen nur einen unveränderlichen Snapshot, der regelmäßig im Server-Thread erzeugt wird.
 * Es werden bewusst keine Koordinaten, Homes, Keys oder Inventare ausgeliefert.
 */
public final class WebApiManager {
    private static final Set<String> METRICS = Set.of("money", "crystals", "gems", "kills", "deaths", "playtime");

    private final SMPCore plugin;
    private HttpServer server;
    private BukkitTask snapshotTask;
    private volatile Snapshot snap = Snapshot.EMPTY;
    private byte[] key = new byte[0];
    private String cors = "";
    private int rateLimit = 120;
    private final Map<String, int[]> rates = new ConcurrentHashMap<>(); // ip -> {minute, count}

    private record Snapshot(long time, int online, int max, List<Map<String, Object>> onlinePlayers,
                            Map<String, List<Map<String, Object>>> top,
                            Map<String, Map<String, Object>> byName, Map<String, Map<String, Object>> byUuid,
                            List<Map<String, Object>> clans, Map<String, Map<String, Object>> clanByName,
                            int totalPlayers) {
        static final Snapshot EMPTY = new Snapshot(0, 0, 0, List.of(), Map.of(), Map.of(), Map.of(), List.of(), Map.of(), 0);
    }

    public WebApiManager(SMPCore plugin) { this.plugin = plugin; }

    public boolean running() { return server != null; }

    public void start() {
        stop();
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("web-api.enabled", false)) return;
        String k = cfg.getString("web-api.api-key", "");
        if (k == null || k.length() < 16 || k.equals("CHANGE-ME-TO-A-LONG-RANDOM-KEY")) {
            plugin.getLogger().warning("web-api: aktiviert, aber 'api-key' fehlt oder ist zu kurz (min. 16 Zeichen). API wird NICHT gestartet.");
            return;
        }
        key = k.getBytes(StandardCharsets.UTF_8);
        cors = cfg.getString("web-api.cors-origin", "");
        rateLimit = Math.max(10, cfg.getInt("web-api.rate-limit-per-minute", 120));
        String host = cfg.getString("web-api.bind", "0.0.0.0");
        int port = cfg.getInt("web-api.port", 8085);
        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext("/api/", this::handle);
            server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "SMPCore-WebApi");
                t.setDaemon(true);
                return t;
            }));
            server.start();
        } catch (IOException e) {
            plugin.getLogger().warning("web-api: konnte nicht auf " + host + ":" + port + " starten: " + e.getMessage());
            server = null;
            return;
        }
        refresh();
        long every = 20L * Math.max(5, cfg.getInt("web-api.refresh-seconds", 30));
        snapshotTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, every, every);
        plugin.getLogger().info("web-api: läuft auf " + host + ":" + port + " (schreibgeschützt, API-Key aktiv)");
    }

    public void stop() {
        if (snapshotTask != null) { snapshotTask.cancel(); snapshotTask = null; }
        if (server != null) { server.stop(0); server = null; }
    }

    // ---------- Snapshot (läuft im Server-Thread) ----------

    private void refresh() {
        int topMax = Math.min(100, Math.max(10, plugin.getConfig().getInt("web-api.max-top", 50)));
        ClanManager cm = plugin.clans();
        Map<String, Map<String, Object>> byName = new HashMap<>(), byUuid = new HashMap<>();
        List<PlayerData> all = new ArrayList<>(plugin.data().all());
        for (PlayerData d : all) {
            ClanManager.Clan c = cm.of(d.uuid);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uuid", d.uuid.toString());
            m.put("name", d.name);
            m.put("rank", d.rank == null ? "default" : d.rank);
            m.put("money", Math.round(d.money * 100) / 100.0);
            m.put("crystals", d.crystals);
            m.put("gems", d.gems);
            m.put("kills", d.kills);
            m.put("deaths", d.deaths);
            m.put("playtimeSeconds", d.playtime);
            m.put("lastSeen", d.lastSeen);
            m.put("clan", c == null ? null : c.name);
            if (d.name != null) byName.put(d.name.toLowerCase(Locale.ROOT), m);
            byUuid.put(d.uuid.toString(), m);
        }
        Map<String, List<Map<String, Object>>> top = new LinkedHashMap<>();
        top.put("money", topBy(all, d -> d.money, topMax, byUuid));
        top.put("crystals", topBy(all, d -> d.crystals, topMax, byUuid));
        top.put("gems", topBy(all, d -> d.gems, topMax, byUuid));
        top.put("kills", topBy(all, d -> d.kills, topMax, byUuid));
        top.put("deaths", topBy(all, d -> d.deaths, topMax, byUuid));
        top.put("playtime", topBy(all, d -> d.playtime, topMax, byUuid));

        List<Map<String, Object>> online = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.vanished().contains(p.getUniqueId())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uuid", p.getUniqueId().toString());
            m.put("name", p.getName());
            PlayerData d = plugin.data().get(p.getUniqueId());
            m.put("rank", d == null || d.rank == null ? "default" : d.rank);
            online.add(m);
        }

        List<Map<String, Object>> clans = new ArrayList<>();
        Map<String, Map<String, Object>> clanByName = new HashMap<>();
        for (ClanManager.Clan c : cm.all()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", c.name);
            m.put("tag", c.tag);
            PlayerData l = plugin.data().get(c.leader);
            m.put("leader", l == null ? null : l.name);
            m.put("created", c.created);
            m.put("bank", Math.round(c.bank * 100) / 100.0);
            m.put("kills", c.kills);
            m.put("deaths", c.deaths);
            m.put("memberCount", c.members.size());
            List<Map<String, Object>> members = new ArrayList<>();
            for (Map.Entry<UUID, ClanManager.Role> e : c.members.entrySet()) {
                PlayerData md = plugin.data().get(e.getKey());
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("uuid", e.getKey().toString());
                mm.put("name", md == null ? null : md.name);
                mm.put("role", e.getValue().name());
                members.add(mm);
            }
            m.put("members", members);
            clans.add(m);
            clanByName.put(c.name.toLowerCase(Locale.ROOT), m);
        }
        clans.sort((a, b) -> Integer.compare((int) b.get("kills"), (int) a.get("kills")));

        snap = new Snapshot(System.currentTimeMillis(), online.size(), Bukkit.getMaxPlayers(), List.copyOf(online),
                top, byName, byUuid, List.copyOf(clans), clanByName, all.size());
    }

    private List<Map<String, Object>> topBy(List<PlayerData> all, ToDoubleFunction<PlayerData> f, int n, Map<String, Map<String, Object>> byUuid) {
        List<PlayerData> sorted = new ArrayList<>(all);
        sorted.sort((a, b) -> Double.compare(f.applyAsDouble(b), f.applyAsDouble(a)));
        List<Map<String, Object>> out = new ArrayList<>();
        int rank = 1;
        for (PlayerData d : sorted) {
            if (rank > n) break;
            Map<String, Object> m = new LinkedHashMap<>(byUuid.get(d.uuid.toString()));
            m.put("position", rank++);
            out.add(m);
        }
        return out;
    }

    // ---------- HTTP ----------

    private void handle(HttpExchange ex) throws IOException {
        try {
            if (!cors.isEmpty()) {
                ex.getResponseHeaders().set("Access-Control-Allow-Origin", cors);
                ex.getResponseHeaders().set("Access-Control-Allow-Headers", "X-API-Key");
                ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            }
            String method = ex.getRequestMethod();
            if (method.equals("OPTIONS")) { send(ex, 204, null); return; }
            if (!method.equals("GET")) { send(ex, 405, Map.of("error", "Nur GET erlaubt")); return; }

            String ip = ex.getRemoteAddress().getAddress().getHostAddress();
            String fwd = ex.getRequestHeaders().getFirst("X-Forwarded-For");
            if (fwd != null && plugin.getConfig().getBoolean("web-api.trust-proxy", false)) ip = fwd.split(",")[0].trim();
            if (limited(ip)) { send(ex, 429, Map.of("error", "Zu viele Anfragen")); return; }

            String given = ex.getRequestHeaders().getFirst("X-API-Key");
            if (given == null || !MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), key)) {
                send(ex, 401, Map.of("error", "API-Key fehlt oder ist ungültig (Header X-API-Key)"));
                return;
            }

            String path = ex.getRequestURI().getPath();
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());
            String[] p = path.substring(1).split("/"); // api, ...
            Snapshot s = snap;

            if (p.length == 2 && p[1].equals("status")) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("online", s.online);
                m.put("max", s.max);
                m.put("totalPlayers", s.totalPlayers);
                m.put("clans", s.clans.size());
                m.put("updated", s.time);
                send(ex, 200, m);
            } else if (p.length == 3 && p[1].equals("players") && p[2].equals("online")) {
                send(ex, 200, Map.of("online", s.online, "max", s.max, "players", s.onlinePlayers));
            } else if (p.length == 3 && p[1].equals("top")) {
                String metric = p[2].toLowerCase(Locale.ROOT);
                if (!METRICS.contains(metric)) { send(ex, 404, Map.of("error", "Unbekannt. Erlaubt: " + METRICS)); return; }
                int limit = clamp(q.get("limit"), 10, 1, 100);
                List<Map<String, Object>> l = s.top.getOrDefault(metric, List.of());
                send(ex, 200, Map.of("metric", metric, "players", l.subList(0, Math.min(limit, l.size()))));
            } else if (p.length == 3 && p[1].equals("player")) {
                String id = dec(p[2]);
                Map<String, Object> m = s.byUuid.get(id.toLowerCase(Locale.ROOT));
                if (m == null) m = s.byName.get(id.toLowerCase(Locale.ROOT));
                if (m == null && plugin.getConfig().getString("bedrock-prefix") != null)
                    m = s.byName.get((plugin.getConfig().getString("bedrock-prefix", ".") + id).toLowerCase(Locale.ROOT));
                if (m == null) send(ex, 404, Map.of("error", "Spieler nicht gefunden"));
                else send(ex, 200, m);
            } else if (p.length == 2 && p[1].equals("clans")) {
                int limit = clamp(q.get("limit"), 50, 1, 200);
                List<Map<String, Object>> l = new ArrayList<>();
                for (Map<String, Object> c : s.clans.subList(0, Math.min(limit, s.clans.size()))) {
                    Map<String, Object> lite = new LinkedHashMap<>(c);
                    lite.remove("members");
                    l.add(lite);
                }
                send(ex, 200, Map.of("clans", l));
            } else if (p.length == 3 && p[1].equals("clans")) {
                Map<String, Object> c = s.clanByName.get(dec(p[2]).toLowerCase(Locale.ROOT));
                if (c == null) send(ex, 404, Map.of("error", "Clan nicht gefunden"));
                else send(ex, 200, c);
            } else {
                send(ex, 404, Map.of("error", "Unbekannter Endpoint",
                        "endpoints", List.of("/api/status", "/api/players/online", "/api/top/{money|crystals|gems|kills|deaths|playtime}?limit=10",
                                "/api/player/{name|uuid}", "/api/clans", "/api/clans/{name}")));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("web-api Fehler: " + e);
            try { send(ex, 500, Map.of("error", "Interner Fehler")); } catch (IOException ignored) { }
        } finally {
            ex.close();
        }
    }

    private boolean limited(String ip) {
        int minute = (int) (System.currentTimeMillis() / 60000L);
        if (rates.size() > 5000) rates.clear();
        int[] r = rates.compute(ip, (k, v) -> {
            if (v == null || v[0] != minute) return new int[]{minute, 1};
            v[1]++;
            return v;
        });
        return r[1] > rateLimit;
    }

    private static int clamp(String s, int def, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(s))); } catch (Exception e) { return def; }
    }

    private static String dec(String s) { return URLDecoder.decode(s, StandardCharsets.UTF_8); }

    private static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String part : raw.split("&")) {
            int i = part.indexOf('=');
            if (i > 0) m.put(dec(part.substring(0, i)), dec(part.substring(i + 1)));
        }
        return m;
    }

    private void send(HttpExchange ex, int code, Object body) throws IOException {
        if (body == null) { ex.sendResponseHeaders(code, -1); return; }
        byte[] out = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, out.length);
        ex.getResponseBody().write(out);
    }

    /** Minimaler JSON-Encoder (Map, List, String, Zahlen, Boolean, null). */
    static final class Json {
        static String write(Object o) { StringBuilder sb = new StringBuilder(); w(sb, o); return sb.toString(); }

        private static void w(StringBuilder sb, Object o) {
            if (o == null) sb.append("null");
            else if (o instanceof Number n) {
                double d = n.doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) sb.append("null"); else sb.append(n);
            }
            else if (o instanceof Boolean b) sb.append(b);
            else if (o instanceof Map<?, ?> m) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    str(sb, String.valueOf(e.getKey()));
                    sb.append(':');
                    w(sb, e.getValue());
                }
                sb.append('}');
            } else if (o instanceof Collection<?> c) {
                sb.append('[');
                boolean first = true;
                for (Object v : c) {
                    if (!first) sb.append(',');
                    first = false;
                    w(sb, v);
                }
                sb.append(']');
            } else str(sb, String.valueOf(o));
        }

        private static void str(StringBuilder sb, String s) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
                }
            }
            sb.append('"');
        }
    }
}
