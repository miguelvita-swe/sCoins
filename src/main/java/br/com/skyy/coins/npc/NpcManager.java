package br.com.skyy.coins.npc;

import br.com.skyy.coins.storage.FileStorage;
import br.com.skyy.coins.util.CoinsFormatter;
import br.com.skyy.core.SCore;
import br.com.skyy.core.providers.hologram.HologramProvider;
import br.com.skyy.core.providers.npc.NPCManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * Gerencia os 3 NPCs do Top de Coins usando a abstração do sCore.
 *
 * <ul>
 *   <li>NPCs   → {@link NPCManager} (Citizens se disponível, ArmorStand como fallback)</li>
 *   <li>Holos  → {@link HologramProvider} (DecentHolograms / HD / ArmorStand)</li>
 * </ul>
 *
 * Compatível com Minecraft 1.8–1.21 sem nenhum import direto de Citizens.
 */
public class NpcManager {

    private static final int    TOP_COUNT   = 3;
    private static final String DATE_FORMAT = "dd/MM/yyyy HH:mm:ss";
    private static final String NPC_KEY     = "npcs.top-";

    private final JavaPlugin       plugin;
    private final FileStorage      fileStorage;
    private final Logger           log;
    private final NPCManager       npcManager;
    private final HologramProvider hologramProvider;

    private File              npcFile;
    private FileConfiguration npcData;
    private volatile boolean  npcDataDirty = false;

    public NpcManager(JavaPlugin plugin, FileStorage fileStorage) {
        this.plugin           = plugin;
        this.fileStorage      = fileStorage;
        this.log              = plugin.getLogger();
        this.npcManager       = SCore.getNPC();
        this.hologramProvider = SCore.getHologram();
        loadNpcData();
    }

    // ─── Inicialização ──────────────────────────────────────────────────

    /**
     * Chamado no onEnable — spawna os NPCs que já têm posição salva.
     * Deve ser chamado com 1 tick de delay para o Citizens terminar de carregar.
     */
    public void spawnAll() {
        for (int pos = 1; pos <= TOP_COUNT; pos++) {
            if (isActive(pos)) spawnOrUpdate(pos, getSavedLocation(pos));
        }
    }

    // ─── Comandos ───────────────────────────────────────────────────────

    /**
     * Define a posição do NPC de uma posição e cria/atualiza ele.
     * Chamado por: /coins npc set <1|2|3>
     * Retorna false se não houver jogador nessa posição do top.
     */
    public boolean setPosition(int position, Location location) {
        List<String[]> top = fileStorage.getTopPlayers(TOP_COUNT);
        if (top.size() < position) {
            return false; // ainda não há jogador nessa posição
        }
        savePosition(position, location);
        spawnOrUpdate(position, location);
        return true;
    }

    /**
     * Remove o NPC de uma posição.
     * Chamado por: /coins npc remove <1|2|3>
     */
    public void removePosition(int position) {
        removeNpcAndHolo(position);
        // Marca como inativo mas mantém o histórico
        npcData.set(NPC_KEY + position + ".active", false);
        npcData.set(NPC_KEY + position + ".npc-id", "");
        npcDataDirty = true;
        flushNpcDataAsync();
    }

