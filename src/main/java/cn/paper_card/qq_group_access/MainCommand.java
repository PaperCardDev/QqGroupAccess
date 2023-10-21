package cn.paper_card.qq_group_access;

import cn.paper_card.mc_command.TheMcCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.NormalMember;
import net.mamoe.mirai.contact.active.MemberActive;
import net.mamoe.mirai.data.UserProfile;
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
        this.addSubCommand(new Member());
        this.addSubCommand(new Reload());
    }

    @Override
    protected boolean canNotExecute(@NotNull CommandSender commandSender) {
        return !commandSender.hasPermission(this.permission);
    }

    private static void sendError(@NotNull CommandSender sender, @NotNull String error) {
        sender.sendMessage(Component.text(error).color(NamedTextColor.DARK_RED));
    }

    class Reload extends TheMcCommand {

        private final Permission permission;

        protected Reload() {
            super("reload");
            this.permission = plugin.addPermission(MainCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            plugin.reloadConfig();
            commandSender.sendMessage(Component.text("已重载配置"));
            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
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

    class Member extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Member() {
            super("member");
            this.permission = plugin.addPermission(MainCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argQq = strings.length > 0 ? strings[0] : null;

            final long qq;
            if (argQq == null) {
                sendError(commandSender, "你必须提供参数：QQ号");
                return true;
            }

            try {
                qq = Long.parseLong(argQq);
            } catch (NumberFormatException e) {
                sendError(commandSender, "%s 不是正确的QQ号".formatted(argQq));
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final long mainGroupId = plugin.getMainGroupId();
                for (Bot bot : Bot.getInstances()) {
                    if (bot == null) continue;
                    if (!bot.isOnline()) continue;


                    final net.mamoe.mirai.contact.Group group = bot.getGroup(mainGroupId);
                    if (group == null) continue;

                    final NormalMember normalMember = group.get(qq);
                    if (normalMember == null) {
                        sendError(commandSender, "QQ群[%d]中不包含成员QQ[%d]".formatted(mainGroupId, qq));
                        return;
                    }

                    final TextComponent.Builder text = Component.text();

                    text.append(Component.text("ID: %d".formatted(normalMember.getId())));
                    text.appendNewline();

                    text.append(Component.text("Nick: %s".formatted(normalMember.getNick())));
                    text.appendNewline();

                    final String nameCard = normalMember.getNameCard();
                    text.append(Component.text("NameCard: %s".formatted(nameCard)));
                    text.appendNewline();

                    final int joinTimestamp = normalMember.getJoinTimestamp();
                    text.append(Component.text("JoinTimeStamp: %d".formatted(joinTimestamp)));
                    text.appendNewline();

                    final String specialTitle = normalMember.getSpecialTitle();
                    text.append(Component.text("SpecialTitle: %s".formatted(specialTitle)));
                    text.appendNewline();

                    final String remark = normalMember.getRemark();
                    text.append(Component.text("Remark: %s".formatted(remark)));
                    text.appendNewline();

                    final String rankTitle = normalMember.getRankTitle();
                    text.append(Component.text("RankTitle: %s".formatted(rankTitle)));
                    text.appendNewline();

                    final String temperatureTitle = normalMember.getTemperatureTitle();
                    text.append(Component.text("TemperatureTitle: %s".formatted(temperatureTitle)));
                    text.appendNewline();

                    final int permissionLevel = normalMember.getPermission().getLevel();
                    text.append(Component.text("PermissionLevel: %d".formatted(permissionLevel)));

                    final MemberActive active = normalMember.getActive();
                    final int temperature = active.getTemperature();
                    text.append(Component.text("ActiveTemperature: %d".formatted(temperature)));
                    text.appendNewline();

                    final int point = active.getPoint();
                    text.append(Component.text("ActivePoint: %d".formatted(point)));
                    text.appendNewline();

                    final int rank = active.getRank();
                    text.append(Component.text("ActiveRank: %d".formatted(rank)));
                    text.appendNewline();

                    final UserProfile userProfile = normalMember.queryProfile();
                    final int age = userProfile.getAge();

                    text.append(Component.text("UserProfile.Age: %d".formatted(age)));
                    text.appendNewline();

                    final String email = userProfile.getEmail();
                    text.append(Component.text("UserProfile.Email: %s".formatted(email)));
                    text.appendNewline();

                    final String sign = userProfile.getSign();
                    text.append(Component.text("UserProfile.Sign: %s".formatted(sign)));
                    text.appendNewline();

                    final UserProfile.Sex sex = userProfile.getSex();
                    text.append(Component.text("UserProfile.Sex: %s(%s)".formatted(sex.toString(), sex.getClass().getSimpleName())));
                    text.appendNewline();

                    final int qLevel = userProfile.getQLevel();
                    text.append(Component.text("UserProfile:QLevel: %d".formatted(qLevel)));
                    text.appendNewline();

                    final String nickname = userProfile.getNickname();
                    text.append(Component.text("UserProfile:NickName: %s".formatted(nickname)));
                    text.appendNewline();

                    final int friendGroupId = userProfile.getFriendGroupId();
                    text.append(Component.text("UserProfile.FriendGroupId: %d".formatted(friendGroupId)));

                    commandSender.sendMessage(text.build());
                    return;
                }

                sendError(commandSender, "没有任何一个机器人能范围QQ群[%d]".formatted(mainGroupId));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String arg = strings[0];
                if (arg.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<QQ>");
                    return list;
                }
            }
            return null;
        }
    }

}
