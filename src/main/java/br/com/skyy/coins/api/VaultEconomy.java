package br.com.skyy.coins.api;

import br.com.skyy.coins.manager.CoinsManager;
import br.com.skyy.coins.storage.FileStorage;
import br.com.skyy.coins.util.CoinsFormatter;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementação do Vault Economy para o sCoins.
 *
 * <p>Registrar o sCoins como provedor Vault garante compatibilidade com
 * QUALQUER plugin do ecossistema que use economia: ShopGUI+, Jobs Reborn,
 * mcMMO, EssentialsX, CMI, AuctionHouse, etc.
 *
 * <p>O plugin funciona normalmente SEM Vault — a integração é soft-depend.
 * Se Vault não estiver instalado, o sCoins usa apenas sua própria API interna.
 *
 * <p><b>Como outros plugins obtêm o provider Vault:</b>
 * <pre>
 * RegisteredServiceProvider&lt;Economy&gt; rsp =
 *         Bukkit.getServicesManager().getRegistration(Economy.class);
 * Economy economy = rsp == null ? null : rsp.getProvider();
 * </pre>
 *
 * <p><b>Sobre banco de dados (bancos Vault):</b>
 * O sCoins não implementa o sistema de bancos do Vault
 * ({@code hasBankSupport()} retorna {@code false}) — bancos são uma feature
 * legada raramente usada e não fazem parte do escopo deste plugin.
 */
public class VaultEconomy extends AbstractEconomy {

    private final CoinsManager coinsManager;
    private final FileStorage fileStorage;
    private final FileConfiguration config;

    public VaultEconomy(CoinsManager coinsManager, FileStorage fileStorage, FileConfiguration config) {
        this.coinsManager = coinsManager;
        this.fileStorage  = fileStorage;
        this.config       = config;
    }

