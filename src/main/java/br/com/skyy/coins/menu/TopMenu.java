package br.com.skyy.coins.menu;

import br.com.skyy.coins.storage.FileStorage;
import br.com.skyy.coins.util.CoinsFormatter;
import br.com.skyy.coins.util.SkullUtil;
import br.com.skyy.coins.util.SoundUtil;
import br.com.skyy.coins.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TopMenu {

    public static String TITLE = null;

    public static void loadTitle(FileConfiguration config) {
        TITLE = color(config.getString("top-menu.title", "&6&lTop Jogadores"));
    }

    private TopMenu() {}

    public static void open(Player player, FileConfiguration config, FileStorage fileStorage) {
        if (TITLE == null) loadTitle(config);
        String title = TITLE;
        int size     = config.getInt("top-menu.size", 54);
        int backSlot = config.getInt("top-menu.back-slot", 39);
        List<Integer> playerSlots = config.getIntegerList("top-menu.player-slots");

        Inventory inv = Bukkit.createInventory(null, size, title);

        // Busca top jogadores do arquivo
        List<String[]> top = fileStorage.getTopPlayers(playerSlots.size());

        if (top.isEmpty()) {
            // Sem nenhum jogador: exibe skull de "sem jogadores" no slot 22
            int noPlayersSlot    = config.getInt("top-menu.no-players-slot", 22);
            String noName        = color(config.getString("top-menu.no-players.name", "&cSem jogadores"));
            List<String> noLore  = colorList(config.getStringList("top-menu.no-players.lore"));
            String texture       = config.getString("top-menu.no-players.skull-texture", "");
            inv.setItem(noPlayersSlot, texture.isEmpty()
                    ? SkullUtil.makeItem(Material.BARRIER, noName, noLore)
                    : SkullUtil.buildCustomSkull(texture, noName, noLore));
        } else {
            // Template configurável em menus.yml: top-menu.player-item
            String nameTemplate     = config.getString("top-menu.player-item.name", "{medal}&f{player}");
            List<String> loreTemplate = config.getStringList("top-menu.player-item.lore");

            for (int i = 0; i < playerSlots.size(); i++) {
                int slot = playerSlots.get(i);
                if (i < top.size()) {
                    String[] entry  = top.get(i);
                    String pName    = entry[0];
                    String pCoins   = CoinsFormatter.format(Long.parseLong(entry[1]));
                    int    position = i + 1;

                    String medal;
                    if      (position == 1) medal = "\uD83E\uDD47 ";
                    else if (position == 2) medal = "\uD83E\uDD48 ";
                    else if (position == 3) medal = "\uD83E\uDD49 ";
                    else                    medal  = "#" + position + " ";

                    String itemName = color(nameTemplate
                            .replace("{medal}", medal)
                            .replace("{player}", pName)
                            .replace("{position}", String.valueOf(position))
                            .replace("{coins}", pCoins));

                    List<String> lore = new ArrayList<>();
                    for (String line : loreTemplate) {
                        String processed = TextUtil.color(line
                                .replace("{medal}", medal)
                                .replace("{player}", pName)
                                .replace("{position}", String.valueOf(position))
                                .replace("{coins}", pCoins));
                        for (String part : processed.split("\n", -1)) lore.add(part);
                    }

                    // Online: usa cabeça sem I/O; offline: SkullUtil.buildOfflineHead sem Mojang lookup
                    ItemStack head;
                    Player onlineTarget = Bukkit.getPlayerExact(pName);
                    if (onlineTarget != null) {
                        head = SkullUtil.buildPlayerHead(onlineTarget, itemName, lore);
                    } else {
                        @SuppressWarnings("deprecation")
                        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(pName);
                        head = SkullUtil.buildOfflineHead(offlineTarget, itemName, lore);
                    }
                    inv.setItem(slot, head);
                }
                // Slots sem jogador ficam vazios
            }
        }

        inv.setItem(backSlot, buildBackButton(config));
        SoundUtil.play(player, "menu-open");
        player.openInventory(inv);
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private static ItemStack buildBackButton(FileConfiguration config) {
        String matStr  = config.getString("top-menu.back-button.material", "ARROW");
        String name    = color(config.getString("top-menu.back-button.name", "&c&lVoltar"));
        List<String> lore = colorList(config.getStringList("top-menu.back-button.lore"));
        String texture = config.getString("top-menu.back-button.skull-texture", "");
        if (!texture.isEmpty()) return SkullUtil.buildCustomSkull(texture, name, lore);
        return SkullUtil.makeItem(parseMaterial(matStr), name, lore);
    }

    private static Material parseMaterial(String name) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.BARRIER; }
    }

    private static String color(String text) { return TextUtil.color(text); }
    private static List<String> colorList(List<String> lines) { return TextUtil.colorLore(lines); }
}
