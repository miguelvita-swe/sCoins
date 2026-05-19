package br.com.skyy.coins.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Utilitário de compatibilidade multi-versão (1.8 → 1.21).
 *
 * Detecta a versão do servidor no boot e fornece métodos que usam a API
 * disponível em cada versão, do mais moderno ao fallback mais antigo.
 *
 *  • sendActionBar — Paper Adventure (1.16+) → Spigot BungeeCord (1.9+) → chat (1.8)
 *  • sendTitle     — Bukkit API nativa (1.12+) → NMS reflection (1.8–1.11)
 *  • broadcast     — Bukkit.broadcastMessage() em todas as versões
 */
public final class VersionUtil {

    // Versão minor detectada: 1.21 → 21, 1.8 → 8
    private static int minor = 21;

    private VersionUtil() {}

    // ─── Inicialização ────────────────────────────────────────────────

    /** Deve ser chamado uma única vez em Main.onEnable(). */
    public static void init() {
        try {
            // Bukkit version: "1.21.1-R0.1-SNAPSHOT"
            String raw  = Bukkit.getBukkitVersion().split("-")[0]; // "1.21.1"
            String[] p  = raw.split("\\.");
            minor = Integer.parseInt(p[1]);
        } catch (Exception ignored) {
            minor = 21; // fallback seguro
        }
    }

    public static int getMinor() { return minor; }

    // ─── Action Bar ───────────────────────────────────────────────────

    /**
     * Envia ActionBar ao jogador com texto em formato legado (&).
     * Compatível com 1.8 → 1.21.
     */
    public static void sendActionBar(Player player, String legacyText) {
        String colored = TextUtil.color(legacyText);

        // Paper 1.16+ — Adventure API
        if (minor >= 16) {
            try {
                player.sendActionBar(
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand()
                        .deserialize(legacyText)
                );
                return;
            } catch (Throwable ignored) {}
        }

        // Spigot 1.9+ — BungeeCord compatibility layer
        if (minor >= 9) {
            try {
                net.md_5.bungee.api.chat.TextComponent tc =
                        new net.md_5.bungee.api.chat.TextComponent(
                                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(colored));
                player.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR, tc);
                return;
            } catch (Throwable ignored) {}
        }

        // 1.8 — NMS reflection
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            String ver = pkg.split("\\.")[3]; // "v1_8_R3"

            Class<?> iChatBase = Class.forName("net.minecraft.server." + ver + ".IChatBaseComponent");
            Class<?> chatSerializer = Class.forName(
                    "net.minecraft.server." + ver + ".IChatBaseComponent$ChatSerializer");
            Object component = chatSerializer.getMethod("a", String.class)
                    .invoke(null, "{\"text\":\"" + colored.replace("\"", "\\\"") + "\"}");

