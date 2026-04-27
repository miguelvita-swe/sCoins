package com.skyy.coins.util;

import com.skyy.coins.manager.CoinsManager;
import com.skyy.coins.manager.RankManager;
import com.skyy.coins.storage.FileStorage;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Placeholders disponíveis para uso com PlaceholderAPI:
 *
 * %scoins_money%              → saldo formatado do jogador        (ex: 1.5M)
 * %scoins_money_raw%          → saldo bruto do jogador            (ex: 1500000)
 * %scoins_magnata%            → tag do magnata caso o jogador seja o magnata (ex: [MAGNATA])
 * %scoins_top_pos%            → posição do próprio jogador no TOP (ex: 3) ou "-"
 * %scoins_top_player_[index]% → nome do jogador na posição X      (ex: Steve)
 * %scoins_top_value_[index]%  → saldo formatado do jogador na posição X (ex: 2.5M)
 */
public class SCoinsExpansion extends PlaceholderExpansion {

    private final CoinsManager coinsManager;
    private final RankManager  rankManager;
    private final FileStorage  fileStorage;
    private final FileConfiguration config;

    public SCoinsExpansion(CoinsManager coinsManager, RankManager rankManager,
                           FileStorage fileStorage, FileConfiguration config) {
        this.coinsManager = coinsManager;
        this.rankManager  = rankManager;
        this.fileStorage  = fileStorage;
        this.config       = config;
    }

    @Override public @NotNull String getIdentifier() { return "scoins"; }
    @Override public @NotNull String getAuthor()     { return "skyy"; }
    @Override public @NotNull String getVersion()    { return "1.0"; }
    @Override public boolean persist()               { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {

        // ── %yeconomy_money% e %yeconomy_money_raw% ─────────────────────
        if (player != null && params.equals("money")) {
            return CoinsFormatter.format(coinsManager.getCoins(player.getUniqueId()));
        }
        if (player != null && params.equals("money_raw")) {
            return String.valueOf(coinsManager.getCoins(player.getUniqueId()));
        }

        // ── %yeconomy_magnata% ───────────────────────────────────────────
        // Retorna a tag configurável se o jogador for o magnata, vazio caso contrário
        if (params.equals("magnata")) {
            if (player == null) return "";
            String[] magnata = fileStorage.getMagnata();
            if (magnata != null && magnata[0].equals(player.getName())) {
                return TextUtil.color(config.getString("magnata-tag", "&6&l[MAGNATA]"));
            }
            return "";
        }

        // ── %yeconomy_top_pos% ───────────────────────────────────────────
        if (player != null && params.equals("top_pos")) {
            int pos = rankManager.getRankPosition(player.getUniqueId());
            return pos == -1 ? "-" : String.valueOf(pos);
        }

        // ── %yeconomy_top_player_[index]% e %yeconomy_top_value_[index]% ─
        if (params.startsWith("top_player_") || params.startsWith("top_value_")) {
            try {
                boolean isPlayer = params.startsWith("top_player_");
                int index = Integer.parseInt(params.substring(isPlayer ? 11 : 10)) - 1;
                var top = fileStorage.getTopPlayersFromCacheOnly(10);
                if (index < 0 || index >= top.size()) return isPlayer ? "-" : "0";
                String[] entry = top.get(index);
                return isPlayer ? entry[0] : CoinsFormatter.format(Long.parseLong(entry[1]));
            } catch (NumberFormatException ignored) {
                // índice inválido — retorna vazio
            }
        }

        return null;
    }
}
