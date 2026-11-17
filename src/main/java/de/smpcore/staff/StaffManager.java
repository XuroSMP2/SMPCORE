package de.smpcore.staff;

import de.smpcore.SMPCore;
import de.smpcore.util.Persist;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/** Staff-Zustand: Mutes (gespeichert) und Freeze (nur bis zum Neustart). */
public final class StaffManager {
    private final SMPCore plugin;
    private final File file;
    private final Set<UUID> frozen = new HashSet<>();
    private final Map<UUID, Long> mutes = new HashMap<>(); // 0 = permanent

    public StaffManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mutes.yml");
    }

    public void load() {
        mutes.clear();
        ConfigurationSection s = YamlConfiguration.loadConfiguration(file).getConfigurationSection("mutes");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try { mutes.put(UUID.fromString(k), s.getLong(k)); } catch (IllegalArgumentException ignored) { }
        }
    }

    public void save(boolean async) {
        YamlConfiguration y = new YamlConfiguration();
        mutes.forEach((k, v) -> y.set("mutes." + k, v));
        Persist.write(plugin, file, y.saveToString(), async);
    }

    public Set<UUID> frozen() { return frozen; }

    public void mute(UUID id, long untilMs) { mutes.put(id, untilMs); save(true); }
    public boolean unmute(UUID id) { boolean r = mutes.remove(id) != null; if (r) save(true); return r; }

    public boolean isMuted(UUID id) {
        Long until = mutes.get(id);
        if (until == null) return false;
        if (until != 0 && until < System.currentTimeMillis()) { mutes.remove(id); return false; }
        return true;
    }

    /** Restzeit in ms, 0 = permanent. */
    public long left(UUID id) {
        Long until = mutes.get(id);
        return until == null || until == 0 ? 0 : Math.max(0, until - System.currentTimeMillis());
    }
}
