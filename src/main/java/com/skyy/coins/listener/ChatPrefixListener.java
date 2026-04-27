package com.skyy.coins.listener;

import com.skyy.coins.manager.RankManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listener de prefixo de chat — registrado APENAS quando nenhum plugin de chat
 * externo (LegendChat, nChat, UltimateChat, NoxusChat) estiver instalado.
 *
 * Quando um plugin de chat for detectado, este listener NÃO é registrado.
 * Nesse caso, adicione %scoins_chat_prefix% ou %scoins_medal% na configuração
 * de formato do seu plugin de chat para exibir as medalhas (🥇🥈🥉).
 *
 * Prioridade LOWEST — garante que o prefixo é injetado antes de qualquer
 * outro plugin que possa modificar a mensagem.
 */
public class ChatPrefixListener implements Listener {

    private final RankManager rankManager;

    public ChatPrefixListener(RankManager rankManager) {
        this.rankManager = rankManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        String prefix = rankManager.getChatPrefix(event.getPlayer().getName());
        if (!prefix.isEmpty()) {
            Component prefixComp = LegacyComponentSerializer.legacySection().deserialize(prefix);
            event.message(prefixComp.append(event.message()));
        }
    }
}
