package com.skyy.coins.npc;

import com.skyy.coins.storage.FileStorage;
import com.skyy.coins.util.CoinsFormatter;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * Gerencia os 3 NPCs do Top de Coins.
 *
 * O arquivo npcs.yml armazena TUDO relacionado aos NPCs:
 *  - Posição no mundo (world, x, y, z, yaw, pitch)
 *  - ID do NPC no Citizens2
 *  - Jogador atual exibido (nome + saldo)
 *  - Data da última atualização
 *  - Status (ativo/inativo)
 */
public class NpcManager {

    private static final int    TOP_COUNT   = 3;
    private static final String DATE_FORMAT = "dd/MM/yyyy HH:mm:ss";
    private static final String NPC_KEY     = "npcs.top-";

    private final JavaPlugin  plugin;
    private final FileStorage fileStorage;
    private final Logger      log;

    private final int[]                  npcIds    = {-1, -1, -1};
    private final List<List<TextDisplay>> holograms = new ArrayList<>();

    private File              npcFile;
    private FileConfiguration npcData;

    // Dirty flag — evita I/O a cada saveRuntimeData(); persiste em lote async
    private volatile boolean npcDataDirty = false;

    public NpcManager(JavaPlugin plugin, FileStorage fileStorage) {
        this.plugin      = plugin;
        this.fileStorage = fileStorage;
        this.log         = plugin.getLogger();

        for (int i = 0; i < TOP_COUNT; i++) holograms.add(new ArrayList<>());

        loadNpcData();
    }

    // ─── Inicialização ──────────────────────────────────────────────────

    /**
     * Chamado no onEnable — spawna os NPCs que já têm posição salva.
     * Deve ser chamado com 1 tick de delay para o Citizens terminar de carregar.
     */
    public void spawnAll() {
        if (!isCitizensAvailable()) return;
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
        removeNpc(position - 1);
        // Marca como inativo mas mantém o histórico
        npcData.set(NPC_KEY + position + ".active", false);
        npcData.set(NPC_KEY + position + ".citizens-id", -1);
        npcDataDirty = true;
        flushNpcDataAsync();
    }

    /**
     * Força atualização de skin e hologram de todos os NPCs.
     * Chamado por: /coins npc reload — remove tudo e respawna após 10s.
     */
    public void reloadAll() {
        // Remove todos imediatamente
        for (int i = 0; i < TOP_COUNT; i++) removeNpc(i);

        // Respawna após 10 segundos (200 ticks) para evitar erros do Citizens
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int pos = 1; pos <= TOP_COUNT; pos++) {
                if (isActive(pos)) {
                    spawnOrUpdate(pos, getSavedLocation(pos));
                }
            }
            log.info("[sCoins NPC] NPCs respawnados após delay de 10s.");
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
        if (!isCitizensAvailable()) return;

        // Usa APENAS cache em memória — nunca dispara query DB na main thread
        List<String[]> top = fileStorage.getTopPlayersFromCacheOnly(TOP_COUNT);

