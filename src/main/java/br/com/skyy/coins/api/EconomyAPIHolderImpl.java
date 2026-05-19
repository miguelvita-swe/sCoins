package br.com.skyy.coins.api;

import br.com.skyy.coins.manager.CoinsManager;
import br.com.skyy.coins.manager.RankManager;
import br.com.skyy.coins.manager.TransactionManager;
import br.com.skyy.coins.model.TransactionType;
import br.com.skyy.coins.storage.FileStorage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Implementação de {@link EconomyAPIHolder}.
 * Não use esta classe diretamente — obtenha via BukkitServicesManager:
 *
 * <pre>
 * RegisteredServiceProvider&lt;EconomyAPIHolder&gt; rsp =
 *         Bukkit.getServicesManager().getRegistration(EconomyAPIHolder.class);
 * EconomyAPIHolder api = rsp == null ? null : rsp.getProvider();
 * </pre>
 */
public class EconomyAPIHolderImpl implements EconomyAPIHolder {

    private final CoinsManager       coinsManager;
    private final TransactionManager transactionManager;
    private final FileStorage        fileStorage;
    private final RankManager        rankManager;
    private final FileConfiguration  config;

    public EconomyAPIHolderImpl(CoinsManager coinsManager,
                                TransactionManager transactionManager,
                                FileStorage fileStorage,
                                RankManager rankManager,
                                FileConfiguration config) {
        this.coinsManager       = coinsManager;
        this.transactionManager = transactionManager;
        this.fileStorage        = fileStorage;
        this.rankManager        = rankManager;
        this.config             = config;
    }

    // ── hasAccount ───────────────────────────────────────────────────────────

    @Override
    public boolean hasAccount(String playerName) {
        return hasAccount(playerName, true);
    }

    @Override
    public boolean hasAccount(String playerName, boolean check) {
        if (playerName == null || playerName.isEmpty()) return false;

        // check=false → modo admin, permite offline sem restrição
        if (!check) {
            return resolveUUID(playerName) != null;
        }

        // check=true → respeita configuração offline-transactions
        boolean offlineAllowed = config.getBoolean("general.offline-transactions", true);
        UUID uuid = resolveUUID(playerName);
        if (uuid == null) return false;

        // Se transações offline desativadas, exige que o jogador esteja online
        if (!offlineAllowed) {
            return Bukkit.getPlayer(playerName) != null;
        }

        return true;
    }

    // ── getAccount ───────────────────────────────────────────────────────────

    @Override
    public Account getAccount(String playerName) {
        if (!hasAccount(playerName, false)) return null;
        UUID uuid = resolveUUID(playerName);
        if (uuid == null) return null;

        // Garante que o perfil está carregado em memória
        ensureLoaded(uuid, playerName);

        return new Account(uuid, playerName, coinsManager, transactionManager, fileStorage);
    }

    // ── getBalance ───────────────────────────────────────────────────────────

    @Override
    public double getBalance(String playerName) {
        UUID uuid = resolveUUID(playerName);
        if (uuid == null) return 0.0;
        ensureLoaded(uuid, playerName);
        return (double) coinsManager.getCoins(uuid);
    }

    // ── set ──────────────────────────────────────────────────────────────────

    @Override
    public void set(String playerName, double amount) {
        UUID uuid = resolveUUID(playerName);
        if (uuid == null) return;
        ensureLoaded(uuid, playerName);
        long safe = Math.max(0L, Math.min((long) amount, coinsManager.getMaxCoins()));
        coinsManager.setCoins(uuid, safe);
        fileStorage.saveAsync(uuid);
    }

    // ── has ──────────────────────────────────────────────────────────────────

    @Override
    public boolean has(String playerName, double amount) {
        if (amount <= 0) return true;
        UUID uuid = resolveUUID(playerName);
        if (uuid == null) return false;
        ensureLoaded(uuid, playerName);
        return coinsManager.getCoins(uuid) >= (long) amount;
    }

