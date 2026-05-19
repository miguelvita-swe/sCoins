package br.com.skyy.coins.util;

import br.com.skyy.core.SCorePlugin;
import br.com.skyy.core.providers.skull.SkullProvider;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/**
 * Utilitário de cabeças (Skull) compatível com 1.8–1.21.
 *
 * Delega para o {@link SkullProvider} do sCore, que seleciona
 * automaticamente o método correto:
 * <ul>
 *   <li>1.18+  → {@code Bukkit.createPlayerProfile()} + {@code PlayerTextures} (API limpa)</li>
 *   <li>1.8–1.17 → Reflection via {@code com.mojang.authlib.GameProfile}</li>
 * </ul>
 *
 * O sCore usa UUID determinístico (baseado na URL), garantindo que o
 * cliente faça cache da textura corretamente e ela apareça sempre.
 */
public final class SkullUtil {

    private SkullUtil() {}

    // ── Material correto por versão ──────────────────────────────────────

    @SuppressWarnings("deprecation")
    public static ItemStack playerHead() {
        if (VersionUtil.getMinor() >= 13) {
            return new ItemStack(Material.valueOf("PLAYER_HEAD"));
        }
        return new ItemStack(Material.valueOf("SKULL_ITEM"), 1, (short) 3);
    }

    @SuppressWarnings("deprecation")
    public static ItemStack skeletonSkull() {
        if (VersionUtil.getMinor() >= 13) {
            return new ItemStack(Material.valueOf("SKELETON_SKULL"));
        }
        return new ItemStack(Material.valueOf("SKULL_ITEM"), 1, (short) 0);
    }

    // ── Construção de cabeças ────────────────────────────────────────────

    /**
     * Cabeça com textura customizada (URL do Mojang ou base64).
     * Funciona em 1.8–1.21 via sCore SkullProvider.
     */
    public static ItemStack buildCustomSkull(String urlOrBase64, String name, List<String> lore) {
        // Resolve a URL real caso seja base64
        String url = resolveUrl(urlOrBase64);

        // Delega para o sCore — ele já trata todas as versões corretamente
        SkullProvider provider = getProvider();
        ItemStack skull = provider.getSkull(url);

        if (skull.getItemMeta() instanceof SkullMeta) {
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            applyNameLore(meta, name, lore);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    /**
     * Cabeça do jogador online.
     */
    public static ItemStack buildPlayerHead(Player player, String name, List<String> lore) {
        ItemStack skull = playerHead();
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;
        meta.setOwningPlayer(player);
        applyNameLore(meta, name, lore);
        skull.setItemMeta(meta);
        return skull;
    }

    /**
     * Cabeça do jogador offline.
     */
    public static ItemStack buildOfflineHead(OfflinePlayer player, String name, List<String> lore) {
        ItemStack skull = playerHead();
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;
        meta.setOwningPlayer(player);
        applyNameLore(meta, name, lore);
        skull.setItemMeta(meta);
        return skull;
    }

    /**
     * Cabeça por nome de jogador (busca skin automaticamente).
     */
    public static ItemStack buildNamedHead(String playerName, String name, List<String> lore) {
        SkullProvider provider = getProvider();
        ItemStack skull = provider.getSkullByName(playerName);

        if (skull.getItemMeta() instanceof SkullMeta) {
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            applyNameLore(meta, name, lore);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    /**
     * Item genérico (não-cabeça).
     */
    public static ItemStack makeItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;
        applyNameLore(meta, name, lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Helpers privados ─────────────────────────────────────────────────

    /**
     * Obtém o SkullProvider do sCore.
     * Fallback seguro caso sCore ainda não esteja inicializado.
     */
    private static SkullProvider getProvider() {
        try {
            SCorePlugin core = SCorePlugin.getInstance();
            if (core != null) return core.getSkullProvider();
        } catch (Throwable ignored) {}
        // Fallback: usa o Legacy direto (compatível com todas as versões)
        return new br.com.skyy.core.providers.skull.SkullProviderLegacy();
    }

    /**
     * Resolve a URL real caso a entrada seja um base64 do Mojang.
     */
    private static String resolveUrl(String urlOrBase64) {
        if (urlOrBase64 == null || urlOrBase64.isEmpty()) return urlOrBase64;
        if (urlOrBase64.startsWith("http://") || urlOrBase64.startsWith("https://")) {
            return urlOrBase64;
        }
        try {
            String decoded = new String(java.util.Base64.getDecoder().decode(urlOrBase64));
            return decoded.split("\"url\":\"")[1].split("\"")[0];
        } catch (Throwable e) {
            return urlOrBase64;
        }
    }

    private static void applyNameLore(ItemMeta meta, String name, List<String> lore) {
        if (name != null) meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) meta.setLore(lore);
    }
}
