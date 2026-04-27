package com.skyy.coins.storage;

import com.skyy.coins.model.Transaction;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * MigrationManager — migra dados do coins.yml para o banco MySQL.
 *
 * Executado automaticamente no onEnable quando:
 *   1. storage-type = "mysql"
 *   2. coins.yml existe e contém dados
 *   3. migration.yml NÃO existe (evita migração dupla)
 *
 * Após migrar com sucesso, cria migration.yml como "lock",
 * e renomeia coins.yml para coins.yml.bak (segurança total).
 */
public class MigrationManager {

    private static final String COINS_KEY   = ".coins";
    private static final String NAME_KEY    = ".name";
    private static final String TOGGLE_KEY  = ".receive";
    private static final String HISTORY_KEY = ".history";

    private final JavaPlugin     plugin;
    private final Logger         log;
    private final DatabaseManager db;

    public MigrationManager(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
        this.db     = db;
    }

    /**
     * Verifica se a migração é necessária e a executa.
     * Retorna true se migrou dados com sucesso, false se pulou.
     */
    public boolean runIfNeeded(int historyMax) {
        File coinsFile     = new File(plugin.getDataFolder(), "coins.yml");
        File migrationLock = new File(plugin.getDataFolder(), "migration.yml");

        // Se o lock já existe, migração já foi feita — pula
        if (migrationLock.exists()) {
            log.info("[sCoins-Migration] Migração já realizada anteriormente. Pulando.");
            return false;
        }

        // Se coins.yml não existe, não há o que migrar
        if (!coinsFile.exists()) {
            createLock(migrationLock, 0);
            return false;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(coinsFile);

        // Verifica se há dados reais no arquivo
        if (yaml.getKeys(false).isEmpty()) {
            createLock(migrationLock, 0);
            return false;
        }

        log.info("[sCoins-Migration] Iniciando migração de coins.yml → MySQL...");
        int migrated = 0;
        int failed   = 0;

        for (String key : yaml.getKeys(false)) {
            try {
                UUID   uuid  = UUID.fromString(key);
                String  name   = yaml.getString(key + NAME_KEY, "Desconhecido");
                long    coins  = yaml.getLong(key + COINS_KEY, 0L);  // long — evita truncamento acima de 2.1B
                boolean toggle = yaml.getBoolean(key + TOGGLE_KEY, true);

                // Salva jogador (UPSERT — não sobrescreve se já existir)
                db.savePlayer(uuid, name, coins, toggle);

                // Migra histórico de transações
                List<String> rawHistory = yaml.getStringList(key + HISTORY_KEY);
                List<Transaction> transactions = new ArrayList<>();
                for (String raw : rawHistory) {
                    Transaction t = Transaction.deserialize(raw);
                    if (t != null) transactions.add(t);
                }

                // Insere transações do mais antigo para o mais recente
                for (Transaction t : transactions) {
                    db.insertTransaction(uuid, t, historyMax);
                }

                migrated++;
            } catch (IllegalArgumentException e) {
                // Chave não é um UUID válido — ignora linha corrompida
                log.warning("[sCoins-Migration] Chave inválida ignorada: " + key);
                failed++;
            } catch (Exception e) {
                log.warning("[sCoins-Migration] Erro ao migrar jogador " + key + ": " + e.getMessage());
                failed++;
            }
        }

        log.info(String.format(
            "[sCoins-Migration] Concluída! %d jogador(es) migrado(s), %d erro(s).", migrated, failed));

        // Cria arquivo de lock com metadados da migração
        createLock(migrationLock, migrated);

        // Renomeia coins.yml para backup (dados ficam seguros)
        File backup = new File(plugin.getDataFolder(), "coins.yml.bak");
        if (coinsFile.renameTo(backup)) {
            log.info("[sCoins-Migration] coins.yml renomeado para coins.yml.bak (backup).");
        } else {
            log.warning("[sCoins-Migration] Não foi possível renomear coins.yml — você pode excluí-lo manualmente.");
        }

        return migrated > 0;
    }

    private void createLock(File lockFile, int migrated) {
        try {
            FileConfiguration lock = new YamlConfiguration();
            lock.set("migrated", true);
            lock.set("date", LocalDateTime.now().toString());
            lock.set("players-migrated", migrated);
            lock.set("info", "Este arquivo evita que a migração rode novamente. Não o exclua.");
            lock.save(lockFile);
        } catch (Exception e) {
            log.warning("[sCoins-Migration] Não foi possível criar migration.yml: " + e.getMessage());
        }
    }
}
