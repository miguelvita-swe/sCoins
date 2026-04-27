package com.skyy.coins.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Transaction {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final TransactionType type;
    private final long amount;           // long — suporta até 9.2 × 10^18, sem risco de overflow
    private final String otherPlayer;
    private final LocalDateTime timestamp;

    public Transaction(TransactionType type, long amount, String otherPlayer) {
        this.type = type;
        this.amount = amount;
        this.otherPlayer = otherPlayer;
        this.timestamp = LocalDateTime.now();
    }

    /** Construtor usado ao carregar do arquivo/banco (timestamp já existe). */
    public Transaction(TransactionType type, long amount, String otherPlayer, LocalDateTime timestamp) {
        this.type = type;
        this.amount = amount;
        this.otherPlayer = otherPlayer;
        this.timestamp = timestamp;
    }

    public TransactionType getType()       { return type; }
    public long getAmount()                { return amount; }
    public String getOtherPlayer()         { return otherPlayer; }
    public String getFormattedDate()       { return timestamp.format(FORMATTER); }
    public LocalDateTime getTimestampRaw() { return timestamp; }

    /** Formato: TIPO;VALOR;JOGADOR;DATA_ISO */
    public String serialize() {
        String player = otherPlayer != null ? otherPlayer : "-";
        return type.name() + ";" + amount + ";" + player + ";" + timestamp;
    }

    public static Transaction deserialize(String raw) {
        try {
            String[] parts = raw.split(";", 4);
            TransactionType type   = TransactionType.valueOf(parts[0]);
            long            amount = Long.parseLong(parts[1]);   // long — retrocompatível com int serializado
            String          player = parts[2].equals("-") ? null : parts[2];
            LocalDateTime   ts     = LocalDateTime.parse(parts[3]);
            return new Transaction(type, amount, player, ts);
        } catch (Exception e) {
            return null;
        }
    }
}
