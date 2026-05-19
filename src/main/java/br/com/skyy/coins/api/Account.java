package br.com.skyy.coins.api;

import br.com.skyy.coins.manager.CoinsManager;
import br.com.skyy.coins.manager.TransactionManager;
import br.com.skyy.coins.model.Transaction;
import br.com.skyy.coins.model.TransactionType;
import br.com.skyy.coins.storage.FileStorage;

import java.util.List;
import java.util.UUID;

/**
 * Representa a conta de economia de um jogador no sCoins.
 *
 * <p>Obtida via {@link EconomyAPIHolder#getAccount(String)}.
 *
 * <pre>
 * Account account = api.getAccount("Steve");
 * if (account != null) {
 *     account.deposit(500, true);
 *     System.out.println("Saldo: " + account.getMoney());
 * }
 * </pre>
 */
public class Account {

    private final UUID            uuid;
    private final String          playerName;
    private final CoinsManager    coinsManager;
    private final TransactionManager transactionManager;
    private final FileStorage     fileStorage;

    Account(UUID uuid, String playerName,
            CoinsManager coinsManager,
            TransactionManager transactionManager,
            FileStorage fileStorage) {
        this.uuid               = uuid;
        this.playerName         = playerName;
        this.coinsManager       = coinsManager;
        this.transactionManager = transactionManager;
        this.fileStorage        = fileStorage;
    }

    // ── Identificação ────────────────────────────────────────────────────────

    /** UUID do jogador dono desta conta. */
    public UUID getUUID() { return uuid; }

    /** Nome do jogador dono desta conta. */
    public String getPlayerName() { return playerName; }

    // ── Saldo ────────────────────────────────────────────────────────────────

    /** Retorna o saldo atual (em coins). */
    public long getMoney() {
        return coinsManager.getCoins(uuid);
    }

    /**
     * Define o saldo exato desta conta.
     * Use apenas para ajustes administrativos — não registra transação.
     *
     * @param amount Novo saldo (será clampado entre 0 e max-coins).
     */
    public void setMoney(long amount) {
        long safe = Math.max(0L, Math.min(amount, coinsManager.getMaxCoins()));
        coinsManager.setCoins(uuid, safe);
        fileStorage.saveAsync(uuid);
    }

    /**
     * Verifica se a conta possui pelo menos {@code amount} coins.
     *
     * @param amount Quantia mínima a verificar.
     * @return {@code true} se o saldo for >= amount.
     */
    public boolean has(double amount) {
        return coinsManager.getCoins(uuid) >= (long) amount;
    }

    // ── Transações ───────────────────────────────────────────────────────────

    /**
     * Adiciona coins a esta conta.
     *
     * @param amount Quantia a depositar (deve ser > 0).
     * @param apply  Se {@code true}, registra no histórico de transações.
     * @return {@link EconomyResponse} com resultado da operação.
     */
    public EconomyResponse deposit(double amount, boolean apply) {
        if (amount <= 0)
            return EconomyResponse.failure("O valor deve ser maior que zero.");

        long longAmount = (long) amount;
        boolean ok = coinsManager.addCoins(uuid, longAmount);
        if (!ok)
            return EconomyResponse.failure("Limite máximo de coins atingido.");

        if (apply) {
            transactionManager.record(uuid, TransactionType.RECEIVED, longAmount, null);
        }
        fileStorage.saveAsync(uuid);
        return EconomyResponse.success(amount, (double) coinsManager.getCoins(uuid));
    }

    /**
     * Remove coins desta conta.
     *
     * @param amount Quantia a sacar (deve ser > 0).
     * @param apply  Se {@code true}, registra no histórico de transações.
     * @return {@link EconomyResponse} com resultado da operação.
     */
    public EconomyResponse withdraw(double amount, boolean apply) {
        if (amount <= 0)
            return EconomyResponse.failure("O valor deve ser maior que zero.");

        long longAmount = (long) amount;
        boolean ok = coinsManager.removeCoins(uuid, longAmount);
        if (!ok)
            return EconomyResponse.failure("Saldo insuficiente.");

        if (apply) {
            transactionManager.record(uuid, TransactionType.SENT, longAmount, null);
        }
        fileStorage.saveAsync(uuid);
        return EconomyResponse.success(amount, (double) coinsManager.getCoins(uuid));
    }

    // ── Histórico ────────────────────────────────────────────────────────────

    /**
     * Retorna o histórico de transações desta conta (mais recente primeiro).
     *
     * @return Lista imutável de {@link Transaction}.
     */
    public List<Transaction> getHistory() {
        return transactionManager.getHistory(uuid);
    }

    @Override
    public String toString() {
        return "Account{player=" + playerName + ", coins=" + getMoney() + "}";
    }
}