    // ── withdraw ─────────────────────────────────────────────────────────────

    @Override
    public EconomyResponse withdraw(String playerName, double amount, boolean apply) {
        if (amount <= 0)
            return EconomyResponse.failure("O valor deve ser maior que zero.");

        UUID uuid = resolveUUID(playerName);
        if (uuid == null)
            return EconomyResponse.failure("Jogador não encontrado: " + playerName);

        ensureLoaded(uuid, playerName);

        long longAmount = (long) amount;
        boolean ok = coinsManager.removeCoins(uuid, longAmount);
        if (!ok)
            return EconomyResponse.failure("Saldo insuficiente para " + playerName + ".");

        if (apply) {
            transactionManager.record(uuid, TransactionType.SENT, longAmount, null);
        }
        fileStorage.saveAsync(uuid);
        return EconomyResponse.success(amount, (double) coinsManager.getCoins(uuid));
    }

    // ── deposit ──────────────────────────────────────────────────────────────

    @Override
    public EconomyResponse deposit(String playerName, double amount, boolean apply) {
        if (amount <= 0)
            return EconomyResponse.failure("O valor deve ser maior que zero.");

        UUID uuid = resolveUUID(playerName);
        if (uuid == null)
            return EconomyResponse.failure("Jogador não encontrado: " + playerName);

        ensureLoaded(uuid, playerName);

        long longAmount = (long) amount;
        boolean ok = coinsManager.addCoins(uuid, longAmount);
        if (!ok)
            return EconomyResponse.failure("Limite máximo de coins atingido para " + playerName + ".");

        if (apply) {
            transactionManager.record(uuid, TransactionType.RECEIVED, longAmount, null);
        }
        fileStorage.saveAsync(uuid);
        return EconomyResponse.success(amount, (double) coinsManager.getCoins(uuid));
    }

    // ── getTop ───────────────────────────────────────────────────────────────

    @Override
    public LinkedHashMap<String, Double> getTop() {
        List<String[]> top = fileStorage.getTopPlayers(10);
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        for (String[] entry : top) {
            result.put(entry[0], Double.parseDouble(entry[1]));
        }
        return result;
    }

    // ── getPlayerTopPosition ─────────────────────────────────────────────────

    @Override
    public int getPlayerTopPosition(Player player) {
        if (player == null) return -1;
        return rankManager.getRankPosition(player.getName());
    }

    @Override
    public int getPlayerTopPosition(String playerName) {
        if (playerName == null || playerName.isEmpty()) return -1;
        return rankManager.getRankPosition(playerName);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Resolve o UUID de um jogador pelo nome.
     * Verifica primeiro os perfis carregados em memória (online),
     * depois o armazenamento offline (YAML/MySQL).
     *
     * @return UUID ou null se nunca tiver entrado no servidor.
     */
    private UUID resolveUUID(String playerName) {
        // 1. Jogador online — mais rápido
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) return online.getUniqueId();

        // 2. Perfis em memória (pode ter jogadores que saíram recentemente)
        for (UUID uuid : coinsManager.getAllProfiles()) {
            String name = coinsManager.getName(uuid);
            if (playerName.equalsIgnoreCase(name)) return uuid;
        }

        // 3. Busca offline pelo Bukkit (pode ser lento no Spigot — apenas como fallback)
        try {
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
            if (op.hasPlayedBefore()) return op.getUniqueId();
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Garante que o perfil do jogador está carregado no CoinsManager.
     * Se não estiver, carrega do storage (pode ser offline).
     */
    private void ensureLoaded(UUID uuid, String playerName) {
        if (coinsManager.isLoaded(uuid)) return;

        // Tenta carregar do storage offline
        long coins = fileStorage.getCoinsOffline(uuid);
        String name = fileStorage.getNameOffline(uuid);
        coinsManager.loadProfile(uuid, name != null ? name : playerName, coins);
    }
}
