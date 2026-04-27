package com.skyy.coins.manager;

import com.skyy.coins.model.Profile;
import com.skyy.coins.storage.FileStorage;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class CoinsManager {

    private final ConcurrentHashMap<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final long maxCoins;

    // Lock dedicado para transferências — garante atomicidade do par remove+add
    // sem bloquear operações independentes (add/remove admin de jogadores diferentes).
    private final ReentrantLock transferLock = new ReentrantLock();

    // Injetado depois da criação para evitar dependência circular
    private MagnataManager magnataManager;
    private RankManager    rankManager;
    private FileStorage    fileStorage;

    public CoinsManager(long maxCoins) {
        this.maxCoins = maxCoins;
    }

    /** Chamado pelo Main após criar MagnataManager, RankManager e FileStorage. */
    public void setMagnataManager(MagnataManager magnataManager, FileStorage fileStorage) {
        this.magnataManager = magnataManager;
        this.fileStorage    = fileStorage;
    }

    public void setRankManager(RankManager rankManager) {
        this.rankManager = rankManager;
    }

    public long getCoins(UUID uuid) {
        Profile p = profiles.get(uuid);
        return p != null ? p.getCoins() : 0L;
    }

    // Quando true, setCoins não chama magnataManager.check() nem rankManager.refresh().
    // Usado pelo RewardTask para evitar 300 chamadas redundantes — check é feito 1x ao final.
    private final AtomicInteger batchDepth = new AtomicInteger(0);

    /** Suspende as verificações de magnata e rank durante operações em lote. */
    public void beginBatch() { batchDepth.incrementAndGet(); }

    /** Reativa as verificações e força uma única checagem ao final do lote. */
    public void endBatch() {
        if (batchDepth.decrementAndGet() <= 0) {
            batchDepth.set(0); // garante que nunca fique negativo
            if (magnataManager != null && fileStorage != null) magnataManager.check(fileStorage);
            if (rankManager != null) rankManager.refresh();
        }
    }

    public void setCoins(UUID uuid, long amount) {
        if (amount < 0) amount = 0;
        if (amount > maxCoins) amount = maxCoins;
        Profile p = profiles.get(uuid);
        if (p != null) {
            long oldCoins = p.getCoins();
            p.setCoins(amount);
            if (fileStorage != null) {
                // YAML: atualiza índice incremental E reconstrói cache imediatamente
                fileStorage.updateYamlIndex(p.getName(), oldCoins, amount);
                // MySQL: reconstrói cache a partir de dados em memória (sem query DB)
                //        garante que getTopPlayersFromCacheOnly nunca retorne vazio após setCoins
                fileStorage.refreshTopCacheFromMemory();
            }
            if (batchDepth.get() <= 0) {
                if (magnataManager != null && fileStorage != null) magnataManager.check(fileStorage);
                if (rankManager != null) rankManager.refresh();
            }
        }
    }

    /**
     * Retorna false se o valor for inválido ou ultrapassar o limite máximo.
     */
    public boolean addCoins(UUID uuid, long amount) {
        if (amount <= 0) return false;
        long current    = getCoins(uuid);
        long newBalance = current + amount;
        if (newBalance > maxCoins) return false;
        setCoins(uuid, newBalance);
        return true;
    }

    /**
     * Retorna false se não houver saldo suficiente.
     */
    public boolean removeCoins(UUID uuid, long amount) {
        if (amount <= 0) return false;
        long current = getCoins(uuid);
        if (current < amount) return false;
        setCoins(uuid, current - amount);
        return true;
    }

    /**
     * Transferência ATÔMICA entre dois jogadores.
     * O ReentrantLock garante que nenhum outro transfer rode em paralelo,
     * eliminando o TOCTOU onde dois envios simultâneos para o mesmo alvo
     * passam no check de maxCoins individualmente mas juntos ultrapassam o limite.
     *
     * Enum de resultado:
     *   SUCCESS           — transferência concluída
     *   NOT_ENOUGH_COINS  — remetente sem saldo
     *   TARGET_MAX        — receptor atingiria o limite máximo
     *   INVALID           — valor inválido (≤ 0)
     */
    public enum TransferStatus { SUCCESS, NOT_ENOUGH_COINS, TARGET_MAX, INVALID }

    public TransferStatus transferCoins(UUID from, UUID to, long amount) {
        if (amount <= 0) return TransferStatus.INVALID;
        transferLock.lock();
        try {
            long fromBalance = getCoins(from);
            if (fromBalance < amount) return TransferStatus.NOT_ENOUGH_COINS;

            long toBalance = getCoins(to);
            if (toBalance + amount > maxCoins) return TransferStatus.TARGET_MAX;

            // Ambas as operações dentro do lock — nenhuma thread pode interleavar
            setCoins(from, fromBalance - amount);
            setCoins(to,   toBalance   + amount);
            return TransferStatus.SUCCESS;
        } finally {
            transferLock.unlock();
        }
    }

    public void loadProfile(UUID uuid, String name, long coins) {
        profiles.put(uuid, new Profile(uuid, name, coins));
    }

    public Profile getProfile(UUID uuid) { return profiles.get(uuid); }
    public boolean isLoaded(UUID uuid)   { return profiles.containsKey(uuid); }
    public void removeProfile(UUID uuid) { profiles.remove(uuid); }

    /**
     * Retorna a view ao vivo do keySet do ConcurrentHashMap.
     * ConcurrentHashMap garante iteração segura sem CME — cópia defensiva é desnecessária
     * e gerava GC pressure com 300+ players sendo iterados em 8+ lugares por ciclo.
     */
    public Set<UUID> getAllProfiles() { return profiles.keySet(); }

    public String getName(UUID uuid) {
        Profile p = profiles.get(uuid);
        return p != null ? p.getName() : null;
    }

    public long getMaxCoins() { return maxCoins; }
}
