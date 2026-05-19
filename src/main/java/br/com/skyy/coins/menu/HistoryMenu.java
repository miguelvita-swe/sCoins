package br.com.skyy.coins.menu;

import br.com.skyy.coins.model.Transaction;
import br.com.skyy.coins.util.CoinsFormatter;
import br.com.skyy.coins.util.SkullUtil;
import br.com.skyy.coins.util.SoundUtil;
import br.com.skyy.coins.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class HistoryMenu {

    public static String TITLE = null;

    public static void loadTitle(FileConfiguration config) {
        TITLE = color(config.getString("history-menu.title", "&6&lHistórico de Transações"));
    }

    private HistoryMenu() {}

    public static void open(Player player, List<Transaction> history, FileConfiguration config) {
        if (TITLE == null) loadTitle(config);
        String title = TITLE;

        int size          = config.getInt("history-menu.size", 54);
        int backSlot      = config.getInt("history-menu.back-slot", 39);
        int toggleSlot    = config.getInt("history-menu.toggle-slot", 41);
        int emptyHeadSlot = config.getInt("history-menu.empty-head-slot", 22);
        List<Integer> historySlots = config.getIntegerList("history-menu.history-slots");

        Inventory inv = Bukkit.createInventory(null, size, title);

        if (history.isEmpty()) {
            inv.setItem(emptyHeadSlot, buildFromSection(config, "history-menu.empty-slot", null));
        } else {
            int limit = Math.min(history.size(), historySlots.size());
            for (int i = 0; i < limit; i++) {
                inv.setItem(historySlots.get(i), buildTransactionItem(config, history.get(i)));
            }
        }

        inv.setItem(backSlot, buildFromSection(config, "history-menu.back-button", null));
        inv.setItem(toggleSlot, ExtratoMenu.buildToggleItem(config, true));

        SoundUtil.play(player, "menu-open");
        player.openInventory(inv);
    }

    private static ItemStack buildTransactionItem(FileConfiguration config, Transaction t) {
        String coins = CoinsFormatter.format(t.getAmount());
        String date  = t.getFormattedDate();
        String other = t.getOtherPlayer() != null ? t.getOtherPlayer() : "";

        String section;
        switch (t.getType()) {
            case SENT:         section = "history-menu.sent";         break;
            case RECEIVED:     section = "history-menu.received";     break;
            case ADMIN_ADD:    section = "history-menu.admin-add";    break;
            case ADMIN_REMOVE: section = "history-menu.admin-remove"; break;
            case REWARD:       section = "history-menu.reward";       break;
            default:           section = "history-menu.empty-slot";   break;
        }

        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList(section + ".lore")) {
            String processed = TextUtil.color(line
                    .replace("{coins}", coins)
                    .replace("{player}", other)
                    .replace("{data}", date));
            for (String part : processed.split("\n", -1)) lore.add(part);
        }

        return buildFromSection(config, section, lore);
    }

    private static ItemStack buildFromSection(FileConfiguration config, String section, List<String> lore) {
        String matName = config.getString(section + ".material", "PAPER");
        String name    = color(config.getString(section + ".name", "&7Item"));
        String texture = config.getString(section + ".skull-texture", "");
        List<String> finalLore = lore != null ? lore : colorList(config.getStringList(section + ".lore"));

        if (matName.equalsIgnoreCase("PLAYER_HEAD") && !texture.isEmpty()) {
            return SkullUtil.buildCustomSkull(texture, name, finalLore);
        }
        return SkullUtil.makeItem(parseMaterial(matName), name, finalLore);
    }

    private static Material parseMaterial(String name) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.PAPER; }
    }

    private static String color(String text) { return TextUtil.color(text); }
    private static List<String> colorList(List<String> lines) { return TextUtil.colorLore(lines); }
}
