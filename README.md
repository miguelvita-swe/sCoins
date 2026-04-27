# 💰 sCoins — Economy Plugin for Minecraft

> Plugin de economia profissional para servidores **Paper/Spigot 1.21+**  
> Desenvolvido por **Skyy** · Java 17 · Maven

---

## ✨ Funcionalidades

| Recurso | Descrição |
|---|---|
| 💵 Sistema de Coins | Economia completa com saldo por UUID |
| 💸 Transferências | `/coins enviar <jogador> <valor>` com cooldown configurável |
| 🏆 Top Jogadores | Ranking top 10 com menu visual |
| 👑 Magnata | Detecta o jogador mais rico e notifica o servidor com título |
| 📜 Histórico | Últimas transações por jogador com menu paginado |
| 📊 Extrato | Saldo atual + total enviado/recebido/reward |
| 🎁 Reward por Tempo | Coins automáticos a cada X minutos online |
| 🔇 Toggle | Ativar/desativar recebimento de coins |
| 🗿 NPCs Top 3 | NPCs com skin real e hologram via Citizens2 |
| 🎖️ Ranking Tab/Chat | Medalhas 🥇🥈🥉 no Tab e no Chat |
| 🔌 API Pública | `SCoinsAPI` para outros desenvolvedores |
| 📡 PlaceholderAPI | Placeholders para uso em qualquer plugin |
| 🗄️ MySQL / YAML | Dual backend configurável no `config.yml` |

---

## 📋 Requisitos

- **Paper** 1.21+ (recomendado) ou Spigot 1.21+
- **Java** 17+
- **Citizens2** *(opcional — necessário para NPCs do top)*
- **PlaceholderAPI** *(opcional — necessário para placeholders externos)*
- **MySQL** *(opcional — backend padrão é YAML)*

---

## 🚀 Instalação

1. Baixe o JAR na aba [Releases](../../releases)
2. Coloque em `plugins/`
3. Reinicie o servidor
4. Configure `plugins/sCoins/config.yml`

---

## ⚙️ Configuração Rápida

```yaml
# config.yml — principais opções
storage-type: "yaml"   # "yaml" ou "mysql"
starting-coins: 0
max-coins: 2000000000

reward:
  enabled: true
  amount: 10
  interval-minutes: 5

cooldown:
  transfer-seconds: 30
```

Para usar MySQL, edite:
```yaml
storage-type: "mysql"
database:
  host: "localhost"
  port: 3306
  name: "scoins"
  username: "seu_usuario"
  password: "sua_senha"
```

---

## 🎮 Comandos

| Comando | Permissão | Descrição |
|---|---|---|
| `/coins` | `scoins.use` | Abre o menu principal |
| `/coins enviar <jogador> <valor>` | `scoins.use` | Transfere coins |
| `/coins historico` | `scoins.use` | Abre o histórico |
| `/coins extrato` | `scoins.use` | Exibe extrato detalhado |
| `/coins top` | `scoins.use` | Abre o ranking |
| `/coins toggle` | `scoins.use` | Ativa/desativa recebimento |
| `/coins add <jogador> <valor>` | `scoins.admin.add` | Admin: adicionar coins |
| `/coins remove <jogador> <valor>` | `scoins.admin.remove` | Admin: remover coins |
| `/coins setar <jogador> <valor>` | `scoins.admin.set` | Admin: definir coins |
| `/coins formatar <valor> <sufixo>` | `scoins.admin.formatar` | Admin: formatação customizada |
| `/coins reload` | `scoins.admin.reload` | Recarrega a config |
| `/coins npc set <1-3>` | `scoins.admin.npc` | Define posição de NPC |
| `/coins npc remove <1-3>` | `scoins.admin.npc` | Remove NPC |
| `/coins npc reload` | `scoins.admin.npc` | Recarrega NPCs |
| `/coins db status` | `scoins.admin.db` | Status do banco de dados |

---

## 📡 Placeholders (PlaceholderAPI)

| Placeholder | Retorno |
|---|---|
| `%scoins_money%` | Saldo do jogador formatado (ex: `1.5M`) |
| `%scoins_money_raw%` | Saldo bruto do jogador (ex: `1500000`) |
| `%scoins_magnata%` | Tag configurável se o jogador for o magnata (ex: `[MAGNATA]`), vazio caso contrário |
| `%scoins_top_pos%` | Posição do próprio jogador no TOP (ex: `3`) ou `-` |
| `%scoins_top_player_[1-10]%` | Nome do jogador na posição X do ranking |
| `%scoins_top_value_[1-10]%` | Saldo formatado do jogador na posição X |

> A tag retornada por `%yeconomy_magnata%` é configurável no `config.yml` via a chave `magnata-tag`.

---

## 💬 Tag de Chat

| Tag | Descrição |
|---|---|
| `{magnata}` | Substituída pela tag do magnata atual em qualquer mensagem de chat |

**Exemplo:** o jogador digita `olá {magnata}` → aparece no chat como `olá &6&l[MAGNATA]`

> Funciona automaticamente quando nenhum plugin de chat externo está presente.  
> Com LegendChat / nChat / UltimateChat / NoxusChat: use `%yeconomy_magnata%` no formato do seu plugin.

---

## 🔌 API para Desenvolvedores

```java
// Obtendo a instância
SCoinsAPI api = SCoinsProvider.getAPI();

// Consultas
long saldo = api.getCoins(uuid);
api.addCoins(uuid, 500);
api.removeCoins(uuid, 100);
api.setCoins(uuid, 1000);

// Transferência segura
TransferResult result = api.transfer(fromUUID, toUUID, 200);

// Top jogadores
List<SCoinsAPI.TopEntry> top = api.getTopPlayers(10);
```

---

## 🏗️ Build

```bash
# Build padrão
mvn clean package

# Build + copia para servidor local (configure o caminho no pom.xml primeiro)
mvn clean package -P copy-server
```

O JAR final estará em `target/sCoins-1.0.0.jar`.

---

## 📁 Estrutura do Projeto

```
src/main/java/com/skyy/coins/
 ├── Main.java                  ← Ponto de entrada do plugin
 ├── api/                       ← API pública (SCoinsAPI, SCoinsProvider)
 ├── commands/                  ← Comandos (/coins)
 ├── listener/                  ← Eventos Bukkit
 ├── manager/                   ← Lógica de negócio (coins, rank, magnata...)
 ├── menu/                      ← Menus de inventário
 ├── model/                     ← Modelos de dados (Profile, Transaction)
 ├── npc/                       ← Integração Citizens2
 ├── storage/                   ← Persistência (YAML / MySQL)
 ├── task/                      ← Tasks agendadas (AutoSave, Reward)
 └── util/                      ← Utilitários (TextUtil, CoinsFormatter...)
```

---

## 📜 Licença

Este projeto é de uso **pessoal/educacional**. Sinta-se livre para estudar o código.  
Para uso comercial, entre em contato com o autor.

---

<div align="center">
  Feito com ☕ e muita dedicação por <b>Skyy</b>
</div>
