package br.com.skyy.coins.listener;

import br.com.skyy.coins.manager.CheckManager;
import br.com.skyy.coins.util.CoinsFormatter;
import br.com.skyy.coins.util.Messages;
import br.com.skyy.coins.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Listener do sistema de cheques.
 *
 * Responsabilidades:
 *  1. Clique direito com cheque na mão → resgata os coins
 *  2. Morte do jogador → dropa cheque de morte (se habilitado no config)
 */
public class CheckListener implements Listener {

    private final CheckManager checkManager;
    private final Messages messages;

    public CheckListener(CheckManager checkManager, Messages messages) {
        this.checkManager = checkManager;
        this.messages     = messages;
    }

    // ─── Resgate ao clicar com botão direito ─────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        // Aceita apenas ACTION_RIGHT_CLICK_AIR e ACTION_RIGHT_CLICK_BLOCK
        org.bukkit.event.block.Action action = event.getAction();
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        // Evita disparar duas vezes (mão principal e off-hand) em versões 1.9+
        try {
            if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
        } catch (Throwable ignored) {} // 1.8 não tem getHand()

        Player    player = event.getPlayer();
        ItemStack item   = player.getInventory().getItemInMainHand();

        if (!checkManager.isCheck(item)) return;
        event.setCancelled(true); // impede a interação padrão (ex: abrir porta)

        long result = checkManager.redeemCheck(player, item);

        if (result == -1L) {
            // Limite máximo atingido — não consome o cheque
            messages.send(player, "cheque-redeemed-max");
            SoundUtil.play(player, "error");
            return;
        }

        if (result > 0) {
            messages.send(player, "cheque-redeemed", "{coins}", CoinsFormatter.format(result));
        }
    }

    // ─── Cheque de morte ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player deceased = event.getEntity();
        long lost = checkManager.issueDeathCheck(deceased);
        if (lost > 0) {
            messages.send(deceased, "cheque-death-lost", "{coins}", CoinsFormatter.format(lost));
        }
    }
}
