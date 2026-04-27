package com.skyy.coins.manager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private int cooldownSeconds;

    public CooldownManager(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    /** Atualiza o cooldown após /coins reload. */
    public void setCooldownSeconds(int seconds) {
        this.cooldownSeconds = seconds;
    }

    /**
     * Verifica se o jogador ainda está em cooldown.
     * Retorna true se estiver bloqueado.
     */
    public boolean isOnCooldown(UUID uuid) {
        if (cooldownSeconds <= 0) return false;
        if (!cooldowns.containsKey(uuid)) return false;

        long lastUsed = cooldowns.get(uuid);
        long elapsed = System.currentTimeMillis() - lastUsed;
        return elapsed < (long) cooldownSeconds * 1000;
    }

    /**
     * Retorna quantos segundos faltam para o cooldown acabar.
     */
    public int getRemainingSeconds(UUID uuid) {
        if (!cooldowns.containsKey(uuid)) return 0;
        long lastUsed = cooldowns.get(uuid);
        long elapsed = System.currentTimeMillis() - lastUsed;
        long remaining = ((long) cooldownSeconds * 1000) - elapsed;
        return (int) Math.ceil(remaining / 1000.0);
    }

    /**
     * Restaura o cooldown a partir de um timestamp salvo (ex: banco de dados).
     * Se o timestamp for 0 ou o cooldown já tiver expirado, não faz nada.
     */
    public void restoreCooldown(UUID uuid, long timestampMillis) {
        if (timestampMillis <= 0) return;
        long elapsed = System.currentTimeMillis() - timestampMillis;
        if (elapsed < (long) cooldownSeconds * 1000) {
            cooldowns.put(uuid, timestampMillis);
        }
    }

    /**
     * Retorna o timestamp (ms) do último envio, para salvar no banco.
     * Retorna 0 se não há cooldown registrado.
     */
    public long getLastTransferTimestamp(UUID uuid) {
        return cooldowns.getOrDefault(uuid, 0L);
    }

    /**
     * Registra o momento do envio para iniciar o cooldown.
     */
    public void setCooldown(UUID uuid) {
        cooldowns.put(uuid, System.currentTimeMillis());
    }

    /**
     * Remove o cooldown de um jogador (útil para admins ou ao sair do servidor).
     */
    public void removeCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
