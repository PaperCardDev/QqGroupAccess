package cn.paper_card.qq_group_access;

import cn.paper_card.player_qq_bind.QqBindApi;
import cn.paper_card.player_qq_group_remark.PlayerQqGroupRemarkApi;
import cn.paper_card.player_qq_in_group.PlayerQqInGroupApi;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.Member;
import net.mamoe.mirai.contact.NormalMember;
import net.mamoe.mirai.contact.PermissionDeniedException;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.events.BotOnlineEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.MemberJoinEvent;
import net.mamoe.mirai.event.events.MemberLeaveEvent;
import net.mamoe.mirai.message.data.At;
import net.mamoe.mirai.message.data.MessageChainBuilder;
import net.mamoe.mirai.message.data.PlainText;
import net.mamoe.mirai.message.data.QuoteReply;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("unused")
public final class QqGroupAccess extends JavaPlugin implements QqGroupAccessApi, Listener {

    private QqBindApi qqBindApi = null;
    private PlayerQqGroupRemarkApi playerQqGroupRemarkApi = null;

    private PlayerQqInGroupApi playerQqInGroupApi = null;

    private final @NotNull Object lock = new Object();
    private final @NotNull TaskScheduler taskScheduler;

    private final static String PATH_BOT_ID = "bot-id";
    private final static String PATH_MAIN_GROUP_ID = "main-group-id";

    private final static String PATH_AUDIT_GROUP_ID = "audit-group-id";

    public QqGroupAccess() {
        this.taskScheduler = UniversalScheduler.getScheduler(this);
    }


    private void getQqBindApi() {
        if (this.qqBindApi == null) {
            final Plugin plugin = this.getServer().getPluginManager().getPlugin("PlayerQqBind");
            if (plugin instanceof final QqBindApi api) {
                this.qqBindApi = api;
            }

        }
    }

    private void getPlayerQqGroupRemarkApi() {
        if (this.playerQqGroupRemarkApi == null) {
            final Plugin plugin = this.getServer().getPluginManager().getPlugin("PlayerQqGroupRemark");
            if (plugin instanceof final PlayerQqGroupRemarkApi api) {
                this.playerQqGroupRemarkApi = api;
            }

        }
    }

    private void getPlayerQqInGroupApi() {
        if (this.playerQqInGroupApi == null) {
            final Plugin plugin = this.getServer().getPluginManager().getPlugin("PlayerQqInGroup");
            if (plugin instanceof final PlayerQqInGroupApi api) {
                this.playerQqInGroupApi = api;
            }

        }
    }

    void setBotId(long id) {
        this.getConfig().set(PATH_BOT_ID, id);
    }

    long getBotId() {
        return this.getConfig().getLong(PATH_BOT_ID, 0);
    }

    void setMainGroupId(long id) {
        this.getConfig().set(PATH_MAIN_GROUP_ID, id);
    }

    @Override
    public long getMainGroupId() {
        return this.getConfig().getLong(PATH_MAIN_GROUP_ID, 0);
    }

    void setAuditGroupId(long id) {
        this.getConfig().set(PATH_AUDIT_GROUP_ID, id);
    }

    @Override
    public long getAuditGroupId() {
        return this.getConfig().getLong(PATH_AUDIT_GROUP_ID, 0);
    }


