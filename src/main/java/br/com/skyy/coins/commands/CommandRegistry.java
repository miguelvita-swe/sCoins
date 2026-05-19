package br.com.skyy.coins.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Registra comandos dinamicamente no CommandMap do Bukkit via reflection.
 *
 * <p>Isso permite que nomes e aliases definidos no commands.yml sejam
 * aplicados em runtime sem precisar listá-los no plugin.yml.
 *
 * <p>Funciona em todas as versões do Bukkit/Spigot/Paper (1.8–1.21).
 */
public final class CommandRegistry {

    private CommandRegistry() {}

    /**
     * Registra um {@link org.bukkit.command.CommandExecutor} sob o nome e aliases
     * definidos no {@link CommandsConfig} para o tipo de comando fornecido.
     *
     * @param plugin       instância do plugin
     * @param commandsCfg  configuração de comandos
     * @param type         tipo do comando a registrar
     * @param executor     executor que processará o comando
     * @param description  descrição exibida no /help
     * @param usage        usage exibido em caso de erro
     * @param tabCompleter completer de tab (pode ser null)
     */
    public static void register(JavaPlugin plugin,
                                CommandsConfig commandsCfg,
                                CommandsConfig.CommandType type,
                                org.bukkit.command.CommandExecutor executor,
                                String description,
                                String usage,
                                org.bukkit.command.TabCompleter tabCompleter) {

        CommandMap commandMap = getCommandMap();
        if (commandMap == null) {
            plugin.getLogger().severe("[sCoins] Não foi possível acessar o CommandMap — comandos de '" + type + "' NÃO serão registrados.");
            return;
        }

        String name       = commandsCfg.getName(type);
        List<String> aliases = commandsCfg.getAliases(type);

        DynamicCommand cmd = new DynamicCommand(plugin, name, aliases, description, usage, executor, tabCompleter);

        // O prefixo "scoins" evita conflito com comandos de outros plugins que
        // usem o mesmo nome (ex: /coins de outro plugin de economia).
        commandMap.register("scoins", cmd);

        plugin.getLogger().info("[sCoins] Comando registrado: /" + name
                + (aliases.isEmpty() ? "" : " (aliases: " + String.join(", ", aliases) + ")"));
    }

    // ─── Reflection helper ───────────────────────────────────────────────

    /**
     * Obtém o {@link CommandMap} do servidor via reflection.
     * Compatível com CraftBukkit/Spigot/Paper em todas as versões.
     */
    private static CommandMap getCommandMap() {
        try {
            // Paper 1.19+ expõe getCommandMap() publicamente
            try {
                java.lang.reflect.Method m = Bukkit.getServer().getClass().getMethod("getCommandMap");
                return (CommandMap) m.invoke(Bukkit.getServer());
            } catch (NoSuchMethodException ignored) {
                // Fallback reflection para versões mais antigas
            }

            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            return (CommandMap) f.get(Bukkit.getServer());
        } catch (Exception e) {
            return null;
        }
    }

    // ─── Implementação interna do Command ────────────────────────────────

    /**
     * Comando dinâmico que delega execução e tab-complete para os executors fornecidos.
     * Implementa {@link PluginIdentifiableCommand} para que o /help identifique o plugin correto.
     */
    static final class DynamicCommand extends Command implements PluginIdentifiableCommand {

        private final JavaPlugin plugin;
        private final org.bukkit.command.CommandExecutor executor;
        private final org.bukkit.command.TabCompleter tabCompleter;

        DynamicCommand(JavaPlugin plugin, String name, List<String> aliases,
                       String description, String usage,
                       org.bukkit.command.CommandExecutor executor,
                       org.bukkit.command.TabCompleter tabCompleter) {
            super(name);
            this.plugin       = plugin;
            this.executor     = executor;
            this.tabCompleter = tabCompleter;
            setAliases(aliases);
            setDescription(description);
            setUsage(usage);
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            return executor.onCommand(sender, this, commandLabel, args);
        }

        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            if (tabCompleter == null) return super.tabComplete(sender, alias, args);
            List<String> result = tabCompleter.onTabComplete(sender, this, alias, args);
            return result != null ? result : super.tabComplete(sender, alias, args);
        }

        @Override
        public @NotNull Plugin getPlugin() {
            return plugin;
        }
    }
}
