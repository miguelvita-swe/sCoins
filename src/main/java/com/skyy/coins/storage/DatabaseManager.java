package com.skyy.coins.storage;

import com.skyy.coins.model.Transaction;
import com.skyy.coins.model.TransactionType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;

/**
 * DatabaseManager — gerencia o pool de conexões HikariCP
 * e todas as queries SQL do plugin sCoins.
 *
 * Tabelas criadas automaticamente:
 *   scoins_players  — UUID, nome, coins, toggle_receive
 *   scoins_history  — histórico de transações por jogador
 */
public class DatabaseManager {

    private final JavaPlugin plugin;
    private final Logger log;
    private final String prefix;
    private final FileConfiguration config;

    private HikariDataSource dataSource;

    // ── Nomes das tabelas (final — nunca mudam após o construtor) ───────────
    private final String TABLE_PLAYERS;
    private final String TABLE_HISTORY;

    public DatabaseManager(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
        this.config = config;
        this.prefix = config.getString("database.table-prefix", "scoins_");

        TABLE_PLAYERS = prefix + "players";
        TABLE_HISTORY = prefix + "history";

        connect();
    }

    // ── Conexão / Reconexão ─────────────────────────────────────────────────

    private void connect() {
        HikariConfig hc = new HikariConfig();

        String host     = config.getString("database.host", "localhost");
        int    port     = config.getInt("database.port", 3306);
        String dbName   = config.getString("database.name", "scoins");
        String user     = config.getString("database.username", "root");
        String password = config.getString("database.password", "");

        hc.setJdbcUrl(
            "jdbc:mysql://" + host + ":" + port + "/" + dbName
            + "?useSSL=false&allowPublicKeyRetrieval=true"
            + "&characterEncoding=utf8&serverTimezone=UTC"
            + "&rewriteBatchedStatements=true"   // otimiza executeBatch() — envia em 1 roundtrip
            + "&useServerPrepStmts=true"          // prepara statements no servidor — reduz parse overhead
            + "&cachePrepStmts=true"              // cache local de PreparedStatements
            + "&prepStmtCacheSize=250"            // até 250 statements no cache
            + "&prepStmtCacheSqlLimit=2048"       // statements até 2KB no cache
        );
        hc.setUsername(user);
        hc.setPassword(password);
        hc.setDriverClassName("com.mysql.cj.jdbc.Driver");

        int poolSize = config.getInt("database.pool-size", 10);
        hc.setMaximumPoolSize(poolSize);

        // minimumIdle = 2: mantém 2 conexões prontas em períodos de baixo uso.
        // Não usar poolSize inteiro idle — reduz carga no MySQL quando o servidor
        // tem poucos jogadores (madrugada, manutenção, etc.)
        hc.setMinimumIdle(Math.min(2, poolSize));

        // connectionTimeout reduzido: 5s. 30s padrão causa thread starvation —
        // 300 jogadores aguardando 30s = 300 threads travadas por até 9000s somados.
        hc.setConnectionTimeout(config.getLong("database.connection-timeout", 5_000));

        hc.setIdleTimeout(config.getLong("database.idle-timeout", 600_000));
        hc.setMaxLifetime(config.getLong("database.max-lifetime", 1_800_000));

        // validationTimeout: máximo 3s para validar se uma conexão está viva.
        // Deve ser MENOR que connectionTimeout.
        hc.setValidationTimeout(3_000);

        // leakDetectionThreshold: loga WARNING se uma conexão ficar aberta mais de 10s.
        // Detecta bugs de conexão não fechada sem falhar o servidor.
        // Valor 0 = desativado. Em produção, 10s é seguro para operações normais.
        hc.setLeakDetectionThreshold(config.getLong("database.leak-detection-threshold", 10_000));

        // keepaliveTime: HikariCP envia SELECT 1 a cada 30s para manter conexões vivas.
        hc.setKeepaliveTime(30_000);

        hc.setPoolName("sCoins-Pool");

        try {
            this.dataSource = new HikariDataSource(hc);
            log.info("[sCoins] Conexão com MySQL estabelecida com sucesso.");
        } catch (Exception e) {
            log.severe("[sCoins] Falha ao conectar com o banco de dados: " + e.getMessage());
            log.severe("[sCoins] Verifique as configurações em config.yml → database.");
            throw new RuntimeException("Falha ao inicializar DatabaseManager", e);
        }
    }

