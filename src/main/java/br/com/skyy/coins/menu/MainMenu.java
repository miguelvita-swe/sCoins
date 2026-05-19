package br.com.skyy.coins.menu;

import br.com.skyy.coins.manager.CoinsManager;
import br.com.skyy.coins.manager.ToggleManager;
import br.com.skyy.coins.manager.TransactionManager;
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
import java.util.Arrays;
import java.util.List;

public class MainMenu {

    // TITLE é atualizado uma única vez ao abrir o primeiro menu (ou após reload).
    // Nunca deve ser sobrescrito dentro do open() em cada chamada,
    // pois isso causaria race condition se dois jogadores abrirem o menu simultaneamente.
    public static String TITLE = null;

    /** Deve ser chamado no onEnable e após /coins reload para pré-computar o título. */
    public static void loadTitle(FileConfiguration config) {
        TITLE = color(config.getString("main-menu.title", "&8Menu de Coins"));
    }

    private MainMenu() {}

    public static void open(Player player, CoinsManager coinsManager,
                             TransactionManager transactionManager,
                             ToggleManager toggleManager,
                             FileStorage fileStorage,
                             FileConfiguration config) {

        // Garante que o título esteja carregado (caso loadTitle não tenha sido chamado)
        if (TITLE == null) loadTitle(config);
        String title = TITLE;
        int size = config.getInt("main-menu.size", 27);
        Inventory inv = Bukkit.createInventory(null, size, title);

        // ── Cabeça do jogador ────────────────────────────────────────────
        int headSlot    = config.getInt("main-menu.player-head.slot", 9);
        String headName = color(config.getString("main-menu.player-head.name", "&aSuas informações"));
        String toggleOn  = color(config.getString("main-menu.player-head.toggle-on",  "&aON"));
        String toggleOff = color(config.getString("main-menu.player-head.toggle-off", "&cOFF"));

        String coinsStr  = CoinsFormatter.format(coinsManager.getCoins(player.getUniqueId()));
        int transacoes   = transactionManager.getHistory(player.getUniqueId()).size();
        String toggleStr = toggleManager.canReceive(player.getUniqueId()) ? toggleOn : toggleOff;

        List<String> headLore = buildLore(config.getStringList("main-menu.player-head.lore"),
                "{coins}",      coinsStr,
                "{transacoes}", String.valueOf(transacoes),
                "{toggle}",     toggleStr);

        inv.setItem(headSlot, SkullUtil.buildPlayerHead(player, headName, headLore));

        // ── Extrato ──────────────────────────────────────────────────────
        int histSlot       = config.getInt("main-menu.extrato-button.slot", 11);
        String histMat     = config.getString("main-menu.extrato-button.material", "BOOK");
        String histName    = color(config.getString("main-menu.extrato-button.name", "&eExtrato"));
        List<String> histLore    = colorList(config.getStringList("main-menu.extrato-button.lore"));
        String histTexture = config.getString("main-menu.extrato-button.skull-texture", "");
        if (histMat.equalsIgnoreCase("PLAYER_HEAD") && !histTexture.isEmpty()) {
            inv.setItem(histSlot, SkullUtil.buildCustomSkull(histTexture, histName, histLore));
        } else {
            inv.setItem(histSlot, SkullUtil.makeItem(parseMaterial(histMat), histName, histLore));
        }

        // ── Top jogadores ────────────────────────────────────────────────
        int topSlot    = config.getInt("main-menu.top-button.slot", 13);
        String topMat  = config.getString("main-menu.top-button.material", "NETHER_STAR");
        String topName = color(config.getString("main-menu.top-button.name", "&aTop Jogadores"));
        List<String> topLore     = colorList(config.getStringList("main-menu.top-button.lore"));
        String topTexture  = config.getString("main-menu.top-button.skull-texture", "");
        if (topMat.equalsIgnoreCase("PLAYER_HEAD") && !topTexture.isEmpty()) {
            inv.setItem(topSlot, SkullUtil.buildCustomSkull(topTexture, topName, topLore));
        } else {
            inv.setItem(topSlot, SkullUtil.makeItem(parseMaterial(topMat), topName, topLore));
        }

        // ── Magnata ──────────────────────────────────────────────────────
        int magnataSlot        = config.getInt("main-menu.magnata-button.slot", 15);
        String magnataItemName = color(config.getString("main-menu.magnata-button.name", "&aMagnata"));
        String[] magnataData   = fileStorage.getMagnata();
        ItemStack magnataItem;

        if (magnataData != null) {
            String magnataPlayer = magnataData[0];
            String magnataCoins  = CoinsFormatter.format(Long.parseLong(magnataData[1]));
            List<String> magnataLore = buildLore(
                    config.getStringList("main-menu.magnata-button.lore"),
                    "{magnata}", magnataPlayer,
                    "{saldo}",   magnataCoins);

            Player onlineMagnata = Bukkit.getPlayerExact(magnataPlayer);
            if (onlineMagnata != null) {
                magnataItem = SkullUtil.buildPlayerHead(onlineMagnata, magnataItemName, magnataLore);
            } else {
                @SuppressWarnings("deprecation")
                OfflinePlayer offlineMagnata = Bukkit.getOfflinePlayer(magnataPlayer);
                magnataItem = SkullUtil.buildOfflineHead(offlineMagnata, magnataItemName, magnataLore);
            }
        } else {
            String emptyName       = color(config.getString("main-menu.magnata-empty.name", "&cSem Magnata"));
            List<String> emptyLore = colorList(config.getStringList("main-menu.magnata-empty.lore"));
            String texture         = config.getString("main-menu.magnata-empty.skull-texture", "");
            if (!texture.isEmpty()) {
                magnataItem = SkullUtil.buildCustomSkull(texture, emptyName, emptyLore);
            } else {
                magnataItem = SkullUtil.makeItem(Material.BONE, emptyName, emptyLore);
            }
        }

        inv.setItem(magnataSlot, magnataItem);
        SoundUtil.play(player, "menu-open");
        player.openInventory(inv);
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private static List<String> buildLore(List<String> raw, String... replacements) {
        List<String> result = new ArrayList<>();
        for (String line : raw) {
            String processed = line;
            for (int i = 0; i + 1 < replacements.length; i += 2) {
                processed = processed.replace(replacements[i], replacements[i + 1]);
            }
            result.addAll(Arrays.asList(TextUtil.color(processed).split("\n", -1)));
        }
        return result;
    }

    private static Material parseMaterial(String name) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.PAPER; }
    }

    private static String color(String text) { return TextUtil.color(text); }

    private static List<String> colorList(List<String> lines) { return TextUtil.colorLore(lines); }
}
