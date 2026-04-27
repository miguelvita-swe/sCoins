package com.skyy.coins.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Carrega e expõe o menus.yml como FileConfiguration.
 * Todos os menus devem receber esta instância em vez do config.yml.
 */
public class MenuConfig {

    private final JavaPlugin plugin;
    private File              file;
    private FileConfiguration config;

    public MenuConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /** Copia o menus.yml padrão se não existir e carrega. */
    private void load() {
        file = new File(plugin.getDataFolder(), "menus.yml");
        if (!file.exists()) {
            plugin.saveResource("menus.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    /** Recarrega o arquivo do disco — chamado pelo /coins reload. */
    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    /** Retorna a FileConfiguration do menus.yml. */
    public FileConfiguration get() {
        return config;
    }
}
