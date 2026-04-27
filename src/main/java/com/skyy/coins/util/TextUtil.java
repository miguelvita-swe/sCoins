package com.skyy.coins.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilitário central de processamento de texto.
 * Garante que & (cores) e \n (quebras de linha) funcionem
 * em qualquer string ou lista vinda de qualquer .yml.
 */
public class TextUtil {

    private TextUtil() {}

    /**
     * Processa uma string: traduz & para §, converte \n literal para quebra real.
     */
    public static String color(String text) {
        if (text == null) return "";
        String colored = ChatColor.translateAlternateColorCodes('&', text);
        return colored.replace("\\n", "\n");
    }

    /**
     * Processa uma lista de strings para uso em lore de itens.
     * - Traduz & para §
     * - Linha com apenas "\n" ou "" vira linha em branco
     * - Linha com \n embutido é DIVIDIDA em múltiplas entradas de lore
     */
    public static List<String> colorLore(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (line == null) { result.add(""); continue; }
            // Processa & e \n
            String processed = color(line);
            // Divide em múltiplas linhas de lore se \n estiver embutido
            String[] parts = processed.split("\n", -1);
            for (String part : parts) {
                result.add(part);
            }
        }
        return result;
    }
}
