package com.skyy.coins.manager;

import com.skyy.coins.api.event.MagnataChangeEvent;
import com.skyy.coins.storage.FileStorage;
import com.skyy.coins.util.CoinsFormatter;
import com.skyy.coins.util.SoundUtil;
import com.skyy.coins.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;

/**
 * Rastreia quem é o magnata atual e dispara a notificação (broadcast + title)
 * apenas UMA vez quando o topo muda.
 */
public class MagnataManager {

    // volatile garante visibilidade entre threads (main thread + endBatch de tasks async)
    private volatile String currentMagnataName = null;

    private final CoinsManager coinsManager;
    private final FileConfiguration config;

    public MagnataManager(CoinsManager coinsManager, FileConfiguration config) {
        this.coinsManager = coinsManager;
        this.config        = config;
    }

    /**
     * Chamado sempre que o saldo de um jogador muda.
     * Verifica se ele passou a ser o novo magnata; se sim, notifica uma vez.
     *
     * USA APENAS DADOS EM MEMÓRIA — nunca faz query SQL na main thread.
     * O jogador que teve o saldo alterado está online, portanto comparação
     * em memória é suficiente para detectar mudança de magnata sem lag de TPS.
     */
    public void check(FileStorage fileStorage) {
        // Encontra o magnata apenas usando dados em memória (sem query DB)
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

        // Sem ninguém com coins — reseta o magnata atual
        if (newMagnataName == null) {
            currentMagnataName = null;
            fileStorage.invalidateMagnataCache();
            return;
        }


        // Ainda é o mesmo magnata — nada a fazer
        if (newMagnataName.equals(currentMagnataName)) return;

        // Novo magnata detectado — atualiza estado
        String previousName = currentMagnataName;
        currentMagnataName  = newMagnataName;

        // Persiste imediatamente no banco
        org.bukkit.entity.Player onlinePlayer = org.bukkit.Bukkit.getPlayerExact(newMagnataName);
        if (onlinePlayer != null) {
            fileStorage.saveAsync(onlinePlayer.getUniqueId());
        }
        // Expira cache sem anular — evita janela de null que causava NPCs "Aguardando..."
        fileStorage.expireMagnataCache();

        String coinsFormatted = CoinsFormatter.format(newMagnataCoins);

        // long direto — sem cast int que causava overflow acima de 2.1B
        Bukkit.getPluginManager().callEvent(
                new MagnataChangeEvent(newMagnataName, newMagnataCoins, previousName));

        broadcast(newMagnataName, coinsFormatted);
        sendTitle(newMagnataName, coinsFormatted);

        for (Player p : Bukkit.getOnlinePlayers()) {
            SoundUtil.play(p, "magnata");
        }
    }

    // ─── Broadcast para todos no servidor ──────────────────────────────

    private void broadcast(String name, String coins) {
        String template = config.getString(
                "messages.magnata-broadcast",
                "\n&6&l✦ NOVO MAGNATA ✦\n\n&f{player} &eassumiu o topo do servidor!\n&7Saldo atual: &a{coins} coins\n"
        );
        // TextUtil processa & e \n literal do YAML
        String msg = TextUtil.color(template
                .replace("{player}", name)
                .replace("{coins}",  coins));

        for (String line : msg.split("\n", -1)) {
            Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(line));
        }
    }

    // ─── Title — Adventure API (Paper 1.19+) ────────────────────────

    private void sendTitle(String name, String coins) {
        String rawTitle = config.getString("messages.magnata-title", "&6&l✦ {player} ✦")
                .replace("{player}", name).replace("{coins}", coins);
        String rawSub   = config.getString("messages.magnata-subtitle", "&eé o novo &6&lMagnata&e do servidor!")
                .replace("{player}", name).replace("{coins}", coins);

        int fadeIn  = config.getInt("messages.magnata-title-fadein",  10);
        int stay    = config.getInt("messages.magnata-title-stay",    60);
        int fadeOut = config.getInt("messages.magnata-title-fadeout", 20);

        Component titleComp = LegacyComponentSerializer.legacyAmpersand().deserialize(rawTitle);
        Component subComp   = LegacyComponentSerializer.legacyAmpersand().deserialize(rawSub);

        Title title = Title.title(
                titleComp,
                subComp,
                Title.Times.times(
                        Duration.ofMillis(fadeIn  * 50L),
                        Duration.ofMillis(stay    * 50L),
                        Duration.ofMillis(fadeOut * 50L)
                )
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
        }
    }
}
