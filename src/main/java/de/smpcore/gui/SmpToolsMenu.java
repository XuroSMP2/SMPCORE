package de.smpcore.gui;

import de.smpcore.crate.CrateManager;
import de.smpcore.spawner.SpawnerManager;
import de.smpcore.tools.ToolManager;
import de.smpcore.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /smptools: alle Spezial-Items des Plugins zum Abholen (Tools, Crystal-Ausrüstung, Smart-Spawner, Keys). */
public final class SmpToolsMenu {
    private SmpToolsMenu() {}

    private static boolean ok(Player p) { return p.hasPermission("smpcore.cmd.smptools") || p.hasPermission("smpcore.admin"); }

    private static void give(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(l -> p.getWorld().dropItemNaturally(p.getLocation(), l));
    }

    public static final class Main extends Menu {
        public Main(Player v) { super(v, 27, "<gold><bold>SMP-Tools"); }
        @Override protected boolean allowed() { return ok(viewer); }

        @Override
        protected void build() {
            set(11, Items.make(Material.NETHERITE_PICKAXE, "<yellow>Tools & Ausrüstung", "<gray>Hammer, Excavator, Crystal-Gear ...", "", "<yellow>Klicken"), e -> go(new Tools(viewer)));
            set(13, Items.make(Material.SPAWNER, "<green>Smart-Spawner", "<gray>Alle Mob-Typen", "", "<yellow>Klicken"), e -> go(new Spawners(viewer)));
            set(15, Items.make(Material.TRIPWIRE_HOOK, "<gold>Crate-Keys", "<gray>Keys für dich selbst", "", "<yellow>Klicken"), e -> go(new Keys(viewer)));
            fill(Items.filler());
        }
    }

    public static final class Tools extends PagedMenu {
        public Tools(Player v) { super(v, "<gold>SMP-Tools » Tools"); }
        @Override protected boolean allowed() { return ok(viewer); }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (ToolManager.Tool t : plugin.tools().all()) {
                out.add(new Entry(Items.withLore(plugin.tools().create(t, 1), List.of("", "<yellow>Klick: 1x erhalten")), e -> give(viewer, plugin.tools().create(t, 1))));
            }
            return out;
        }

        @Override
        protected void controls() { set(49, Items.make(Material.ARROW, "<red>Zurück"), e -> go(new Main(viewer))); }
    }

    public static final class Spawners extends PagedMenu {
        public Spawners(Player v) { super(v, "<gold>SMP-Tools » Spawner"); }
        @Override protected boolean allowed() { return ok(viewer); }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (SpawnerManager.Mob m : plugin.spawners().mobs()) {
                out.add(new Entry(Items.withLore(plugin.spawners().createItem(m.type(), 1), List.of("", "<yellow>Klick: 1x erhalten")), e -> plugin.spawners().give(viewer, m.type(), 1)));
            }
            return out;
        }

        @Override
        protected void controls() { set(49, Items.make(Material.ARROW, "<red>Zurück"), e -> go(new Main(viewer))); }
    }

    public static final class Keys extends PagedMenu {
        public Keys(Player v) { super(v, "<gold>SMP-Tools » Keys"); }
        @Override protected boolean allowed() { return ok(viewer); }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (CrateManager.Crate c : plugin.crates().all()) {
                out.add(new Entry(Items.make(Material.TRIPWIRE_HOOK, c.keyName, "<gray>Deine Keys: <white>" + plugin.crates().keys(viewer.getUniqueId(), c.id),
                        "", "<yellow>Klick: +1 Key"), e -> { plugin.crates().addKeys(viewer.getUniqueId(), c.id, 1); refresh(); }));
            }
            return out;
        }

        @Override
        protected void controls() { set(49, Items.make(Material.ARROW, "<red>Zurück"), e -> go(new Main(viewer))); }
    }
}
