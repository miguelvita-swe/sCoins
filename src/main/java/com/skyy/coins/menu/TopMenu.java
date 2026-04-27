package com.skyy.coins.menu;

import com.skyy.coins.storage.FileStorage;
import com.skyy.coins.util.CoinsFormatter;
import com.skyy.coins.util.SoundUtil;
import com.skyy.coins.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

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
            int noPlayersSlot = config.getInt("top-menu.no-players-slot", 22);
            String noName    = color(config.getString("top-menu.no-players.name", "&cSem jogadores"));
            List<String> noLore = colorList(config.getStringList("top-menu.no-players.lore"));
            String texture   = config.getString("top-menu.no-players.skull-texture", "");
            inv.setItem(noPlayersSlot, texture.isEmpty()
                    ? makeItem(Material.BARRIER, noName, noLore)
                    : buildSkull(texture, noName, noLore));
        } else {
            // Template configurável em menus.yml: top-menu.player-item
            String nameTemplate = config.getString("top-menu.player-item.name", "{medal}&f{player}");
            List<String> loreTemplate = config.getStringList("top-menu.player-item.lore");

            for (int i = 0; i < playerSlots.size(); i++) {
                int slot = playerSlots.get(i);
                if (i < top.size()) {
                    String[] entry   = top.get(i);
                    String pName     = entry[0];
                    String pCoins    = CoinsFormatter.format(Long.parseLong(entry[1]));
                    int    position  = i + 1;

                    String medal = switch (position) {
                        case 1 -> "🥇 ";
                        case 2 -> "🥈 ";
                        case 3 -> "🥉 ";
                        default -> "#" + position + " ";
                    };

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
                        for (String part : processed.split("\n", -1)) {
                            lore.add(part);
                        }
                    }

                    // Usa o jogador online se disponível (sem I/O).
                    // getOfflinePlayer(nome) pode bloquear a main thread consultando a Mojang
                    // para cada jogador do top — até 10 chamadas bloqueantes de uma vez.
                    ItemStack head;
                    Player onlineTarget = Bukkit.getPlayerExact(pName);
                    if (onlineTarget != null) {
                        head = new ItemStack(Material.PLAYER_HEAD);
                        SkullMeta meta = (SkullMeta) head.getItemMeta();
                        if (meta != null) {
                            meta.setOwningPlayer(onlineTarget);
                            meta.setDisplayName(itemName);
                            if (!lore.isEmpty()) meta.setLore(lore);
                            head.setItemMeta(meta);
                        }
                    } else {
                        // Offline: cabeça genérica sem lookup de API
                        head = makeItem(Material.PLAYER_HEAD, itemName, lore);
                    }
                    inv.setItem(slot, head);
                }
                // Slots sem jogador ficam vazios
            }
        }

        inv.setItem(backSlot, buildBackButton(config));
        player.openInventory(inv);
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private static ItemStack buildBackButton(FileConfiguration config) {
        String matStr = config.getString("top-menu.back-button.material", "ARROW");
        String name   = color(config.getString("top-menu.back-button.name", "&c&lVoltar"));
        List<String> lore = colorList(config.getStringList("top-menu.back-button.lore"));
        // suporta skull no back button também
        String texture = config.getString("top-menu.back-button.skull-texture", "");
        if (!texture.isEmpty()) return buildSkull(texture, name, lore);
        return makeItem(parseMaterial(matStr), name, lore);
    }

    private static ItemStack buildSkull(String urlOrBase64, String name, List<String> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;
        try {
            String url = (urlOrBase64.startsWith("http://") || urlOrBase64.startsWith("https://"))
                    ? urlOrBase64
                    : new String(Base64.getDecoder().decode(urlOrBase64)).split("\"url\":\"")[1].split("\"")[0];
            PlayerProfile profile   = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(url));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (MalformedURLException | ArrayIndexOutOfBoundsException ignored) {}
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
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.BARRIER; }
    }

    private static String color(String text) {
        return TextUtil.color(text);
    }

    private static List<String> colorList(List<String> lines) {
        return TextUtil.colorLore(lines);
    }
}