    public synchronized boolean reconnect() {
        log.warning("[sCoins] Tentando reconectar ao banco de dados...");
        if (dataSource != null && !dataSource.isClosed()) {
            try { dataSource.close(); } catch (Exception ignored) {}
        }
        try {
            connect();
            initTables();
            log.info("[sCoins] Reconexão com MySQL bem-sucedida.");
            return true;
        } catch (Exception e) {
            log.severe("[sCoins] Falha na reconexão: " + e.getMessage());
            return false;
        }
    }

    public boolean isHealthy() {
        if (dataSource == null || dataSource.isClosed()) return false;
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1")) {
            ps.setQueryTimeout(3);
            ps.execute();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getPoolStatus() {
        if (dataSource == null || dataSource.isClosed()) return "§cDesconectado";
        var pool = dataSource.getHikariPoolMXBean();
        return String.format(
            "§aConectado §7| §fAtivas: §e%d §7| §fOciosas: §e%d §7| §fAguardando: §e%d §7| §fTotal: §e%d",
            pool.getActiveConnections(),
            pool.getIdleConnections(),
            pool.getThreadsAwaitingConnection(),
            pool.getTotalConnections()
        );
    }

    // ── DDL ─────────────────────────────────────────────────────────────────

    public void initTables() {
        String createPlayers =
            "CREATE TABLE IF NOT EXISTS `" + TABLE_PLAYERS + "` (" +
            "  `uuid`            VARCHAR(36)  NOT NULL," +
            "  `name`            VARCHAR(16)  NOT NULL," +
            "  `coins`           BIGINT       NOT NULL DEFAULT 0," +
            "  `toggle_receive`  TINYINT(1)   NOT NULL DEFAULT 1," +
            "  `last_transfer`   BIGINT       NOT NULL DEFAULT 0," +
            "  `first_join`      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "  `last_seen`       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "  PRIMARY KEY (`uuid`)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String createHistory =
            "CREATE TABLE IF NOT EXISTS `" + TABLE_HISTORY + "` (" +
            "  `id`           INT UNSIGNED  NOT NULL AUTO_INCREMENT," +
            "  `uuid`         VARCHAR(36)   NOT NULL," +
            "  `type`         VARCHAR(20)   NOT NULL," +
            "  `amount`       BIGINT        NOT NULL," +
            "  `other_player` VARCHAR(16)   DEFAULT NULL," +
            "  `timestamp`    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "  PRIMARY KEY (`id`)," +
            "  INDEX `idx_uuid` (`uuid`)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (Connection con = getConnection();
             Statement stmt = con.createStatement()) {
            stmt.execute(createPlayers);
            stmt.execute(createHistory);

            // Adiciona coluna last_transfer em servidores que já tinham a tabela
            // sem essa coluna — IF NOT EXISTS evita erro se já existir
            try {
                stmt.execute(
                    "ALTER TABLE `" + TABLE_PLAYERS + "` " +
                    "ADD COLUMN IF NOT EXISTS `last_transfer` BIGINT NOT NULL DEFAULT 0"
                );
            } catch (SQLException ignored) {
                // MariaDB/MySQL < 10.3 não suporta IF NOT EXISTS no ALTER TABLE
                // Nesse caso a coluna já existe e podemos ignorar o erro
            }

            log.info("[sCoins] Tabelas verificadas/criadas com sucesso.");
        } catch (SQLException e) {
            log.severe("[sCoins] Erro ao criar tabelas: " + e.getMessage());
            throw new RuntimeException("Falha ao criar tabelas SQL", e);
        }
    }

    // ── CRUD — Players ──────────────────────────────────────────────────────

