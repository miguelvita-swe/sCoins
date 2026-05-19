package br.com.skyy.coins.api;

import org.bukkit.entity.Player;

import java.util.LinkedHashMap;

/**
 * Interface pública da API de economia do sCoins.
 *
 * <p>Exposta via {@link org.bukkit.plugin.ServicesManager} como um BukkitService,
 * seguindo o mesmo padrão do Vault. Qualquer plugin pode obtê-la sem depender
 * das classes internas do sCoins:
 *
 * <pre>
 * RegisteredServiceProvider&lt;EconomyAPIHolder&gt; rsp =
 *         Bukkit.getServicesManager().getRegistration(EconomyAPIHolder.class);
 * EconomyAPIHolder api = rsp == null ? null : rsp.getProvider();
 * </pre>
 *
 * <p><b>Boas práticas:</b>
 * <ul>
 *   <li>Use {@link #hasAccount(String)} antes de fazer operações.</li>
 *   <li>Valide com {@link #has(String, double)} antes de {@link #withdraw(String, double, boolean)}.</li>
 *   <li>Use {@code apply=true} em withdraw/deposit para manter o histórico.</li>
 *   <li>Use {@link #set(String, double)} apenas para ajustes administrativos.</li>
 *   <li>Sempre verifique {@link EconomyResponse#transactionSuccess()}.</li>
 * </ul>
 */
public interface EconomyAPIHolder {

    // ── Conta ────────────────────────────────────────────────────────────────

    /**
     * Verifica se o jogador possui conta na economia.
     * Considera a configuração de transações offline.
     *
     * @param playerName Nome do jogador.
     * @return {@code true} se o jogador possui conta.
     */
    boolean hasAccount(String playerName);

    /**
     * Verifica se o jogador possui conta, com controle de verificação online.
     *
     * @param playerName Nome do jogador.
     * @param check      Se {@code true}, respeita a config {@code offline-transactions}.
     *                   Se {@code false}, permite operações offline (modo admin).
     * @return {@code true} se a conta existe (e, quando check=true, se está acessível).
     */
    boolean hasAccount(String playerName, boolean check);

    /**
     * Recupera a conta de um jogador.
     *
     * @param playerName Nome do jogador.
     * @return {@link Account} ou {@code null} se não encontrado.
     */
    Account getAccount(String playerName);

    // ── Saldo ────────────────────────────────────────────────────────────────

    /**
     * Obtém o saldo do jogador.
     *
     * @param playerName Nome do jogador.
     * @return Saldo em coins (0.0 se o jogador não tiver conta).
     */
    double getBalance(String playerName);

    /**
     * Define o saldo exato do jogador. Use apenas para ajustes administrativos.
     * Não registra transação no histórico.
     *
     * @param playerName Nome do jogador.
     * @param amount     Novo saldo.
     */
    void set(String playerName, double amount);

    /**
     * Verifica se o jogador possui pelo menos {@code amount} coins.
     *
     * @param playerName Nome do jogador.
     * @param amount     Quantia mínima.
     * @return {@code true} se o saldo for >= amount.
     */
    boolean has(String playerName, double amount);

    // ── Transações ───────────────────────────────────────────────────────────

    /**
     * Remove uma quantia do saldo do jogador.
     *
     * @param playerName Nome do jogador.
     * @param amount     Quantia a remover.
     * @param apply      Se {@code true}, registra no histórico de transações.
     * @return {@link EconomyResponse} com o resultado.
     */
    EconomyResponse withdraw(String playerName, double amount, boolean apply);

    /**
     * Adiciona uma quantia ao saldo do jogador.
     *
     * @param playerName Nome do jogador.
     * @param amount     Quantia a adicionar.
     * @param apply      Se {@code true}, registra no histórico de transações.
     * @return {@link EconomyResponse} com o resultado.
     */
    EconomyResponse deposit(String playerName, double amount, boolean apply);

    // ── Ranking ──────────────────────────────────────────────────────────────

    /**
     * Retorna o ranking dos top jogadores por saldo, ordenado do maior para o menor.
     *
     * <p>A estrutura retornada é um {@link LinkedHashMap} para preservar a ordem.
     * Exemplo de uso para leaderboard:
     * <pre>
     * int pos = 1;
     * for (Map.Entry&lt;String, Double&gt; e : api.getTop().entrySet()) {
     *     sender.sendMessage(pos++ + "º - " + e.getKey() + ": " + e.getValue());
     * }
     * </pre>
     *
     * @return {@link LinkedHashMap}&lt;nomeJogador, saldo&gt; ordenado decrescente.
     */
    LinkedHashMap<String, Double> getTop();

    /**
     * Obtém a posição no ranking do jogador (1-indexed).
     *
     * @param player Objeto {@link Player} do Bukkit.
     * @return Posição no ranking (≥ 1) ou {@code -1} se não estiver no ranking.
     */
    int getPlayerTopPosition(Player player);

    /**
     * Obtém a posição no ranking do jogador pelo nome (case-insensitive).
     *
     * @param playerName Nome do jogador.
     * @return Posição no ranking (≥ 1) ou {@code -1} se não estiver no ranking.
     */
    int getPlayerTopPosition(String playerName);
}
