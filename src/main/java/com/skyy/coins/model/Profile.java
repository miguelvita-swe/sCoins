package com.skyy.coins.model;

import java.util.UUID;

public class Profile {

    private final UUID uuid;
    private final String name;
    private long coins;   // long — consistente com DB BIGINT, sem risco de truncamento

    public Profile(UUID uuid, String name, long coins) {
        this.uuid  = uuid;
        this.name  = name;
        this.coins = coins;
    }

    public UUID   getUuid()  { return uuid; }
    public String getName()  { return name; }
    public long   getCoins() { return coins; }
    public void   setCoins(long coins) { this.coins = coins; }
}