    private void transportGroupMessage(@NotNull Group group, @NotNull Member member, @NotNull String message) {

        if (message.length() > 32) return;

        // 转发到游戏内
        if (this.qqBindApi != null) {
            final QqBindApi.BindInfo bindInfo;
            try {
                bindInfo = this.qqBindApi.queryByQq(member.getId());
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            if (bindInfo == null || bindInfo.uuid() == null) return;

            final String name = getServer().getOfflinePlayer(bindInfo.uuid()).getName();

            if (name == null) return;

            this.taskScheduler.runTask(() -> getServer().broadcast(Component.text()
                    .append(Component.text("<"))
                    .append(Component.text(name))
                    .append(Component.text("> "))
                    .append(Component.text(message))
                    .build()
            ));

        }


    }

    @Override
    public void onEnable() {

        this.getServer().getPluginManager().registerEvents(this, this);

        final PluginCommand command = this.getCommand("qq-group-access");
        final MainCommand mainCommand = new MainCommand(this);
        assert command != null;
        command.setTabCompleter(mainCommand);
        command.setExecutor(mainCommand);

        this.setMainGroupId(this.getMainGroupId());
        this.setAuditGroupId(this.getAuditGroupId());
        this.setBotId(this.getBotId());
        this.saveConfig();

        this.getQqBindApi();
        this.getPlayerQqGroupRemarkApi();
        this.getPlayerQqInGroupApi();

        GlobalEventChannel.INSTANCE.subscribeAlways(GroupMessageEvent.class, event -> {
            if (event.getBot().getId() != this.getBotId()) return;

            final Group group = event.getGroup();
            if (group.getId() != this.getMainGroupId()) return;

            final Member sender = event.getSender();
            final String messageStr = event.getMessage().contentToString();


            if (this.playerQqGroupRemarkApi != null) {
                this.playerQqGroupRemarkApi.updateRemarkByGroupMessage(sender.getId(), sender.getNameCard());
            }

            this.transportGroupMessage(group, sender, messageStr);

            if (this.qqBindApi != null) {
                final List<String> reply = this.qqBindApi.onMainGroupMessage(sender.getId(), messageStr);
                if (reply != null) {
                    for (final String s : reply) {
                        group.sendMessage(new MessageChainBuilder()
                                .append(new QuoteReply(event.getMessage()))
                                .append(new At(sender.getId()))
                                .append(new PlainText(" "))
                                .append(new PlainText(s))
                                .build());
                    }
                }
            }
        });

        GlobalEventChannel.INSTANCE.subscribeAlways(MemberJoinEvent.class, event -> {
            if (event.getBot().getId() != this.getBotId()) return;

            if (event.getGroupId() != this.getMainGroupId()) return;
            if (this.playerQqInGroupApi != null) {
                this.playerQqInGroupApi.onMemberJoinGroup(event.getMember().getId());
            }
        });

        GlobalEventChannel.INSTANCE.subscribeAlways(MemberLeaveEvent.class, event -> {
            if (event.getBot().getId() != this.getBotId()) return;

            if (event.getGroupId() != this.getMainGroupId()) return;
            if (this.playerQqInGroupApi != null) {
                this.playerQqInGroupApi.onMemberQuitGroup(event.getMember().getId());
            }
        });


        GlobalEventChannel.INSTANCE.subscribeAlways(MemberJoinEvent.class, event -> {
            if (event.getBot().getId() != this.getBotId()) return;

            if (event.getGroupId() == getAuditGroupId()) {
                final String msg = """
                        \n欢迎来到PaperCard服务器审核群~
                        -
                        你只需要完成以下两件事情就可以游玩本服务器：
                        1. 查看【游玩手册】并填写简单的【白名单审核问卷】
                        2. 给我们的【宣传视频】三连支持，并发送【三连截图】到本群中，然后@管理审核
                        -
                        游玩手册：https://docs.qq.com/doc/DV0VmWXVSVVBadWVW
                        -
                        白名单审核问卷：https://docs.qq.com/form/page/DV2x3TnVqYnVpZHBW
                        -
                        当前B站宣传视频：https://www.bilibili.com/video/BV13s4y1F7xh
                        -
                        目前服务器MC版本为：%s。
                        -
                        在填写审核问卷后，在得到审核结果之前，请勿退出审核群，否则问卷【作废处理】
                        -
                        请在入群后【三天内】提交审核问卷和三连截图，三天后你将会被自动踢出此群。
                        -
                        有其它疑问可以查看游玩手册、群公告或咨询管理员~""".formatted(this.getServer().getMinecraftVersion());

                final Group group = event.getGroup();
                group.sendMessage(new MessageChainBuilder()
                        .append(new At(event.getMember().getId()))
                        .append(msg)
                        .build());
                return;
            }

            if (event.getGroupId() == getMainGroupId()) {
                final String msg = """
                        \n欢迎新伙伴入裙~
                        请查看群公告【新人必看】
                        祝您游戏愉快~
                        """;
                final Group group = event.getGroup();
                group.sendMessage(new MessageChainBuilder()
                        .append(new At(event.getMember().getId()))
                        .append(msg)
                        .build());
            }

        });

        GlobalEventChannel.INSTANCE.subscribeAlways(BotOnlineEvent.class, event -> {
            if (event.getBot().getId() != this.getBotId()) return;

            final int currentTick = this.getServer().getCurrentTick();
            if (currentTick < 20 * 60 * 5) {
                final GroupAccess mainGroupAccess;
                try {
                    mainGroupAccess = getMainGroupAccess();
                } catch (Exception e) {
                    this.getLogger().warning(e.toString());
                    return;
                }

                mainGroupAccess.sendNormalMessage("服务器已经启动啦~");
            }
        });

    }

    @Override
    public void onDisable() {
        this.saveConfig();
    }


    @Override
    public @NotNull GroupAccess getMainGroupAccess() throws Exception {
        final long mainGroupId = this.getMainGroupId();
        final long botId = this.getBotId();

        if (botId == 0) throw new Exception("没有配置用于访问QQ群的QQ机器人账号！");
        if (mainGroupId == 0) throw new Exception("没有配置主群的QQ群号！");

        synchronized (this.lock) {


            final Bot bot = Bot.findInstance(botId);

            if (bot == null) {
                throw new Exception("QQ机器人[%d]未登录！".formatted(botId));
            }

            if (!bot.isOnline()) {
                throw new Exception("QQ机器人[%d]离线！".formatted(botId));
            }


            final Group group = bot.getGroup(mainGroupId);

            if (group == null) {
                throw new Exception("QQ机器人[%d]无法访问QQ主群[%d]，请手动邀请QQ机器人入群！".formatted(botId, mainGroupId));
            }

            return new GroupAccess() {
                @Override
                public boolean hasMember(long qq) {
                    final NormalMember normalMember = group.get(qq);
                    return normalMember != null;
                }

                @Override
                public void setGroupMemberRemark(long qq, @NotNull String remark) throws Exception {
                    final NormalMember normalMember = group.get(qq);
                    if (normalMember == null) throw new Exception("群里没有该成员：%d".formatted(qq));

                    try {
                        normalMember.setNameCard(remark);
                    } catch (PermissionDeniedException e) {
                        throw new Exception(e);
                    }
                }

                @Override
                public void sendNormalMessage(@NotNull String message) {
                    group.sendMessage(message);
                }

                @Override
                public void sendAtMessage(long qq, @NotNull String message) {
                    group.sendMessage(new MessageChainBuilder()
                            .append(new At(qq))
                            .append(new PlainText(" "))
                            .append(new PlainText(message))
                            .build());
                }
            };
        }
    }

    @Override
    public @NotNull GroupAccess getAuditGroupAccess() throws Exception {
        final long auditGroupId = this.getAuditGroupId();
        final long botId = this.getBotId();

        if (botId == 0) throw new Exception("没有配置用于访问QQ群的QQ机器人账号！");
        if (auditGroupId == 0) throw new Exception("没有配置审核群的QQ群号！");

        synchronized (this.lock) {

            final Bot bot = Bot.findInstance(botId);

            if (bot == null) {
                throw new Exception("QQ机器人[%d]未登录！".formatted(botId));
            }

            if (!bot.isOnline()) {
                throw new Exception("QQ机器人[%d]离线！".formatted(botId));
            }


            final Group group = bot.getGroup(auditGroupId);

            if (group == null) {
                throw new Exception("QQ机器人[%d]无法访问QQ群[%d]，请手动邀请QQ机器人入群！".formatted(botId, auditGroupId));
            }

            return new GroupAccess() {
                @Override
                public boolean hasMember(long qq) {
                    final NormalMember normalMember = group.get(qq);
                    return normalMember != null;
                }

                @Override
                public void setGroupMemberRemark(long qq, @NotNull String remark) throws Exception {
                    final NormalMember normalMember = group.get(qq);
                    if (normalMember == null) throw new Exception("群里没有该成员：%d".formatted(qq));

                    try {
                        normalMember.setNameCard(remark);
                    } catch (PermissionDeniedException e) {
                        throw new Exception(e);
                    }
                }

                @Override
                public void sendNormalMessage(@NotNull String message) {
                    group.sendMessage(message);
                }

                @Override
                public void sendAtMessage(long qq, @NotNull String message) {
                    group.sendMessage(new MessageChainBuilder()
                            .append(new At(qq))
                            .append(new PlainText(" "))
                            .append(new PlainText(message))
                            .build());
                }
            };
        }
    }

    @EventHandler
    public void onChat(@NotNull AsyncChatEvent event) {
        final Component message = event.message();
        if (message instanceof final TextComponent textComponent) {
            final GroupAccess mainGroupAccess;
            try {
                mainGroupAccess = getMainGroupAccess();
            } catch (Exception e) {
                getLogger().warning(e.toString());
                return;
            }

            mainGroupAccess.sendNormalMessage("<%s> %s".formatted(event.getPlayer().getName(), textComponent.content()));
        }
    }

    @NotNull Permission addPermission(@NotNull String name) {
        final Permission permission = new Permission(name);
        this.getServer().getPluginManager().addPermission(permission);
        return permission;
    }
}
