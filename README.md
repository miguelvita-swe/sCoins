# sCoins

**sCoins** é um plugin de economia completo para servidores Minecraft, com sistema de coins, ranking de jogadores, magnata, NPCs de top, hologramas, histórico de transações, extrato, sistema de cheques, recompensa por tempo online, menus interativos e API pública para integração com outros plugins. Suporta **1.8 até 1.21** com fallback automático para todas as versões.

---

## 🧩 Plugins da Suite

| Plugin | Descrição |
|--------|-----------|
| **sCoins** | **Sistema de economia customizada com coins, ranking, NPCs e API** |

---

## ✨ Funcionalidades

- 💰 **Sistema de Coins** — saldo por jogador com suporte a valores long (até 9 quintilhões)
- 🏆 **Ranking Top 10** — atualização periódica configurável, com menu interativo
- 👑 **Sistema de Magnata** — notificação com título, subtítulo e mensagem no chat ao virar o mais rico
- 🗿 **NPCs de Top** — NPCs visuais com skin do jogador e holograma acima, via Citizens ou ArmorStand (fallback)
- 📜 **Histórico de Transações** — menu com as últimas transferências de cada jogador
- 📊 **Extrato** — saldo atual, total enviado, total recebido e total de rewards no período
- 🎟️ **Sistema de Cheques** — emita cheques físicos (itens) com valor resgatável
- ⏱️ **Recompensa por Tempo Online** — dá coins automaticamente a cada X minutos com ActionBar animada
- 🔇 **Toggle de Recebimento** — jogador pode ativar/desativar o recebimento de coins
- 🎨 **Menus Interativos** — menu principal, top, histórico e extrato com sons e cabeças customizadas
- 🔗 **API Pública** — `EconomyAPIHolder` via `BukkitServicesManager` + integração Vault
- 📡 **PlaceholderAPI** — placeholders `%scoins_*%` para uso em qualquer plugin
- 🏷️ **Tag no Chat** — tag `{magnata}` e medalhas 🥇🥈🥉 no chat e no TAB para os top 3
- 💾 **MySQL + YAML** — suporte a YAML local ou MySQL com HikariCP
- 🔄 **Migração Automática** — YAML → MySQL sem perda de dados
- ⚙️ **100% Configurável** — mensagens, menus, sons, NPCs e comandos personalizáveis via YML

---

## 📦 Instalação

