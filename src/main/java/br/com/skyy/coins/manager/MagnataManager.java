package br.com.skyy.coins.manager;

import br.com.skyy.coins.api.event.MagnataChangeEvent;
import br.com.skyy.coins.storage.FileStorage;
import br.com.skyy.coins.util.CoinsFormatter;
import br.com.skyy.coins.util.SoundUtil;
import br.com.skyy.coins.util.TextUtil;
import br.com.skyy.coins.util.VersionUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Rastreia quem é o magnata atual e dispara a notificação (broadcast + title)
 * apenas UMA vez quando o topo muda.
 * Compatível com Minecraft 1.8–1.21.
 */
public class MagnataManager {

    private volatile String currentMagnataName  = null;
    private volatile long   currentMagnataCoins = 0L;

    private final CoinsManager    coinsManager;
    private final FileConfiguration config;

    public MagnataManager(CoinsManager coinsManager, FileConfiguration config) {
        this.coinsManager = coinsManager;
        this.config        = config;
    }

    /**
     * Chamado sempre que o saldo de um jogador muda.
     *
     * Notifica APENAS quando o magnata muda de pessoa.
     * Se o mesmo jogador continua no topo (independente do saldo),
     * não dispara notificação novamente.
     */
    public void check(FileStorage fileStorage) {
        // Encontra o magnata em memória
        String newMagnataName  = null;
        long   newMagnataCoins = 0L;
        for (UUID uid : coinsManager.getAllProfiles()) {
            long   c = coinsManager.getCoins(uid);
            String n = coinsManager.getName(uid);
            if (n != null && c > newMagnataCoins) {
                newMagnataCoins = c;
                newMagnataName  = n;
            }
        }

        // Sem ninguém com coins — reseta
        if (newMagnataName == null) {
            currentMagnataName  = null;
            currentMagnataCoins = 0L;
            fileStorage.invalidateMagnataCache();
            return;
        }

        // Atualiza os coins do magnata atual mesmo sem mudança de pessoa
        // (necessário para o cache e menus refletirem o valor correto)
        if (newMagnataName.equals(currentMagnataName)) {
            currentMagnataCoins = newMagnataCoins;
            fileStorage.expireMagnataCache();
            return;
        }

        // Novo magnata (ou primeiro detectado) — notifica
        String previousName    = currentMagnataName;
        currentMagnataName     = newMagnataName;
        currentMagnataCoins    = newMagnataCoins;

        Player onlinePlayer = Bukkit.getPlayerExact(newMagnataName);
        if (onlinePlayer != null) {
            fileStorage.saveAsync(onlinePlayer.getUniqueId());
        }
        fileStorage.expireMagnataCache();

        String coinsFormatted = CoinsFormatter.format(newMagnataCoins);

        Bukkit.getPluginManager().callEvent(
                new MagnataChangeEvent(newMagnataName, newMagnataCoins, previousName));

        broadcast(newMagnataName, coinsFormatted);
        sendTitleAll(newMagnataName, coinsFormatted);

        for (Player p : Bukkit.getOnlinePlayers()) {
            SoundUtil.play(p, "magnata");
        }
    }

    // ─── Broadcast ──────────────────────────────────────────────────

    private void broadcast(String name, String coins) {
        String template = config.getString(
                "messages.magnata-broadcast",
                "\n &a&lNOVO MAGNATA\n\n  &eO jogador &a{player} &eé o novo &a&lMAGNATA\n" +
                        " &edo servidor!\n  &eO novo magnata possui &8→ &2⛃&a{coins} &ecoins\n "
        );        String msg = TextUtil.color(template
                .replace("{player}", name)
                .replace("{coins}",  coins));

        // Compatível com 1.8–1.21 — sem Adventure
        for (String line : msg.split("\n", -1)) {
            Bukkit.broadcastMessage(line);
        }
    }

    // ─── Title — compatível com 1.8–1.21 via VersionUtil ─────────────

    private void sendTitleAll(String name, String coins) {
        String rawTitle = config.getString("messages.magnata-title", "&a{player}")
                .replace("{player}", name).replace("{coins}", coins);
        String rawSub   = config.getString("messages.magnata-subtitle", "&eé o novo magnata!")
                .replace("{player}", name).replace("{coins}", coins);

        int fadeIn  = config.getInt("messages.magnata-title-fadein",  10);
        int stay    = config.getInt("messages.magnata-title-stay",    60);
        int fadeOut = config.getInt("messages.magnata-title-fadeout", 20);

        for (Player p : Bukkit.getOnlinePlayers()) {
            VersionUtil.sendTitle(p, rawTitle, rawSub, fadeIn, stay, fadeOut);
        }
    }
}
