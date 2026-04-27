package com.skyy.coins.model;

public enum TransactionType {
    SENT,           // /coins enviar — quem enviou
    RECEIVED,       // /coins enviar — quem recebeu
    ADMIN_ADD,      // /coins add
    ADMIN_REMOVE,   // /coins remove
    REWARD          // recompensa por tempo online
}
