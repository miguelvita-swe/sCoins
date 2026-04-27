package com.skyy.coins.listener;

import com.skyy.coins.storage.FileStorage;
import com.skyy.coins.util.TextUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listener de chat — registrado APENAS quando nenhum plugin de chat externo
 * (LegendChat, nChat, UltimateChat, NoxusChat) estiver instalado.
 *
 * Funcionalidades:
 *  • Substitui {magnata} na mensagem pela tag configurável do magnata atual.
 *    Ex: config: magnata-tag: "&6&l[MAGNATA]"
 *        jogador digita: "olá {magnata}" → "olá [MAGNATA]"
 *
 * Quando um plugin de chat for detectado, este listener NÃO é registrado.
 * Use %yeconomy_magnata% no formato do seu plugin de chat.
 */
public class ChatPrefixListener implements Listener {

    private final FileStorage fileStorage;
    private final FileConfiguration config;

    public ChatPrefixListener(FileStorage fileStorage, FileConfiguration config) {
        this.fileStorage = fileStorage;
        this.config      = config;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        // Converte a mensagem Adventure para string plain para processar a tag {magnata}
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (plain.contains("{magnata}")) {
            String[] magnata = fileStorage.getMagnata();
            String tag = magnata != null
                    ? TextUtil.color(config.getString("magnata-tag", "&6&l[MAGNATA]"))
                    : "";
            String replaced = plain.replace("{magnata}", tag);
            // Re-serializa de volta para Component respeitando as cores (&)
            event.message(LegacyComponentSerializer.legacyAmpersand().deserialize(replaced));
        }
    }
}
