package br.com.skyy.coins.manager;

import br.com.skyy.coins.model.TransactionType;
import br.com.skyy.coins.storage.FileStorage;
import br.com.skyy.coins.util.CoinsFormatter;
import br.com.skyy.coins.util.SoundUtil;
import br.com.skyy.coins.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Gerencia o sistema de cheques do sCoins.
 *
 * Um cheque é um ItemStack (PAPER) com NBT customizado na lore que
 * armazena o valor em coins. Ao clicar com botão direito, o jogador
 * resgata os coins do cheque.
 *
 * Tipos de cheque:
 *   main-check  — emitido pelo /coins cheque <valor>
 *   media-check — emitido via API (outros plugins)
 *   death-check — dropado ao morrer (se checks.death-check.enabled: true)
 */
public class CheckManager {

    // Identificador único na lore para reconhecer um cheque — nunca muda
    // Isso evita que qualquer papel comum seja confundido com um cheque.
    static final String CHECK_SIGNATURE = "§0§1§2§3§scoins_check:";

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy");
    private static final SimpleDateFormat HOUR_FMT = new SimpleDateFormat("HH:mm:ss");

    private final CoinsManager coinsManager;
    private final TransactionManager transactionManager;
    private final FileStorage fileStorage;
    private final FileConfiguration config;

    public CheckManager(CoinsManager coinsManager, TransactionManager transactionManager,
                        FileStorage fileStorage, FileConfiguration config) {
        this.coinsManager       = coinsManager;
        this.transactionManager = transactionManager;
        this.fileStorage        = fileStorage;
        this.config             = config;
    }

    // ─── Criar cheque ────────────────────────────────────────────────────

    /**
     * Cria e entrega um cheque principal ao jogador, debitando os coins.
     * Retorna false se o jogador não tiver saldo suficiente.
     */
    public boolean issueMainCheck(Player player, long amount) {
        if (!coinsManager.removeCoins(player.getUniqueId(), amount)) return false;
        transactionManager.record(player.getUniqueId(), TransactionType.ADMIN_REMOVE, amount, "Cheque emitido");
        fileStorage.saveAsync(player.getUniqueId());

        String now   = DATE_FMT.format(new Date());
        String hour  = HOUR_FMT.format(new Date());
        ItemStack item = buildCheck("checks.main-check", amount, player.getName(), now, hour);
        giveOrDrop(player, item);
        SoundUtil.play(player, "cheque-create");
        return true;
    }

    /**
     * Cria um cheque de mídia sem debitar coins — entregue via API.
     */
    public ItemStack buildMediaCheck(long amount) {
        return buildCheck("checks.media-check", amount, null, null, null);
    }

    /**
     * Cria um cheque de morte e o entrega ao mundo (drop).
     * Debita coins do jogador morto conforme configuração.
     * Retorna a quantidade de coins perdida (0 se desativado ou sem saldo).
     */
    public long issueDeathCheck(Player deceased) {
        if (!config.getBoolean("checks.death-check.enabled", false)) return 0L;

        long balance = coinsManager.getCoins(deceased.getUniqueId());
        if (balance <= 0) return 0L;

        double percentage = config.getDouble("checks.death-check.percentage", 10.0);
        double maximum    = config.getDouble("checks.death-check.maximum", 20000.0);

        long loss = (long) (balance * (percentage / 100.0));
        loss = Math.min(loss, (long) maximum);
        loss = Math.max(loss, 1L); // mínimo 1 coin se o jogador tiver saldo

        if (!coinsManager.removeCoins(deceased.getUniqueId(), loss)) return 0L;
        transactionManager.record(deceased.getUniqueId(), TransactionType.ADMIN_REMOVE, loss, "Morte");
        fileStorage.saveAsync(deceased.getUniqueId());

        String now  = DATE_FMT.format(new Date());
        String hour = HOUR_FMT.format(new Date());
        ItemStack item = buildCheck("checks.death-check.item", loss, deceased.getName(), now, hour);
        deceased.getWorld().dropItemNaturally(deceased.getLocation(), item);
        return loss;
    }

