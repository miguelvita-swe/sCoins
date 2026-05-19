# sCoins — API para Desenvolvedores

## Obtendo a instância da API

A API segue o padrão do **Vault** — exposta via `BukkitServicesManager`.  
Nenhuma dependência compile-time necessária (use `softdepend`).

```java
import br.com.skyy.coins.api.EconomyAPIHolder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public EconomyAPIHolder getAPI() {
    try {
        RegisteredServiceProvider<EconomyAPIHolder> rsp =
            Bukkit.getServicesManager().getRegistration(EconomyAPIHolder.class);
        return rsp == null ? null : rsp.getProvider();
    } catch (Throwable t) {
        return null;
    }
}
```

No seu `plugin.yml`:
```yaml
softdepend: [sCoins]
```

---

## Métodos disponíveis

### `hasAccount(String playerName)`
Verifica se o jogador possui conta. Respeita a config `offline-transactions`.
```java
boolean hasAccount = api.hasAccount("Steve");
```

### `hasAccount(String playerName, boolean check)`
- `check = true` → respeita configuração `offline-transactions`
- `check = false` → modo admin, permite jogadores offline sempre
```java
boolean hasAccount = api.hasAccount("Steve", false); // modo admin
```

### `getAccount(String playerName)` → `Account`
Retorna o objeto completo da conta, ou `null` se não encontrado.
```java
Account account = api.getAccount("Steve");
if (account != null) {
    long saldo = account.getMoney();
    account.deposit(500, true);
}
```

### `getBalance(String playerName)` → `double`
Consulta rápida de saldo. Retorna `0.0` se não tiver conta.
```java
double balance = api.getBalance("Steve");
```

### `set(String playerName, double amount)`
Define saldo exato. **Não registra transação.** Use apenas para ajustes admin.
```java
api.set("Steve", 5000.0);
```

### `has(String playerName, double amount)` → `boolean`
Verifica se o jogador tem pelo menos `amount` coins.
```java
if (api.has("Steve", 1000)) {
    // Jogador tem pelo menos 1000
}
```

### `withdraw(String playerName, double amount, boolean apply)` → `EconomyResponse`
Remove coins. Se `apply = true`, registra no histórico.
```java
EconomyResponse response = api.withdraw("Steve", 500, true);
if (response.transactionSuccess()) {
    sender.sendMessage("Saque realizado! Saldo: " + response.balance);
} else {
    sender.sendMessage("Erro: " + response.errorMessage);
}
```

### `deposit(String playerName, double amount, boolean apply)` → `EconomyResponse`
Adiciona coins. Se `apply = true`, registra no histórico.
```java
EconomyResponse response = api.deposit("Steve", 1000, true);
if (response.transactionSuccess()) {
    player.sendMessage("Você ganhou " + response.amount + " coins!");
}
```

### `getTop()` → `LinkedHashMap<String, Double>`
Retorna top 10 ordenado do maior para o menor saldo.
```java
LinkedHashMap<String, Double> top = api.getTop();
int pos = 1;
for (Map.Entry<String, Double> entry : top.entrySet()) {
    sender.sendMessage(pos++ + "º - " + entry.getKey() + ": " + entry.getValue());
}
```

### `getPlayerTopPosition(Player player)` → `int`
### `getPlayerTopPosition(String playerName)` → `int`
Retorna posição no ranking (1-indexed). Retorna `-1` se não estiver no top.
```java
int position = api.getPlayerTopPosition(player);
if (position > 0) {
    player.sendMessage("Você está em " + position + "º lugar!");
}
```

---

## Estrutura `EconomyResponse`

| Campo | Tipo | Descrição |
|---|---|---|
| `amount` | `double` | Quantia da operação |
| `balance` | `double` | Saldo após a operação |
| `type` | `ResponseType` | `SUCCESS` ou `FAILURE` |
| `errorMessage` | `String` | Mensagem de erro (vazio se sucesso) |

```java
response.transactionSuccess() // true se type == SUCCESS
```

---

## Estrutura `Account`

```java
Account account = api.getAccount("Steve");

account.getUUID()          // UUID do jogador
account.getPlayerName()    // Nome do jogador
account.getMoney()         // Saldo atual (long)
account.setMoney(5000)     // Define saldo (não registra transação)
account.has(500)           // Verifica saldo mínimo
account.deposit(1000, true)  // Adiciona e registra histórico
account.withdraw(500, true)  // Remove e registra histórico
account.getHistory()       // Lista de Transaction (mais recente primeiro)
```

---

## Exemplos completos

### Cobrar por serviço
```java
String playerName = "Steve";
double preco = 500;

if (!api.hasAccount(playerName)) {
    sender.sendMessage("Conta não encontrada!");
    return;
}
if (!api.has(playerName, preco)) {
    sender.sendMessage("Saldo insuficiente!");
    return;
}
EconomyResponse r = api.withdraw(playerName, preco, true);
if (r.transactionSuccess()) {
    sender.sendMessage("Pagamento realizado! Saldo atual: " + r.balance);
}
```

### Dar recompensa
```java
EconomyResponse r = api.deposit(winner.getName(), 2000, true);
if (r.transactionSuccess()) {
    winner.sendMessage("Você ganhou 2000 coins!");
}
```

### Transferência entre jogadores
```java
public boolean transferir(String de, String para, double quantia) {
    if (!api.has(de, quantia)) return false;
    EconomyResponse saque = api.withdraw(de, quantia, true);
    if (!saque.transactionSuccess()) return false;
    EconomyResponse deposito = api.deposit(para, quantia, true);
    return deposito.transactionSuccess();
}
```

### Leaderboard
```java
public void mostrarTop(CommandSender sender) {
    sender.sendMessage("§6§l═══════════════════");
    sender.sendMessage("§6§l   TOP COINS");
    sender.sendMessage("§6§l═══════════════════");
    int pos = 1;
    for (Map.Entry<String, Double> e : api.getTop().entrySet()) {
        if (pos > 10) break;
        sender.sendMessage("§f" + pos++ + "º §7" + e.getKey() + " §8→ §a" + e.getValue());
    }
    sender.sendMessage("§6§l═══════════════════");
}
```

### Dashboard do jogador
```java
public void dashboard(Player player) {
    double balance  = api.getBalance(player.getName());
    int    position = api.getPlayerTopPosition(player);
    player.sendMessage("§6Saldo: §a" + balance);
    player.sendMessage("§6Posição: §a" + (position > 0 ? position + "º" : "Fora do top"));
}
```
