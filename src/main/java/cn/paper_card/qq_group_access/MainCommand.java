package cn.paper_card.qq_group_access;

import cn.paper_card.mc_command.TheMcCommand;
import cn.paper_card.qq_group_member_info.api.QqGroupMemberInfo;
import cn.paper_card.qq_group_member_info.api.QqGroupMemberInfoApi;
import cn.paper_card.qq_group_member_info.api.QqGroupMemberInfoService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

    private final @NotNull ThePlugin plugin;
    private final @NotNull Permission permission;

    public MainCommand(@NotNull ThePlugin plugin) {
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
        this.addSubCommand(new Test());
    }

    @Override
    protected boolean canNotExecute(@NotNull CommandSender commandSender) {
        return !commandSender.hasPermission(this.permission);
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
            plugin.getConfigManager().reload();

            final TextComponent.Builder text = Component.text();
            plugin.appendPrefix(text);
            text.appendSpace();
            text.append(Component.text("已重载配置").color(NamedTextColor.GREEN));
            commandSender.sendMessage(text.build());

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
                    plugin.sendError(commandSender, "你没有权限查看QQ机器人ID！");
                    return true;
                }

                final long botId = plugin.getConfigManager().getBotId();

                final TextComponent.Builder text = Component.text();
                plugin.appendPrefix(text);
                text.appendSpace();
                text.append(Component.text("当前所使用的QQ机器人为: %d".formatted(botId)).color(NamedTextColor.GREEN));

                commandSender.sendMessage(text.build());

                return true;
            }

            if (!commandSender.hasPermission(this.permSet)) {
                plugin.sendError(commandSender, "你没有权限设置QQ机器人ID！");
                return true;
            }

            final long botId;

            try {
                botId = Long.parseLong(argId);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是一个正确的QQ号码！".formatted(argId));
                return true;
            }

            plugin.getConfigManager().setBotId(botId);
            plugin.getConfigManager().save();

            final TextComponent.Builder text = Component.text();
            plugin.appendPrefix(text);
            text.appendSpace();
            text.append(Component.text("已将QQ机器人ID设置为: %d".formatted(plugin.getConfigManager().getBotId())).color(NamedTextColor.GREEN));

            commandSender.sendMessage(text.build());

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
                    plugin.sendError(commandSender, "你没有权限查看%sID！".formatted(name));
                    return true;
                }

                final long id = this.isMain ? plugin.getConfigManager().getMainGroupId() : plugin.getConfigManager().getAuditGroupId();

                final TextComponent.Builder text = Component.text();
                plugin.appendPrefix(text);
                text.appendSpace();
                text.append(Component.text("当前%sID为: %d".formatted(name, id)).color(NamedTextColor.GREEN));

                commandSender.sendMessage(text.build());

                return true;
            }

            if (!commandSender.hasPermission(this.permSet)) {
                plugin.sendError(commandSender, "你没有权限设置%sID！".formatted(name));
                return true;
            }

            final long id;

            try {
                id = Long.parseLong(argId);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是一个正确的QQ群号！".formatted(argId));
                return true;
            }

            if (this.isMain) {
                plugin.getConfigManager().setMainGroupId(id);
            } else {
                plugin.getConfigManager().setAuditGroupId(id);
            }
            plugin.getConfigManager().save();

            final TextComponent.Builder text = Component.text();
            plugin.appendPrefix(text);
            text.appendSpace();
            text.append(Component.text("已将%sID设置为: %d".formatted(name, id)).color(NamedTextColor.GREEN));
            commandSender.sendMessage(text.build());

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


            if (argQq == null) {
                plugin.sendError(commandSender, "你必须提供参数：QQ号");
                return true;
            }

            final long qq;
            try {
                qq = Long.parseLong(argQq);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是正确的QQ号".formatted(argQq));
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final long mainGroupId = plugin.getConfigManager().getMainGroupId();
                for (Bot bot : Bot.getInstances()) {
                    if (bot == null) continue;
                    if (!bot.isOnline()) continue;


                    final net.mamoe.mirai.contact.Group group = bot.getGroup(mainGroupId);
                    if (group == null) continue;

                    final NormalMember normalMember = group.get(qq);
                    if (normalMember == null) {
                        plugin.sendError(commandSender, "QQ群[%d]中不包含成员QQ[%d]".formatted(mainGroupId, qq));
                        return;
                    }

                    final TextComponent.Builder text = Component.text();

                    plugin.appendPrefix(text);
                    text.append(Component.text(" ==== QQ群成员信息 ====").color(NamedTextColor.GREEN));

                    text.appendNewline();
                    text.append(Component.text("ID: %d".formatted(normalMember.getId())));

                    text.appendNewline();
                    text.append(Component.text("Nick: %s".formatted(normalMember.getNick())));


                    final String avatarUrl = normalMember.getAvatarUrl();
                    text.appendNewline();
                    text.append(Component.text("AvatarUrl: "));
                    text.append(Component.text(avatarUrl).color(NamedTextColor.GREEN)
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.openUrl(avatarUrl))
                    );

                    final String nameCard = normalMember.getNameCard();
                    text.appendNewline();
                    text.append(Component.text("NameCard: %s".formatted(nameCard)));

                    final int joinTimestamp = normalMember.getJoinTimestamp();
                    text.appendNewline();
                    text.append(Component.text("JoinTimeStamp: %d".formatted(joinTimestamp)));

                    final String specialTitle = normalMember.getSpecialTitle();
                    text.appendNewline();
                    text.append(Component.text("SpecialTitle: %s".formatted(specialTitle)));

                    final String remark = normalMember.getRemark();
                    text.appendNewline();
                    text.append(Component.text("Remark: %s".formatted(remark)));

                    final String rankTitle = normalMember.getRankTitle();
                    text.appendNewline();
                    text.append(Component.text("RankTitle: %s".formatted(rankTitle)));

                    final String temperatureTitle = normalMember.getTemperatureTitle();
                    text.appendNewline();
                    text.append(Component.text("TemperatureTitle: %s".formatted(temperatureTitle)));

                    final int permissionLevel = normalMember.getPermission().getLevel();
                    text.appendNewline();
                    text.append(Component.text("PermissionLevel: %d".formatted(permissionLevel)));

                    final MemberActive active = normalMember.getActive();
                    final int temperature = active.getTemperature();
                    text.appendNewline();
                    text.append(Component.text("ActiveTemperature: %d".formatted(temperature)));


                    final int point = active.getPoint();
                    text.appendNewline();
                    text.append(Component.text("ActivePoint: %d".formatted(point)));

                    final int rank = active.getRank();
                    text.appendNewline();
                    text.append(Component.text("ActiveRank: %d".formatted(rank)));

                    final UserProfile userProfile = normalMember.queryProfile();
                    final int age = userProfile.getAge();

                    text.appendNewline();
                    text.append(Component.text("UserProfile.Age: %d".formatted(age)));


                    final String email = userProfile.getEmail();
                    text.appendNewline();
                    text.append(Component.text("UserProfile.Email: %s".formatted(email)));

                    final String sign = userProfile.getSign();
                    text.appendNewline();
                    text.append(Component.text("UserProfile.Sign: %s".formatted(sign)));

                    final UserProfile.Sex sex = userProfile.getSex();
                    final int ordinal = sex.ordinal();
                    final String name = sex.name();
                    text.appendNewline();
                    text.append(Component.text("UserProfile.Sex: %s(%d)".formatted(name, ordinal)));

                    final int qLevel = userProfile.getQLevel();
                    text.appendNewline();
                    text.append(Component.text("UserProfile:QLevel: %d".formatted(qLevel)));

                    final String nickname = userProfile.getNickname();
                    text.appendNewline();
                    text.append(Component.text("UserProfile:NickName: %s".formatted(nickname)));

                    final int friendGroupId = userProfile.getFriendGroupId();
                    text.appendNewline();
                    text.append(Component.text("UserProfile.FriendGroupId: %d".formatted(friendGroupId)));

                    commandSender.sendMessage(text.build());

                    // 更新信息
                    final QqGroupMemberInfoApi api = plugin.getQqGroupMemberInfoApi();
                    if (api != null) {
                        final QqGroupMemberInfoService service = api.getQqGroupMemberInfoService();
                        try {
                            final boolean added = service.addOrUpdateByQq(new QqGroupMemberInfo(
                                    normalMember.getId(),
                                    normalMember.getNick(),
                                    normalMember.getNameCard(),
                                    System.currentTimeMillis(),
                                    true,
                                    userProfile.getQLevel()
                            ));

                            final TextComponent.Builder t = Component.text();
                            plugin.appendPrefix(t);
                            t.appendSpace();
                            t.append(Component.text("已%sQQ群成员信息".formatted(
                                    added ? "添加" : "更新"
                            )).color(NamedTextColor.GREEN));

                            commandSender.sendMessage(t.build());

                        } catch (Exception e) {
                            plugin.handleException(e);
                        }
                    }

                    return;

                } // end for

                plugin.sendError(commandSender, "没有任何一个机器人能访问QQ群[%d]".formatted(mainGroupId));
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

    class Test extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Test() {
            super("test");
            this.permission = plugin.addPermission(MainCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                try {
                    plugin.createMainGroupAccess();
                    plugin.sendInfo(commandSender, "可以访问主群");
                } catch (Exception e) {
                    plugin.sendException(commandSender, e);
                }

                try {
                    plugin.createAuditGroupAccess();
                    plugin.sendInfo(commandSender, "可以访问审核群");
                } catch (Exception e) {
                    plugin.sendException(commandSender, e);
                }

            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }
}