    // ── Metadados ────────────────────────────────────────────────────────

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "sCoins";
    }

    /** sCoins não implementa sistema de bancos. */
    @Override
    public boolean hasBankSupport() {
        return false;
    }

    /**
     * Retorna 0 para indicar que a moeda é inteira (sem centavos).
     * Plugins como ShopGUI+ respeitam isso e não exibem ".00".
     */
    @Override
    public int fractionalDigits() {
        return 0;
    }

    /** Formata o valor usando o sistema de formatação do sCoins (ex: 1.5M). */
    @Override
    public String format(double amount) {
        return CoinsFormatter.format((long) amount)
                + " " + currencyNamePlural();
    }

    @Override
    public String currencyNamePlural() {
        return config.getString("coin-preferences.plural-format", "coins");
    }

    @Override
    public String currencyNameSingular() {
        return config.getString("coin-preferences.singular-format", "coin");
    }

    // ── Conta ────────────────────────────────────────────────────────────

    @Override
    public boolean hasAccount(String playerName) {
        return resolveUUID(playerName) != null;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        if (player == null) return false;
        // Online → em memória
        if (coinsManager.isLoaded(player.getUniqueId())) return true;
        // Offline → verificar se já jogou (logo terá dados persistidos)
        return player.hasPlayedBefore();
    }

    /** World-specific — sCoins é global (sem mundos), delega para o método sem world. */
    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    // ── Saldo ────────────────────────────────────────────────────────────

    @Override
    public double getBalance(String playerName) {
        UUID uuid = resolveUUID(playerName);
        if (uuid == null) return 0.0;
        return coinsManager.getCoins(uuid);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (player == null) return 0.0;
        return coinsManager.getCoins(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    // ── Verificação ──────────────────────────────────────────────────────

    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    // ── Sacar (withdraw) ─────────────────────────────────────────────────

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        if (amount < 0) {
            return fail(amount, getBalance(playerName), "Valor negativo não permitido.");
        }
        UUID uuid = resolveUUID(playerName);
        if (uuid == null) {
            return fail(amount, 0, "Conta não encontrada: " + playerName);
        }
        boolean ok = coinsManager.removeCoins(uuid, (long) amount);
        if (!ok) {
            return fail(amount, coinsManager.getCoins(uuid), "Saldo insuficiente.");
        }
        fileStorage.saveAsync(uuid);
        return success(amount, coinsManager.getCoins(uuid));
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (player == null) return fail(amount, 0, "Jogador nulo.");
        if (amount < 0)     return fail(amount, 0, "Valor negativo não permitido.");

        boolean ok = coinsManager.removeCoins(player.getUniqueId(), (long) amount);
        if (!ok) {
            return fail(amount, coinsManager.getCoins(player.getUniqueId()), "Saldo insuficiente.");
        }
        fileStorage.saveAsync(player.getUniqueId());
        return success(amount, coinsManager.getCoins(player.getUniqueId()));
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    // ── Depositar (deposit) ───────────────────────────────────────────────

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        if (amount < 0) {
            return fail(amount, getBalance(playerName), "Valor negativo não permitido.");
        }
        UUID uuid = resolveUUID(playerName);
        if (uuid == null) {
            return fail(amount, 0, "Conta não encontrada: " + playerName);
        }
        boolean ok = coinsManager.addCoins(uuid, (long) amount);
        if (!ok) {
            return fail(amount, coinsManager.getCoins(uuid), "Limite máximo de coins atingido.");
        }
        fileStorage.saveAsync(uuid);
        return success(amount, coinsManager.getCoins(uuid));
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (player == null) return fail(amount, 0, "Jogador nulo.");
        if (amount < 0)     return fail(amount, 0, "Valor negativo não permitido.");

        boolean ok = coinsManager.addCoins(player.getUniqueId(), (long) amount);
        if (!ok) {
            return fail(amount, coinsManager.getCoins(player.getUniqueId()), "Limite máximo de coins atingido.");
        }
        fileStorage.saveAsync(player.getUniqueId());
        return success(amount, coinsManager.getCoins(player.getUniqueId()));
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    // ── Criar conta ───────────────────────────────────────────────────────

    /**
     * Cria conta se o jogador não tiver uma.
     * O sCoins cria conta automaticamente ao entrar, mas este método
     * satisfaz a interface Vault para plugins que chamam createPlayerAccount() antes de depositar.
     */
    @Override
    public boolean createPlayerAccount(String playerName) {
        UUID uuid = resolveUUID(playerName);
        if (uuid == null) return false;
        if (coinsManager.isLoaded(uuid)) return true;
        coinsManager.setCoins(uuid, 0);
        fileStorage.saveAsync(uuid);
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (player == null) return false;
        if (coinsManager.isLoaded(player.getUniqueId())) return true;
        coinsManager.setCoins(player.getUniqueId(), 0);
        fileStorage.saveAsync(player.getUniqueId());
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    // ── Bancos (não suportados) ───────────────────────────────────────────

    @Override
    public EconomyResponse createBank(String name, String player) {
        return fail(0, 0, "sCoins não suporta bancos Vault.");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return fail(0, 0, "sCoins não suporta bancos Vault.");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return fail(0, 0, "sCoins não suporta bancos Vault.");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return fail(0, 0, "sCoins não suporta bancos Vault.");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return fail(amount, 0, "sCoins não suporta bancos Vault.");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return fail(amount, 0, "sCoins não suporta bancos Vault.");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return fail(amount, 0, "sCoins não suporta bancos Vault.");
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return fail(0, 0, "sCoins não suporta bancos Vault.");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return fail(0, 0, "sCoins não suporta bancos Vault.");
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return fail(0, 0, "sCoins não suporta bancos Vault.");
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return fail(0, 0, "sCoins não suporta bancos Vault.");
    }

    @Override
    public List<String> getBanks() {
        return new ArrayList<>();
    }

    // ── Utilitários internos ──────────────────────────────────────────────

    /** Resolução de UUID a partir do nome — online primeiro, offline com cache Bukkit. */
    @SuppressWarnings("deprecation")
    private UUID resolveUUID(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;

        // 1. Tenta online (mais rápido, sem I/O)
        org.bukkit.entity.Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) return online.getUniqueId();

        // 2. Tenta no cache offline do Bukkit (jogadores que já entraram no servidor)
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        if (offline.hasPlayedBefore()) return offline.getUniqueId();

        return null;
    }

    private static EconomyResponse success(double amount, double balance) {
        return new EconomyResponse(amount, balance,
                EconomyResponse.ResponseType.SUCCESS, null);
    }

    private static EconomyResponse fail(double amount, double balance, String message) {
        return new EconomyResponse(amount, balance,
                EconomyResponse.ResponseType.FAILURE, message);
    }
}
