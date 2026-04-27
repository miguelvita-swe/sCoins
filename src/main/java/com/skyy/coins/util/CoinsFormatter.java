package com.skyy.coins.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CoinsFormatter {

    // volatile + copy-on-write: load() cria uma nova lista e faz swap atômico da referência.
    // format() lê a referência imutável — nunca vê uma lista parcialmente construída.
    // Elimina ConcurrentModificationException durante /coins reload com jogadores online.
    private static volatile List<Tier> tiers = new ArrayList<>();

    /**
     * Carrega os tiers do config.yml.
     * Thread-safe: constrói uma nova lista e faz swap atômico da referência (copy-on-write).
     */
    public static void load(FileConfiguration config) {
        List<Tier> newTiers = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("formatting");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    long threshold = Long.parseLong(key);
                    String suffix  = section.getString(key, "");
                    if (!suffix.isEmpty()) {
                        newTiers.add(new Tier(threshold, suffix));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // Ordena do maior para o menor threshold
        newTiers.sort(Comparator.comparingLong(Tier::getThreshold).reversed());

        // Se nenhum tier foi carregado, usa os padrões
        if (newTiers.isEmpty()) {
            newTiers.add(new Tier(1_000_000_000L, "B"));
            newTiers.add(new Tier(1_000_000L, "M"));
            newTiers.add(new Tier(1_000L, "K"));
        }

        // Swap atômico — format() nunca vê lista parcial
        tiers = newTiers;
    }

    public static String format(int value) {
        return format((long) value);
    }

    public static String format(long value) {
        for (Tier tier : tiers) {
            if (value >= tier.getThreshold()) {
                return formatDecimal(value / (double) tier.getThreshold()) + tier.getSuffix();
            }
        }
        return String.valueOf(value);
    }

    /** Retorna uma cópia dos tiers atuais (para listar no /coins formatar). */
    public static List<Tier> getTiers() {
        return new ArrayList<>(tiers);
    }

    private static String formatDecimal(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.1f", value).replace(".", ",");
    }

    public static class Tier {
        private final long threshold;
        private final String suffix;

        public Tier(long threshold, String suffix) {
            this.threshold = threshold;
            this.suffix = suffix;
        }

        public long getThreshold() { return threshold; }
        public String getSuffix()  { return suffix; }
    }
}
