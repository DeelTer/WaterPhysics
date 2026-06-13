package ru.deelter.waterphysics.command;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import ru.deelter.waterphysics.WaterPhysics;
import ru.deelter.waterphysics.engine.WaterQueue;

import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
public final class WaterCommand implements CommandExecutor, TabCompleter {

    private final WaterPhysics plugin;
    private final WaterQueue   queue;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("waterphysics.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(Component.text("[WaterPhysics] Config reloaded.", NamedTextColor.GREEN));
            }
            case "enable" -> {
                plugin.setPhysicsEnabled(true);
                sender.sendMessage(Component.text("[WaterPhysics] Water physics enabled.", NamedTextColor.GREEN));
            }
            case "disable" -> {
                plugin.setPhysicsEnabled(false);
                sender.sendMessage(Component.text("[WaterPhysics] Water physics disabled.", NamedTextColor.YELLOW));
            }
            case "stop" -> {
                queue.clear();
                sender.sendMessage(Component.text("[WaterPhysics] Queue cleared.", NamedTextColor.YELLOW));
            }
            case "status" -> {
                sender.sendMessage(Component.text()
                    .append(Component.text("[WaterPhysics] ", NamedTextColor.AQUA))
                    .append(Component.text("Status: ", NamedTextColor.WHITE))
                    .append(Component.text(plugin.isPhysicsEnabled() ? "ENABLED" : "DISABLED",
                            plugin.isPhysicsEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED))
                    .append(Component.newline())
                    .append(Component.text("  Queue size: ", NamedTextColor.WHITE))
                    .append(Component.text(queue.size(), NamedTextColor.YELLOW))
                    .build());
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text()
            .append(Component.text("──── WaterPhysics ────", NamedTextColor.AQUA))
            .append(Component.newline())
            .append(Component.text("/wp reload   ", NamedTextColor.YELLOW))
            .append(Component.text("Reload config", NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("/wp enable   ", NamedTextColor.YELLOW))
            .append(Component.text("Enable physics", NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("/wp disable  ", NamedTextColor.YELLOW))
            .append(Component.text("Disable physics", NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("/wp stop     ", NamedTextColor.YELLOW))
            .append(Component.text("Clear pending queue", NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("/wp status   ", NamedTextColor.YELLOW))
            .append(Component.text("Show plugin status", NamedTextColor.WHITE))
            .build());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "enable", "disable", "stop", "status");
        }
        return List.of();
    }
}
