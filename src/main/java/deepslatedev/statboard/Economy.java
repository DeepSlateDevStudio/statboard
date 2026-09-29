package deepslatedev.statboard;

import org.powernukkitx.utils.Config;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Locale;

public final class Economy {
    private final File folder;
    private Object api;
    private String backend;
    private Config fallback;

    public Economy(File folder) {
        this.folder = folder;
    }

    private void detect() {
        if (backend != null) {
            return;
        }
        String[][] candidates = {
                {"net.lldv.llamaeconomy.LlamaEconomy", "getAPI", "LlamaEconomy"},
                {"me.onebone.economyapi.EconomyAPI", "getInstance", "EconomyAPI"},
                {"psycofeu.economy.FrenchEconomy", "getWalletManager", "FrenchEconomy"},
        };
        for (String[] candidate : candidates) {
            try {
                Object found = Class.forName(candidate[0]).getMethod(candidate[1]).invoke(null);
                if (found != null) {
                    api = found;
                    backend = candidate[2];
                    return;
                }
            } catch (Throwable ignored) {
                api = null;
            }
        }
        backend = "built-in";
        fallback = new Config(new File(folder, "balances.yml"), Config.YAML);
    }

    public String name() {
        detect();
        return backend;
    }

    private static Object invoke(Object target, String method, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            for (Method m : target.getClass().getMethods()) {
                if (!m.getName().equals(method) || m.getParameterCount() != args.length) {
                    continue;
                }
                Class<?>[] types = m.getParameterTypes();
                boolean ok = true;
                for (int i = 0; i < types.length; i++) {
                    if (args[i] instanceof String && types[i] != String.class) {
                        ok = false;
                    }
                    if (args[i] instanceof Double && types[i] != double.class && types[i] != Double.class) {
                        ok = false;
                    }
                }
                if (ok) {
                    return m.invoke(target, args);
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
        return null;
    }

    private Object wallet(String player, boolean create) {
        Object wallet = invoke(api, "getWallet", player);
        if (wallet == null && create) {
            invoke(api, "loadPlayer", player);
            wallet = invoke(api, "getWallet", player);
        }
        return wallet;
    }

    public long balance(String player) {
        detect();
        Object value = switch (backend) {
            case "LlamaEconomy" -> invoke(api, "getMoney", player);
            case "EconomyAPI" -> invoke(api, "myMoney", player);
            case "FrenchEconomy" -> invoke(wallet(player, false), "getMoney");
            default -> fallback.getLong(player.toLowerCase(Locale.ROOT), 0L);
        };
        return value instanceof Number n ? (long) n.doubleValue() : 0L;
    }

    public boolean take(String player, long amount) {
        detect();
        if (amount <= 0) {
            return true;
        }
        if (balance(player) < amount) {
            return false;
        }
        switch (backend) {
            case "LlamaEconomy", "EconomyAPI" -> invoke(api, "reduceMoney", player, (double) amount);
            case "FrenchEconomy" -> invoke(wallet(player, true), "reduceMoney", (double) amount);
            default -> {
                fallback.set(player.toLowerCase(Locale.ROOT), balance(player) - amount);
                fallback.save();
            }
        }
        return true;
    }

    public void give(String player, long amount) {
        detect();
        if (amount <= 0) {
            return;
        }
        switch (backend) {
            case "LlamaEconomy", "EconomyAPI" -> invoke(api, "addMoney", player, (double) amount);
            case "FrenchEconomy" -> invoke(wallet(player, true), "addMoney", (double) amount);
            default -> {
                fallback.set(player.toLowerCase(Locale.ROOT), balance(player) + amount);
                fallback.save();
            }
        }
    }
}
