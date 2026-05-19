package br.com.skyy.coins.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Executor para o comando /check (e aliases: cheque, cheques).
 *
 * <p>Uso: {@code /check <valor>}
 * Equivale a {@code /coins cheque <valor>}.
 *
 * <p>Emite um cheque de coins no valor indicado, debitando do saldo do jogador.
 */
public class CheckCommand implements CommandExecutor, TabCompleter {

    private final CoinsCommand delegate;

    /**
     * @param delegate instância do {@link CoinsCommand} principal.
     */
    public CheckCommand(CoinsCommand delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // /check <valor>  →  /coins cheque <valor>
        String[] forwarded = new String[args.length + 1];
        forwarded[0] = "cheque";
        System.arraycopy(args, 0, forwarded, 1, args.length);
        return delegate.onCommand(sender, command, label, forwarded);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            for (String ex : new String[]{"100", "500", "1000", "5000", "10000"}) {
                if (ex.startsWith(args[0])) suggestions.add(ex);
            }
            return suggestions;
        }
        return new ArrayList<>();
    }
}