    /**
     * Força atualização de skin e hologram de todos os NPCs.
     * Chamado por: /coins npc reload — remove tudo e respawna após 10s.
     */
    public void reloadAll() {
        // Remove todos imediatamente
        for (int i = 1; i <= TOP_COUNT; i++) removeNpcAndHolo(i);

        // Respawna após 10 segundos (200 ticks) para evitar erros do Citizens
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                for (int pos = 1; pos <= TOP_COUNT; pos++) {
                    if (isActive(pos)) spawnOrUpdate(pos, getSavedLocation(pos));
                }
                log.info("[sCoins NPC] NPCs respawnados após delay de 10 s.");
            }
        }, 200L);
    }

    /** Recarrega o npcs.yml do disco e reaplicar holograms/skins. */
    public void reloadConfig() {
        npcData = YamlConfiguration.loadConfiguration(npcFile);
        reloadAll();
        log.info("[sCoins NPC] npcs.yml recarregado.");
    }

    // ─── Atualização automática ─────────────────────────────────────────

    /**
     * Chamado pelo RankManager quando o ranking muda.
     * Atualiza skins e holograms sem remover/recriar os NPCs.
     */
    public void onRankChange() {
        List<String[]> top = fileStorage.getTopPlayersFromCacheOnly(TOP_COUNT);

        for (int i = 0; i < TOP_COUNT; i++) {
            int      position = i + 1;
            Location spawnLoc = getSavedLocation(position);
            if (spawnLoc == null) continue;

            if (i < top.size()) {
                String playerName = top.get(i)[0];
                long   coins      = Long.parseLong(top.get(i)[1]);
                npcManager.updateSkin(npcId(position), playerName);
                updateHologram(position, spawnLoc, playerName, coins);
                saveRuntimeData(position, playerName, coins);
            } else {
                updateHologramEmpty(position, spawnLoc);
                saveRuntimeData(position, null, 0L);
            }
        }
        flushNpcDataAsync();
    }

    // ─── Limpeza ────────────────────────────────────────────────────────

    /** Remove todos os NPCs e holograms ao desligar o servidor. */
    public void removeAll() {
        for (int pos = 1; pos <= TOP_COUNT; pos++) removeNpcAndHolo(pos);
        // Garante que qualquer dado pendente seja salvo de forma síncrona no shutdown
        if (npcDataDirty) saveNpcFile();
    }

    // ─── Spawn interno ───────────────────────────────────────────────────

    private void spawnOrUpdate(int position, Location location) {
        if (location == null) return;

        int            index      = position - 1;
        List<String[]> top        = fileStorage.getTopPlayersFromCacheOnly(TOP_COUNT);
        String         playerName = index < top.size() ? top.get(index)[0] : null;
        long           coins      = index < top.size() ? Long.parseLong(top.get(index)[1]) : 0L;

        removeNpcAndHolo(position);

        // textureUrl via Paper API (reflection), null = sCore usará o nome
        String textureUrl = playerName != null ? resolveTextureUrl(playerName) : null;
        npcManager.spawnNPC(npcId(position), location, playerName != null ? playerName : "", textureUrl);

        npcData.set(NPC_KEY + position + ".active", true);
        saveRuntimeData(position, playerName, coins);
        flushNpcDataAsync();

        if (playerName != null) updateHologram(position, location, playerName, coins);
        else                    updateHologramEmpty(position, location);

        log.info(String.format("[sCoins NPC] Top %d spawnado [%s]%s",
                position,
                npcManager.getProvider().getProviderName(),
                playerName != null ? " → " + playerName + " (" + CoinsFormatter.format(coins) + ")" : " (vazio)"));
    }

