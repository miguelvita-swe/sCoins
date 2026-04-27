package com.skyy.coins.util;

import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Utilitário centralizado de sons do sCoins.
 * Todos os sons são configuráveis no config.yml na seção "sounds".
 *
 * Chave de cada som: sounds.<chave>.sound | .volume | .pitch
 * Exemplo: sounds.menu-open.sound: BLOCK_CHEST_OPEN
 */
public class SoundUtil {

    private static FileConfiguration config;

    private SoundUtil() {}

    /** Carrega/recarrega a config — chamado no onEnable e /coins reload. */
    public static void load(FileConfiguration cfg) {
        config = cfg;
    }

    /**
     * Toca um som para o jogador usando a chave definida no config.yml.
     * Se a chave não existir ou o som for inválido, nada acontece.
     */
    public static void play(Player player, String key) {
        if (config == null || player == null) return;

        String path   = "sounds." + key;
        String name   = config.getString(path + ".sound", "");
        if (name.isEmpty() || name.equalsIgnoreCase("none")) return;

        float volume = (float) config.getDouble(path + ".volume", 1.0);
        float pitch  = (float) config.getDouble(path + ".pitch",  1.0);

        try {
            Sound sound = Sound.valueOf(name.toUpperCase().replace(".", "_"));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            // Som inválido no config — ignora silenciosamente
        }
    }
}
