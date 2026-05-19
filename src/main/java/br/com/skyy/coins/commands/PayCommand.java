package br.com.skyy.coins.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Executor para o comando /pay (e aliases: pagar, enviar).
 *
 * <p>Uso: {@code /pay <jogador> <valor>}
 * Equivale a {@code /coins enviar <jogador> <valor>}.
 *
 * <p>A lógica real (cooldown, toggle, transferência atômica…)
 * reside no {@link CoinsCommand} e é reutilizada aqui.
 */
public class PayCommand implements CommandExecutor, TabCompleter {

    private final CoinsCommand delegate;

    public PayCommand(CoinsCommand delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // /pay <jogador> <valor>  →  /coins enviar <jogador> <valor>
        String[] forwarded = new String[args.length + 1];
        forwarded[0] = "enviar";
        System.arraycopy(args, 0, forwarded, 1, args.length);
        return delegate.onCommand(sender, command, label, forwarded);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(sender))
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            List<String> suggestions = new ArrayList<>();
            for (String ex : new String[]{"100", "500", "1000", "5000", "10000"}) {
                if (ex.startsWith(args[1])) suggestions.add(ex);
            }
            return suggestions;
        }
        return new ArrayList<>();
    }
}
