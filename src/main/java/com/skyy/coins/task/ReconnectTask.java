package com.skyy.coins.task;

import com.skyy.coins.storage.DatabaseManager;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * ReconnectTask — roda de forma assíncrona a cada 30 segundos.
 *
 * Verifica se o pool MySQL está respondendo com isHealthy().
 * Se a conexão estiver morta, tenta reconectar automaticamente.
 *
 * Por que isso é importante em produção?
 * Servidores MySQL podem encerrar conexões idle após um tempo (wait_timeout).
 * HikariCP já lida com isso internamente, mas uma verificação extra
 * garante que crashes silenciosos de rede sejam detectados e recuperados.
 */
public class ReconnectTask extends BukkitRunnable {

    private final DatabaseManager db;
    private boolean wasUnhealthy = false;

    public ReconnectTask(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void run() {
        if (db.isHealthy()) {
            // Se estava com problema e voltou por conta própria (HikariCP reconectou sozinho)
            if (wasUnhealthy) {
                db.getLogger().info("[sCoins] Conexão com o banco de dados restaurada.");
                wasUnhealthy = false;
            }
            return;
        }

        // Conexão caiu — tenta reconectar
        wasUnhealthy = true;
        db.getLogger().warning("[sCoins] Conexão com o banco perdida! Tentando reconectar...");
        boolean success = db.reconnect();

        if (!success) {
            db.getLogger().severe("[sCoins] Reconexão falhou. Tentando novamente em 30 segundos...");
        }
    }
}
