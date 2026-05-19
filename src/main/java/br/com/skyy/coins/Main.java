package br.com.skyy.coins;

import br.com.skyy.coins.api.EconomyAPIHolder;
import br.com.skyy.coins.api.EconomyAPIHolderImpl;
import br.com.skyy.coins.api.EconomyProviderSCoins;
import br.com.skyy.coins.api.SCoinsAPIImpl;
import br.com.skyy.coins.api.SCoinsProvider;
import br.com.skyy.core.SCorePlugin;
import br.com.skyy.coins.commands.CheckCommand;
import br.com.skyy.coins.commands.CoinsCommand;
import br.com.skyy.coins.commands.CoinsTabCompleter;
import br.com.skyy.coins.commands.CommandRegistry;
import br.com.skyy.coins.commands.CommandsConfig;
import br.com.skyy.coins.commands.MoneyCommand;
import br.com.skyy.coins.commands.PayCommand;
import br.com.skyy.coins.commands.RichCommand;
import br.com.skyy.coins.listener.ChatPrefixListener;
import br.com.skyy.coins.listener.CheckListener;
import br.com.skyy.coins.listener.PlayerListener;
import br.com.skyy.coins.manager.*;
import br.com.skyy.coins.util.*;
import br.com.skyy.coins.menu.ExtratoMenu;
import br.com.skyy.coins.menu.ExtratoMenuListener;
import br.com.skyy.coins.menu.HistoryMenu;
import br.com.skyy.coins.menu.HistoryMenuListener;
import br.com.skyy.coins.menu.MainMenu;
import br.com.skyy.coins.menu.MainMenuListener;
import br.com.skyy.coins.menu.TopMenu;
import br.com.skyy.coins.menu.TopMenuListener;
import br.com.skyy.coins.npc.NpcManager;
import br.com.skyy.coins.storage.FileStorage;
import br.com.skyy.coins.storage.MigrationManager;
import br.com.skyy.coins.task.AutoSaveTask;
import br.com.skyy.coins.task.ReconnectTask;
import br.com.skyy.coins.task.RewardTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Main extends JavaPlugin {

    // Static para acesso global sem precisar passar instância — padrão de plugins profissionais
    private static boolean debugMode = false;

    /** Retorna true se debug-mode está ativo. Usado em qualquer classe com Main.isDebug(). */
    public static boolean isDebug() { return debugMode; }

    /** Loga uma mensagem apenas quando debug-mode: true — não polui o console em produção. */
    public static void debug(java.util.logging.Logger log, String msg) {
        if (debugMode) log.info("[sCoins DEBUG] " + msg);
    }

    private CoinsManager coinsManager;
    private FileStorage fileStorage;
    private Messages messages;
    private MenuConfig menuConfig;
    private CommandsConfig commandsConfig;
    private CooldownManager cooldownManager;
    private TransactionManager transactionManager;
    private ToggleManager toggleManager;
    private NpcManager npcManager;
    private CheckManager checkManager;


    @Override
    public void onEnable() {
        // Detecta versão do servidor — deve ser o primeiro passo
        VersionUtil.init();

        saveDefaultConfig();
        // Garante que chaves novas da config padrão sejam adicionadas
        // automaticamente ao config.yml do servidor sem sobrescrever os valores existentes
        getConfig().options().copyDefaults(true);
        saveConfig();

        // Lê debug-mode o quanto antes para logar o restante do boot se necessário
        debugMode = getConfig().getBoolean("debug-mode", false);
        if (debugMode) getLogger().info("[sCoins] Modo de depuração ATIVADO.");

        // Carrega menus.yml
        this.menuConfig = new MenuConfig(this);

        // Carrega commands.yml — aliases configuráveis pelo admin
        this.commandsConfig = new CommandsConfig(this);

        long maxCoins         = getConfig().getLong("max-coins", 2_000_000_000L);
        // coin-preferences.initial-amount tem prioridade sobre o legado starting-coins
        long startingCoins    = getConfig().getLong("coin-preferences.initial-amount",
                                    getConfig().getLong("starting-coins", 0L));
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

        // Task periódica do magnata — verifica e notifica a cada top-delay segundos
        int topDelay = getConfig().getInt("top-delay", 600);
        long topDelayTicks = topDelay * 20L;
        final MagnataManager magnataManagerFinal = magnataManager;
        getServer().getScheduler().runTaskTimer(this, () ->
                        magnataManagerFinal.check(fileStorage),
                topDelayTicks, topDelayTicks);
        getLogger().info("[sCoins] Verificação de magnata ativa a cada " + topDelay + "s.");

        // NpcManager — lê settings.enabled do npcs.yml
        // Funciona com Citizens (NPC realista) ou ArmorStand (fallback — sem dependência)
        File npcFile = new File(getDataFolder(), "npcs.yml");
        if (!npcFile.exists()) saveResource("npcs.yml", false);
        FileConfiguration npcConfig = YamlConfiguration.loadConfiguration(npcFile);
        if (npcConfig.getBoolean("settings.enabled", true)) {
            this.npcManager = new NpcManager(this, fileStorage);
            rankManager.setNpcManager(this.npcManager);
            getServer().getScheduler().runTaskLater(this, this.npcManager::spawnAll, 1L);
            getLogger().info("Sistema de NPCs ativado [" + SCorePlugin.getInstance().getNPCManager().getProvider().getProviderName() + "].");
        }

        SCoinsProvider.register(new SCoinsAPIImpl(coinsManager, transactionManager, toggleManager, fileStorage, rankManager));
        getLogger().info("sCoins API registrada — SCoinsProvider.get() disponível.");

        // Registra EconomyAPIHolder via BukkitServicesManager (padrão Vault)
        // Outros plugins obtêm via: Bukkit.getServicesManager().getRegistration(EconomyAPIHolder.class)
        EconomyAPIHolderImpl economyHolder = new EconomyAPIHolderImpl(
                coinsManager, transactionManager, fileStorage, rankManager, getConfig());
        getServer().getServicesManager().register(
                EconomyAPIHolder.class,
                economyHolder,
                this,
                ServicePriority.Normal);
        getLogger().info("[sCoins] EconomyAPIHolder registrado no ServicesManager.");

        // ── Vault Economy (soft-depend) ──────────────────────────────────────
        // Registra o sCoins como provedor de economia no Vault.
        // Isso garante compatibilidade com ShopGUI+, Jobs Reborn, EssentialsX,
        // AuctionHouse e qualquer plugin que use a API Vault de economy.
        // Se o Vault não estiver instalado, este bloco é simplesmente ignorado.
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            getServer().getServicesManager().register(
                    net.milkbowl.vault.economy.Economy.class,
                    new br.com.skyy.coins.api.VaultEconomy(coinsManager, fileStorage, getConfig()),
                    this,
                    ServicePriority.Normal);
            getLogger().info("[sCoins] Vault detectado → sCoins registrado como provedor de Economy.");
        } else {
            getLogger().info("[sCoins] Vault não encontrado → integração Vault desativada (opcional).");
        }

        // Registra sCoins como provedor de economia no sCore
        // Outros plugins podem usar: SCorePlugin.getInstance().getEconomyManager().deposit(player, 100, "scoins")
        SCorePlugin.getInstance().getEconomyManager().register(new EconomyProviderSCoins(coinsManager));
        getLogger().info("[sCoins] Provedor de economia registrado no sCore → 'scoins'.");

        // ── Integrações com soft-depends ────────────────────────────────
        // PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SCoinsExpansion(coinsManager, rankManager, fileStorage, getConfig()).register();
            getLogger().info("[sCoins] PlaceholderAPI detectado → placeholders %scoins_*% registrados.");
        } else {
            getLogger().info("[sCoins] PlaceholderAPI não encontrado → placeholders desativados (opcional).");
        }

        // Citizens (NPCs) — gerenciado pelo sCore NPCManager com fallback ArmorStand
        if (getServer().getPluginManager().getPlugin("Citizens") != null) {
            getLogger().info("[sCoins] Citizens detectado → NPCs usarão Citizens.");
        } else {
            getLogger().info("[sCoins] Citizens não encontrado → NPCs usarão ArmorStand (fallback nativo).");
        }

        // HolographicDisplays — não utilizado; o plugin usa TextDisplay nativo do Paper 1.19.4+
        if (getServer().getPluginManager().getPlugin("HolographicDisplays") != null) {
            getLogger().info("[sCoins] HolographicDisplays detectado → holograms usam TextDisplay nativo (sem conflito).");
        }

        // Chat plugins — prefixo de medalha no chat
        String chatPlugin = ChatIntegration.getDetectedPlugin();
        if (chatPlugin != null) {
            getLogger().info("[sCoins] Plugin de chat detectado: " + chatPlugin
                    + " → listener interno desativado.");
            getLogger().info("[sCoins] Adicione %scoins_chat_prefix% no formato de mensagem do " + chatPlugin + " para exibir medalhas.");
        } else {
            // Nenhum plugin de chat → usa o listener interno do sCoins
            getServer().getPluginManager().registerEvents(new ChatPrefixListener(fileStorage, getConfig()), this);
            getLogger().info("[sCoins] Nenhum plugin de chat detectado → tag {magnata} ativa no chat.");
        }

        // Sistema de cheques — deve ser criado ANTES do CoinsCommand que o recebe
        this.checkManager = new CheckManager(coinsManager, transactionManager, fileStorage, getConfig());
        getServer().getPluginManager().registerEvents(new CheckListener(checkManager, messages), this);
        getLogger().info("Sistema de cheques ativado.");

        PluginCommand coinsCmd = getCommand("coins");
        CoinsCommand coinsExecutor = new CoinsCommand(coinsManager, fileStorage, messages, cooldownManager, transactionManager, toggleManager, getConfig(), this, minTransfer, this.npcManager, menuConfig, this.checkManager);
        CoinsTabCompleter coinsTabCompleter = new CoinsTabCompleter(this.npcManager);
        if (coinsCmd != null) {
            coinsCmd.setExecutor(coinsExecutor);
            coinsCmd.setTabCompleter(coinsTabCompleter);
        } else {
            getLogger().severe("Comando 'coins' não encontrado no plugin.yml!");
        }

        // ── Comandos dinâmicos via commands.yml ──────────────────────────────
        // Cada comando delega para o CoinsCommand central — sem duplicação de lógica.
        CommandRegistry.register(this, commandsConfig, CommandsConfig.CommandType.MONEY,
                new MoneyCommand(coinsExecutor),
                "Veja ou gerencie seus coins.",
                "/<command> [jogador]",
                coinsTabCompleter);

        CommandRegistry.register(this, commandsConfig, CommandsConfig.CommandType.RICH,
                new RichCommand(coinsExecutor),
                "Veja os jogadores mais ricos do servidor.",
                "/<command>",
                null);

        CommandRegistry.register(this, commandsConfig, CommandsConfig.CommandType.PAY,
                new PayCommand(coinsExecutor),
                "Envie coins para outro jogador.",
                "/<command> <jogador> <valor>",
                new PayCommand(coinsExecutor));

        CommandRegistry.register(this, commandsConfig, CommandsConfig.CommandType.CHECK,
                new CheckCommand(coinsExecutor),
                "Emita um cheque de coins.",
                "/<command> <valor>",
                new CheckCommand(coinsExecutor));


        FileConfiguration mc = menuConfig.get();
        getServer().getPluginManager().registerEvents(new PlayerListener(coinsManager, fileStorage, cooldownManager, transactionManager, toggleManager, rankManager, this), this);
        getServer().getPluginManager().registerEvents(new HistoryMenuListener(coinsManager, transactionManager, toggleManager, fileStorage, mc), this);
        getServer().getPluginManager().registerEvents(new ExtratoMenuListener(coinsManager, transactionManager, toggleManager, fileStorage, mc), this);
        getServer().getPluginManager().registerEvents(new MainMenuListener(coinsManager, transactionManager, toggleManager, fileStorage, messages, mc), this);
        getServer().getPluginManager().registerEvents(new TopMenuListener(coinsManager, transactionManager, toggleManager, fileStorage, mc), this);


        boolean rewardEnabled = getConfig().getBoolean("reward-enabled", true);
        if (rewardEnabled) {
            int rewardInterval = getConfig().getInt("reward-interval", 5);
            long rewardAmount   = getConfig().getLong("reward-amount", 10);
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
        getServer().getServicesManager().unregisterAll(this);
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
        if (commandsConfig != null)  commandsConfig.load();
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

