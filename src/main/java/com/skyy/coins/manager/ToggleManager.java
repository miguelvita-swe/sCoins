package com.skyy.coins.manager;

import com.skyy.coins.storage.FileStorage;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ToggleManager {

    private final ConcurrentHashMap<UUID, Boolean> toggles = new ConcurrentHashMap<>();

    // Injetado após criação para evitar dependência circular
    private FileStorage fileStorage;

    /** Injeta o FileStorage para persistência imediata no YAML ao alterar toggle. */
    public void setFileStorage(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    /**
     * Retorna true se o jogador está aceitando coins de outros jogadores.
     */
    public boolean canReceive(UUID uuid) {
        return toggles.getOrDefault(uuid, true);
    }

    /**
     * Alterna o estado de forma ATÔMICA usando compute().
     * compute() é garantidamente atômico no ConcurrentHashMap —
     * elimina a race condition do par canReceive() + put() separados.
     * Retorna o novo estado (true = ativo).
     */
    public boolean toggle(UUID uuid) {
        // compute() é atômico: lê e escreve sem janela entre os dois
        boolean[] result = {false};
        toggles.compute(uuid, (k, current) -> {
            boolean newState = current == null || !current; // null = padrão true → inverte para false
            result[0] = newState;
            return newState;
        });
        boolean newState = result[0];

        if (fileStorage != null) {
            if (fileStorage.isUsingDatabase()) {
                // MySQL: persiste imediatamente de forma assíncrona
                fileStorage.saveAsync(uuid);
            } else {
                // YAML: apenas marca como dirty — NÃO chama flushDirty() aqui.
                // flushDirty() escreve no disco (blocking I/O) na main thread.
                // O AutoSave (runTaskTimerAsynchronously) fará o flush em background.
                // O dado está seguro em memória e no dirtyProfiles set.
                fileStorage.saveProfile(uuid);
            }
        }

        return newState;
    }

    /**
     * Define o estado diretamente (usado ao carregar do arquivo).
     */
    public void set(UUID uuid, boolean canReceive) {
        toggles.put(uuid, canReceive);
    }

    /**
     * Remove da memória (chamado no onQuit).
     */
    public void remove(UUID uuid) {
        toggles.remove(uuid);
    }
}
