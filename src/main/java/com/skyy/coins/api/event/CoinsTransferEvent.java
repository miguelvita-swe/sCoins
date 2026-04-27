package com.skyy.coins.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Disparado quando um jogador tenta enviar coins para outro (/coins enviar).
 * Cancelável — se cancelado, a transferência NÃO ocorre.
 */
public class CoinsTransferEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID sender;
    private final UUID receiver;
    private       long amount;   // long
    private boolean cancelled = false;

    public CoinsTransferEvent(UUID sender, UUID receiver, long amount) {
        this.sender   = sender;
        this.receiver = receiver;
        this.amount   = amount;
    }

    /** UUID de quem está enviando. */
    public UUID getSender()   { return sender; }

    /** UUID de quem vai receber. */
    public UUID getReceiver() { return receiver; }

    /** Valor sendo transferido (pode ser alterado por outros plugins). */
    public long getAmount()               { return amount; }
    public void setAmount(long amount)    { this.amount = amount; }

    @Override public boolean isCancelled()             { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()           { return HANDLERS; }
}
