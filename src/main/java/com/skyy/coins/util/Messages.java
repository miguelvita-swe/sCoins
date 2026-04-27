package com.skyy.coins.util;

import org.bukkit.configuration.file.FileConfiguration;

public class Messages {

    private FileConfiguration config;

    public Messages(FileConfiguration config) {
        this.config = config;
    }

    /** Atualiza a referência da config após um /coins reload. */
    public void reload(FileConfiguration newConfig) {
        this.config = newConfig;
    }

    /**
     * Busca a mensagem na config, processa & (cores) e \n (quebras de linha).
     */
    public String get(String key) {
        String raw = config.getString("messages." + key, "&cMensagem não encontrada: " + key);
        return TextUtil.color(raw);
    }

    /**
     * Busca a mensagem e substitui placeholders.
     * Exemplo: replace("balance-self", "{coins}", "1000")
     */
    public String get(String key, Object... replacements) {
        String message = get(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i].toString(), replacements[i + 1].toString());
        }
        return message;
    }

    /**
     * Envia uma mensagem ao destinatário — já faz o split por \n
     * para garantir que cada linha seja enviada separadamente.
     */
    public void send(org.bukkit.command.CommandSender sender, String key, Object... replacements) {
        String message = get(key, replacements);
        for (String line : message.split("\n", -1)) {
            sender.sendMessage(line);
        }
    }
}
