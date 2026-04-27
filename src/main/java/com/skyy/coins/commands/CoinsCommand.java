package com.skyy.coins.commands;

import com.skyy.coins.Main;
import com.skyy.coins.manager.CoinsManager;
import com.skyy.coins.manager.CooldownManager;
import com.skyy.coins.manager.ToggleManager;
import com.skyy.coins.manager.TransactionManager;
import com.skyy.coins.menu.ExtratoMenu;
import com.skyy.coins.menu.HistoryMenu;
import com.skyy.coins.menu.MainMenu;
import com.skyy.coins.menu.TopMenu;
import com.skyy.coins.model.TransactionType;
import com.skyy.coins.npc.NpcManager;
import com.skyy.coins.storage.FileStorage;
import com.skyy.coins.util.CoinsFormatter;
import com.skyy.coins.util.Messages;
import com.skyy.coins.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.skyy.coins.util.MenuConfig;

import java.util.List;

public class CoinsCommand implements CommandExecutor {

    private final CoinsManager coinsManager;
    private final FileStorage fileStorage;
    private final Messages messages;
    private final CooldownManager cooldownManager;
    private final TransactionManager transactionManager;
    private final ToggleManager toggleManager;
    private final FileConfiguration config;
    private final MenuConfig menuConfig;
    private final Main plugin;
    private final int minTransfer;
    private final NpcManager npcManager;