1. Baixe o `sCore.jar` em [sCore Releases](https://github.com/miguelvita-swe/sCore/releases)
2. Baixe o `sCoins.jar` em [Releases](../../releases)
3. Coloque ambos em `plugins/` do servidor
4. Reinicie o servidor
5. Configure `plugins/sCoins/config.yml`

### Dependências

| Plugin | Tipo | Função |
|--------|------|--------|
| **sCore** | **Obrigatório** | Núcleo da suite — hologramas, NPCs, skulls, database |
| Vault | Opcional | Compatibilidade com plugins que usam a API Vault Economy |
| PlaceholderAPI | Opcional | Placeholders `%scoins_*%` em outros plugins |
| Citizens | Opcional | NPCs realistas para o ranking top (fallback: ArmorStand) |
| HolographicDisplays / DecentHolograms | Opcional | Hologramas acima dos NPCs (fallback: nativo) |
| LegendChat / nChat / UltimateChat / NoxusChat | Opcional | Tag `{magnata}` no chat via plugin de chat |

---

## ⚙️ Configuração

```yaml
# plugins/sCoins/config.yml

# Modo de depuração para correção de problemas no plugin.
debug-mode: false

# Banco de dados
database:
  # YAML ou MYSQL
  storage-type: YAML
  data:
    host: localhost
    port: 3306
    database: ''
    username: ''
    password: ''

# Configurações gerais
max-coins: 2000000000
starting-coins: 0
min-transfer: 1
transfer-cooldown: 30

# Recompensa por tempo online
reward-enabled: true
reward-interval: 5       # minutos
reward-amount: 10        # coins

# Ranking
top-delay: 600           # segundos para atualizar

# Formatações de coins (ex: 1000 = 1K)
formats:
  - amount: 1000
    suffix: K
  - amount: 1000000
    suffix: M
```

---

## 🎮 Comandos

| Comando | Aliases | Descrição | Permissão |
|---------|---------|-----------|-----------|
| `/coins` | `coin`, `balance` | Abre o menu principal ou exibe saldo | `scoins.use` |
| `/coins <jogador>` | — | Ver coins de outro jogador | `scoins.use` |
| `/coins enviar <jogador> <valor>` | — | Enviar coins para outro jogador | `scoins.use` |
| `/coins historico` | — | Abre o menu de histórico/extrato | `scoins.use` |
| `/coins top` | — | Abre o menu de top jogadores | `scoins.use` |
| `/coins toggle` | — | Ativar/desativar recebimento de coins | `scoins.use` |
| `/coins cheque <valor>` | — | Emitir um cheque de coins | `scoins.use` |
| `/coins ajuda` | `/coins help` | Exibir ajuda | `scoins.use` |
| `/coins add <jogador> <valor>` | — | Adicionar coins **(admin)** | `scoins.admin.add` |
| `/coins remove <jogador> <valor>` | — | Remover coins **(admin)** | `scoins.admin.remove` |
| `/coins setar <jogador> <valor>` | — | Definir coins exatos **(admin)** | `scoins.admin.set` |
| `/coins formatar <valor> <sufixo>` | — | Adicionar formatação **(admin)** | `scoins.admin.formatar` |
| `/coins npc set <1-3>` | — | Setar NPC do top **(admin)** | `scoins.admin.npc` |
| `/coins npc reload` | — | Recarregar NPCs **(admin)** | `scoins.admin.npc` |
| `/coins db status` | — | Status do banco de dados **(admin)** | `scoins.admin.db` |
| `/coins reload` | — | Recarregar configurações **(admin)** | `scoins.admin.reload` |
| `/rich` | `rico`, `magnata` | Ver o magnata atual | `scoins.use` |
| `/pay <jogador> <valor>` | `pagar`, `enviar` | Enviar coins | `scoins.use` |

---

## 🔑 Permissões

| Permissão | Descrição |
|-----------|-----------|
| `scoins.*` | Acesso total ao plugin |
| `scoins.admin` | Acesso a todos os comandos administrativos |
| `scoins.use` | Acesso aos comandos de jogador |
| `scoins.admin.add` | `/coins add` |
| `scoins.admin.remove` | `/coins remove` |
| `scoins.admin.set` | `/coins setar` |
| `scoins.admin.reload` | `/coins reload` |
| `scoins.admin.formatar` | `/coins formatar` |
| `scoins.admin.npc` | `/coins npc` |
| `scoins.admin.db` | `/coins db` |

---

## 🏗️ Estrutura do Projeto

```
sCoins/
├── src/main/java/br/com/skyy/coins/
│   ├── Main.java                        ← Plugin principal (onEnable/onDisable)
│   ├── api/
│   │   ├── SCoinsAPI.java               ← Interface pública da API
│   │   ├── SCoinsAPIImpl.java           ← Implementação da API
│   │   ├── SCoinsProvider.java          ← Acesso estático à API
│   │   ├── EconomyAPIHolder.java        ← Interface para integração via ServicesManager
│   │   ├── EconomyAPIHolderImpl.java    ← Implementação do EconomyAPIHolder
│   │   ├── EconomyProviderSCoins.java   ← Provedor de economia no sCore
│   │   ├── VaultEconomy.java            ← Integração com Vault Economy
│   │   └── event/                       ← Eventos: CoinsChange, Transfer, MagnataChange
│   ├── commands/
│   │   ├── CoinsCommand.java            ← Comando principal /coins
│   │   ├── CoinsTabCompleter.java       ← Tab completion
│   │   ├── CommandRegistry.java         ← Registro dinâmico de aliases
│   │   ├── CommandsConfig.java          ← Leitura do commands.yml
│   │   ├── MoneyCommand.java            ← Alias /money → /coins
│   │   ├── RichCommand.java             ← /rich
│   │   ├── PayCommand.java              ← /pay
│   │   └── CheckCommand.java            ← /cheque
│   ├── listener/
│   │   ├── PlayerListener.java          ← Join/Quit, carrega/salva perfil
│   │   ├── ChatPrefixListener.java      ← Tag {magnata} e medalhas no chat
│   │   └── CheckListener.java           ← Ativação de cheques
│   ├── manager/
│   │   ├── CoinsManager.java            ← Lógica central de coins (HashMap em memória)
│   │   ├── MagnataManager.java          ← Detecção e notificação do magnata
│   │   ├── RankManager.java             ← Top jogadores + scoreboard/TAB
│   │   ├── CooldownManager.java         ← Cooldown de transferências
│   │   ├── ToggleManager.java           ← Toggle de recebimento
│   │   ├── TransactionManager.java      ← Histórico de transações
│   │   └── CheckManager.java            ← Emissão e resgate de cheques
│   ├── menu/
│   │   ├── MainMenu.java                ← Menu principal (/coins)
│   │   ├── HistoryMenu.java             ← Menu de histórico
│   │   ├── ExtratoMenu.java             ← Menu de extrato
│   │   └── TopMenu.java                 ← Menu de top jogadores
│   ├── model/
│   │   ├── Profile.java                 ← Perfil do jogador (UUID, nome, coins, toggle)
│   │   ├── Transaction.java             ← Modelo de transação
│   │   └── TransactionType.java         ← Enum: SEND, RECEIVE, ADD, REMOVE, REWARD
│   ├── npc/
│   │   └── NpcManager.java              ← Gerencia NPCs do top 1/2/3
│   ├── storage/
│   │   ├── FileStorage.java             ← YAML + MySQL (HikariCP)
│   │   ├── DatabaseManager.java         ← Abstração do banco de dados
│   │   └── MigrationManager.java        ← Migração YAML → MySQL
│   ├── task/
│   │   ├── AutoSaveTask.java            ← Auto-save periódico
│   │   ├── RewardTask.java              ← Recompensa por tempo online
│   │   └── ReconnectTask.java           ← Monitor de reconexão MySQL
│   └── util/
│       ├── CoinsFormatter.java          ← Formatação de valores (1K, 1M...)
│       ├── Messages.java                ← Mensagens configuráveis
│       ├── MenuConfig.java              ← Wrapper do menus.yml
│       ├── SCoinsExpansion.java         ← Placeholders PlaceholderAPI
│       ├── SoundUtil.java               ← Sons configuráveis
│       ├── SkullUtil.java               ← Cabeças customizadas multi-versão
│       ├── TextUtil.java                ← Colorização, \n, HEX
│       ├── VersionUtil.java             ← Detecção de versão do servidor
│       └── ChatIntegration.java         ← Detecção de plugins de chat
├── src/main/resources/
│   ├── config.yml                       ← Configuração principal
│   ├── commands.yml                     ← Aliases de comandos
│   ├── menus.yml                        ← Configuração dos menus
│   ├── npcs.yml                         ← Configuração dos NPCs
│   └── plugin.yml                       ← Metadados do plugin
└── pom.xml                              ← Build Maven (shade + Java 8)
```

---

## 🔧 Build

```bash
mvn clean package
```

Requer Java 8+. O JAR gerado em `target/sCoins-1.0.0-shaded.jar` é copiado automaticamente para a pasta de plugins configurada no `pom.xml`.

---

## 📋 Versões Suportadas

| Versão MC | NBT | Skull | Hologramas | Hex Colors |
|-----------|-----|-------|------------|------------|
| 1.8 – 1.12 | NMS Reflection | GameProfile | DH / HD | ❌ |
| 1.13 | NMS Reflection | GameProfile | DH / HD | ❌ |
| 1.14 – 1.15 | PDC | GameProfile | DH / HD | ❌ |
| 1.16 – 1.17 | PDC | GameProfile | DH / HD | ✅ |
| 1.18 – 1.21 | PDC | PlayerProfile | DH / HD | ✅ |

---

## 🔌 API

O sCoins expõe uma API pública via `BukkitServicesManager`, compatível com o padrão Vault:

```java
RegisteredServiceProvider<EconomyAPIHolder> rsp =
    Bukkit.getServicesManager().getRegistration(EconomyAPIHolder.class);
EconomyAPIHolder api = rsp == null ? null : rsp.getProvider();

// Verificar saldo
double balance = api.getBalance("Steve");

// Depositar
EconomyResponse res = api.deposit("Steve", 1000, true);

// Sacar
EconomyResponse res = api.withdraw("Steve", 500, true);

// Top jogadores
HashMap<String, Double> top = api.getTop();
```

### PlaceholderAPI

| Placeholder | Retorno |
|-------------|---------|
| `%scoins_money%` | Saldo formatado |
| `%scoins_money_raw%` | Saldo sem formatação |
| `%scoins_magnata%` | Tag do magnata |
| `%scoins_top_pos%` | Posição no ranking |
| `%scoins_top_player_[1-10]%` | Nome do jogador na posição |
| `%scoins_top_value_[1-10]%` | Coins do jogador na posição |

---

## 👤 Autor

**Skyy** — Suite de plugins "s" para Minecraft

---

## 📄 Licença

Este projeto é de uso privado. Todos os direitos reservados.
