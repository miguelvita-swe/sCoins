package com.skyy.coins.menu;

import com.skyy.coins.manager.CoinsManager;
import com.skyy.coins.manager.ToggleManager;
import com.skyy.coins.manager.TransactionManager;
import com.skyy.coins.storage.FileStorage;
import com.skyy.coins.util.Messages;
import com.skyy.coins.util.SoundUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class MainMenuListener implements Listener {

    private final CoinsManager coinsManager;
    private final TransactionManager transactionManager;
    private final ToggleManager toggleManager;
    private final FileStorage fileStorage;
    private final Messages messages;
    private final FileConfiguration config;

    public MainMenuListener(CoinsManager coinsManager, TransactionManager transactionManager,
                             ToggleManager toggleManager, FileStorage fileStorage,
                             Messages messages, FileConfiguration config) {
        this.coinsManager = coinsManager;
        this.transactionManager = transactionManager;
        this.toggleManager = toggleManager;
        this.fileStorage = fileStorage;
        this.messages = messages;
        this.config = config;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (MainMenu.TITLE == null || !event.getView().getTitle().equals(MainMenu.TITLE)) return;

        // Cancela todos os cliques — ninguém tira item do menu
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        int headSlot    = config.getInt("main-menu.player-head.slot", 9);
        int histSlot    = config.getInt("main-menu.history-button.slot", 11);
        int topSlot     = config.getInt("main-menu.top-button.slot", 13);

        // Clique na cabeça → toggle
        if (slot == headSlot) {
            boolean newState = toggleManager.toggle(player.getUniqueId());
            messages.send(player, newState ? "toggle-on" : "toggle-off");
            SoundUtil.play(player, newState ? "toggle-on" : "toggle-off");
            // Reabre o menu para refletir o novo estado
            MainMenu.open(player, coinsManager, transactionManager, toggleManager, fileStorage, config);
            return;
        }

        // Clique no histórico → abre menu de histórico
        if (slot == histSlot) {
            SoundUtil.play(player, "menu-click");
            HistoryMenu.open(player, transactionManager.getHistory(player.getUniqueId()), config);
            return;
        }

        // Clique no botão superior → abre o TopMenu
        if (slot == topSlot) {
            SoundUtil.play(player, "menu-click");
            TopMenu.open(player, config, fileStorage);
        }
    }
}
