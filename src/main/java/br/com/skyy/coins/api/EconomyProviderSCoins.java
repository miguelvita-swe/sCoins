package br.com.skyy.coins.api;

import br.com.skyy.coins.manager.CoinsManager;
import br.com.skyy.core.providers.economy.EconomyProvider;
import org.bukkit.entity.Player;

/**
 * Registra o sCoins como provedor de economia no sCore.
 *
 * Outros plugins que usam sCore podem fazer:
 * <pre>
 *   EconomyManager eco = SCorePlugin.getInstance().getEconomyManager();
 *   eco.deposit(player, 1000, "scoins");
 *   eco.has(player, 500, "scoins");
 * </pre>
 *
 * O nome do provider é "scoins" (minúsculo) — padrão do sCore.
 */
public class EconomyProviderSCoins implements EconomyProvider {

    private final CoinsManager coinsManager;

    public EconomyProviderSCoins(CoinsManager coinsManager) {
        this.coinsManager = coinsManager;
    }

    @Override
    public String getName() {
        return "scoins";
    }

    @Override
    public boolean isAvailable() {
        return true; // sCoins sempre está disponível quando carregado
    }

    @Override
    public double getBalance(Player player) {
        return coinsManager.getCoins(player.getUniqueId());
    }

    @Override
    public boolean has(Player player, double amount) {
        return coinsManager.getCoins(player.getUniqueId()) >= (long) amount;
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (amount <= 0) return false;
        return coinsManager.removeCoins(player.getUniqueId(), (long) amount);
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (amount <= 0) return false;
        return coinsManager.addCoins(player.getUniqueId(), (long) amount);
    }
}
