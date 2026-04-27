package com.skyy.coins.task;

import com.skyy.coins.manager.CoinsManager;
import com.skyy.coins.manager.TransactionManager;
import com.skyy.coins.model.TransactionType;
import com.skyy.coins.storage.FileStorage;
import com.skyy.coins.util.CoinsFormatter;
import com.skyy.coins.util.Messages;
import com.skyy.coins.util.SoundUtil;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class RewardTask extends BukkitRunnable {

    private final CoinsManager coinsManager;
    private final TransactionManager transactionManager;
    private final FileStorage fileStorage;
    private final Messages messages;
    private final int rewardAmount;
    private final int rewardIntervalMinutes;
    private final JavaPlugin plugin;


    public RewardTask(CoinsManager coinsManager, TransactionManager transactionManager,
                      FileStorage fileStorage, Messages messages,
                      int rewardAmount, int rewardIntervalMinutes, JavaPlugin plugin) {
        this.coinsManager = coinsManager;
        this.transactionManager = transactionManager;
        this.fileStorage = fileStorage;
        this.messages = messages;
        this.rewardAmount = rewardAmount;
        this.rewardIntervalMinutes = rewardIntervalMinutes;
        this.plugin = plugin;
    }

    @Override
    public void run() {
        String  tempoFormatado = formatarTempo(rewardIntervalMinutes);
        boolean anyRewarded    = false;

        // Suspende magnata/rank checks durante o loop — serão disparados 1x ao final.
        // Com 300 jogadores isso corta de 300 iterações para 1.
        coinsManager.beginBatch();
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean rewarded = coinsManager.addCoins(player.getUniqueId(), rewardAmount);

                if (!rewarded) {
                    messages.send(player, "reward-max-reached",
                            "{max}", CoinsFormatter.format(coinsManager.getMaxCoins()));
                    continue;
                }

                anyRewarded = true;
                String coinsFormatado = CoinsFormatter.format(rewardAmount);

                transactionManager.record(player.getUniqueId(), TransactionType.REWARD, rewardAmount, null);

                messages.send(player, "reward-chat", "{coins}", coinsFormatado);
                SoundUtil.play(player, "reward");

                String actionbar = messages.get("reward-actionbar",
                        "{coins}", coinsFormatado,
                        "{tempo}", tempoFormatado);
                player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(actionbar));
            }
        } finally {
            // endBatch() reativa os checks e dispara magnata+rank uma única vez
            coinsManager.endBatch();
        }

        if (anyRewarded && fileStorage.isUsingDatabase()) {
            if (plugin != null) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, fileStorage::saveAll);
            }
        }
    }

    /**
     * Formata o intervalo de minutos para exibição amigável.
     * Ex: 1 → "1 minuto" | 5 → "5 minutos" | 60 → "1 hora" | 90 → "1h 30min"
     */
    private String formatarTempo(int minutos) {
        if (minutos < 60) {
            return minutos + (minutos == 1 ? " minuto" : " minutos");
        }
        int horas = minutos / 60;
        int resto = minutos % 60;
        if (resto == 0) {
            return horas + (horas == 1 ? " hora" : " horas");
        }
        return horas + "h " + resto + "min";
    }
}
