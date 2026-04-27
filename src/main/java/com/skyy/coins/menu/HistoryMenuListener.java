package com.skyy.coins.menu;

import com.skyy.coins.manager.CoinsManager;
import com.skyy.coins.manager.ToggleManager;
import com.skyy.coins.manager.TransactionManager;
import com.skyy.coins.storage.FileStorage;
import com.skyy.coins.util.SoundUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class HistoryMenuListener implements Listener {

    private final CoinsManager coinsManager;
    private final TransactionManager transactionManager;
    private final ToggleManager toggleManager;
    private final FileStorage fileStorage;
    private final FileConfiguration config;

    public HistoryMenuListener(CoinsManager coinsManager, TransactionManager transactionManager,
                                ToggleManager toggleManager, FileStorage fileStorage,
                                FileConfiguration config) {
        this.coinsManager       = coinsManager;
        this.transactionManager = transactionManager;
        this.toggleManager      = toggleManager;
        this.fileStorage        = fileStorage;
        this.config             = config;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (HistoryMenu.TITLE == null || !event.getView().getTitle().equals(HistoryMenu.TITLE)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        int rawSlot    = event.getRawSlot();
        int backSlot   = config.getInt("history-menu.back-slot",   39);
        int toggleSlot = config.getInt("history-menu.toggle-slot", 41);

        if (rawSlot == backSlot) {
            SoundUtil.play(player, "menu-back");
            MainMenu.open(player, coinsManager, transactionManager, toggleManager, fileStorage, config);
            return;
        }

        if (rawSlot == toggleSlot) {
            // Alterna para o menu de extrato
            SoundUtil.play(player, "menu-click");
            ExtratoMenu.open(player, coinsManager, transactionManager, config);
        }
    }
}
