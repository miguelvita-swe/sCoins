package com.skyy.coins.util;

import com.skyy.coins.manager.CoinsManager;
import com.skyy.coins.manager.RankManager;
import com.skyy.coins.storage.FileStorage;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Placeholders disponíveis para uso com PlaceholderAPI:
 *
 * %scoins_coins%          → saldo formatado do jogador  (ex: 1.5M)
 * %scoins_coins_raw%      → saldo bruto do jogador      (ex: 1500000)
 * %scoins_rank%           → posição no ranking          (ex: 1) ou "-" se não rankear
 * %scoins_medal%          → medalha do top 3            (🥇 / 🥈 / 🥉 / "")
 * %scoins_chat_prefix%    → prefixo de chat do rank     (igual à medalha + espaço)
 * %scoins_top_name_[1-10] → nome do jogador na posição X do ranking
 * %scoins_top_coins_[1-10]→ saldo formatado do jogador na posição X
 */
public class SCoinsExpansion extends PlaceholderExpansion {

    private final CoinsManager coinsManager;
    private final RankManager rankManager;
    private final FileStorage fileStorage;

    public SCoinsExpansion(CoinsManager coinsManager, RankManager rankManager, FileStorage fileStorage) {
        this.coinsManager = coinsManager;
        this.rankManager  = rankManager;
        this.fileStorage  = fileStorage;
    }

    @Override
    public @NotNull String getIdentifier() { return "scoins"; }

    @Override
    public @NotNull String getAuthor() { return "skyy"; }

    @Override
    public @NotNull String getVersion() { return "1.0"; }

    @Override
    public boolean persist() { return true; } // mantém ativo sem recarregar

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {

        // ── Placeholders do próprio jogador ─────────────────────────────
        if (player != null) {
            switch (params) {
                case "coins":
                    return CoinsFormatter.format(coinsManager.getCoins(player.getUniqueId()));

                case "coins_raw":
                    return String.valueOf(coinsManager.getCoins(player.getUniqueId()));

                case "rank": {
                    int pos = rankManager.getRankPosition(player.getUniqueId());
                    return pos == -1 ? "-" : String.valueOf(pos);
                }

                case "medal":
                    return rankManager.getMedal(player.getName());

                case "chat_prefix":
                    return rankManager.getChatPrefix(player.getName());
            }
        }

        // ── Placeholders do ranking global ──────────────────────────────
        if (params.startsWith("top_name_") || params.startsWith("top_coins_")) {
            try {
                boolean isName  = params.startsWith("top_name_");
                int position    = Integer.parseInt(params.substring(isName ? 9 : 10)) - 1;
                // Usa cache em memória — nunca dispara query DB na main thread
                var top         = fileStorage.getTopPlayersFromCacheOnly(10);
                if (position < 0 || position >= top.size()) return isName ? "-" : "0";
                String[] entry  = top.get(position);
                return isName ? entry[0] : CoinsFormatter.format(Long.parseLong(entry[1]));
            } catch (NumberFormatException ignored) {}
        }

        return null; // placeholder não reconhecido
    }
}
