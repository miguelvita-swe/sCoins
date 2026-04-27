package com.skyy.coins.api;

import org.jetbrains.annotations.Nullable;

/**
 * Ponto de acesso estático à API do sCoins.
 *
 * Uso em qualquer plugin externo:
 * <pre>
 *   SCoinsAPI api = SCoinsProvider.get();
 *   if (api != null) {
 *       api.addCoins(player.getUniqueId(), 1000);
 *   }
 * </pre>
 */
public final class SCoinsProvider {

    private static SCoinsAPI instance;

    private SCoinsProvider() {}

    /**
     * Retorna a instância da API, ou null se o sCoins não estiver ativo.
     * Sempre verifique se o retorno é null antes de usar.
     */
    @Nullable
    public static SCoinsAPI get() {
        return instance;
    }

    /**
     * Registra a implementação da API.
     * Chamado internamente pelo sCoins — não use em outros plugins.
     */
    public static void register(SCoinsAPI api) {
        if (instance != null) throw new IllegalStateException("sCoins API já está registrada.");
        instance = api;
    }

    /**
     * Remove a implementação da API.
     * Chamado internamente pelo sCoins no onDisable.
     */
    public static void unregister() {
        instance = null;
    }
}
