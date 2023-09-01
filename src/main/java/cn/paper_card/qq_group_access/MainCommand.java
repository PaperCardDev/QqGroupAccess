package cn.paper_card.qq_group_access;

import cn.paper_card.mc_command.TheMcCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mamoe.mirai.Bot;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

class MainCommand extends TheMcCommand.HasSub {

    private final @NotNull QqGroupAccess plugin;
    private final @NotNull Permission permission;

    public MainCommand(@NotNull QqGroupAccess plugin) {
        super("qq-group-access");
        this.plugin = plugin;


        final Permission p = plugin.getServer().getPluginManager().getPermission("qq-group-access.command");
        assert p != null;
        this.permission = p;

        this.addSubCommand(new BotId());
        this.addSubCommand(new Group(true));
        this.addSubCommand(new Group(false));
    }

    @Override
    protected boolean canNotExecute(@NotNull CommandSender commandSender) {
        return !commandSender.hasPermission(this.permission);
    }

    private static void sendError(@NotNull CommandSender sender, @NotNull String error) {
        sender.sendMessage(Component.text(error).color(NamedTextColor.DARK_RED));
    }

    class BotId extends TheMcCommand {

        private final @NotNull Permission permGet;
        private final @NotNull Permission permSet;

        protected BotId() {
            super("bot-id");

            this.permGet = plugin.addPermission(MainCommand.this.permission.getName() + ".bot-id.get");
            this.permSet = plugin.addPermission(MainCommand.this.permission.getName() + ".bot-id.set");
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return false;
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            // [QQ号码]

            final String argId = strings.length > 0 ? strings[0] : null;

            if (argId == null) {
                if (!commandSender.hasPermission(this.permGet)) {
                    sendError(commandSender, "你没有权限查看QQ机器人ID！");
                    return true;
                }

                final long botId = plugin.getBotId();

                commandSender.sendMessage(Component.text("当前所使用的QQ机器人为: %d".formatted(botId)));

                return true;
            }

            if (!commandSender.hasPermission(this.permSet)) {
                sendError(commandSender, "你没有权限设置QQ机器人ID！");
                return true;
            }

            final long botId;

            try {
                botId = Long.parseLong(argId);
            } catch (NumberFormatException e) {
                sendError(commandSender, "%s 不是一个正确的QQ号码！".formatted(argId));
                return true;
            }

            plugin.setBotId(botId);
            commandSender.sendMessage(Component.text("已将QQ机器人ID设置为: %d".formatted(plugin.getBotId())));

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String arg = strings[0];
                final LinkedList<String> list = new LinkedList<>();

                if (arg.isEmpty()) list.add("[QQ机器人ID]");

                final List<Bot> instances = Bot.getInstances();
                for (Bot instance : instances) {
                    final String str = "%d".formatted(instance.getId());
                    if (str.startsWith(arg)) list.add(str);
                }

                return list;
            }

            return null;
        }
    }

    class Group extends TheMcCommand {

        private final @NotNull Permission permGet;
        private final @NotNull Permission permSet;

        private final boolean isMain;

        protected Group(boolean isMain) {
            super(isMain ? "main-group-id" : "audit-group-id");
            this.isMain = isMain;

            this.permGet = plugin.addPermission(MainCommand.this.permission.getName() + ".%s.get".formatted(getLabel()));
            this.permSet = plugin.addPermission(MainCommand.this.permission.getName() + ".%s.set".formatted(getLabel()));
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return false;
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            // [QQ号码]

            final String argId = strings.length > 0 ? strings[0] : null;

            final String name = this.isMain ? "主群" : "审核群";

            if (argId == null) {
                if (!commandSender.hasPermission(this.permGet)) {
                    sendError(commandSender, "你没有权限查看%sID！".formatted(name));
                    return true;
                }

                final long id = this.isMain ? plugin.getMainGroupId() : plugin.getAuditGroupId();

                commandSender.sendMessage(Component.text("当前%sID为: %d".formatted(name, id)));

                return true;
            }

            if (!commandSender.hasPermission(this.permSet)) {
                sendError(commandSender, "你没有权限设置%sID！".formatted(name));
                return true;
            }

            final long id;

            try {
                id = Long.parseLong(argId);
            } catch (NumberFormatException e) {
                sendError(commandSender, "%s 不是一个正确的QQ群号！".formatted(argId));
                return true;
            }

            if (this.isMain) {
                plugin.setMainGroupId(id);
            } else {
                plugin.setAuditGroupId(id);
            }

            commandSender.sendMessage(Component.text("已将%sID设置为: %d".formatted(name, id)));

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String arg = strings[0];
                final LinkedList<String> list = new LinkedList<>();

                if (arg.isEmpty()) list.add("[QQ群号]");

                return list;
            }

            return null;
        }
    }
}