            Class<?> ppOutChat = Class.forName("net.minecraft.server." + ver + ".PacketPlayOutChat");
            Class<?> chatMsgType = Class.forName("net.minecraft.server." + ver + ".ChatMessageType");
            Object type = chatMsgType.getField("GAME_INFO").get(null);
            Object packet = ppOutChat.getConstructor(iChatBase, chatMsgType)
                    .newInstance(component, type);

            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object conn   = handle.getClass().getField("playerConnection").get(handle);
            Class<?> pkt  = Class.forName("net.minecraft.server." + ver + ".Packet");
            conn.getClass().getMethod("sendPacket", pkt).invoke(conn, packet);
        } catch (Throwable e) {
            // Fallback final: mensagem no chat
            player.sendMessage(colored);
        }
    }

    // ─── Title ────────────────────────────────────────────────────────

    /**
     * Envia Title + Subtitle ao jogador.
     * Compatível com 1.8 → 1.21.
     *
     * @param fadeIn  ticks de fade-in
     * @param stay    ticks de exibição
     * @param fadeOut ticks de fade-out
     */
    public static void sendTitle(Player player,
                                 String title, String subtitle,
                                 int fadeIn, int stay, int fadeOut) {
        String cTitle = TextUtil.color(title);
        String cSub   = TextUtil.color(subtitle);

        // Bukkit API (1.12+) — simples e direto
        if (minor >= 12) {
            try {
                player.sendTitle(cTitle, cSub, fadeIn, stay, fadeOut);
                return;
            } catch (Throwable ignored) {}
        }

        // 1.8 – 1.11 — NMS reflection
        try {
            sendTitleNms(player, cTitle, cSub, fadeIn, stay, fadeOut);
        } catch (Throwable e) {
            // Fallback final: mensagem no chat
            if (cTitle   != null && !cTitle.isEmpty())   player.sendMessage(cTitle);
            if (cSub     != null && !cSub.isEmpty())     player.sendMessage(cSub);
        }
    }

    /**
     * Envia title via NMS — usado apenas em 1.8–1.11.
     */
    private static void sendTitleNms(Player player,
                                     String title, String subtitle,
                                     int fi, int stay, int fo) throws Exception {
        String pkg = Bukkit.getServer().getClass().getPackage().getName();
        String ver = pkg.split("\\.")[3];

        Class<?> craftPlayer    = Class.forName("org.bukkit.craftbukkit." + ver + ".entity.CraftPlayer");
        Object   handle         = craftPlayer.getMethod("getHandle").invoke(player);
        Class<?> packetClass    = Class.forName("net.minecraft.server." + ver + ".PacketPlayOutTitle");
        Class<?> enumClass      = Class.forName("net.minecraft.server." + ver + ".PacketPlayOutTitle$EnumTitleAction");
        Class<?> chatBase       = Class.forName("net.minecraft.server." + ver + ".IChatBaseComponent");
        Class<?> chatSerializer = Class.forName("net.minecraft.server." + ver + ".IChatBaseComponent$ChatSerializer");

        java.lang.reflect.Method serialize = chatSerializer.getMethod("a", String.class);
        Object titleObj    = serialize.invoke(null, "{\"text\":\"" + title.replace("\"","\\\"")    + "\"}");
        Object subtitleObj = serialize.invoke(null, "{\"text\":\"" + subtitle.replace("\"","\\\"") + "\"}");

        Object TIMES    = Enum.valueOf((Class) enumClass, "TIMES");
        Object TITLE    = Enum.valueOf((Class) enumClass, "TITLE");
        Object SUBTITLE = Enum.valueOf((Class) enumClass, "SUBTITLE");

        Object pktTimes = packetClass.getConstructor(enumClass, chatBase, int.class, int.class, int.class)
                .newInstance(TIMES, null, fi, stay, fo);
        Object pktTitle = packetClass.getConstructor(enumClass, chatBase, int.class, int.class, int.class)
                .newInstance(TITLE, titleObj, fi, stay, fo);
        Object pktSub   = packetClass.getConstructor(enumClass, chatBase, int.class, int.class, int.class)
                .newInstance(SUBTITLE, subtitleObj, fi, stay, fo);

        Object conn = handle.getClass().getField("playerConnection").get(handle);
        Class<?> pkt = Class.forName("net.minecraft.server." + ver + ".Packet");
        java.lang.reflect.Method send = conn.getClass().getMethod("sendPacket", pkt);
        send.invoke(conn, pktTimes);
        send.invoke(conn, pktTitle);
        send.invoke(conn, pktSub);
    }

    // ─── Broadcast ────────────────────────────────────────────────────

    /**
     * Broadcast de mensagem legada compatível com todas as versões.
     * Usa Bukkit.broadcastMessage() — sem Adventure, sem NMS.
     */
    public static void broadcast(String legacyText) {
        // TextUtil.color já processa & e \n
        String colored = TextUtil.color(legacyText);
        for (String line : colored.split("\n", -1)) {
            Bukkit.broadcastMessage(line);
        }
    }

    // ─── TextDisplay (1.19.4+) ────────────────────────────────────────

    /**
     * Retorna true se o servidor suporta TextDisplay (1.19.4+).
     * Usado pelo NpcManager para decidir se usa hologramas ou não.
     */
    public static boolean supportsTextDisplay() {
        if (minor < 19) return false;
        if (minor > 19) return true;
        // 1.19.x — só 1.19.4+
        try {
            Class.forName("org.bukkit.entity.TextDisplay");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
