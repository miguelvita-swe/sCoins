package com.skyy.coins.menu;

import com.skyy.coins.model.Transaction;
import com.skyy.coins.util.CoinsFormatter;
import com.skyy.coins.util.SoundUtil;
import com.skyy.coins.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

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

        // Botão para alternar para o extrato (true = leva ao extrato)
        inv.setItem(toggleSlot, ExtratoMenu.buildToggleItem(config, true));

        SoundUtil.play(player, "menu-open");
        player.openInventory(inv);
    }

    // -------------------------------------------------------

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

        // Substitui placeholders na lore
        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList(section + ".lore")) {
            String processed = TextUtil.color(line
                    .replace("{coins}", coins)
                    .replace("{player}", other)
                    .replace("{data}", date));
            // suporta \n dentro de linha de lore
            for (String part : processed.split("\n", -1)) {
                lore.add(part);
            }
        }

        return buildFromSection(config, section, lore);
    }

    /**
     * Constrói um item a partir de uma seção do config.
     * Suporta PLAYER_HEAD com skull-texture.
     * Se lore for null, lê da config sem substituir placeholders.
     */
    private static ItemStack buildFromSection(FileConfiguration config, String section, List<String> lore) {
        String matName  = config.getString(section + ".material", "PAPER");
        String name     = color(config.getString(section + ".name", "&7Item"));
        String texture  = config.getString(section + ".skull-texture", "");

        List<String> finalLore = lore != null ? lore : colorList(config.getStringList(section + ".lore"));

        if (matName.equalsIgnoreCase("PLAYER_HEAD") && !texture.isEmpty()) {
            return buildSkull(texture, name, finalLore);
        }

        return makeItem(parseMaterial(matName), name, finalLore);
    }

    /**
     * Constrói um PLAYER_HEAD com textura Base64 customizada.
     * Compatível com Paper/Spigot 1.19+.
     */
    /**
     * Constrói um PLAYER_HEAD com textura Base64 customizada.
     * Usa a API PlayerProfile do Bukkit — compatível com Paper 1.19+.
     */
    private static ItemStack buildSkull(String base64, String name, List<String> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        try {
            String url;

            // Aceita URL direta (http/https) ou Base64
            if (base64.startsWith("http://") || base64.startsWith("https://")) {
                url = base64;
            } else {
                String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
                url = decoded.split("\"url\":\"")[1].split("\"")[0];
            }

            PlayerProfile profile   = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(url));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);

        } catch (MalformedURLException | ArrayIndexOutOfBoundsException e) {
            // URL inválida — exibe cabeça padrão
        }

        meta.setDisplayName(name);
        if (!lore.isEmpty()) meta.setLore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    private static ItemStack makeItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        if (!lore.isEmpty()) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.PAPER;
        }
    }

    private static String color(String text) {
        return TextUtil.color(text);
    }

    private static List<String> colorList(List<String> lines) {
        return TextUtil.colorLore(lines);
    }
}
