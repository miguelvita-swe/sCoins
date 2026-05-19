package br.com.skyy.coins.commands;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Carrega e gerencia o commands.yml do plugin.
 *
 * <p>O commands.yml define os nomes e aliases de cada comando
 * no formato "nome|alias1|alias2|...".
 * O primeiro token é o nome principal; os demais são aliases.
 *
 * <p>Exemplo:
 * <pre>
 * commands:
 *   money: 'money|coin|coins|balance'
 * </pre>
 */
public class CommandsConfig {

    /** Tipos de comando suportados pelo plugin. */
    public enum CommandType {
        MONEY,  // Menu/saldo principal
        RICH,   // Top jogadores mais ricos
        PAY,    // Enviar coins a outro jogador
        CHECK   // Sistema de cheques
    }

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public CommandsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * Carrega (ou cria) o commands.yml.
     * Se o arquivo não existir, exporta o padrão empacotado no jar.
     */
    public void load() {
        File file = new File(plugin.getDataFolder(), "commands.yml");

        if (!file.exists()) {
            plugin.saveResource("commands.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(file);

        // Garante que chaves padrão existam sem sobrescrever as configuradas
        InputStream stream = plugin.getResource("commands.yml");
        if (stream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[sCoins] Não foi possível salvar commands.yml: " + e.getMessage());
        }
    }

    // ─── Leitura dos comandos ─────────────────────────────────────────────

    /**
     * Retorna o nome principal de um tipo de comando.
     * Exemplo: {@code getName(MONEY)} → "money"
     */
    public String getName(CommandType type) {
        return parse(type).get(0);
    }

    /**
     * Retorna a lista de aliases (sem o nome principal) de um tipo de comando.
     */
    public List<String> getAliases(CommandType type) {
        List<String> all = parse(type);
        if (all.size() <= 1) return Collections.emptyList();
        return all.subList(1, all.size());
    }

    /**
     * Retorna todos os tokens (nome + aliases) de um tipo de comando.
     */
    public List<String> getAll(CommandType type) {
        return Collections.unmodifiableList(parse(type));
    }

    // ─── Internos ─────────────────────────────────────────────────────────

    /**
     * Parseia a string "nome|alias1|alias2" do YAML e retorna a lista de tokens
     * em lowercase, sem espaços e sem duplicatas.
     */
    private List<String> parse(CommandType type) {
        String key = "commands." + type.name().toLowerCase();
        String raw = config.getString(key, type.name().toLowerCase());

        List<String> result = new ArrayList<>();
        for (String token : raw.split("\\|")) {
            String t = token.trim().toLowerCase();
            if (!t.isEmpty() && !result.contains(t)) {
                result.add(t);
            }
        }

        // Garante ao menos um nome (fallback para o nome do enum em lowercase)
        if (result.isEmpty()) {
            result.add(type.name().toLowerCase());
        }

        return result;
    }
}
