package com.skyy.coins.storage;

import com.skyy.coins.manager.CoinsManager;
import com.skyy.coins.manager.CooldownManager;
import com.skyy.coins.manager.TransactionManager;
import com.skyy.coins.manager.ToggleManager;
import com.skyy.coins.model.Transaction;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FileStorage — camada de persistência do sCoins.
 *
 * Suporta dois backends configuráveis via config.yml (storage-type):
 *   • "yaml"  → persiste em coins.yml (padrão, sem dependência externa)
 *   • "mysql" → persiste via HikariCP + MySQL/MariaDB (recomendado em produção)
 *
 * O cache em memória é SEMPRE usado — o banco/arquivo só é acessado para
 * leitura no join e escrita no quit/auto-save/transação.
 */
public class FileStorage {

    // ── Chaves do YAML ──────────────────────────────────────────────────────
    private static final String COINS_KEY      = ".coins";
    private static final String FIRST_JOIN_KEY = ".firstJoin";
    private static final String HISTORY_KEY    = ".history";
    private static final String TOGGLE_KEY     = ".receive";
    private static final String NAME_KEY       = ".name";

    private final JavaPlugin plugin;
    private final CoinsManager coinsManager;
    private final TransactionManager transactionManager;
    private final ToggleManager toggleManager;
    private CooldownManager cooldownManager;
    private final long startingCoins;   // long — consistente com CoinsManager
    private final int  historyMax;

    // ── Backend selecionado ─────────────────────────────────────────────────
    private final boolean useDatabase;
    private DatabaseManager databaseManager;

    // ── Backend YAML ────────────────────────────────────────────────────────
    private File yamlFile;
    private FileConfiguration yamlConfig;
    private final Set<UUID> dirtyProfiles = ConcurrentHashMap.newKeySet();

    // ── Construtor principal ────────────────────────────────────────────────

    public FileStorage(JavaPlugin plugin,
                       CoinsManager coinsManager,
                       TransactionManager transactionManager,
                       ToggleManager toggleManager,
                       long startingCoins,
                       int historyMax,
                       FileConfiguration mainConfig) {
        this.plugin             = plugin;
        this.coinsManager       = coinsManager;
        this.transactionManager = transactionManager;
        this.toggleManager      = toggleManager;
        this.startingCoins      = startingCoins;
        this.historyMax         = historyMax;

        String storageType = mainConfig.getString("storage-type", "yaml").toLowerCase();
        this.useDatabase = storageType.equals("mysql");

        if (useDatabase) {
            plugin.getLogger().info("[sCoins] Backend: MySQL (HikariCP)");
            this.databaseManager = new DatabaseManager(plugin, mainConfig);
            databaseManager.initTables();
        } else {
            plugin.getLogger().info("[sCoins] Backend: YAML (coins.yml)");
            setupYaml();
            buildYamlIndex(); // constrói índice incremental — O(n) apenas no boot
        }
    }

    // ── Construtor legado (compatibilidade) ─────────────────────────────────
    public FileStorage(JavaPlugin plugin,
                       CoinsManager coinsManager,
                       TransactionManager transactionManager,
                       ToggleManager toggleManager,
                       int startingCoins) {
        this(plugin, coinsManager, transactionManager, toggleManager,
             startingCoins, 10, plugin.getConfig());
    }

    /** Injeta o CooldownManager após criação para evitar dependência circular. */
    public void setCooldownManager(CooldownManager cooldownManager) {
        this.cooldownManager = cooldownManager;
    }

    // ── Setup YAML ──────────────────────────────────────────────────────────

