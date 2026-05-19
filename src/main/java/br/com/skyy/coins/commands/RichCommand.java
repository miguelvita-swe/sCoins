package br.com.skyy.coins.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Executor para o comando /rich (e aliases: rico, magnata).
 *
 * <p>Abre o menu de top jogadores (se {@code general.top-menu: true})
 * ou exibe o ranking no chat — exatamente como {@code /coins top}.
 */
public class RichCommand implements CommandExecutor {

    private final CoinsCommand delegate;

    /**
     * @param delegate instância do {@link CoinsCommand} principal.
     *                 Reutilizamos a lógica interna de {@code /coins top}.
     */
    public RichCommand(CoinsCommand delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // Injeta o subcomando "top" como primeiro argumento e repassa.
        // /rich        → equivale a /coins top
        // /rico        → equivale a /coins top
        String[] forwarded = new String[]{"top"};
        return delegate.onCommand(sender, command, label, forwarded);
    }
}
