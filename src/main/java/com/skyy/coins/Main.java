package com.skyy.coins;

import com.skyy.coins.api.SCoinsAPIImpl;
import com.skyy.coins.api.SCoinsProvider;
import com.skyy.coins.commands.CoinsCommand;
import com.skyy.coins.commands.CoinsTabCompleter;
import com.skyy.coins.listener.PlayerListener;
import com.skyy.coins.manager.*;
import com.skyy.coins.menu.ExtratoMenu;
import com.skyy.coins.menu.ExtratoMenuListener;
import com.skyy.coins.menu.HistoryMenu;
import com.skyy.coins.menu.HistoryMenuListener;
import com.skyy.coins.menu.MainMenu;
import com.skyy.coins.menu.MainMenuListener;
import com.skyy.coins.menu.TopMenu;
import com.skyy.coins.menu.TopMenuListener;
import com.skyy.coins.npc.NpcManager;
import com.skyy.coins.storage.FileStorage;
import com.skyy.coins.storage.MigrationManager;
import com.skyy.coins.task.AutoSaveTask;
import com.skyy.coins.task.ReconnectTask;
import com.skyy.coins.task.RewardTask;
import com.skyy.coins.util.*;
import org.bukkit.command.PluginCommand;import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Main extends JavaPlugin {

    private CoinsManager coinsManager;
    private FileStorage fileStorage;
    private Messages messages;
    private MenuConfig menuConfig;
    private CooldownManager cooldownManager;
    private TransactionManager transactionManager;
    private ToggleManager toggleManager;
    private NpcManager npcManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Garante que chaves novas da config padrão sejam adicionadas
        // automaticamente ao config.yml do servidor sem sobrescrever os valores existentes
        getConfig().options().copyDefaults(true);
        saveConfig();

        // Carrega menus.yml
        this.menuConfig = new MenuConfig(this);

        long maxCoins         = getConfig().getLong("max-coins", 2_000_000_000L);
        long startingCoins    = getConfig().getLong("starting-coins", 0L);
        int  minTransfer      = getConfig().getInt("min-transfer", 1);
        int  transferCooldown = getConfig().getInt("transfer-cooldown", 30);
        int  historyMax       = getConfig().getInt("history-max-entries", 10);

        CoinsFormatter.load(getConfig());
        SoundUtil.load(getConfig());

        this.messages           = new Messages(getConfig());
        this.coinsManager       = new CoinsManager(maxCoins);
        this.cooldownManager    = new CooldownManager(transferCooldown);
        this.transactionManager = new TransactionManager(historyMax);
        this.toggleManager      = new ToggleManager();

        try {
            this.fileStorage = new FileStorage(this, coinsManager, transactionManager,
                    toggleManager, startingCoins, historyMax, getConfig());
        } catch (Exception e) {
            getLogger().severe("══════════════════════════════════════════════════");
            getLogger().severe("[sCoins] FALHA CRÍTICA ao inicializar o storage!");
            getLogger().severe("[sCoins] Causa: " + e.getMessage());
            getLogger().severe("[sCoins] Verifique as configurações de database no config.yml.");
            getLogger().severe("[sCoins] O plugin será desativado para evitar perda de dados.");
            getLogger().severe("══════════════════════════════════════════════════");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.fileStorage.setCooldownManager(this.cooldownManager);
        this.toggleManager.setFileStorage(this.fileStorage);   // persiste toggle imediatamente em ambos os backends
        transactionManager.setFileStorage(this.fileStorage);

        // Migração automática YAML → MySQL — roda async para não travar o startup
        if (fileStorage.isUsingDatabase()) {
            final int hMax = historyMax;
            getServer().getScheduler().runTaskAsynchronously(this, () ->
                new MigrationManager(this, fileStorage.getDatabaseManager()).runIfNeeded(hMax)
            );
        }

        MagnataManager magnataManager = new MagnataManager(coinsManager, getConfig());
        coinsManager.setMagnataManager(magnataManager, fileStorage);

        RankManager rankManager = new RankManager(coinsManager, fileStorage, getConfig());
        coinsManager.setRankManager(rankManager);

        // NpcManager — lê settings.enabled do npcs.yml
        File npcFile = new File(getDataFolder(), "npcs.yml");
        if (!npcFile.exists()) saveResource("npcs.yml", false);
        FileConfiguration npcConfig = YamlConfiguration.loadConfiguration(npcFile);
        if (npcConfig.getBoolean("settings.enabled", true)) {
            this.npcManager = new NpcManager(this, fileStorage);
            rankManager.setNpcManager(this.npcManager);
            getServer().getScheduler().runTaskLater(this, this.npcManager::spawnAll, 1L);
            getLogger().info("Sistema de NPCs ativado.");
        }

        SCoinsProvider.register(new SCoinsAPIImpl(coinsManager, transactionManager, toggleManager, fileStorage, rankManager));
        getLogger().info("sCoins API registrada — SCoinsProvider.get() disponível.");

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SCoinsExpansion(coinsManager, rankManager, fileStorage).register();
            getLogger().info("PlaceholderAPI detectado — placeholders %scoins_*% registrados.");
        }

        PluginCommand coinsCmd = getCommand("coins");
        if (coinsCmd != null) {
            coinsCmd.setExecutor(new CoinsCommand(coinsManager, fileStorage, messages, cooldownManager, transactionManager, toggleManager, getConfig(), this, minTransfer, this.npcManager, menuConfig));
            coinsCmd.setTabCompleter(new CoinsTabCompleter(this.npcManager));
        } else {
            getLogger().severe("Comando 'coins' não encontrado no plugin.yml!");
        }


        FileConfiguration mc = menuConfig.get();
        getServer().getPluginManager().registerEvents(new PlayerListener(coinsManager, fileStorage, cooldownManager, transactionManager, toggleManager, rankManager, this), this);
        getServer().getPluginManager().registerEvents(new HistoryMenuListener(coinsManager, transactionManager, toggleManager, fileStorage, mc), this);
        getServer().getPluginManager().registerEvents(new ExtratoMenuListener(coinsManager, transactionManager, toggleManager, fileStorage, mc), this);
        getServer().getPluginManager().registerEvents(new MainMenuListener(coinsManager, transactionManager, toggleManager, fileStorage, messages, mc), this);
        getServer().getPluginManager().registerEvents(new TopMenuListener(coinsManager, transactionManager, toggleManager, fileStorage, mc), this);

        boolean rewardEnabled = getConfig().getBoolean("reward-enabled", true);
        if (rewardEnabled) {
            int rewardInterval = getConfig().getInt("reward-interval", 5);
            int rewardAmount   = getConfig().getInt("reward-amount", 10);
            long intervalTicks = rewardInterval * 60L * 20L;
            new RewardTask(coinsManager, transactionManager, fileStorage, messages, rewardAmount, rewardInterval, this)
                    .runTaskTimer(this, intervalTicks, intervalTicks);
            getLogger().info("Recompensa por tempo online ativa: " + rewardAmount + " coins a cada " + rewardInterval + " min.");
        }

        // Auto-save a cada N minutos como segurança contra crashes
        int autoSaveMinutes = getConfig().getInt("auto-save-interval", 5);
        long autoSaveTicks = autoSaveMinutes * 60L * 20L;
        new AutoSaveTask(fileStorage).runTaskTimerAsynchronously(this, autoSaveTicks, autoSaveTicks);
        getLogger().info("Auto-save ativo a cada " + autoSaveMinutes + " minuto(s).");

        // Reconexão automática com o banco (MySQL only) — verifica a cada 30 segundos
        if (fileStorage.isUsingDatabase()) {
            long reconnectTicks = 30L * 20L; // 30 segundos
            new ReconnectTask(fileStorage.getDatabaseManager())
                    .runTaskTimerAsynchronously(this, reconnectTicks, reconnectTicks);
            getLogger().info("[sCoins] Monitor de reconexão MySQL ativo (30s).");
        }

        getLogger().info("sCoins ativado com sucesso!");

        // Pré-computa os títulos dos menus — evita race condition se dois jogadores
        // abrirem menus simultaneamente e sobrescreverem o campo static TITLE
        FileConfiguration mc2 = menuConfig.get();
        MainMenu.loadTitle(mc2);
        HistoryMenu.loadTitle(mc2);
        ExtratoMenu.loadTitle(mc2);
        TopMenu.loadTitle(mc2);
    }

    @Override
    public void onDisable() {
        if (npcManager != null) npcManager.removeAll();
        if (fileStorage != null) {
            fileStorage.saveAll();
            fileStorage.close(); // fecha pool HikariCP se MySQL
        }
        if (transactionManager != null) transactionManager.shutdown();
        SCoinsProvider.unregister();
        getLogger().info("sCoins desativado. Dados salvos.");
    }

    public void reload() {
        reloadConfig();
        // NÃO chamamos saveConfig() aqui — isso evitaria sobrescrever as alterações
        // feitas pelo admin no config.yml do servidor (ex: storage-type, database.password).
        // copyDefaults só é necessário no primeiro boot (onEnable).
        CoinsFormatter.load(getConfig());
        SoundUtil.load(getConfig());
        if (messages != null)        messages.reload(getConfig());
        if (menuConfig != null)      menuConfig.reload();
        if (cooldownManager != null) cooldownManager.setCooldownSeconds(getConfig().getInt("transfer-cooldown", 30));
        if (npcManager != null)      npcManager.reloadConfig();
        // Recalcula títulos dos menus após reload da config
        FileConfiguration mc = menuConfig.get();
        MainMenu.loadTitle(mc);
        HistoryMenu.loadTitle(mc);
        ExtratoMenu.loadTitle(mc);
        TopMenu.loadTitle(mc);
        getLogger().info("sCoins recarregado com sucesso.");
    }
}