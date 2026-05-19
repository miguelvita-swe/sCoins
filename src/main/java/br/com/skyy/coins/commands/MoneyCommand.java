package br.com.skyy.coins.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Executor para o comando /money (e aliases: coin, coins, balance…).
 *
 * <p>Comportamento:
 * <ul>
 *   <li>Sem argumentos → abre o menu principal (se {@code general.money-menu: true})
 *       ou exibe o saldo no chat.</li>
 *   <li>Com argumento {@code <jogador>} → exibe o saldo do jogador informado.</li>
 * </ul>
 *
 * <p>Delega ao mesmo fluxo do {@code /coins} para evitar duplicação de lógica.
 */
public class MoneyCommand implements CommandExecutor {

    private final CoinsCommand delegate;

    /**
     * @param delegate instância já construída do {@link CoinsCommand} principal.
     *                 Toda a lógica real reside lá — este executor apenas repassa.
     */
    public MoneyCommand(CoinsCommand delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // Repassa integralmente ao CoinsCommand principal.
        // O CoinsCommand já trata /coins sem args (menu/saldo) e /coins <jogador>.
        return delegate.onCommand(sender, command, label, args);
    }
}