    public CoinsCommand(CoinsManager coinsManager, FileStorage fileStorage, Messages messages,
                        CooldownManager cooldownManager, TransactionManager transactionManager,
                        ToggleManager toggleManager, FileConfiguration config,
                        Main plugin, int minTransfer, NpcManager npcManager, MenuConfig menuConfig) {
        this.coinsManager = coinsManager;
        this.fileStorage = fileStorage;
        this.messages = messages;
        this.cooldownManager = cooldownManager;
        this.transactionManager = transactionManager;
        this.toggleManager = toggleManager;
        this.config = config;
        this.menuConfig = menuConfig;
        this.plugin = plugin;
        this.minTransfer = minTransfer;
        this.npcManager = npcManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        // /coins — abre o menu principal
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                messages.send(sender, "only-players");
                return true;
            }
            Player player = (Player) sender;
            MainMenu.open(player, coinsManager, transactionManager, toggleManager, fileStorage, menuConfig.get());
            return true;
        }

        // /coins historico
        if (args[0].equalsIgnoreCase("historico") && args.length == 1) {
            if (!(sender instanceof Player)) { messages.send(sender, "only-players"); return true; }
            if (!sender.hasPermission("scoins.use.historico")) { messages.send(sender, "no-permission"); return true; }
            Player player = (Player) sender;
            HistoryMenu.open(player, transactionManager.getHistory(player.getUniqueId()), menuConfig.get());
            return true;
        }

        // /coins extrato
        if (args[0].equalsIgnoreCase("extrato") && args.length == 1) {
            if (!(sender instanceof Player)) { messages.send(sender, "only-players"); return true; }
            if (!sender.hasPermission("scoins.use.historico")) { messages.send(sender, "no-permission"); return true; }
            Player player = (Player) sender;
            ExtratoMenu.open(player, coinsManager, transactionManager, menuConfig.get());
            return true;
        }

        // /coins top
        if (args[0].equalsIgnoreCase("top") && args.length == 1) {
            if (!(sender instanceof Player)) { messages.send(sender, "only-players"); return true; }
            if (!sender.hasPermission("scoins.use.top")) { messages.send(sender, "no-permission"); return true; }
            Player player = (Player) sender;
            TopMenu.open(player, menuConfig.get(), fileStorage);
            return true;
        }

        // /coins toggle
        if (args[0].equalsIgnoreCase("toggle") && args.length == 1) {
            if (!(sender instanceof Player)) { messages.send(sender, "only-players"); return true; }
            if (!sender.hasPermission("scoins.use.toggle")) { messages.send(sender, "no-permission"); return true; }
            Player player = (Player) sender;
            boolean newState = toggleManager.toggle(player.getUniqueId());
            messages.send(player, newState ? "toggle-on" : "toggle-off");
            return true;
        }

        // /coins formatar [<valor> <sufixo|remover>]
        if (args[0].equalsIgnoreCase("formatar")) {
            if (!sender.hasPermission("scoins.admin.formatar")) { messages.send(sender, "no-permission"); return true; }

            // /coins formatar — lista formatos + mostra como usar
            if (args.length == 1) {
                messages.send(sender, "formatar-usage");          // instrução de uso
                messages.send(sender, "formatar-list-header");
                List<CoinsFormatter.Tier> tiers = CoinsFormatter.getTiers();
                if (tiers.isEmpty()) {
                    messages.send(sender, "formatar-list-empty");
                } else {
                    for (CoinsFormatter.Tier tier : tiers) {
                        messages.send(sender, "formatar-list-entry",
                                "{valor}", String.valueOf(tier.getThreshold()),
                                "{sufixo}", tier.getSuffix());
                    }
                }
                return true;
            }

            // /coins formatar <valor> <sufixo|remover>
            if (args.length == 2) {
                // só digitou o número mas esqueceu o sufixo
                messages.send(sender, "formatar-usage");
                return true;
            }

            if (args.length == 3) {
                long threshold;
                try {
                    threshold = Long.parseLong(args[1]);
                    // Valida: deve ser positivo e menor ou igual ao limite máximo de coins.
                    // Impede que admins definam formatadores absurdos (ex: 9999999999999)
                    // que nunca seriam atingidos e poluem a lista de formatadores.
                    if (threshold <= 0 || threshold > coinsManager.getMaxCoins()) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    messages.send(sender, "formatar-invalid-value");
                    return true;
                }

                // Remover formato
                if (args[2].equalsIgnoreCase("remover")) {
                    if (!config.contains("formatting." + threshold)) {
                        messages.send(sender, "formatar-not-found", "{valor}", String.valueOf(threshold));
                        return true;
                    }
                    config.set("formatting." + threshold, null);
                    plugin.saveConfig();
                    CoinsFormatter.load(config);
                    messages.send(sender, "formatar-removed", "{valor}", String.valueOf(threshold));
                    return true;
                }

                // Adicionar / atualizar formato
                String sufixo = args[2];
                // Valida o sufixo — máx 8 chars, apenas letras/números.
                // Impede injeção de §k (obfuscated), \n, strings longas que quebram formatação.
                if (sufixo.length() > 8 || !sufixo.matches("[a-zA-Z0-9+]+")) {
                    messages.send(sender, "formatar-invalid-value");
                    return true;
                }
                config.set("formatting." + threshold, sufixo);
                plugin.saveConfig();
                CoinsFormatter.load(config);
                messages.send(sender, "formatar-success",
                        "{valor}", String.valueOf(threshold),
                        "{sufixo}", sufixo);
                return true;
            }

            messages.send(sender, "formatar-usage");
            return true;
        }

        // /coins add <jogador> <valor>
        if (args[0].equalsIgnoreCase("add") && args.length == 3) {
            if (!sender.hasPermission("scoins.admin.add")) { messages.send(sender, "no-permission"); return true; }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { messages.send(sender, "player-not-found"); return true; }
            long amount = parseAmount(sender, args[2]);
            if (amount < 0) return true;
            boolean success = coinsManager.addCoins(target.getUniqueId(), amount);
            if (!success) {
                messages.send(sender, "add-max-reached", "{player}", target.getName(), "{max}", CoinsFormatter.format(coinsManager.getMaxCoins()));
                return true;
            }
            transactionManager.record(target.getUniqueId(), TransactionType.ADMIN_ADD, amount, null);
            fileStorage.saveAsync(target.getUniqueId());
            messages.send(sender, "add-success-sender", "{coins}", CoinsFormatter.format(amount), "{player}", target.getName());
            messages.send(target, "add-success-target", "{coins}", CoinsFormatter.format(amount));
            SoundUtil.play(target, "coins-add");
            return true;
        }

        // /coins remove <jogador> <valor>
        if (args[0].equalsIgnoreCase("remove") && args.length == 3) {
            if (!sender.hasPermission("scoins.admin.remove")) { messages.send(sender, "no-permission"); return true; }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { messages.send(sender, "player-not-found"); return true; }
            long amount = parseAmount(sender, args[2]);
            if (amount < 0) return true;
            boolean success = coinsManager.removeCoins(target.getUniqueId(), amount);
            if (!success) { messages.send(sender, "remove-no-balance"); return true; }
            transactionManager.record(target.getUniqueId(), TransactionType.ADMIN_REMOVE, amount, null);
            fileStorage.saveAsync(target.getUniqueId());
            messages.send(sender, "remove-success-sender", "{coins}", CoinsFormatter.format(amount), "{player}", target.getName());
            messages.send(target, "remove-success-target", "{coins}", CoinsFormatter.format(amount));
            SoundUtil.play(target, "coins-remove");
            return true;
        }

        // /coins setar <jogador> <valor>
        if (args[0].equalsIgnoreCase("setar") && args.length == 3) {
            if (!sender.hasPermission("scoins.admin.set")) { messages.send(sender, "no-permission"); return true; }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { messages.send(sender, "player-not-found"); return true; }
            long amount = parseAmount(sender, args[2]);
            if (amount < 0) return true;
            coinsManager.setCoins(target.getUniqueId(), amount);
            fileStorage.saveAsync(target.getUniqueId());
            messages.send(sender, "set-success-sender", "{coins}", CoinsFormatter.format(amount), "{player}", target.getName());
            messages.send(target, "set-success-target", "{coins}", CoinsFormatter.format(amount));
            SoundUtil.play(target, "coins-add");
            return true;
        }

        // /coins enviar <jogador> <valor>
        if (args[0].equalsIgnoreCase("enviar") && args.length == 3) {
            if (!(sender instanceof Player)) { messages.send(sender, "transfer-only-players"); return true; }
            if (!sender.hasPermission("scoins.use.enviar")) { messages.send(sender, "no-permission"); return true; }
            Player player = (Player) sender;
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { messages.send(sender, "player-not-found"); return true; }
            if (target.getUniqueId().equals(player.getUniqueId())) { messages.send(sender, "transfer-self"); return true; }

            // Verifica se o alvo está aceitando coins
            if (!toggleManager.canReceive(target.getUniqueId())) {
                messages.send(sender, "transfer-blocked-toggle", "{player}", target.getName());
                SoundUtil.play(player, "error");
                return true;
            }

            if (cooldownManager.isOnCooldown(player.getUniqueId())) {
                messages.send(sender, "transfer-cooldown", "{time}", String.valueOf(cooldownManager.getRemainingSeconds(player.getUniqueId())));
                SoundUtil.play(player, "error");
                return true;
            }
            long amount = parseAmount(sender, args[2]);
            if (amount < 0) return true;
            if (amount < minTransfer) {
                messages.send(sender, "transfer-min", "{min}", CoinsFormatter.format(minTransfer));
                SoundUtil.play(player, "error");
                return true;
            }

            // Usa transferCoins() ATÔMICO — elimina o TOCTOU onde dois envios simultâneos
            // para o mesmo alvo passam no check individualmente mas juntos ultrapassam maxCoins.
            CoinsManager.TransferStatus status = coinsManager.transferCoins(
                    player.getUniqueId(), target.getUniqueId(), amount);

            switch (status) {
                case NOT_ENOUGH_COINS:
                    messages.send(sender, "transfer-no-balance");
                    SoundUtil.play(player, "error");
                    return true;
                case TARGET_MAX:
                    messages.send(sender, "transfer-target-max", "{player}", target.getName());
                    SoundUtil.play(player, "error");
                    return true;
                case INVALID:
                    messages.send(sender, "invalid-amount");
                    SoundUtil.play(player, "error");
                    return true;
                default: // SUCCESS
                    break;
            }

            cooldownManager.setCooldown(player.getUniqueId());
            transactionManager.record(player.getUniqueId(), TransactionType.SENT, amount, target.getName());
            transactionManager.record(target.getUniqueId(), TransactionType.RECEIVED, amount, player.getName());
            fileStorage.saveAsync(player.getUniqueId());
            fileStorage.saveAsync(target.getUniqueId());
            messages.send(player, "transfer-success-sender", "{coins}", CoinsFormatter.format(amount), "{player}", target.getName());
            messages.send(target, "transfer-success-target", "{coins}", CoinsFormatter.format(amount), "{player}", player.getName());
            SoundUtil.play(player, "transfer-send");
            SoundUtil.play(target, "transfer-receive");
            return true;
        }

        // /coins reload
        if (args[0].equalsIgnoreCase("reload") && args.length == 1) {
            if (!sender.hasPermission("scoins.admin.reload")) { messages.send(sender, "no-permission"); return true; }
            plugin.reload();
            sender.sendMessage("§6§lCOINS §ePlugin recarregado com sucesso.");
            // Avisa se o storage-type foi alterado e requer restart
            String newType = plugin.getConfig().getString("storage-type", "yaml").toLowerCase();
            boolean nowDb = newType.equals("mysql");
            if (nowDb != fileStorage.isUsingDatabase()) {
                sender.sendMessage("§c§l⚠ ATENÇÃO: §cVocê alterou o §fstorage-type §cpara §f" + newType + "§c.");
                sender.sendMessage("§cEssa mudança só tem efeito após §lreiniciar o servidor§c. Use §f/stop§c e inicie novamente.");
            }
            return true;
        }

        // /coins db status
        if (args[0].equalsIgnoreCase("db") && args.length == 2 && args[1].equalsIgnoreCase("status")) {
            if (!sender.hasPermission("scoins.admin.db")) { messages.send(sender, "no-permission"); return true; }

            if (!fileStorage.isUsingDatabase()) {
                sender.sendMessage("§6§lCOINS §eBackend atual: §bYAML §7(coins.yml)");
                sender.sendMessage("§7Para usar MySQL, altere §fstorage-type: mysql §7na config.yml.");
                return true;
            }

            var db = fileStorage.getDatabaseManager();
            sender.sendMessage("§6§lCOINS §e— Status do Banco de Dados");
            sender.sendMessage("§7Backend: §fMySQL §7(HikariCP)");
            sender.sendMessage("§7Saúde: " + (db.isHealthy() ? "§a✔ Online" : "§c✘ Offline / sem resposta"));
            sender.sendMessage("§7Pool: " + db.getPoolStatus());
            return true;
        }

        // /coins npc <set|remove|reload> [1|2|3]
        if (args[0].equalsIgnoreCase("npc")) {
            if (!sender.hasPermission("scoins.admin.npc")) { messages.send(sender, "no-permission"); return true; }
            if (npcManager == null) {
                sender.sendMessage("§c[sCoins] Sistema de NPCs desativado (Citizens não encontrado).");
                return true;
            }

            if (args.length == 3) {
                int pos;
                try {
                    pos = Integer.parseInt(args[2]);
                    if (pos < 1 || pos > 3) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c[sCoins] Posição inválida. Use 1, 2 ou 3.");
                    return true;
                }

                if (args[1].equalsIgnoreCase("set")) {
                    if (!(sender instanceof Player)) { messages.send(sender, "only-players"); return true; }
                    Player player = (Player) sender;
                    boolean ok = npcManager.setPosition(pos, player.getLocation());
                    if (!ok) {
                        sender.sendMessage("§c§lCOINS §cNão há nenhum jogador no top " + pos + " ainda. Adicione coins a jogadores primeiro.");
                    } else {
                        sender.sendMessage("§6[sCoins] §eNPC top §6" + pos + " §eposicionado aqui com sucesso.");
                    }
                    return true;
                }

                if (args[1].equalsIgnoreCase("remove")) {
                    npcManager.removePosition(pos);
                    sender.sendMessage("§6[sCoins] §eNPC top §6" + pos + " §eremovido.");
                    return true;
                }
            }

            // /coins npc reload
            if (args.length == 2 && args[1].equalsIgnoreCase("reload")) {
                npcManager.reloadAll();
                sender.sendMessage("§6[sCoins] §eNPCs removidos. Respawnando em §a10 segundos§e...");
                return true;
            }

            sender.sendMessage("§6[sCoins] §eUso dos NPCs:\n" +
                    " §7/coins npc set <1|2|3> §e— Posicionar NPC onde você está\n" +
                    " §7/coins npc remove <1|2|3> §e— Remover NPC\n" +
                    " §7/coins npc reload §e— Atualizar skins e holograms");
            return true;
        }

        // /coins <jogador> ou /coins help/ajuda
        if (args.length == 1) {
            String arg = args[0].toLowerCase();
            if (arg.equals("help") || arg.equals("ajuda")) { messages.send(sender, "help"); return true; }

            // Jogador online — consulta em memória, sem I/O
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                long coins = coinsManager.getCoins(target.getUniqueId());
                messages.send(sender, "balance-other-online", "{player}", target.getName(), "{coins}", CoinsFormatter.format(coins));
                return true;
            }

            // Jogador offline — consulta assíncrona para NÃO bloquear a main thread.
            // Bukkit.getOfflinePlayer(nome) pode disparar requisição à API Mojang se o jogador
            // nunca entrou no servidor, causando TPS drop de vários segundos.
            final String searchName = args[0];
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                @SuppressWarnings("deprecation")
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(searchName);
                if (!offlinePlayer.hasPlayedBefore()) {
                    plugin.getServer().getScheduler().runTask(plugin,
                        () -> messages.send(sender, "never-joined"));
                    return;
                }
                long coinsOffline = fileStorage.getCoinsOffline(offlinePlayer.getUniqueId());
                String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : searchName;
                plugin.getServer().getScheduler().runTask(plugin,
                    () -> messages.send(sender, "balance-other-offline",
                            "{player}", playerName,
                            "{coins}", CoinsFormatter.format(coinsOffline)));
            });
            return true;
        }

        messages.send(sender, "help");
        return true;
    }

    /**
     * Parseia um valor de coins de forma segura.
     * Retorna -1 e envia mensagem de erro se inválido.
     */
    private long parseAmount(CommandSender sender, String input) {
        long amount;
        try {
            amount = Long.parseLong(input.trim());
        } catch (NumberFormatException e) {
            messages.send(sender, "invalid-number");
            return -1L;
        }
        if (amount <= 0) { messages.send(sender, "invalid-amount"); return -1L; }
        return amount;
    }
}
