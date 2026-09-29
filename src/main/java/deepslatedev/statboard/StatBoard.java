package deepslatedev.statboard;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.EventPriority;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.block.BlockBreakEvent;
import org.powernukkitx.event.block.BlockPlaceEvent;
import org.powernukkitx.event.entity.EntityDamageByEntityEvent;
import org.powernukkitx.event.entity.EntityDamageEvent;
import org.powernukkitx.event.entity.EntityDeathEvent;
import org.powernukkitx.event.entity.EntityLevelChangeEvent;
import org.powernukkitx.event.player.PlayerDeathEvent;
import org.powernukkitx.event.player.PlayerJoinEvent;
import org.powernukkitx.form.window.SimpleForm;
import org.powernukkitx.plugin.PluginBase;
import org.powernukkitx.utils.Config;

import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StatBoard extends PluginBase implements Listener {
    static final List<String> ALL = List.of("kills", "deaths", "kdr", "mobs", "broken", "placed", "playtime", "streak", "balance");
    private Config stats;
    private Economy economy;
    private Holograms holograms;
    private boolean dirty;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        stats = new Config(new File(getDataFolder(), "stats.yml"), Config.YAML);
        economy = new Economy(getDataFolder());
        holograms = new Holograms(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().scheduleRepeatingTask(this, this::minute, 1200);
        int save = Math.max(10, getConfig().getInt("save-interval-seconds", 60)) * 20;
        getServer().getScheduler().scheduleRepeatingTask(this, this::flush, save);
        int refresh = Math.max(5, getConfig().getInt("hologram-refresh-seconds", 30)) * 20;
        getServer().getScheduler().scheduleRepeatingTask(this, () -> holograms.refresh(null), refresh);
        getServer().getScheduler().scheduleDelayedTask(this, () -> holograms.refresh(null), 40);
    }

    @Override
    public void onDisable() {
        if (stats != null) {
            snapshotBalances();
            dirty = true;
            flush();
        }
    }

    String msg(String key, Object... pairs) {
        String text = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return text;
    }

    private String prefixed(String key, Object... pairs) {
        return msg("prefix") + msg(key, pairs);
    }

    List<String> categories() {
        List<String> list = new ArrayList<>();
        for (String category : ALL) {
            if (getConfig().getBoolean("categories." + category, true)) {
                list.add(category);
            }
        }
        return list;
    }

    String categoryName(String category) {
        return msg("category-" + category);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private long get(String player, String stat) {
        return stats.getLong(key(player) + "." + stat, 0L);
    }

    private void add(Player player, String stat, long amount) {
        String k = key(player.getName());
        stats.set(k + ".name", player.getName());
        stats.set(k + "." + stat, get(player.getName(), stat) + amount);
        dirty = true;
    }

    private void set(Player player, String stat, long value) {
        String k = key(player.getName());
        stats.set(k + ".name", player.getName());
        stats.set(k + "." + stat, value);
        dirty = true;
    }

    private void flush() {
        if (dirty) {
            dirty = false;
            stats.save();
        }
    }

    private void minute() {
        for (Player player : getServer().getOnlinePlayers().values()) {
            add(player, "playtime", 60);
        }
        snapshotBalances();
    }

    private void snapshotBalances() {
        if (!categories().contains("balance")) {
            return;
        }
        for (Player player : getServer().getOnlinePlayers().values()) {
            set(player, "balance", economy.balance(player.getName()));
        }
    }

    double value(String player, String category) {
        return switch (category) {
            case "kdr" -> {
                long kills = get(player, "kills");
                long deaths = get(player, "deaths");
                yield kills < getConfig().getInt("kdr-min-kills", 10) ? -1 : (deaths == 0 ? kills : (double) kills / deaths);
            }
            case "streak" -> get(player, "best_streak");
            default -> get(player, category);
        };
    }

    String format(String category, double value) {
        return switch (category) {
            case "kdr" -> String.format(Locale.US, "%.2f", value);
            case "playtime" -> {
                long seconds = (long) value;
                long h = seconds / 3600;
                long m = seconds % 3600 / 60;
                yield h > 0 ? h + "h " + m + "m" : m + "m";
            }
            case "balance" -> msg("currency", "amount", NumberFormat.getIntegerInstance(Locale.US).format((long) value));
            default -> NumberFormat.getIntegerInstance(Locale.US).format((long) value);
        };
    }

    List<Map.Entry<String, Double>> ranking(String category) {
        List<Map.Entry<String, Double>> list = new ArrayList<>();
        Map<String, Object> all = stats.getAll();
        for (Map.Entry<String, Object> entry : all.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> map)) {
                continue;
            }
            String name = map.get("name") != null ? String.valueOf(map.get("name")) : entry.getKey();
            double value = value(entry.getKey(), category);
            if (value > 0) {
                list.add(Map.entry(name, value));
            }
        }
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list;
    }

    String hologramText(String category) {
        List<Map.Entry<String, Double>> ranking = ranking(category);
        int size = Math.max(1, getConfig().getInt("hologram-size", 10));
        if (ranking.isEmpty()) {
            return msg("hologram-empty");
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(size, ranking.size()); i++) {
            lines.add(msg("hologram-line", "rank", i + 1, "player", ranking.get(i).getKey(), "value", format(category, ranking.get(i).getValue())));
        }
        return String.join("\n", lines);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        add(event.getPlayer(), "broken", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        add(event.getPlayer(), "placed", 1);
    }

    private static Player killerOf(Entity victim) {
        EntityDamageEvent cause = victim.getLastDamageCause();
        if (cause instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player player && player != victim) {
            return player;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        Entity victim = event.getEntity();
        Player killer = killerOf(victim);
        if (event instanceof PlayerDeathEvent playerDeath) {
            Player dead = playerDeath.getEntity();
            add(dead, "deaths", 1);
            set(dead, "streak", 0);
            if (killer != null) {
                add(killer, "kills", 1);
                long streak = get(killer.getName(), "streak") + 1;
                set(killer, "streak", streak);
                if (streak > get(killer.getName(), "best_streak")) {
                    set(killer, "best_streak", streak);
                }
            }
            return;
        }
        if (killer != null) {
            add(killer, "mobs", 1);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        set(player, "joined", Math.max(1, get(player.getName(), "joined")));
        getServer().getScheduler().scheduleDelayedTask(this, () -> {
            if (player.isOnline()) {
                holograms.refresh(player);
            }
        }, 40);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLevelChange(EntityLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            getServer().getScheduler().scheduleDelayedTask(this, () -> {
                if (player.isOnline()) {
                    holograms.refresh(player);
                }
            }, 20);
        }
    }

    private void openStats(Player viewer, String target) {
        String name = stats.getString(key(target) + ".name", target);
        StringBuilder body = new StringBuilder();
        for (String category : categories()) {
            double value = value(target, category);
            String shown = value < 0 ? "-" : format(category, value);
            body.append(msg("stat-entry", "category", categoryName(category), "value", shown)).append('\n');
        }
        body.append('\n').append(msg("stat-click"));
        SimpleForm form = new SimpleForm(msg("stats-title", "player", name), body.toString());
        for (String category : categories()) {
            form.addButton(msg("top-switch", "category", categoryName(category)), p -> openTop(p, category));
        }
        form.send(viewer);
    }

    private void openTop(Player player, String category) {
        List<Map.Entry<String, Double>> ranking = ranking(category);
        int size = Math.max(1, getConfig().getInt("leaderboard-size", 10));
        StringBuilder body = new StringBuilder();
        if (ranking.isEmpty()) {
            body.append(msg("top-empty"));
        }
        for (int i = 0; i < Math.min(size, ranking.size()); i++) {
            body.append(msg("top-line", "rank", i + 1, "player", ranking.get(i).getKey(), "value", format(category, ranking.get(i).getValue()))).append("\n");
        }
        int rank = -1;
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).getKey().equalsIgnoreCase(player.getName())) {
                rank = i + 1;
                break;
            }
        }
        body.append("\n").append(rank > 0 ? msg("top-you", "rank", rank, "value", format(category, ranking.get(rank - 1).getValue())) : msg("top-you-none"));
        SimpleForm form = new SimpleForm(msg("top-title", "category", categoryName(category)), body.toString());
        for (String other : categories()) {
            if (!other.equals(category)) {
                form.addButton(msg("top-switch", "category", categoryName(other)), p -> openTop(p, other));
            }
        }
        form.send(player);
    }

    private void delayed(Player player, Runnable task) {
        getServer().getScheduler().scheduleDelayedTask(this, () -> {
            if (player.isOnline()) {
                task.run();
            }
        }, Math.max(1, getConfig().getInt("command-delay-ticks", 10)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("top")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(prefixed("players-only"));
                return true;
            }
            String category = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : categories().get(0);
            if (!categories().contains(category)) {
                player.sendMessage(prefixed("unknown-category", "categories", String.join(", ", categories())));
                return true;
            }
            delayed(player, () -> openTop(player, category));
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("hologram") && sender.hasPermission("statboard.admin")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(prefixed("players-only"));
                return true;
            }
            String category = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
            if (category.equals("remove")) {
                player.sendMessage(prefixed(holograms.removeNearest(player) ? "hologram-removed" : "hologram-none"));
                return true;
            }
            if (!categories().contains(category)) {
                player.sendMessage(prefixed("unknown-category", "categories", String.join(", ", categories())));
                return true;
            }
            flush();
            holograms.create(player, category);
            player.sendMessage(prefixed("hologram-created", "category", categoryName(category)));
            return true;
        }
        if (sub.equals("reload") && sender.hasPermission("statboard.admin")) {
            reloadConfig();
            holograms.refresh(null);
            sender.sendMessage(prefixed("reloaded"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefixed("players-only"));
            return true;
        }
        String target = args.length > 0 ? args[0] : player.getName();
        if (stats.get(key(target)) == null && !target.equalsIgnoreCase(player.getName())) {
            player.sendMessage(prefixed("unknown-player", "player", target));
            return true;
        }
        snapshotBalances();
        delayed(player, () -> openStats(player, target));
        return true;
    }
}
