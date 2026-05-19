package br.com.skyy.coins.listener;

import br.com.skyy.coins.storage.FileStorage;
import br.com.skyy.coins.util.TextUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Listener de chat — registrado APENAS quando nenhum plugin de chat externo
 * (LegendChat, nChat, UltimateChat, NoxusChat) estiver instalado.
 *
 * Usa AsyncPlayerChatEvent (Bukkit 1.8+) para máxima compatibilidade.
 * Quando um plugin de chat for detectado, este listener NÃO é registrado.
 * Use %scoins_magnata% no formato do seu plugin de chat.
 */
public class ChatPrefixListener implements Listener {

    private final FileStorage fileStorage;
    private final FileConfiguration config;

    public ChatPrefixListener(FileStorage fileStorage, FileConfiguration config) {
        this.fileStorage = fileStorage;
        this.config      = config;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();

        if (message.contains("{magnata}")) {
            String[] magnata = fileStorage.getMagnata();
            String tag = magnata != null
                    ? TextUtil.color(config.getString("messages.magnata-tag", "&6&l[MAGNATA]"))
                    : "";
            event.setMessage(message.replace("{magnata}", tag));
        }
    }
}
