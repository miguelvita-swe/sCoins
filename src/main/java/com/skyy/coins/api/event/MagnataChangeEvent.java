package com.skyy.coins.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Disparado quando o magnata do servidor muda.
 * Não é cancelável — é apenas informativo.
 */
public class MagnataChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String newMagnataName;
    private final long   newMagnataCoins;  // long — evita overflow acima de 2.1B
    private final String previousMagnataName; // null se não havia magnata antes

    public MagnataChangeEvent(String newMagnataName, long newMagnataCoins,
                               @Nullable String previousMagnataName) {
        this.newMagnataName      = newMagnataName;
        this.newMagnataCoins     = newMagnataCoins;
        this.previousMagnataName = previousMagnataName;
    }

    /** Nome do novo magnata. */
    public String getNewMagnataName() { return newMagnataName; }

    /** Saldo do novo magnata no momento da mudança. */
    public long getNewMagnataCoins() { return newMagnataCoins; }

    /** Nome do magnata anterior, ou null se não havia nenhum. */
    @Nullable
    public String getPreviousMagnataName() { return previousMagnataName; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()           { return HANDLERS; }
}