    public void insertPlayer(UUID uuid, String name, long coins) {
        String sql = "INSERT INTO `" + TABLE_PLAYERS + "` (`uuid`, `name`, `coins`) VALUES (?, ?, ?)";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setLong(3, coins);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[sCoins] Erro em insertPlayer: " + e.getMessage());
        }
    }

    /**
     * Carrega o perfil de um jogador.
     * Coins é retornado como long para evitar overflow com valores próximos de Integer.MAX_VALUE.
     */
    public Map<String, Object> loadPlayer(UUID uuid) {
        String sql = "SELECT `name`, `coins`, `toggle_receive`, `last_transfer` FROM `" + TABLE_PLAYERS + "` WHERE `uuid` = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> data = new HashMap<>();
                data.put("name",          rs.getString("name"));
                data.put("coins",         rs.getLong("coins"));
                data.put("toggle",        rs.getBoolean("toggle_receive"));
                data.put("lastTransfer",  rs.getLong("last_transfer"));
                return data;
            }
        } catch (SQLException e) {
            log.warning("[sCoins] Erro em loadPlayer: " + e.getMessage());
            return null;
        }
    }

    public void savePlayer(UUID uuid, String name, long coins, boolean toggleReceive) {
        savePlayer(uuid, name, coins, toggleReceive, 0L);
    }

    public void savePlayer(UUID uuid, String name, long coins, boolean toggleReceive, long lastTransfer) {
        String sql =
            "INSERT INTO `" + TABLE_PLAYERS + "` (`uuid`, `name`, `coins`, `toggle_receive`, `last_transfer`) " +
            "VALUES (?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `coins` = VALUES(`coins`), " +
            "`toggle_receive` = VALUES(`toggle_receive`), `last_transfer` = VALUES(`last_transfer`), " +
            "`last_seen` = CURRENT_TIMESTAMP";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setLong(3, coins);
            ps.setBoolean(4, toggleReceive);
            ps.setLong(5, lastTransfer);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[sCoins] Erro em savePlayer: " + e.getMessage());
        }
    }

    public long getCoinsOffline(UUID uuid) {
        String sql = "SELECT `coins` FROM `" + TABLE_PLAYERS + "` WHERE `uuid` = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("coins") : 0L;
            }
        } catch (SQLException e) {
            log.warning("[sCoins] Erro em getCoinsOffline: " + e.getMessage());
            return 0L;
        }
    }

    public String getNameOffline(UUID uuid) {
        String sql = "SELECT `name` FROM `" + TABLE_PLAYERS + "` WHERE `uuid` = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("name") : null;
            }
        } catch (SQLException e) {
            log.warning("[sCoins] Erro em getNameOffline: " + e.getMessage());
            return null;
        }
    }

    /**
     * Retorna nome e coins de um jogador offline em uma única query.
     * Evita 2 roundtrips ao banco quando apenas o nome ou o saldo são necessários.
     * Retorna null se o jogador não existe.
     */
    public String[] getPlayerOffline(UUID uuid) {
        String sql = "SELECT `name`, `coins` FROM `" + TABLE_PLAYERS + "` WHERE `uuid` = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new String[]{ rs.getString("name"), String.valueOf(rs.getLong("coins")) };
            }
        } catch (SQLException e) {
            log.warning("[sCoins] Erro em getPlayerOffline: " + e.getMessage());
            return null;
        }
    }

    // ── CRUD — Histórico ────────────────────────────────────────────────────

    /**
     * Carrega histórico em ordem cronológica (mais antigo → mais recente).
     * Busca direto em ASC — sem inversão desnecessária.
     */
    public List<Transaction> loadHistory(UUID uuid, int maxEntries) {
        String sql =
            "SELECT `type`, `amount`, `other_player`, `timestamp` FROM `" + TABLE_HISTORY + "` " +
            "WHERE `uuid` = ? ORDER BY `timestamp` ASC LIMIT ?";
        List<Transaction> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, maxEntries);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TransactionType type  = TransactionType.valueOf(rs.getString("type"));
                    long            amt   = rs.getLong("amount");
                    String          other = rs.getString("other_player");
                    LocalDateTime   ts    = rs.getTimestamp("timestamp").toLocalDateTime();
                    list.add(new Transaction(type, amt, other, ts));
                }
            }
        } catch (SQLException e) {
            log.warning("[sCoins] Erro em loadHistory: " + e.getMessage());
        }
        return list;
    }

    /**
     * Insere transação e remove o excesso — na mesma conexão e transação SQL.
     * Atômico: ou tudo é salvo, ou nada é salvo.
     */
    public void insertTransaction(UUID uuid, Transaction t, int maxEntries) {
        String sqlInsert =
            "INSERT INTO `" + TABLE_HISTORY + "` (`uuid`, `type`, `amount`, `other_player`, `timestamp`) " +
            "VALUES (?, ?, ?, ?, ?)";

        // Prune eficiente: primeiro conta quantos registros existem para este UUID.
        // Se count > maxEntries, deleta os mais antigos em 1 query com ORDER BY + LIMIT.
        // Evita o subquery-in-subquery anterior (3 níveis) que era lento para tabelas grandes.
        String sqlCount = "SELECT COUNT(*) FROM `" + TABLE_HISTORY + "` WHERE `uuid` = ?";
        String sqlPrune =
            "DELETE FROM `" + TABLE_HISTORY + "` WHERE `uuid` = ? " +
            "ORDER BY `timestamp` ASC LIMIT ?";

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                // 1. Insere a nova transação
                try (PreparedStatement ps = con.prepareStatement(sqlInsert)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, t.getType().name());
                    ps.setLong(3, t.getAmount());
                    ps.setString(4, t.getOtherPlayer());
                    ps.setTimestamp(5, Timestamp.valueOf(t.getTimestampRaw()));
                    ps.executeUpdate();
                }

                // 2. Verifica se precisa podar (só faz a query de DELETE se necessário)
                int count;
                try (PreparedStatement ps = con.prepareStatement(sqlCount)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        count = rs.next() ? rs.getInt(1) : 0;
                    }
                }

                if (count > maxEntries) {
                    try (PreparedStatement ps = con.prepareStatement(sqlPrune)) {
                        ps.setString(1, uuid.toString());
                        ps.setInt(2, count - maxEntries); // remove apenas o excesso
                        ps.executeUpdate();
                    }
                }

                con.commit();
            } catch (SQLException e) {
                con.rollback();
                log.warning("[sCoins] Erro em insertTransaction (rollback): " + e.getMessage());
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.warning("[sCoins] Erro ao obter conexão em insertTransaction: " + e.getMessage());
        }
    }

    // ── Top Players & Magnata ───────────────────────────────────────────────

    public List<String[]> getTopPlayers(int limit) {
        String sql =
            "SELECT `name`, `coins` FROM `" + TABLE_PLAYERS + "` " +
            "WHERE `coins` > 0 ORDER BY `coins` DESC LIMIT ?";
        List<String[]> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new String[]{ rs.getString("name"), String.valueOf(rs.getLong("coins")) });
                }
            }
        } catch (SQLException e) {
            log.warning("[sCoins] Erro em getTopPlayers: " + e.getMessage());
        }
        return list;
    }

    public String[] getMagnata() {
        String sql =
            "SELECT `name`, `coins` FROM `" + TABLE_PLAYERS + "` " +
            "WHERE `coins` > 0 ORDER BY `coins` DESC LIMIT 1";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new String[]{ rs.getString("name"), String.valueOf(rs.getLong("coins")) };
                }
            }
        } catch (SQLException e) {
            log.warning("[sCoins] Erro em getMagnata: " + e.getMessage());
        }
        return null;
    }

    /**
     * Salva todos os perfis em batch dentro de uma única transação SQL.
     * Garante consistência: ou todos são salvos, ou nenhum.
     */
    public void batchSavePlayers(List<PlayerSnapshot> snapshots) {
        if (snapshots.isEmpty()) return;
        String sql =
            "INSERT INTO `" + TABLE_PLAYERS + "` (`uuid`, `name`, `coins`, `toggle_receive`, `last_transfer`) " +
            "VALUES (?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `coins` = VALUES(`coins`), " +
            "`toggle_receive` = VALUES(`toggle_receive`), `last_transfer` = VALUES(`last_transfer`), " +
            "`last_seen` = CURRENT_TIMESTAMP";

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (PlayerSnapshot s : snapshots) {
                    ps.setString(1, s.uuid().toString());
                    ps.setString(2, s.name());
                    ps.setLong(3, s.coins());
                    ps.setBoolean(4, s.toggleReceive());
                    ps.setLong(5, s.lastTransfer());
                    ps.addBatch();
                }
                ps.executeBatch();
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                log.warning("[sCoins] Erro em batchSavePlayers (rollback): " + e.getMessage());
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.warning("[sCoins] Erro ao obter conexão em batchSavePlayers: " + e.getMessage());
        }
    }

    // ── Shutdown ────────────────────────────────────────────────────────────

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("[sCoins] Pool de conexões MySQL fechado.");
        }
    }


    public Logger getLogger() { return log; }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // ── Record auxiliar para batch save ────────────────────────────────────
    public record PlayerSnapshot(UUID uuid, String name, long coins, boolean toggleReceive, long lastTransfer) {}
}
