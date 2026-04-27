package com.skyy.coins.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Disparado sempre que o saldo de um jogador é alterado (add, remove, set).
 * Pode ser cancelado — se cancelado, a operação NÃO acontece.
 *
 * Exemplo de uso:
 * <pre>
 *   {@literal @}EventHandler
 *   public void onCoinsChange(CoinsChangeEvent event) {
 *       if (event.getNewAmount() > 1_000_000) {
 *           event.setCancelled(true); // impede passar de 1M
 *       }
 *   }
 * </pre>
 */
public class CoinsChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum Cause { ADD, REMOVE, SET, TRANSFER, REWARD, ADMIN }

    private final UUID uuid;
    private final long previousAmount;   // long
    private       long newAmount;        // long
    private final Cause cause;
    private boolean cancelled = false;

    public CoinsChangeEvent(UUID uuid, long previousAmount, long newAmount, Cause cause) {
        this.uuid           = uuid;
        this.previousAmount = previousAmount;
        this.newAmount      = newAmount;
        this.cause          = cause;
    }

    /** UUID do jogador cujo saldo está mudando. */
    public UUID getPlayerUUID() { return uuid; }

    /** Saldo antes da mudança. */
    public long getPreviousAmount() { return previousAmount; }

    /** Saldo resultante (pode ser alterado por outros plugins). */
    public long getNewAmount() { return newAmount; }

    /** Permite que outro plugin altere o valor final antes de ser aplicado. */
    public void setNewAmount(long newAmount) { this.newAmount = newAmount; }

    /** Motivo da alteração. */
    public Cause getCause() { return cause; }

    /** Diferença entre o novo e o antigo saldo (pode ser negativa). */
    public long getDelta() { return newAmount - previousAmount; }

    @Override public boolean isCancelled()            { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()          { return HANDLERS; }
}