    private void setupYaml() {
        yamlFile = new File(plugin.getDataFolder(), "coins.yml");
        if (!yamlFile.exists()) {
            if (!plugin.getDataFolder().mkdirs() && !plugin.getDataFolder().exists()) {
                plugin.getLogger().severe("[sCoins] Falha ao criar o diretório de dados do plugin.");
            }
            try {
                if (!yamlFile.createNewFile()) {
                    plugin.getLogger().warning("[sCoins] coins.yml já existia ao tentar criar.");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("[sCoins] Erro ao criar coins.yml: " + e.getMessage());
            }
        }
        yamlConfig = YamlConfiguration.loadConfiguration(yamlFile);
    }

    // ── loadProfile (onJoin) ────────────────────────────────────────────────

    public void loadProfile(UUID uuid, String name) {
        if (useDatabase) loadProfileFromDatabase(uuid, name);
        else             loadProfileFromYaml(uuid, name);
    }

    private void loadProfileFromDatabase(UUID uuid, String name) {
        Map<String, Object> data = databaseManager.loadPlayer(uuid);
        boolean isNew = (data == null);

        long    coins      = isNew ? startingCoins : (long) data.get("coins");
        boolean canReceive = isNew || (boolean) data.get("toggle");
        long    lastXfer   = isNew ? 0L : (long) data.get("lastTransfer");

        coinsManager.loadProfile(uuid, name, coins);
        toggleManager.set(uuid, canReceive);
        transactionManager.loadHistory(uuid, databaseManager.loadHistory(uuid, historyMax));

        if (cooldownManager != null && lastXfer > 0) {
            cooldownManager.restoreCooldown(uuid, lastXfer);
        }

        if (isNew) {
            // Só persiste se for novo jogador — evitar write desnecessário
            // ao ler dados que acabaram de vir do banco (save redundante eliminado)
            databaseManager.insertPlayer(uuid, name, startingCoins);
        }
        // Jogador existente: os dados são autoritativos do banco — sem re-save aqui.
        // O próximo save acontece no onQuit ou AutoSave.
    }

    private void loadProfileFromYaml(UUID uuid, String name) {
        boolean isNew;
        long    coins;
        boolean canReceive;
        List<Transaction> transactions = new ArrayList<>();

        synchronized (this) {
            isNew      = !yamlConfig.contains(uuid.toString());
            coins      = isNew ? startingCoins : yamlConfig.getLong(uuid + COINS_KEY, 0L);
            canReceive = yamlConfig.getBoolean(uuid + TOGGLE_KEY, true);

            for (String raw : yamlConfig.getStringList(uuid + HISTORY_KEY)) {
                Transaction t = Transaction.deserialize(raw);
                if (t != null) transactions.add(t);
            }

            yamlConfig.set(uuid + NAME_KEY, name);
            if (isNew) {
                yamlConfig.set(uuid + FIRST_JOIN_KEY, true);
                yamlConfig.set(uuid + COINS_KEY, coins);
                saveYamlFile();
            }
        }

        coinsManager.loadProfile(uuid, name, coins);
        transactionManager.loadHistory(uuid, transactions);
        toggleManager.set(uuid, canReceive);
    }

    // ── saveProfile ─────────────────────────────────────────────────────────

    public synchronized void saveProfile(UUID uuid) {
        if (useDatabase) {
            String name = coinsManager.getName(uuid);
            if (name == null) return;
            databaseManager.savePlayer(uuid, name,
                coinsManager.getCoins(uuid), toggleManager.canReceive(uuid));
        } else {
            yamlConfig.set(uuid + COINS_KEY,  coinsManager.getCoins(uuid));
            yamlConfig.set(uuid + TOGGLE_KEY, toggleManager.canReceive(uuid));
            saveHistoryYaml(uuid);
            dirtyProfiles.add(uuid);
        }
    }

    /**
     * Persiste uma única transação imediatamente (MySQL).
     * No YAML o histórico é gerenciado via flush.
     */
    public void persistTransaction(UUID uuid, Transaction t) {
        if (useDatabase) {
            databaseManager.insertTransaction(uuid, t, historyMax);
        }
    }

    /**
     * Salva dados de um perfil a partir de um snapshot pré-capturado.
     * Usado no onQuit para evitar race condition: os dados são capturados
     * enquanto o perfil ainda está em memória, antes de removeProfile().
     * No YAML marca como sujo para o próximo flush.
     */
    public synchronized void saveProfileSnapshot(UUID uuid, String name, long coins, boolean toggle) {
        if (useDatabase) {
            databaseManager.savePlayer(uuid, name, coins, toggle);
        } else {
            yamlConfig.set(uuid + COINS_KEY,  coins);
            yamlConfig.set(uuid + TOGGLE_KEY, toggle);
            yamlConfig.set(uuid + NAME_KEY,   name);
            dirtyProfiles.add(uuid);
        }
    }

    // ── flushDirty (YAML) ───────────────────────────────────────────────────

    public synchronized void flushDirty() {
        if (!useDatabase && !dirtyProfiles.isEmpty()) {
            dirtyProfiles.clear();
            saveYamlFile();
        }
    }

    // ── Consultas offline ───────────────────────────────────────────────────

    public long getCoinsOffline(UUID uuid) {
        if (useDatabase) return databaseManager.getCoinsOffline(uuid);
        return yamlConfig.getLong(uuid + COINS_KEY, 0L);
    }

    public String getNameOffline(UUID uuid) {
        if (useDatabase) return databaseManager.getNameOffline(uuid);
        return yamlConfig.getString(uuid + NAME_KEY, null);
    }

    // ── Top Players ─────────────────────────────────────────────────────────

    private volatile List<String[]> topPlayersCache   = null;
    private volatile long           topPlayersCacheTs = 0L;
    private static final long TOP_CACHE_TTL  = 30_000L;

    public List<String[]> getTopPlayers(int limit) {
        long now = System.currentTimeMillis();
        if (topPlayersCache == null || (now - topPlayersCacheTs) > TOP_CACHE_TTL) {
            synchronized (this) {
                if (topPlayersCache == null || (System.currentTimeMillis() - topPlayersCacheTs) > TOP_CACHE_TTL) {
                    topPlayersCache   = buildTopPlayers();
                    topPlayersCacheTs = System.currentTimeMillis();
                }
            }
        }
        List<String[]> cache = topPlayersCache;
        return cache.subList(0, Math.min(limit, cache.size()));
    }

    public synchronized void expireTopCache()    { topPlayersCacheTs = 0L; }
    public synchronized void expireMagnataCache(){ magnataCacheTs    = 0L; }

    /**
     * Reconstrói o cache do top APENAS a partir dos dados em memória, sem query DB.
     * Seguro para chamar na main thread — usado pelo CoinsManager após setCoins no MySQL.
     *
     * Para MySQL: faz merge dos dados em memória sobre o cache anterior (se existir),
     * garantindo que onRankChange/getTopPlayersFromCacheOnly nunca retornem lista vazia
     * após uma mudança de coins de um jogador online.
     */
    private volatile long lastMemoryCacheRefreshMs = 0L;
    private static final long MEMORY_CACHE_THROTTLE_MS = 500L; // max 2x por segundo

    public synchronized void refreshTopCacheFromMemory() {
        if (!useDatabase) return;

        // Throttle: evita sort+rebuild em toda chamada de setCoins com 300 jogadores online.
        // 500ms é imperceptível para o usuário mas evita 300 sorts/ciclo no RewardTask.
        long now = System.currentTimeMillis();
        if (now - lastMemoryCacheRefreshMs < MEMORY_CACHE_THROTTLE_MS) return;
        lastMemoryCacheRefreshMs = now;

        List<String[]> base = topPlayersCache != null ? new ArrayList<>(topPlayersCache) : new ArrayList<>();

        Map<String, Long> overrides = new LinkedHashMap<>();
        for (UUID uuid : coinsManager.getAllProfiles()) {
            String name  = coinsManager.getName(uuid);
            long   coins = coinsManager.getCoins(uuid);
            if (name != null) overrides.put(name, coins);
        }

        if (!overrides.isEmpty()) {
            base.removeIf(e -> overrides.containsKey(e[0]));
            for (Map.Entry<String, Long> e : overrides.entrySet()) {
                if (e.getValue() > 0) base.add(new String[]{ e.getKey(), String.valueOf(e.getValue()) });
            }
            base.sort((a, b) -> Long.compare(Long.parseLong(b[1]), Long.parseLong(a[1])));
        }

        topPlayersCache   = base;
        topPlayersCacheTs = now;
    }

    /**
     * Retorna o top APENAS do cache em memória — nunca dispara query DB.
     * Seguro para chamar na main thread (usado pelo RankManager.refresh).
     */
    public List<String[]> getTopPlayersFromCacheOnly(int limit) {
        List<String[]> cached = topPlayersCache;
        if (cached == null) return new ArrayList<>();
        return cached.subList(0, Math.min(limit, cached.size()));
    }

    private synchronized List<String[]> buildTopPlayers() {
        if (useDatabase) return buildTopPlayersDatabase();
        return buildTopPlayersYaml();
    }

    private List<String[]> buildTopPlayersDatabase() {
        List<String[]> dbList = databaseManager.getTopPlayers(200);
        Map<String, Long> merged = new LinkedHashMap<>();
        for (String[] row : dbList) merged.put(row[0], Long.parseLong(row[1]));
        for (UUID uuid : coinsManager.getAllProfiles()) {
            String name  = coinsManager.getName(uuid);
            long   coins = coinsManager.getCoins(uuid);
            if (name == null) continue;
            if (coins > 0) merged.put(name, coins);
            else merged.remove(name);
        }
        List<String[]> list = new ArrayList<>();
        for (Map.Entry<String, Long> e : merged.entrySet())
            list.add(new String[]{ e.getKey(), String.valueOf(e.getValue()) });
        list.sort((a, b) -> Long.compare(Long.parseLong(b[1]), Long.parseLong(a[1])));
        return list;
    }

    // ── Índice incremental YAML (só usado no backend YAML) ──────────────────
    // TreeMap<coins, Set<name>> — mantido ordenado; atualizado em O(log n) por setCoins.
    // Evita percorrer TODOS os jogadores do arquivo a cada rebuild do top.
    private final java.util.TreeMap<Long, java.util.Set<String>> yamlTopIndex =
            new java.util.TreeMap<>(java.util.Comparator.reverseOrder());

    /**
     * Atualiza o índice incremental quando o saldo de um jogador muda (YAML only).
     * Chamado pelo CoinsManager via updateYamlIndex() no FileStorage após setCoins.
     */
    public synchronized void updateYamlIndex(String name, long oldCoins, long newCoins) {
        if (useDatabase || name == null) return;
        // Remove da posição antiga
        if (oldCoins > 0) {
            java.util.Set<String> bucket = yamlTopIndex.get(oldCoins);
            if (bucket != null) { bucket.remove(name); if (bucket.isEmpty()) yamlTopIndex.remove(oldCoins); }
        }
        // Insere na nova posição
        if (newCoins > 0) {
            yamlTopIndex.computeIfAbsent(newCoins, k -> new java.util.HashSet<>()).add(name);
        }
        // Reconstrói o cache IMEDIATAMENTE a partir do índice já atualizado.
        // NÃO apenas zeramos — isso faria getTopPlayersFromCacheOnly retornar vazio,
        // causando o bug onde o NPC exibe "Aguardando..." após remover coins de um jogador.
        topPlayersCache   = buildTopPlayersYaml();
        topPlayersCacheTs = System.currentTimeMillis();
    }

    /**
     * Constrói o índice a partir do YAML existente (chamado uma única vez no startup).
     * Complexidade: O(n) — apenas uma vez por boot, ao invés de a cada rebuild.
     */
    private synchronized void buildYamlIndex() {
        if (useDatabase) return;
        yamlTopIndex.clear();
        for (String key : yamlConfig.getKeys(false)) {
            long   coins = yamlConfig.getLong(key + COINS_KEY, 0L);
            String name  = yamlConfig.getString(key + NAME_KEY, null);
            if (coins > 0 && name != null) {
                yamlTopIndex.computeIfAbsent(coins, k -> new java.util.HashSet<>()).add(name);
            }
        }
    }

    private synchronized List<String[]> buildTopPlayersYaml() {
        // Usa o índice já ordenado — O(k) para os top-k, sem varrer o arquivo inteiro
        List<String[]> list = new ArrayList<>();
        outer:
        for (Map.Entry<Long, java.util.Set<String>> entry : yamlTopIndex.entrySet()) {
            for (String name : entry.getValue()) {
                list.add(new String[]{ name, String.valueOf(entry.getKey()) });
                if (list.size() >= 200) break outer;
            }
        }
        // Sobrescreve com dados em memória (jogadores online sempre têm prioridade)
        Map<String, Long> overrides = new HashMap<>();
        for (UUID uuid : coinsManager.getAllProfiles()) {
            String name  = coinsManager.getName(uuid);
            long   coins = coinsManager.getCoins(uuid);
            if (name != null) overrides.put(name, coins);
        }
        if (!overrides.isEmpty()) {
            list.removeIf(e -> overrides.containsKey(e[0]));
            for (Map.Entry<String, Long> e : overrides.entrySet()) {
                if (e.getValue() > 0) list.add(new String[]{ e.getKey(), String.valueOf(e.getValue()) });
            }
            list.sort((a, b) -> Long.compare(Long.parseLong(b[1]), Long.parseLong(a[1])));
        }
        return list;
    }

    // ── Magnata ─────────────────────────────────────────────────────────────

    private volatile String[] magnataCache   = null;
    private volatile long     magnataCacheTs = 0L;
    private static final long MAGNATA_CACHE_TTL = 10_000L;

    public String[] getMagnata() {
        long now = System.currentTimeMillis();
        if (magnataCache == null || (now - magnataCacheTs) > MAGNATA_CACHE_TTL) {
            synchronized (this) {
                if (magnataCache == null || (System.currentTimeMillis() - magnataCacheTs) > MAGNATA_CACHE_TTL) {
                    magnataCache   = buildMagnata();
                    magnataCacheTs = System.currentTimeMillis();
                }
            }
        }
        return magnataCache;
    }

    public synchronized void invalidateMagnataCache() { magnataCache = null; }

    private String[] buildMagnata() {
        if (useDatabase) return buildMagnataDatabase();
        return buildMagnataYaml();
    }

    private String[] buildMagnataDatabase() {
        String[] dbMagnata = databaseManager.getMagnata();
        String topName  = dbMagnata != null ? dbMagnata[0] : null;
        long   topCoins = dbMagnata != null ? Long.parseLong(dbMagnata[1]) : 0L;
        for (UUID uuid : coinsManager.getAllProfiles()) {
            long   coins = coinsManager.getCoins(uuid);
            String name  = coinsManager.getName(uuid);
            if (name != null && coins > topCoins) { topCoins = coins; topName = name; }
        }
        return topName == null ? null : new String[]{ topName, String.valueOf(topCoins) };
    }

    private String[] buildMagnataYaml() {
        // O(1): yamlTopIndex já é TreeMap<coins DESC, Set<nome>>
        // O primeiro entry tem o maior saldo — sem varrer o arquivo inteiro.
        String topName  = null;
        long   topCoins = 0L;

        Map.Entry<Long, java.util.Set<String>> first = yamlTopIndex.isEmpty() ? null : yamlTopIndex.firstEntry();
        if (first != null && !first.getValue().isEmpty()) {
            topCoins = first.getKey();
            topName  = first.getValue().iterator().next();
        }

        // Sobrescreve com dados online — jogadores online têm prioridade sobre YAML em disco
        for (UUID uuid : coinsManager.getAllProfiles()) {
            long   coins = coinsManager.getCoins(uuid);
            String name  = coinsManager.getName(uuid);
            if (name != null && coins > topCoins) {
                topCoins = coins;
                topName  = name;
            }
        }
        return topName == null ? null : new String[]{ topName, String.valueOf(topCoins) };
    }

    // ── saveAll (onDisable / AutoSave) ──────────────────────────────────────

    public synchronized void saveAll() {
        if (useDatabase) saveAllDatabase();
        else             saveAllYaml();
    }

    private void saveAllDatabase() {
        List<DatabaseManager.PlayerSnapshot> snapshots = new ArrayList<>();
        for (UUID uuid : coinsManager.getAllProfiles()) {
            String name = coinsManager.getName(uuid);
            if (name == null) continue;
            long lastXfer = cooldownManager != null ? cooldownManager.getLastTransferTimestamp(uuid) : 0L;
            snapshots.add(new DatabaseManager.PlayerSnapshot(
                uuid, name, coinsManager.getCoins(uuid), toggleManager.canReceive(uuid), lastXfer));
        }
        if (!snapshots.isEmpty()) databaseManager.batchSavePlayers(snapshots);
    }

    private void saveAllYaml() {
        for (UUID uuid : coinsManager.getAllProfiles()) {
            yamlConfig.set(uuid + COINS_KEY,  coinsManager.getCoins(uuid));
            yamlConfig.set(uuid + TOGGLE_KEY, toggleManager.canReceive(uuid));
            saveHistoryYaml(uuid);
        }
        dirtyProfiles.clear();
        saveYamlFile();
        plugin.getLogger().info("[sCoins] Todos os dados de coins foram salvos.");
    }

    // ── Helpers YAML ────────────────────────────────────────────────────────

    private void saveHistoryYaml(UUID uuid) {
        List<String> serialized = new ArrayList<>();
        for (Transaction t : transactionManager.getRawHistory(uuid)) serialized.add(t.serialize());
        yamlConfig.set(uuid + HISTORY_KEY, serialized);
    }

    private void saveYamlFile() {
        try { yamlConfig.save(yamlFile); }
        catch (IOException e) {
            plugin.getLogger().severe("[sCoins] Erro ao salvar coins.yml: " + e.getMessage());
        }
    }

    /**
     * Persiste os coins de um jogador no banco imediatamente (async).
     * Chamado após cada operação de add/remove/set/transfer quando usando MySQL.
     * No YAML isso seria desnecessariamente lento — o auto-save cuida disso.
     */
    public void saveAsync(UUID uuid) {
        if (!useDatabase) return;
        String  name   = coinsManager.getName(uuid);
        long    coins  = coinsManager.getCoins(uuid);
        boolean toggle = toggleManager.canReceive(uuid);
        long lastXfer  = cooldownManager != null ? cooldownManager.getLastTransferTimestamp(uuid) : 0L;
        if (name == null) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
            databaseManager.savePlayer(uuid, name, coins, toggle, lastXfer)
        );
    }

    // ── Shutdown ────────────────────────────────────────────────────────────

    public void close() {
        if (useDatabase && databaseManager != null) databaseManager.close();
    }

    // ── Getters ─────────────────────────────────────────────────────────────

    public boolean isUsingDatabase() { return useDatabase; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public JavaPlugin getPlugin() { return plugin; }
}
