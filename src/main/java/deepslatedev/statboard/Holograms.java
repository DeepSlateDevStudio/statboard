package deepslatedev.statboard;

import org.powernukkitx.Player;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.particle.FloatingTextParticle;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.utils.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Holograms {
    private final StatBoard plugin;
    private final File file;
    private final List<Entry> entries = new ArrayList<>();

    private record Entry(String category, String world, Vector3 position, FloatingTextParticle particle) {
    }

    public Holograms(StatBoard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "holograms.yml");
        Config config = new Config(file, Config.YAML);
        for (Object value : config.getList("holograms", new ArrayList<>())) {
            if (value instanceof Map<?, ?> map && map.get("category") != null && map.get("world") != null) {
                Vector3 position = new Vector3(num(map.get("x")), num(map.get("y")), num(map.get("z")));
                entries.add(new Entry(String.valueOf(map.get("category")), String.valueOf(map.get("world")), position, new FloatingTextParticle(position, "", "")));
            }
        }
    }

    private static double num(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0;
    }

    private void save() {
        Config config = new Config(file, Config.YAML);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Entry entry : entries) {
            Map<String, Object> map = new HashMap<>();
            map.put("category", entry.category());
            map.put("world", entry.world());
            map.put("x", Math.round(entry.position().x * 100) / 100.0);
            map.put("y", Math.round(entry.position().y * 100) / 100.0);
            map.put("z", Math.round(entry.position().z * 100) / 100.0);
            list.add(map);
        }
        config.set("holograms", list);
        config.save();
    }

    public void create(Player player, String category) {
        Vector3 position = player.getPosition().add(0, 1.8, 0);
        entries.add(new Entry(category, player.getLevel().getFolderName(), position, new FloatingTextParticle(position, "", "")));
        save();
        refresh(null);
    }

    public boolean removeNearest(Player player) {
        Entry best = null;
        double bestDistance = 25;
        for (Entry entry : entries) {
            if (!entry.world().equals(player.getLevel().getFolderName())) {
                continue;
            }
            double distance = entry.position().distanceSquared(player.getPosition());
            if (distance < bestDistance) {
                best = entry;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return false;
        }
        best.particle().setInvisible(true);
        player.getLevel().addParticle(best.particle());
        entries.remove(best);
        save();
        return true;
    }

    public void refresh(Player only) {
        Map<String, String> cache = new HashMap<>();
        for (Entry entry : entries) {
            Level level = plugin.getServer().getLevelByName(entry.world());
            if (level == null || (only != null && only.getLevel() != level)) {
                continue;
            }
            String text = cache.computeIfAbsent(entry.category(), plugin::hologramText);
            entry.particle().setTitle(plugin.msg("hologram-title", "category", plugin.categoryName(entry.category())));
            entry.particle().setText(text);
            entry.particle().setInvisible(false);
            if (only != null) {
                level.addParticle(entry.particle(), only);
            } else {
                level.addParticle(entry.particle());
            }
        }
    }
}