// ─── Holograms ──────────────────────────────────────────────────────

    private void updateHologram(int position, Location spawnLoc, String playerName, long coins) {
        double height    = npcData.getDouble("settings.hologram-height", 2.3);
        double npcHeight = npcManager.isCitizens() ? 1.95 : 1.8;
        Location loc     = spawnLoc.clone().add(0, height + npcHeight, 0);
        List<String> lines = buildHologramLines(position, playerName, coins);
        hologramProvider.removeHologram(holoId(position));
        hologramProvider.createHologram(holoId(position), loc, lines);
    }

    private void updateHologramEmpty(int position, Location spawnLoc) {
        double height    = npcData.getDouble("settings.hologram-height", 2.3);
        double npcHeight = npcManager.isCitizens() ? 1.95 : 1.8;
        Location loc     = spawnLoc.clone().add(0, height + npcHeight, 0);
        List<String> raw   = npcData.getStringList("hologram.empty.lines");
        List<String> lines = new ArrayList<>();
        for (String line : raw) {
            lines.add(line.replace("{position}", String.valueOf(position))
                    .replace("{pos}",       String.valueOf(position)));
        }
        hologramProvider.removeHologram(holoId(position));
        hologramProvider.createHologram(holoId(position), loc, lines);
    }

    private List<String> buildHologramLines(int position, String playerName, long coins) {
        List<String> raw = npcData.getStringList("hologram.top-" + position + ".lines");
        if (raw.isEmpty()) raw = npcData.getStringList("hologram.lines");
        List<String> result = new ArrayList<>();
        for (String line : raw) {
            result.add(line
                    .replace("{player}",   playerName != null ? playerName : "Nenhum")
                    .replace("{coins}",    CoinsFormatter.format(coins))
                    .replace("{position}", String.valueOf(position))
                    .replace("{pos}",      String.valueOf(position)));
        }
        return result;
    }

    private void removeNpcAndHolo(int position) {
        npcManager.removeNPC(npcId(position));
        hologramProvider.removeHologram(holoId(position));
    }

    // ─── Persistência ───────────────────────────────────────────────────

    private void loadNpcData() {
        npcFile = new File(plugin.getDataFolder(), "npcs.yml");
        // Copia o npcs.yml padrão do resources se não existir
        if (!npcFile.exists()) {
            plugin.saveResource("npcs.yml", false);
        }

        npcData = YamlConfiguration.loadConfiguration(npcFile);
    }

    /** Salva a posição física + metadados estáticos do NPC no npcs.yml. */
    private void savePosition(int position, Location loc) {
        String key = NPC_KEY + position;
        npcData.set(key + ".position.world", loc.getWorld().getName());
        npcData.set(key + ".position.x",     Math.round(loc.getX() * 100.0) / 100.0);
        npcData.set(key + ".position.y",     Math.round(loc.getY() * 100.0) / 100.0);
        npcData.set(key + ".position.z",     Math.round(loc.getZ() * 100.0) / 100.0);
        npcData.set(key + ".position.yaw",   Math.round(loc.getYaw() * 100.0) / 100.0);
        npcData.set(key + ".position.pitch", Math.round(loc.getPitch() * 100.0) / 100.0);
        npcData.set(key + ".active",         true);
        npcData.set(key + ".placed-at",      new SimpleDateFormat(DATE_FORMAT).format(new Date()));
        npcDataDirty = true;
        flushNpcDataAsync();
    }

    /** Salva os dados do jogador exibido + timestamp no npcs.yml (lazy — marca dirty). */
    private void saveRuntimeData(int position, String playerName, long coins) {
        String key = NPC_KEY + position;
        npcData.set(key + ".current-player.name",            playerName != null ? playerName : "Nenhum");
        npcData.set(key + ".current-player.coins",           coins);
        npcData.set(key + ".current-player.coins-formatted", CoinsFormatter.format(coins));
        npcData.set(key + ".last-updated", new SimpleDateFormat(DATE_FORMAT).format(new Date()));
        npcDataDirty = true; // não salva no disco agora — flush virá async
    }

    /** Persiste o npcs.yml de forma assíncrona. Chamado após operações em lote. */
    private void flushNpcDataAsync() {
        if (!npcDataDirty) return;
        npcDataDirty = false;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override public void run() { saveNpcFile(); }
        });
    }

    private void saveNpcFile() {
        try { npcData.save(npcFile); }
        catch (IOException e) { log.severe("[sCoins NPC] Erro ao salvar npcs.yml: " + e.getMessage()); }
    }

    private Location getSavedLocation(int position) {
        String key = NPC_KEY + position + ".position";
        if (!npcData.contains(key)) return null;
        String worldName = npcData.getString(key + ".world", "world");
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            log.warning("[sCoins NPC] Mundo '" + worldName + "' não encontrado para NPC top-" + position);
            return null;
        }
        return new Location(world,
                npcData.getDouble(key + ".x"),
                npcData.getDouble(key + ".y"),
                npcData.getDouble(key + ".z"),
                (float) npcData.getDouble(key + ".yaw"),
                (float) npcData.getDouble(key + ".pitch"));
    }

    private boolean isActive(int position) {
        return npcData.getBoolean(NPC_KEY + position + ".active", false)
                && getSavedLocation(position) != null;
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private String npcId(int position)  { return "scoins_top_"    + position; }
    private String holoId(int position) { return "scoins_npc_top" + position; }

    /**
     * Tenta resolver a URL de textura Mojang via API do Paper (reflection).
     * Retorna null em servidores Spigot/Bukkit puros — o sCore usará updateSkin(name) nesses casos.
     */
    private String resolveTextureUrl(String playerName) {
        try {
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
            Object profile  = op.getClass().getMethod("getPlayerProfile").invoke(op);
            Object textures = profile.getClass().getMethod("getTextures").invoke(profile);
            java.net.URL skin = (java.net.URL) textures.getClass().getMethod("getSkin").invoke(textures);
            return skin != null ? skin.toString() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
