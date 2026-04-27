package com.skyy.coins.menu;

import com.skyy.coins.manager.CoinsManager;
import com.skyy.coins.manager.TransactionManager;
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

/**
 * Menu /coins extrato — mostra um resumo econômico do jogador:
 *   Slot 10 — Saldo atual
 *   Slot 12 — Total enviado
 *   Slot 14 — Total recebido
 *   Slot 16 — Total de rewards
 *   Slot 39 — Voltar ao menu principal
 *   Slot 41 — Alternar para o histórico
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

        // ── Calcula estatísticas a partir do histórico em memória ──────────
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

        // ── Monta o inventário ─────────────────────────────────────────────
        int size = config.getInt("extrato-menu.size", 54);
        Inventory inv = Bukkit.createInventory(null, size, TITLE);

        // Slot 10 — Saldo atual
        int slotSaldo = config.getInt("extrato-menu.saldo-slot", 10);
        inv.setItem(slotSaldo, buildSaldo(config, player, saldoAtual));

        // Slot 12 — Total enviado
        int slotEnviado = config.getInt("extrato-menu.enviado-slot", 12);
        inv.setItem(slotEnviado, buildStatItem(config, "extrato-menu.enviado",
                CoinsFormatter.format(totalEnviado), String.valueOf(totalEnviado)));

        // Slot 14 — Total recebido
        int slotRecebido = config.getInt("extrato-menu.recebido-slot", 14);
        inv.setItem(slotRecebido, buildStatItem(config, "extrato-menu.recebido",
                CoinsFormatter.format(totalRecebido), String.valueOf(totalRecebido)));

        // Slot 16 — Total rewards
        int slotReward = config.getInt("extrato-menu.reward-slot", 16);
        inv.setItem(slotReward, buildStatItem(config, "extrato-menu.reward",
                CoinsFormatter.format(totalReward), String.valueOf(totalReward)));

        // Slot 39 — Voltar
        int backSlot = config.getInt("extrato-menu.back-slot", 39);
        inv.setItem(backSlot, buildFromSection(config, "extrato-menu.back-button", null));

        // Slot 41 — Alterar para histórico
        int toggleSlot = config.getInt("extrato-menu.toggle-slot", 41);
        inv.setItem(toggleSlot, buildToggleItem(config, false)); // false = estamos no extrato, opção leva ao histórico

        SoundUtil.play(player, "menu-open");
        player.openInventory(inv);
    }

    // ─── Construtores de itens ────────────────────────────────────────────

    /** Cabeça do jogador com saldo atual. */
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

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;
        meta.setOwningPlayer(player);
        meta.setDisplayName(name);
        if (!lore.isEmpty()) meta.setLore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    /** Item genérico de estatística (enviado / recebido / reward). */
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

    /**
     * Constrói o item "Alterar Opção" mostrando o estado atual e para onde vai.
     * @param forHistorico — true = botão leva ao histórico (estamos no extrato)
     *                       false = botão leva ao extrato (estamos no histórico) — não usado aqui, mas reutilizável
     */
    public static ItemStack buildToggleItem(FileConfiguration config, boolean forHistorico) {
        String texture  = config.getString("extrato-menu.toggle-button.skull-texture",
                "http://textures.minecraft.net/texture/a92e31ffb59c90ab08fc9dc1fe26802035a3a47c42fee63423bcdb4262ecb9b6");
        String name     = color(config.getString("extrato-menu.toggle-button.name", "&eAlterar Opção"));

        String historicoLabel = color(config.getString("extrato-menu.toggle-button.historico-label", "&7Histórico"));
        String extratoLabel   = color(config.getString("extrato-menu.toggle-button.extrato-label",   "&7Extrato"));

        // Linha ativa é a que o botão vai ABRIR; a atual aparece com cor diferente
        String linhaCima  = forHistorico ? "&a " + historicoLabel : "&8  " + historicoLabel;
        String linhaBaixo = forHistorico ? "&8  " + extratoLabel   : "&a " + extratoLabel;

        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList("extrato-menu.toggle-button.lore")) {
            String p = TextUtil.color(line
                    .replace("{historico}", color(linhaCima))
                    .replace("{extrato}",   color(linhaBaixo)));
            for (String part : p.split("\n", -1)) lore.add(part);
        }

        return buildSkull(texture, name, lore);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private static ItemStack buildFromSection(FileConfiguration config, String section, List<String> lore) {
        String matName  = config.getString(section + ".material", "PAPER");
        String name     = color(config.getString(section + ".name", "&7Item"));
        String texture  = config.getString(section + ".skull-texture", "");
        List<String> finalLore = lore != null ? lore : TextUtil.colorLore(config.getStringList(section + ".lore"));
        if (matName.equalsIgnoreCase("PLAYER_HEAD") && !texture.isEmpty()) {
            return buildSkull(texture, name, finalLore);
        }
        return makeItem(parseMaterial(matName), name, finalLore);
    }

    private static ItemStack buildFromSection(FileConfiguration config, String section,
                                               List<String> lore, String nameOverride) {
        String matName  = config.getString(section + ".material", "PAPER");
        String texture  = config.getString(section + ".skull-texture", "");
        List<String> finalLore = lore != null ? lore : TextUtil.colorLore(config.getStringList(section + ".lore"));
        if (matName.equalsIgnoreCase("PLAYER_HEAD") && !texture.isEmpty()) {
            return buildSkull(texture, nameOverride, finalLore);
        }
        return makeItem(parseMaterial(matName), nameOverride, finalLore);
    }

    private static ItemStack buildSkull(String urlOrBase64, String name, List<String> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;
        try {
            String url;
            if (urlOrBase64.startsWith("http://") || urlOrBase64.startsWith("https://")) {
                url = urlOrBase64;
            } else {
                String decoded = new String(Base64.getDecoder().decode(urlOrBase64), StandardCharsets.UTF_8);
                url = decoded.split("\"url\":\"")[1].split("\"")[0];
            }
            PlayerProfile profile   = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(url));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (MalformedURLException | ArrayIndexOutOfBoundsException | IllegalArgumentException ignored) {}
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
        catch (IllegalArgumentException e) { return Material.PAPER; }
    }

    private static String color(String text) { return TextUtil.color(text); }
}
