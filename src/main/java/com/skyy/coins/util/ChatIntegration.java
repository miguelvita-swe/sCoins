package com.skyy.coins.util;

import org.bukkit.Bukkit;

/**
 * Detecta qual plugin de chat (se algum) está instalado no servidor.
 *
 * Plugins suportados como soft-depend:
 *   • LegendChat
 *   • nChat (OpeNChat)
 *   • UltimateChat
 *   • NoxusChat
 *
 * Se nenhum plugin de chat for detectado, o sCoins registra seu próprio
 * listener (ChatPrefixListener) para injetar o prefixo de medalha via
 * Paper's AsyncChatEvent.
 *
 * Se algum plugin de chat for detectado, o sCoins NÃO registra o listener
 * interno — o admin deve adicionar %scoins_chat_prefix% (ou %scoins_medal%)
 * na configuração de formato do plugin de chat.
 *
 * Isso evita duplicação de prefixo e conflito de formatação.
 */
public final class ChatIntegration {

    private static final String[] KNOWN_CHAT_PLUGINS = {
            "LegendChat",
            "nChat",
            "UltimateChat",
            "NoxusChat"
    };

    private ChatIntegration() {}

    /**
     * Retorna true se algum plugin de chat conhecido estiver instalado e ativo.
     */
    public static boolean isChatPluginPresent() {
        for (String name : KNOWN_CHAT_PLUGINS) {
            if (Bukkit.getPluginManager().getPlugin(name) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retorna o nome do primeiro plugin de chat detectado, ou null se nenhum.
     */
    public static String getDetectedPlugin() {
        for (String name : KNOWN_CHAT_PLUGINS) {
            if (Bukkit.getPluginManager().getPlugin(name) != null) {
                return name;
            }
        }
        return null;
    }
}
