package br.com.skyy.coins.menu;

import br.com.skyy.coins.manager.CoinsManager;
import br.com.skyy.coins.manager.TransactionManager;
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

/**
 * Menu /coins extrato — compatível com 1.8–1.21.
 */
public class ExtratoMenu {

    public static String TITLE = null;

    public static void loadTitle(FileConfiguration config) {
        TITLE = color(config.getString("extrato-menu.title", "&6&lExtrato Econômico"));
    }

    private ExtratoMenu() {}

    public static void open(Player player, CoinsManager coinsManager,
                             TransactionManager transactionManager,
                             FileConfiguration config) {
        if (TITLE == null) loadTitle(config);

        List<Transaction> history = transactionManager.getHistory(player.getUniqueId());
        long totalEnviado  = 0L;
        long totalRecebido = 0L;
        long totalReward   = 0L;

        for (Transaction t : history) {
            switch (t.getType()) {
                case SENT:         totalEnviado  += t.getAmount(); break;
                case RECEIVED:     totalRecebido += t.getAmount(); break;
                case ADMIN_ADD:    totalRecebido += t.getAmount(); break;
                case REWARD:       totalReward   += t.getAmount(); break;
                default: break;
            }
        }

        long saldoAtual = coinsManager.getCoins(player.getUniqueId());
        int size = config.getInt("extrato-menu.size", 54);
        Inventory inv = Bukkit.createInventory(null, size, TITLE);

        int slotSaldo    = config.getInt("extrato-menu.saldo-slot",    10);
        int slotEnviado  = config.getInt("extrato-menu.enviado-slot",  12);
        int slotRecebido = config.getInt("extrato-menu.recebido-slot", 14);
        int slotReward   = config.getInt("extrato-menu.reward-slot",   16);
        int backSlot     = config.getInt("extrato-menu.back-slot",     39);
        int toggleSlot   = config.getInt("extrato-menu.toggle-slot",   41);

        inv.setItem(slotSaldo,    buildSaldo(config, player, saldoAtual));
        inv.setItem(slotEnviado,  buildStatItem(config, "extrato-menu.enviado",
                CoinsFormatter.format(totalEnviado),  String.valueOf(totalEnviado)));
        inv.setItem(slotRecebido, buildStatItem(config, "extrato-menu.recebido",
                CoinsFormatter.format(totalRecebido), String.valueOf(totalRecebido)));
        inv.setItem(slotReward,   buildStatItem(config, "extrato-menu.reward",
                CoinsFormatter.format(totalReward),   String.valueOf(totalReward)));
        inv.setItem(backSlot,     buildFromSection(config, "extrato-menu.back-button", null));
        inv.setItem(toggleSlot,   buildToggleItem(config, false));

        SoundUtil.play(player, "menu-open");
        player.openInventory(inv);
    }

    private static ItemStack buildSaldo(FileConfiguration config, Player player, long saldo) {
        String saldoFormatado = CoinsFormatter.format(saldo);
        String saldoBruto     = String.valueOf(saldo);

        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList("extrato-menu.saldo.lore")) {
            String p = TextUtil.color(line
                    .replace("{coins}", saldoFormatado)
                    .replace("{coins_raw}", saldoBruto));
            for (String part : p.split("\n", -1)) lore.add(part);
        }

        String name = color(config.getString("extrato-menu.saldo.name", "&a&lSEU SALDO ATUAL")
                .replace("{coins}", saldoFormatado));

        return SkullUtil.buildPlayerHead(player, name, lore);
    }

    private static ItemStack buildStatItem(FileConfiguration config, String section,
                                            String formatted, String raw) {
        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList(section + ".lore")) {
            String p = TextUtil.color(line
                    .replace("{coins}", formatted)
                    .replace("{coins_raw}", raw));
            for (String part : p.split("\n", -1)) lore.add(part);
        }

        String name = color(config.getString(section + ".name", "&7Estatística")
                .replace("{coins}", formatted));

        return buildFromSection(config, section, lore, name);
    }

    public static ItemStack buildToggleItem(FileConfiguration config, boolean forHistorico) {
        String texture = config.getString("extrato-menu.toggle-button.skull-texture",
                "http://textures.minecraft.net/texture/a92e31ffb59c90ab08fc9dc1fe26802035a3a47c42fee63423bcdb4262ecb9b6");
        String name    = color(config.getString("extrato-menu.toggle-button.name", "&eAlterar Opção"));

        String historicoLabel = color(config.getString("extrato-menu.toggle-button.historico-label", "&7Histórico"));
        String extratoLabel   = color(config.getString("extrato-menu.toggle-button.extrato-label",   "&7Extrato"));

        String linhaCima  = forHistorico ? "&a " + historicoLabel : "&8  " + historicoLabel;
        String linhaBaixo = forHistorico ? "&8  " + extratoLabel  : "&a " + extratoLabel;

        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList("extrato-menu.toggle-button.lore")) {
            String p = TextUtil.color(line
                    .replace("{historico}", color(linhaCima))
                    .replace("{extrato}",   color(linhaBaixo)));
            for (String part : p.split("\n", -1)) lore.add(part);
        }

        return SkullUtil.buildCustomSkull(texture, name, lore);
    }

    private static ItemStack buildFromSection(FileConfiguration config, String section, List<String> lore) {
        String matName = config.getString(section + ".material", "PAPER");
        String name    = color(config.getString(section + ".name", "&7Item"));
        String texture = config.getString(section + ".skull-texture", "");
        List<String> finalLore = lore != null ? lore : TextUtil.colorLore(config.getStringList(section + ".lore"));
        if (matName.equalsIgnoreCase("PLAYER_HEAD") && !texture.isEmpty()) {
            return SkullUtil.buildCustomSkull(texture, name, finalLore);
        }
        return SkullUtil.makeItem(parseMaterial(matName), name, finalLore);
    }

    private static ItemStack buildFromSection(FileConfiguration config, String section,
                                               List<String> lore, String nameOverride) {
        String matName = config.getString(section + ".material", "PAPER");
        String texture = config.getString(section + ".skull-texture", "");
        List<String> finalLore = lore != null ? lore : TextUtil.colorLore(config.getStringList(section + ".lore"));
        if (matName.equalsIgnoreCase("PLAYER_HEAD") && !texture.isEmpty()) {
            return SkullUtil.buildCustomSkull(texture, nameOverride, finalLore);
        }
        return SkullUtil.makeItem(parseMaterial(matName), nameOverride, finalLore);
    }

    private static Material parseMaterial(String name) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.PAPER; }
    }

    private static String color(String text) { return TextUtil.color(text); }
}
