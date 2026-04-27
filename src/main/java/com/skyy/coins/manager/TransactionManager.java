package com.skyy.coins.manager;

import com.skyy.coins.model.Transaction;
import com.skyy.coins.model.TransactionType;
import com.skyy.coins.storage.FileStorage;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransactionManager {

    // ConcurrentLinkedDeque — thread-safe sem blocos synchronized manuais
    private final ConcurrentHashMap<UUID, ConcurrentLinkedDeque<Transaction>> history = new ConcurrentHashMap<>();
    private final int maxEntries;
    // Executor de 1 thread para salvar histórico de forma assíncrona sem bloquear o servidor
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "sCoins-HistorySave"));

    private FileStorage fileStorage; // injetado após criação para evitar circular

    public TransactionManager(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    /** Injeta o FileStorage depois que ambos foram criados. */
    public void setFileStorage(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    /**
     * Registra uma nova transação e persiste de forma assíncrona.
     * ConcurrentLinkedDeque é lock-free para add/remove nas pontas — sem synchronized.
     */
    public void record(UUID uuid, TransactionType type, long amount, String otherPlayer) {
        ConcurrentLinkedDeque<Transaction> deque =
                history.computeIfAbsent(uuid, k -> new ConcurrentLinkedDeque<>());

        Transaction t = new Transaction(type, amount, otherPlayer);
        deque.addLast(t);

        // Mantém o tamanho máximo removendo o mais antigo
        // pollFirst() é atômico em ConcurrentLinkedDeque
        while (deque.size() > maxEntries) deque.pollFirst();

        // Persiste de forma assíncrona
        if (fileStorage != null) {
            final Transaction tFinal = t;
            saveExecutor.submit(() -> {
                // MySQL: insere transação diretamente; YAML: salva perfil e faz flush
                if (fileStorage.isUsingDatabase()) {
                    fileStorage.persistTransaction(uuid, tFinal);
                } else {
                    fileStorage.saveProfile(uuid);
                    fileStorage.flushDirty();
                }
            });
        }
    }

    /**
     * Retorna o histórico como lista — mais recente primeiro.
     * Usa descendingIterator() diretamente — evita criar cópia + reverse()
     * o que gerava GC pressure desnecessária em chamadas frequentes (menu aberto).
     */
    public List<Transaction> getHistory(UUID uuid) {
        ConcurrentLinkedDeque<Transaction> deque = history.get(uuid);
        if (deque == null || deque.isEmpty()) return new ArrayList<>();
        List<Transaction> list = new ArrayList<>(deque.size());
        // descendingIterator() percorre do último (mais recente) para o primeiro
        // sem criar uma cópia intermediária e sem Collections.reverse()
        deque.descendingIterator().forEachRemaining(list::add);
        return list;
    }

    /**
     * Carrega o histórico de um jogador (chamado pelo FileStorage no onJoin).
     */
    public void loadHistory(UUID uuid, List<Transaction> transactions) {
        history.put(uuid, new ConcurrentLinkedDeque<>(transactions));
    }

    /**
     * Remove da memória (chamado no onQuit).
     */
    public void removeHistory(UUID uuid) {
        history.remove(uuid);
    }

    /** Retorna o deque bruto para salvar no arquivo (compatível com FileStorage). */
    public Deque<Transaction> getRawHistory(UUID uuid) {
        ConcurrentLinkedDeque<Transaction> deque = history.get(uuid);
        return deque != null ? deque : new ConcurrentLinkedDeque<>();
    }

    /** Encerra o executor ao desligar o servidor, aguardando tasks pendentes (max 5s). */
    public void shutdown() {
        saveExecutor.shutdown();
        try {
            // Aguarda até 5 segundos para saves pendentes terminarem antes do shutdown
            if (!saveExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                saveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
