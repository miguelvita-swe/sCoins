package br.com.skyy.coins.api;

/**
 * Resposta padrão de operações de economia do sCoins.
 *
 * <p>Segue o mesmo contrato do {@code net.milkbowl.vault.economy.EconomyResponse}
 * para facilitar a migração de plugins que já usam Vault.
 *
 * <pre>
 * EconomyResponse response = api.deposit("Steve", 1000, true);
 * if (response.transactionSuccess()) {
 *     player.sendMessage("Saldo atual: " + response.balance);
 * } else {
 *     player.sendMessage("Erro: " + response.errorMessage);
 * }
 * </pre>
 */
public class EconomyResponse {

    /** Tipo de resultado da transação. */
    public enum ResponseType {
        /** Operação bem-sucedida. */
        SUCCESS,
        /** Operação falhou (sem saldo, conta inexistente, limite atingido, etc.). */
        FAILURE,
        /** Operação não suportada neste contexto. */
        NOT_IMPLEMENTED
    }

    /** Quantia envolvida na transação. */
    public final double amount;

    /** Saldo do jogador APÓS a operação (0 em caso de falha). */
    public final double balance;

    /** Tipo do resultado. */
    public final ResponseType type;

    /** Mensagem de erro em caso de falha, ou string vazia em caso de sucesso. */
    public final String errorMessage;

    public EconomyResponse(double amount, double balance, ResponseType type, String errorMessage) {
        this.amount       = amount;
        this.balance      = balance;
        this.type         = type;
        this.errorMessage = errorMessage != null ? errorMessage : "";
    }

    /**
     * Retorna {@code true} se a transação foi executada com sucesso.
     * Equivalente a {@code type == ResponseType.SUCCESS}.
     */
    public boolean transactionSuccess() {
        return type == ResponseType.SUCCESS;
    }

    // ── Factory helpers ──────────────────────────────────────────────────────

    static EconomyResponse success(double amount, double newBalance) {
        return new EconomyResponse(amount, newBalance, ResponseType.SUCCESS, "");
    }

    static EconomyResponse failure(String reason) {
        return new EconomyResponse(0, 0, ResponseType.FAILURE, reason);
    }
}
