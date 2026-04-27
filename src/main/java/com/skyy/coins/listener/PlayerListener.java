package com.skyy.coins.listener;

import com.skyy.coins.manager.CoinsManager;
import com.skyy.coins.manager.CooldownManager;
import com.skyy.coins.manager.RankManager;
import com.skyy.coins.manager.ToggleManager;
import com.skyy.coins.manager.TransactionManager;
import com.skyy.coins.storage.FileStorage;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerListener implements Listener {

    private final CoinsManager coinsManager;
    private final FileStorage fileStorage;
    private final CooldownManager cooldownManager;
    private final TransactionManager transactionManager;
    private final ToggleManager toggleManager;
    private final RankManager rankManager;
    private final JavaPlugin plugin;

    /**
     * Contador de geração por UUID.
     * Problema: jogador sai → save async disparado (geração N).
     *           jogador reconecta antes do save terminar → load async (geração N+1).
     *           save da geração N termina DEPOIS e sobrescreve load N+1 → dados perdidos.
     * Solução: cada onQuit captura a geração atual e só executa o save se a geração
     *          ainda bater — se o jogador reconectou, a geração foi incrementada e
     *          o save antigo é descartado.
     */
    private final ConcurrentHashMap<UUID, AtomicInteger> saveGeneration = new ConcurrentHashMap<>();

    public PlayerListener(CoinsManager coinsManager, FileStorage fileStorage,
                          CooldownManager cooldownManager, TransactionManager transactionManager,
                          ToggleManager toggleManager, RankManager rankManager, JavaPlugin plugin) {
        this.coinsManager = coinsManager;
        this.fileStorage = fileStorage;
        this.cooldownManager = cooldownManager;
        this.transactionManager = transactionManager;
        this.toggleManager = toggleManager;
        this.rankManager = rankManager;
        this.plugin = plugin;
    }

    /**
     * Pré-carrega o perfil de forma assíncrona ANTES do jogador entrar.
     * AsyncPlayerPreLoginEvent roda em thread separada — ideal para I/O.
     * Quando termina, agenda de volta na main thread para popular os managers.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();

        // Incrementa a geração ANTES de carregar — invalida qualquer save async pendente
        // de uma sessão anterior (reconnect rápido antes do save terminar).
        saveGeneration.computeIfAbsent(uuid, k -> new AtomicInteger(0)).incrementAndGet();

        try {
            // Já está em thread async — carrega direto
            fileStorage.loadProfile(uuid, name);
        } catch (Exception e) {
            plugin.getLogger().warning("[sCoins] Erro ao carregar perfil de " + name
                    + " no preLogin: " + e.getMessage() + " — perfil de fallback será usado.");
            if (!coinsManager.isLoaded(uuid)) {
                coinsManager.loadProfile(uuid, name, 0L);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Perfil já foi carregado no preLogin — apenas aplica o tab
        rankManager.refresh();
        rankManager.applyTab(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        final long    coins        = coinsManager.getCoins(uuid);
        final String  name         = coinsManager.getName(uuid);
        final boolean toggle       = toggleManager.canReceive(uuid);
        final long    lastTransfer = cooldownManager.getLastTransferTimestamp(uuid);

        // Captura a geração ATUAL antes de remover o perfil.
        // Se o jogador reconectar antes do save terminar, onPreLogin incrementa a geração
        // e o save abaixo detecta a discrepância → descarta o save stale.
        final int myGeneration = saveGeneration.computeIfAbsent(uuid, k -> new AtomicInteger(0))
                .incrementAndGet();

        coinsManager.removeProfile(uuid);
        cooldownManager.removeCooldown(uuid);
        transactionManager.removeHistory(uuid);
        toggleManager.remove(uuid);
        rankManager.removePlayer(uuid);

        if (name != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                // Verifica se ainda somos a geração mais recente.
                // Se o jogador reconectou, onPreLogin já incrementou para myGeneration+1.
                AtomicInteger gen = saveGeneration.get(uuid);
                if (gen == null || gen.get() != myGeneration) {
                    // Geração superada — save stale descartado para não sobrescrever load recente
                    return;
                }
                if (fileStorage.isUsingDatabase()) {
                    fileStorage.getDatabaseManager().savePlayer(uuid, name, coins, toggle, lastTransfer);
                } else {
                    fileStorage.saveProfileSnapshot(uuid, name, coins, toggle);
                    fileStorage.flushDirty();
                }
                // Remove o registro de geração após save bem-sucedido para não vazar memória
                saveGeneration.remove(uuid);
            });
        }
    }
}
