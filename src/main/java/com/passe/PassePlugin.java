package com.passe;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PassePlugin extends JavaPlugin {

    static final String PREFIX = "&8[&6Passe&8] &r";

    /** Um nivel do passe (lido do config.yml). */
    static class Nivel {
        int numero;
        int minutos;
        Map<String, Integer> recompensas = new LinkedHashMap<String, Integer>();
        List<String> extras = new ArrayList<String>();
    }

    private final Map<UUID, PlayerData> data = new HashMap<UUID, PlayerData>();
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<UUID, Long>();
    private final TreeMap<Integer, Nivel> niveis = new TreeMap<Integer, Nivel>();
    private File dataFile;
    private PasseGui gui;
    private boolean dirty = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        dataFile = new File(getDataFolder(), "playtime.yml");
        loadNiveis();
        loadData();

        gui = new PasseGui(this);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(new ActivityListener(this), this);

        // quem ja estava online (ex: /reload) conta como ativo agora
        for (Player p : getServer().getOnlinePlayers()) {
            touch(p);
        }

        // a cada 1 minuto: soma tempo de jogo
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                tickMinute();
            }
        }, 1200L, 1200L);

        // a cada 5 minutos: salva no arquivo
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                if (dirty) {
                    saveData();
                }
            }
        }, 6000L, 6000L);

        getLogger().info(niveis.size() + " nivel(is) carregado(s).");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    // ------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------

    static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private void msg(CommandSender to, String text) {
        to.sendMessage(c(text));
    }

    static String formatTempo(int minutos) {
        if (minutos < 0) {
            minutos = 0;
        }
        int h = minutos / 60;
        int m = minutos % 60;
        if (h > 0) {
            return h + "h " + m + "min";
        }
        return m + "min";
    }

    void touch(Player p) {
        lastActive.put(p.getUniqueId(), System.currentTimeMillis());
    }

    void forget(Player p) {
        lastActive.remove(p.getUniqueId());
    }

    PlayerData getData(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        if (d == null) {
            d = new PlayerData();
            data.put(p.getUniqueId(), d);
        }
        d.name = p.getName();
        return d;
    }

    List<Nivel> getNiveis() {
        return new ArrayList<Nivel>(niveis.values());
    }

    // ------------------------------------------------------------
    // Configuracao e dados
    // ------------------------------------------------------------

    private void loadNiveis() {
        niveis.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("niveis");
        if (sec == null) {
            getLogger().warning("Nenhum nivel encontrado em config.yml (secao 'niveis').");
            return;
        }
        for (String key : sec.getKeys(false)) {
            int numero;
            try {
                numero = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                getLogger().warning("Nivel ignorado (nome precisa ser numero): " + key);
                continue;
            }
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            Nivel n = new Nivel();
            n.numero = numero;
            n.minutos = s.getInt("minutos", 0);
            ConfigurationSection r = s.getConfigurationSection("recompensas");
            if (r != null) {
                for (String rk : r.getKeys(false)) {
                    n.recompensas.put(rk, r.getInt(rk));
                }
            }
            n.extras = s.getStringList("comandos-extra");
            niveis.put(numero, n);
        }
    }

    private void loadData() {
        data.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection root = yml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            PlayerData d = new PlayerData();
            d.name = s.getString("name", "?");
            d.minutes = s.getInt("minutes", 0);
            d.claimed.addAll(s.getIntegerList("claimed"));
            data.put(id, d);
        }
    }

    void saveData() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerData> e : data.entrySet()) {
            String base = "players." + e.getKey().toString() + ".";
            PlayerData d = e.getValue();
            yml.set(base + "name", d.name);
            yml.set(base + "minutes", d.minutes);
            yml.set(base + "claimed", new ArrayList<Integer>(d.claimed));
        }
        try {
            yml.save(dataFile);
            dirty = false;
        } catch (IOException ex) {
            getLogger().severe("Erro ao salvar playtime.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------
    // Tempo de jogo
    // ------------------------------------------------------------

    private void tickMinute() {
        int afk = getConfig().getInt("afk-minutos", 5);
        long now = System.currentTimeMillis();
        for (Player p : getServer().getOnlinePlayers()) {
            if (afk > 0) {
                Long last = lastActive.get(p.getUniqueId());
                if (last == null || now - last > afk * 60000L) {
                    continue; // AFK
                }
            }
            PlayerData d = getData(p);
            d.minutes++;
            dirty = true;
            for (Nivel n : niveis.values()) {
                if (n.minutos == d.minutes) {
                    msg(p, PREFIX + "&aVoc\u00ea desbloqueou o &fn\u00edvel " + n.numero
                            + " &ado passe! Use &f/passe &apara resgatar.");
                }
            }
        }
    }

    // ------------------------------------------------------------
    // Recompensas
    // ------------------------------------------------------------

    /** Linhas de texto descrevendo as recompensas (para o menu). */
    List<String> describe(Nivel n) {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : n.recompensas.entrySet()) {
            String tpl = getConfig().getString("nomes." + e.getKey(), e.getKey() + ": %valor%");
            out.add(tpl.replace("%valor%", String.valueOf(e.getValue())));
        }
        if (!n.extras.isEmpty()) {
            out.add("&7(+ b\u00f4nus extra)");
        }
        return out;
    }

    private void runConsole(String command, Player p) {
        String cmd = command.replace("%player%", p.getName());
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } catch (Exception ex) {
            getLogger().warning("Erro ao executar '" + cmd + "': " + ex.getMessage());
        }
    }

    private void giveRewards(Player p, Nivel n) {
        for (Map.Entry<String, Integer> e : n.recompensas.entrySet()) {
            String key = e.getKey();
            int valor = e.getValue();
            if (key.equals("xp-pontos")) {
                p.giveExp(valor);
            } else if (key.equals("xp-niveis")) {
                p.giveExpLevels(valor);
            } else {
                String tpl = getConfig().getString("comandos." + key);
                if (tpl == null) {
                    getLogger().warning("Recompensa '" + key + "' sem comando em config.yml (comandos." + key + ")");
                    continue;
                }
                runConsole(tpl.replace("%valor%", String.valueOf(valor)), p);
            }
        }
        for (String extra : n.extras) {
            runConsole(extra, p);
        }
    }

    /** Tenta resgatar um nivel (mostra mensagem se nao puder). */
    void tryClaim(Player p, Nivel n) {
        PlayerData d = getData(p);
        if (d.claimed.contains(n.numero)) {
            msg(p, PREFIX + "&cVoc\u00ea j\u00e1 resgatou o n\u00edvel " + n.numero + ".");
            return;
        }
        if (d.minutes < n.minutos) {
            msg(p, PREFIX + "&cFaltam &f" + formatTempo(n.minutos - d.minutes) + " &cpara o n\u00edvel " + n.numero + ".");
            return;
        }
        d.claimed.add(n.numero);
        dirty = true;
        giveRewards(p, n);
        saveData();
        msg(p, PREFIX + "&aN\u00edvel &f" + n.numero + " &aresgatado!");
    }

    private void claimAll(Player p) {
        PlayerData d = getData(p);
        int count = 0;
        for (Nivel n : niveis.values()) {
            if (d.minutes >= n.minutos && !d.claimed.contains(n.numero)) {
                d.claimed.add(n.numero);
                giveRewards(p, n);
                count++;
            }
        }
        if (count == 0) {
            msg(p, PREFIX + "&cNenhuma recompensa dispon\u00edvel agora.");
            return;
        }
        dirty = true;
        saveData();
        msg(p, PREFIX + "&aVoc\u00ea resgatou &f" + count + " &an\u00edvel(is)!");
    }

    // ------------------------------------------------------------
    // Comandos
    // ------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if (sub.equals("reload") || sub.equals("addtempo") || sub.equals("reset")) {
                admin(sender, sub, args);
                return true;
            }
        }
        if (!(sender instanceof Player)) {
            msg(sender, PREFIX + "&cApenas jogadores podem usar este comando.");
            return true;
        }
        Player p = (Player) sender;
        if (!p.hasPermission("passe.usar")) {
            msg(p, PREFIX + "&cSem permiss\u00e3o.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("resgatar")) {
            claimAll(p);
            return true;
        }
        gui.open(p);
        return true;
    }

    private void admin(CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission("passe.admin")) {
            msg(sender, PREFIX + "&cSem permiss\u00e3o.");
            return;
        }
        if (sub.equals("reload")) {
            reloadConfig();
            loadNiveis();
            msg(sender, PREFIX + "&aConfig recarregada (" + niveis.size() + " n\u00edveis).");
            return;
        }
        if (args.length < 2) {
            msg(sender, PREFIX + "&eUso: /passe addtempo <nick> <minutos>  |  /passe reset <nick>  |  /passe reload");
            return;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null) {
            msg(sender, PREFIX + "&cJogador offline.");
            return;
        }
        PlayerData d = getData(t);
        if (sub.equals("reset")) {
            d.minutes = 0;
            d.claimed.clear();
            msg(sender, PREFIX + "&aPasse de &f" + t.getName() + " &aresetado.");
        } else {
            if (args.length < 3) {
                msg(sender, PREFIX + "&eUso: /passe addtempo <nick> <minutos>");
                return;
            }
            int add;
            try {
                add = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                msg(sender, PREFIX + "&cN\u00famero inv\u00e1lido.");
                return;
            }
            d.minutes = Math.max(0, d.minutes + add);
            msg(sender, PREFIX + "&aTempo de &f" + t.getName() + " &aagora: &f" + formatTempo(d.minutes));
        }
        dirty = true;
        saveData();
    }
}
