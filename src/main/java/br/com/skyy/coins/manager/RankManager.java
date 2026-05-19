package br.com.skyy.coins.manager;

import br.com.skyy.coins.npc.NpcManager;
import br.com.skyy.coins.storage.FileStorage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rastreia os top 3 jogadores e aplica prefixo no Tab e no Chat.
 *
 * USA SCOREBOARD DEDICADO POR JOGADOR — nunca toca o MainScoreboard.
 * Isso evita conflito com LuckPerms, EssentialsX e qualquer plugin
 * que use teams no scoreboard principal.
 *
 * Estratégia: cada jogador recebe seu próprio Scoreboard com 3 teams
 * (scoins_1/2/3). Ao entrar um jogador, todos os outros atualizam
 * seus scoreboards para incluí-lo.
 */
public class RankManager {

    private static final String TEAM_1 = "scoins_rank_1";
    private static final String TEAM_2 = "scoins_rank_2";
    private static final String TEAM_3 = "scoins_rank_3";

    private final FileConfiguration config;
    private final FileStorage fileStorage;
    private NpcManager npcManager;

    // Cache: nomes dos top 3 atuais
    private final String[] topNames = new String[3];

    // Scoreboard dedicado por jogador — não usa getMainScoreboard()
    private final ConcurrentHashMap<UUID, Scoreboard> playerBoards = new ConcurrentHashMap<>();

    public RankManager(CoinsManager coinsManager, FileStorage fileStorage, FileConfiguration config) {
        this.fileStorage  = fileStorage;
        this.config       = config;
    }

    public void setNpcManager(NpcManager npcManager) { this.npcManager = npcManager; }

    // ─── API pública ────────────────────────────────────────────────────

    public String getMedal(String playerName) {
        for (int i = 0; i < topNames.length; i++) {
            if (playerName.equals(topNames[i])) return medalOf(i + 1);
        }
        return "";
    }

    public int getRankPosition(UUID uuid) {
        // Tenta pelo jogador online primeiro (mais rápido)
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return positionOf(online.getName());
        // Fallback: busca pelo nome no storage offline
        String name = fileStorage.getNameOffline(uuid);
        return name != null ? positionOf(name) : -1;
    }

    private long lastRefreshMs = 0L;
    private static final long REFRESH_THROTTLE_MS = 2_000L;

    /**
     * Recalcula o top 3 e atualiza prefixos no Tab de todos os jogadores online.
     * Throttled: no máximo 1x a cada 2s.
     * Usa APENAS cache em memória — sem queries DB na main thread.
     */
    public void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs < REFRESH_THROTTLE_MS) return;
        lastRefreshMs = now;

        List<String[]> top = fileStorage.getTopPlayersFromCacheOnly(3);

        for (int i = 0; i < 3; i++) {
            topNames[i] = i < top.size() ? top.get(i)[0] : null;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            applyTab(p);
        }

        if (npcManager != null) npcManager.onRankChange();
    }

    /**
     * Aplica o prefixo de tab para um jogador específico usando scoreboard dedicado.
     * Nunca altera o MainScoreboard — compatível com qualquer plugin de prefixo.
     */
    public void applyTab(Player player) {
        // Obtém ou cria o scoreboard dedicado deste jogador
        Scoreboard board = playerBoards.computeIfAbsent(player.getUniqueId(),
                k -> Bukkit.getScoreboardManager().getNewScoreboard());

        // Garante que o jogador está usando o scoreboard correto
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }

        // Sincroniza os 3 teams: remove todos os jogadores online dos teams sCoins,
        // depois re-adiciona apenas quem está no top 3 com o prefixo correto
        ensureTeams(board);
        for (String teamName : new String[]{TEAM_1, TEAM_2, TEAM_3}) {
            Team t = board.getTeam(teamName);
            if (t != null) {
                // Remove APENAS jogadores que não são mais top
                for (String entry : new java.util.HashSet<>(t.getEntries())) {
                    int pos = positionOf(entry);
                    if (pos == -1) t.removeEntry(entry); // saiu do top
                }
            }
        }

        // Pré-calcula os 3 prefixos fora do loop — evita 300 chamadas config.getString()
        // por refresh com 300 jogadores online (cada jogador iteraria 3 posições)
        String prefix1 = color(config.getString("rank.tab-prefix-1", medalOf(1))) + " ";
        String prefix2 = color(config.getString("rank.tab-prefix-2", medalOf(2))) + " ";
        String prefix3 = color(config.getString("rank.tab-prefix-3", medalOf(3))) + " ";

        // Adiciona/atualiza todos os jogadores online no scoreboard deste jogador
        for (Player online : Bukkit.getOnlinePlayers()) {
            int pos = positionOf(online.getName());
            if (pos == -1) {
                // Remove de todos os teams sCoins se não estiver no top
                removeFromAllTeams(board, online.getName());
                continue;
            }
            String teamName = teamNameOf(pos);
            if (teamName == null) continue;

            Team team = board.getTeam(teamName);
            if (team == null) team = board.registerNewTeam(teamName);

            String prefixStr = pos == 1 ? prefix1 : pos == 2 ? prefix2 : prefix3;
            team.setPrefix(prefixStr);

            if (!team.hasEntry(online.getName())) team.addEntry(online.getName());
        }
    }

    /** Remove o scoreboard dedicado quando o jogador sai (evita memory leak). */
    public void removePlayer(UUID uuid) {
        playerBoards.remove(uuid);
    }

    // ─── Chat prefix ────────────────────────────────────────────────────

    public String getChatPrefix(String playerName) {
        int pos = positionOf(playerName);
        if (pos == -1) return "";
        return color(config.getString("rank.chat-prefix-" + pos, medalOf(pos))) + " ";
    }

    /**
     * Retorna a posição no ranking (1-indexed) do jogador pelo nome (case-sensitive).
     * Retorna -1 se não estiver no top cached.
     */
    public int getRankPosition(String playerName) {
        return positionOf(playerName);
    }


    // ─── Helpers privados ───────────────────────────────────────────────

    private void ensureTeams(Scoreboard board) {
        for (String name : new String[]{TEAM_1, TEAM_2, TEAM_3}) {
            if (board.getTeam(name) == null) board.registerNewTeam(name);
        }
    }

    private void removeFromAllTeams(Scoreboard board, String entry) {
        for (String teamName : new String[]{TEAM_1, TEAM_2, TEAM_3}) {
            Team t = board.getTeam(teamName);
            if (t != null) t.removeEntry(entry);
        }
    }

    private int positionOf(String name) {
        for (int i = 0; i < topNames.length; i++) {
            if (name.equals(topNames[i])) return i + 1;
        }
        return -1;
    }

    private String teamNameOf(int pos) {
        if (pos == 1) return TEAM_1;
        if (pos == 2) return TEAM_2;
        if (pos == 3) return TEAM_3;
        return null;
    }

    private String medalOf(int position) {
        if (position == 1) return "&6&l[🥇]";
        if (position == 2) return "&7&l[🥈]";
        if (position == 3) return "&e&l[🥉]";
        return "";
    }

    private String color(String text) {
        if (text == null) return "";
        // Traduz & para § mantendo compatibilidade com configs existentes
        return text.replace("&", "§");
    }
}