        for (int i = 0; i < TOP_COUNT; i++) {
            int position = i + 1;
            NPC npc = getNpc(i);
            if (npc == null) continue;

            if (i < top.size()) {
                String playerName = top.get(i)[0];
                long   coins      = Long.parseLong(top.get(i)[1]);
                updateSkin(npc, playerName);
                updateHologram(position, playerName, coins);
                saveRuntimeData(position, playerName, coins);
            } else {
                updateHologramEmpty(position, position);
                saveRuntimeData(position, null, 0L);
            }
        }
        // 3 saveRuntimeData marcaram dirty — 1 único flush async cobre tudo
        flushNpcDataAsync();
    }

    // ─── Limpeza ────────────────────────────────────────────────────────

    /** Remove todos os NPCs e holograms ao desligar o servidor. */
    public void removeAll() {
        for (int i = 0; i < TOP_COUNT; i++) removeNpc(i);
        // Garante que qualquer dado pendente seja salvo de forma síncrona no shutdown
        if (npcDataDirty) saveNpcFile();
    }

    // ─── Lógica interna ─────────────────────────────────────────────────

    private void spawnOrUpdate(int position, Location location) {
        if (!isCitizensAvailable() || location == null) return;

        int            index  = position - 1;
        // USA APENAS cache em memória — getTopPlayers() pode disparar query SQL na main thread.
        // getTopPlayersFromCacheOnly() é sempre seguro pois nunca faz I/O.
        List<String[]> top    = fileStorage.getTopPlayersFromCacheOnly(TOP_COUNT);
        String playerName     = index < top.size() ? top.get(index)[0] : null;
        long   coins          = index < top.size() ? Long.parseLong(top.get(index)[1]) : 0L;

        // Remove NPC antigo se existir
        removeNpc(index);

        // Cria NPC novo — nome vazio para não aparecer tag acima da cabeça
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        NPC npc = registry.createNPC(EntityType.PLAYER, "");
        npc.setProtected(true);
        npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false);

        // Faz o NPC olhar para jogadores próximos (corpo acompanha o jogador)
        LookClose lookClose = npc.getOrAddTrait(LookClose.class);
        double lookRange = npcData.getDouble("settings.look-range", 8.0);
        lookClose.setRange(lookRange);
        lookClose.setRealisticLooking(true);
        lookClose.toggle();


        // Define skin do jogador
        if (playerName != null) updateSkin(npc, playerName);

        npc.spawn(location);
        npcIds[index] = npc.getId();

        // Persiste o Citizens ID gerado
        npcData.set(NPC_KEY + position + ".citizens-id", npc.getId());
        npcData.set(NPC_KEY + position + ".active", true);
        saveRuntimeData(position, playerName, coins);
        flushNpcDataAsync(); // único flush cobre citizens-id + runtime data

        // Cria hologram
        if (playerName != null) updateHologram(position, playerName, coins);
        else                    updateHologramEmpty(position, position);

        log.info(String.format("[sCoins NPC] Top %d spawnado%s", position,
                playerName != null ? " → " + playerName + " (" + CoinsFormatter.format(coins) + ")" : " (vazio)"));
    }

    /** Salva os dados do jogador exibido + timestamp no npcs.yml (lazy — marca dirty). */
    private void saveRuntimeData(int position, String playerName, long coins) {
        String key   = NPC_KEY + position;
        String stamp = new SimpleDateFormat(DATE_FORMAT).format(new Date());

        if (playerName != null) {
            npcData.set(key + ".current-player.name",            playerName);
            npcData.set(key + ".current-player.coins",           coins);
            npcData.set(key + ".current-player.coins-formatted", CoinsFormatter.format(coins));
        } else {
            npcData.set(key + ".current-player.name",            "Nenhum");
            npcData.set(key + ".current-player.coins",           0L);
            npcData.set(key + ".current-player.coins-formatted", "0");
        }
        npcData.set(key + ".last-updated", stamp);
        npcDataDirty = true; // não salva no disco agora — flush virá async
    }

    /** Persiste o npcs.yml de forma assíncrona. Chamado após operações em lote. */
    private void flushNpcDataAsync() {
        if (!npcDataDirty) return;
        npcDataDirty = false;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::saveNpcFile);
    }

    private void updateSkin(NPC npc, String playerName) {
        try {
            SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
            skin.setSkinName(playerName, false);
        } catch (Exception e) {
            log.warning("[sCoins NPC] Erro ao definir skin de " + playerName + ": " + e.getMessage());
        }
    }


    /** Cria ou atualiza o hologram (TextDisplay) acima do NPC. */
    private void updateHologram(int position, String playerName, long coins) {
        int index = position - 1;
        clearHolograms(index);

        NPC npc = getNpc(index);
        if (npc == null || !npc.isSpawned()) return;

        double height  = npcData.getDouble("settings.hologram-height", 2.3);
        double lineGap = npcData.getDouble("settings.hologram-line-gap", 0.28);

        Location     base  = npc.getEntity().getLocation().clone().add(0, height, 0);
        List<String> lines = buildHologramLines(position, playerName, coins);

        for (int i = 0; i < lines.size(); i++) {
            Location lineLoc = base.clone().add(0, (lines.size() - 1 - i) * lineGap, 0);
            holograms.get(index).add(spawnTextDisplay(lineLoc, lines.get(i)));
        }
    }

    private void updateHologramEmpty(int position, int positionNumber) {
        int index = position - 1;
        clearHolograms(index);

        NPC npc = getNpc(index);
        if (npc == null || !npc.isSpawned()) return;

        double height  = npcData.getDouble("settings.hologram-height", 2.3);
        double lineGap = npcData.getDouble("settings.hologram-line-gap", 0.28);

        List<String> raw = npcData.getStringList("hologram.empty.lines");
        List<String> lines = new ArrayList<>();
        for (String line : raw) {
            lines.add(line.replace("{position}", String.valueOf(positionNumber)));
        }

        Location base = npc.getEntity().getLocation().clone().add(0, height, 0);
        for (int i = 0; i < lines.size(); i++) {
            Location lineLoc = base.clone().add(0, (lines.size() - 1 - i) * lineGap, 0);
            holograms.get(index).add(spawnTextDisplay(lineLoc, color(lines.get(i))));
        }
    }

    private TextDisplay spawnTextDisplay(Location loc, String text) {
        TextDisplay display = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        display.text(net.kyori.adventure.text.Component.text(color(text)));
        display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        display.setShadowed(true);
        display.setPersistent(false);
        float scale = (float) npcData.getDouble("settings.hologram-scale", 1.2);
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 0, 1)));
        return display;
    }

    private List<String> buildHologramLines(int position, String playerName, long coins) {
        List<String> raw    = npcData.getStringList("hologram.top-" + position + ".lines");
        List<String> result = new ArrayList<>();
        for (String line : raw) {
            result.add(line
                    .replace("{player}",   playerName)
                    .replace("{coins}",    CoinsFormatter.format(coins))
                    .replace("{position}", String.valueOf(position)));
        }
        return result;
    }

    private void removeNpc(int index) {
        clearHolograms(index);
        if (!isCitizensAvailable() || npcIds[index] == -1) return;

        NPC npc = CitizensAPI.getNPCRegistry().getById(npcIds[index]);
        if (npc != null) {
            if (npc.isSpawned()) npc.despawn();
            npc.destroy();
        }
        npcIds[index] = -1;
    }

    private void clearHolograms(int index) {
        for (TextDisplay d : holograms.get(index)) {
            if (d != null && !d.isDead()) d.remove();
        }
        holograms.get(index).clear();
    }

    private NPC getNpc(int index) {
        if (!isCitizensAvailable() || npcIds[index] == -1) return null;
        return CitizensAPI.getNPCRegistry().getById(npcIds[index]);
    }

    // ─── Persistência — npcs.yml ─────────────────────────────────────────

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
        String key   = NPC_KEY + position;
        String stamp = new SimpleDateFormat(DATE_FORMAT).format(new Date());

        npcData.set(key + ".position.world",  loc.getWorld().getName());
        npcData.set(key + ".position.x",      Math.round(loc.getX() * 100.0) / 100.0);
        npcData.set(key + ".position.y",      Math.round(loc.getY() * 100.0) / 100.0);
        npcData.set(key + ".position.z",      Math.round(loc.getZ() * 100.0) / 100.0);
        npcData.set(key + ".position.yaw",    Math.round(loc.getYaw() * 100.0) / 100.0);
        npcData.set(key + ".position.pitch",  Math.round(loc.getPitch() * 100.0) / 100.0);
        npcData.set(key + ".active",          true);
        npcData.set(key + ".placed-at",       stamp);
        npcDataDirty = true;
        flushNpcDataAsync(); // async — não bloqueia a main thread
    }

    private Location getSavedLocation(int position) {
        String key = NPC_KEY + position + ".position";
        if (!npcData.contains(key)) return null;

        String worldName = npcData.getString(key + ".world", "world");
        var    world     = Bukkit.getWorld(worldName);
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

    private void saveNpcFile() {
        try { npcData.save(npcFile); }
        catch (IOException e) { log.severe("[sCoins NPC] Erro ao salvar npcs.yml: " + e.getMessage()); }
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private boolean isCitizensAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("Citizens");
    }

    private String medalOf(int position) {
        return switch (position) {
            case 1 -> "&6[&e🥇&6]";
            case 2 -> "&7[&f🥈&7]";
            case 3 -> "&c[&6🥉&c]";
            default -> "#" + position;
        };
    }

    private String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