    // ─── Resgatar cheque ─────────────────────────────────────────────────

    /**
     * Tenta resgatar um cheque.
     * Retorna o valor resgatado, 0 se o item não for um cheque,
     * ou -1 se o jogador atingiria o limite máximo.
     */
    public long redeemCheck(Player player, ItemStack item) {
        long value = extractValue(item);
        if (value <= 0) return 0L;

        if (!coinsManager.addCoins(player.getUniqueId(), value)) return -1L;
        transactionManager.record(player.getUniqueId(), TransactionType.RECEIVED, value, "Cheque resgatado");
        fileStorage.saveAsync(player.getUniqueId());

        // Remove 1 do stack
        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else item.setType(Material.AIR);

        SoundUtil.play(player, "cheque-redeem");
        return value;
    }

    // ─── Utilitários ─────────────────────────────────────────────────────

    /**
     * Retorna true se o item é um cheque válido do sCoins.
     */
    public boolean isCheck(ItemStack item) {
        return extractValue(item) > 0;
    }

    /** Extrai o valor de coins do cheque a partir da lore assinada. */
    long extractValue(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0L;
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) return 0L;
        for (String line : item.getItemMeta().getLore()) {
            if (line != null && line.startsWith(CHECK_SIGNATURE)) {
                try { return Long.parseLong(line.substring(CHECK_SIGNATURE.length())); }
                catch (NumberFormatException ignored) {}
            }
        }
        return 0L;
    }

    /** Constrói o ItemStack do cheque com a lore configurável + assinatura oculta. */
    private ItemStack buildCheck(String path, long amount, String playerName,
                                 String data, String hora) {
        String matStr = config.getString(path + ".material", "PAPER").toUpperCase();
        Material mat;
        try { mat = Material.valueOf(matStr); } catch (IllegalArgumentException e) { mat = Material.PAPER; }

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        // Nome
        String rawName = config.getString(path + ".name", "&aCheque de Coins");
        meta.setDisplayName(applyPlaceholders(rawName, amount, playerName, data, hora));

        // Lore visível
        List<String> rawLore = config.getStringList(path + ".lore");
        List<String> lore    = new ArrayList<>();
        for (String line : rawLore) {
            lore.add(applyPlaceholders(line, amount, playerName, data, hora));
        }

        // Assinatura invisível: linha colorida com código §0§1§2§3 nunca exibida normalmente
        lore.add(CHECK_SIGNATURE + amount);
        meta.setLore(lore);

        // Glow (brilho de encantamento) — compatível com 1.8–1.21
        if (config.getBoolean(path + ".glow", true)) {
            try {
                // 1.21+: UNBREAKING  |  1.8–1.20: DURABILITY
                Enchantment ench;
                try {
                    ench = (Enchantment) Enchantment.class.getField("UNBREAKING").get(null);
                } catch (NoSuchFieldException e) {
                    ench = (Enchantment) Enchantment.class.getField("DURABILITY").get(null);
                }
                meta.addEnchant(ench, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } catch (Exception ignored) {}
        }

        item.setItemMeta(meta);
        return item;
    }

    private String applyPlaceholders(String text, long amount, String player, String data, String hora) {
        String coinsFormatted = CoinsFormatter.format(amount);
        String result = text
                .replace("{coins}", coinsFormatted)
                .replace("{money}", coinsFormatted); // compatibilidade com config da referência
        if (player != null) result = result.replace("{player}", player);
        if (data   != null) result = result.replace("{data}",   data);
        if (hora   != null) result = result.replace("{hora}",   hora);
        return TextUtil.color(result);
    }

    /** Entrega o item ao jogador; se inventário cheio, dropa no chão. */
    private void giveOrDrop(Player player, ItemStack item) {
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        } else {
            player.getInventory().addItem(item);
        }
    }
}
