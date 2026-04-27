package com.skyy.coins.api;

import com.skyy.coins.model.Transaction;

import java.util.List;
import java.util.UUID;

/**
 * API pública do sCoins.
 *
 * Como usar em outro plugin:
 * <pre>
 *   SCoinsAPI api = SCoinsProvider.get();
 *   if (api != null) {
 *       int coins = api.getCoins(player.getUniqueId());
 *       api.addCoins(player.getUniqueId(), 500);
 *   }
 * </pre>
 *
 * Adicione o sCoins como depend ou softdepend no seu plugin.yml.
 */
public interface SCoinsAPI {

    // ─── Saldo ──────────────────────────────────────────────────────────

    /** Retorna o saldo atual do jogador. */
    long getCoins(UUID uuid);

    /**
     * Adiciona coins ao jogador.
     * @return false se ultrapassar o limite máximo ou valor inválido.
     */
    boolean addCoins(UUID uuid, long amount);

    /**
     * Remove coins do jogador.
     * @return false se não houver saldo suficiente.
     */
    boolean removeCoins(UUID uuid, long amount);

    /**
     * Define o saldo do jogador para um valor exato.
     * Respeita o limite máximo configurado.
     */
    void setCoins(UUID uuid, long amount);

    /** Retorna o limite máximo de coins configurado no servidor. */
    long getMaxCoins();

    // ─── Transferência ──────────────────────────────────────────────────

    /**
     * Transfere coins de um jogador para outro.
     * Valida: saldo suficiente, limite máximo do receptor, toggle do receptor.
     * @return resultado da operação descrito em {@link TransferResult}
     */
    TransferResult transfer(UUID from, UUID to, long amount);

    // ─── Histórico ──────────────────────────────────────────────────────

    /** Retorna o histórico de transações do jogador (mais recente primeiro). */
    List<Transaction> getHistory(UUID uuid);

    // ─── Toggle ─────────────────────────────────────────────────────────

    /** Retorna true se o jogador está aceitando receber coins. */
    boolean canReceive(UUID uuid);

    /** Define se o jogador pode receber coins. */
    void setReceive(UUID uuid, boolean canReceive);

    // ─── Ranking ────────────────────────────────────────────────────────

    /**
     * Retorna os top N jogadores por saldo.
     * Cada entrada é {@link TopEntry} com nome e saldo.
     */
    List<TopEntry> getTopPlayers(int limit);

    /** Retorna o nome do magnata atual, ou null se não houver. */
    String getMagnata();

    /** Retorna a medalha do top 3 para o jogador (🥇/🥈/🥉) ou "" se não estiver no top 3. */
    String getMedal(String playerName);

    /** Retorna a posição do jogador no ranking (1+) ou -1 se não rankear. */
    int getRankPosition(UUID uuid);

    // ─── Formatação ─────────────────────────────────────────────────────

    /** Retorna o saldo formatado com os sufixos configurados (ex: 1.5M). */
    String formatCoins(long amount);

    // ─── Modelos de dados ───────────────────────────────────────────────

    /** Resultado de uma transferência entre jogadores. */
    enum TransferResult {
        SUCCESS,
        NOT_ENOUGH_COINS,
        RECEIVER_MAX_REACHED,
        RECEIVER_TOGGLE_OFF,
        SAME_PLAYER,
        INVALID_AMOUNT
    }

    /** Entrada de ranking com nome e saldo. */
    class TopEntry {
        private final String name;
        private final long coins;   // long — sem truncamento

        public TopEntry(String name, long coins) {
            this.name  = name;
            this.coins = coins;
        }

        public String getName()  { return name; }
        public long   getCoins() { return coins; }

        @Override
        public String toString() { return name + " → " + coins; }
    }
}
