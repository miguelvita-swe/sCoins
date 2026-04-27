package com.skyy.coins.commands;

import com.skyy.coins.npc.NpcManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CoinsTabCompleter implements TabCompleter {

    private final NpcManager npcManager;

    public CoinsTabCompleter(NpcManager npcManager) {
        this.npcManager = npcManager;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            // Subcomandos de todos os jogadores
            options.addAll(Arrays.asList("enviar", "historico", "extrato", "toggle", "top", "help", "ajuda"));
            // Subcomandos de admin
            if (sender.hasPermission("scoins.admin.add"))      options.add("add");
            if (sender.hasPermission("scoins.admin.remove"))   options.add("remove");
            if (sender.hasPermission("scoins.admin.set"))      options.add("setar");
            if (sender.hasPermission("scoins.admin.formatar")) options.add("formatar");
            if (sender.hasPermission("scoins.admin.reload"))   options.add("reload");
            if (sender.hasPermission("scoins.admin.db"))       options.add("db");
            // Só sugere "npc" se Citizens estiver instalado E o sender tiver permissão
            if (npcManager != null && sender.hasPermission("scoins.admin.npc")) options.add("npc");
            // Nomes de jogadores online para /coins <jogador>
            Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(options::add);
            return filter(options, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("add") || sub.equals("remove") || sub.equals("enviar") || sub.equals("setar")) {
                return filter(onlineNames(), args[1]);
            }
            if (sub.equals("npc") && npcManager != null) {
                return filter(Arrays.asList("set", "remove", "reload"), args[1]);
            }
            if (sub.equals("db") && sender.hasPermission("scoins.admin.db")) {
                return filter(Arrays.asList("status"), args[1]);
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("formatar")) {
                return filter(Arrays.asList("remover"), args[2]);
            }
            if (sub.equals("npc") && npcManager != null) {
                String npcSub = args[1].toLowerCase();
                if (npcSub.equals("set") || npcSub.equals("remove")) {
                    return filter(Arrays.asList("1", "2", "3"), args[2]);
                }
            }
        }

        return new ArrayList<>();
    }

    private List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    private List<String> filter(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
