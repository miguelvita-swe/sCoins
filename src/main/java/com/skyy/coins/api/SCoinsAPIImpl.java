package com.skyy.coins.api;

import com.skyy.coins.api.event.CoinsChangeEvent;
import com.skyy.coins.api.event.CoinsTransferEvent;
import com.skyy.coins.manager.CoinsManager;
import com.skyy.coins.manager.RankManager;
import com.skyy.coins.manager.ToggleManager;
import com.skyy.coins.model.Transaction;
import com.skyy.coins.manager.TransactionManager;
import com.skyy.coins.storage.FileStorage;
import com.skyy.coins.util.CoinsFormatter;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementação interna da SCoinsAPI.
 * Não use esta classe diretamente — use SCoinsProvider.get().
 */
public class SCoinsAPIImpl implements SCoinsAPI {

    private final CoinsManager coinsManager;
    private final TransactionManager transactionManager;
    private final ToggleManager toggleManager;
    private final FileStorage fileStorage;
    private final RankManager rankManager;

    public SCoinsAPIImpl(CoinsManager coinsManager,
                          TransactionManager transactionManager,
                          ToggleManager toggleManager,
                          FileStorage fileStorage,
                          RankManager rankManager) {
        this.coinsManager        = coinsManager;
        this.transactionManager  = transactionManager;
        this.toggleManager       = toggleManager;
        this.fileStorage         = fileStorage;
        this.rankManager         = rankManager;
    }

    // ─── Saldo ──────────────────────────────────────────────────────────

    @Override
    public long getCoins(UUID uuid) {
        return coinsManager.getCoins(uuid);
    }

    @Override
    public boolean addCoins(UUID uuid, long amount) {
        if (amount <= 0) return false;
        long previous = coinsManager.getCoins(uuid);
        long newAmount = previous + amount;

        CoinsChangeEvent event = new CoinsChangeEvent(uuid, previous, newAmount, CoinsChangeEvent.Cause.ADD);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        // Garante que o delta calculado pelo evento seja sempre positivo
        // Um plugin externo malicioso poderia setar newAmount < previous
        long delta = event.getNewAmount() - previous;
        if (delta <= 0) return false;
        return coinsManager.addCoins(uuid, delta);
    }

    @Override
    public boolean removeCoins(UUID uuid, long amount) {
        if (amount <= 0) return false;
        long previous = coinsManager.getCoins(uuid);
        if (previous < amount) return false;
        long newAmount = previous - amount;

        CoinsChangeEvent event = new CoinsChangeEvent(uuid, previous, newAmount, CoinsChangeEvent.Cause.REMOVE);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        // Garante que o delta seja positivo e não ultrapasse o saldo atual
        long delta = previous - event.getNewAmount();
        if (delta <= 0 || delta > previous) return false;
        return coinsManager.removeCoins(uuid, delta);
    }

    @Override
    public void setCoins(UUID uuid, long amount) {
        long previous = coinsManager.getCoins(uuid);

        CoinsChangeEvent event = new CoinsChangeEvent(uuid, previous, amount, CoinsChangeEvent.Cause.SET);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        // Clamp para garantir que nenhum evento externo injete valor negativo
        long safeAmount = Math.max(0L, event.getNewAmount());
        coinsManager.setCoins(uuid, safeAmount);
    }

    @Override
    public long getMaxCoins() {
        return coinsManager.getMaxCoins();
    }

    // ─── Transferência ──────────────────────────────────────────────────

    @Override
    public TransferResult transfer(UUID from, UUID to, long amount) {
        if (amount <= 0) return TransferResult.INVALID_AMOUNT;
        if (from.equals(to)) return TransferResult.SAME_PLAYER;
        if (!toggleManager.canReceive(to)) return TransferResult.RECEIVER_TOGGLE_OFF;

        long newReceiverBalance = coinsManager.getCoins(to) + amount;
        if (newReceiverBalance > coinsManager.getMaxCoins()) return TransferResult.RECEIVER_MAX_REACHED;
        if (coinsManager.getCoins(from) < amount) return TransferResult.NOT_ENOUGH_COINS;

        // Dispara evento cancelável antes de executar
        CoinsTransferEvent event = new CoinsTransferEvent(from, to, amount);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return TransferResult.INVALID_AMOUNT;

        // Re-valida o amount após o evento — um plugin externo poderia ter chamado
        // event.setAmount(-5000) ou event.setAmount(Long.MAX_VALUE), bypassando
        // todas as verificações feitas anteriormente.
        long finalAmount = event.getAmount();
        if (finalAmount <= 0) return TransferResult.INVALID_AMOUNT;
        if (coinsManager.getCoins(from) < finalAmount) return TransferResult.NOT_ENOUGH_COINS;
        if (coinsManager.getCoins(to) + finalAmount > coinsManager.getMaxCoins()) return TransferResult.RECEIVER_MAX_REACHED;

        // Usa transferCoins() atômico — elimina TOCTOU também na API pública
        CoinsManager.TransferStatus status = coinsManager.transferCoins(from, to, finalAmount);
        return switch (status) {
            case SUCCESS           -> TransferResult.SUCCESS;
            case NOT_ENOUGH_COINS  -> TransferResult.NOT_ENOUGH_COINS;
            case TARGET_MAX        -> TransferResult.RECEIVER_MAX_REACHED;
            default                -> TransferResult.INVALID_AMOUNT;
        };
    }

    // ─── Histórico ──────────────────────────────────────────────────────

    @Override
    public List<Transaction> getHistory(UUID uuid) {
        return transactionManager.getHistory(uuid);
    }

    // ─── Toggle ─────────────────────────────────────────────────────────

    @Override
    public boolean canReceive(UUID uuid) {
        return toggleManager.canReceive(uuid);
    }

    @Override
    public void setReceive(UUID uuid, boolean canReceive) {
        toggleManager.set(uuid, canReceive);
    }

    // ─── Ranking ────────────────────────────────────────────────────────

    @Override
    public List<TopEntry> getTopPlayers(int limit) {
        List<TopEntry> result = new ArrayList<>();
        for (String[] entry : fileStorage.getTopPlayers(limit)) {
            long coins = Long.parseLong(entry[1]);
            result.add(new TopEntry(entry[0], coins));   // long — sem truncamento
        }
        return result;
    }

    @Override
    public String getMagnata() {
        String[] data = fileStorage.getMagnata();
        return data != null ? data[0] : null;
    }

    @Override
    public String getMedal(String playerName) {
        return rankManager.getMedal(playerName);
    }

    @Override
    public int getRankPosition(UUID uuid) {
        return rankManager.getRankPosition(uuid);
    }

    // ─── Formatação ─────────────────────────────────────────────────────

    @Override
    public String formatCoins(long amount) {
        return CoinsFormatter.format(amount);
    }
}
