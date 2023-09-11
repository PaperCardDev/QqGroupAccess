package cn.paper_card.qq_group_access;

import cn.paper_card.player_qq_bind.QqBindApi;
import cn.paper_card.player_qq_group_remark.PlayerQqGroupRemarkApi;
import cn.paper_card.player_qq_in_group.PlayerQqInGroupApi;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.TitlePart;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.*;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.events.*;
import net.mamoe.mirai.message.data.*;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@SuppressWarnings("unused")
public final class QqGroupAccess extends JavaPlugin implements QqGroupAccessApi, Listener {

    private QqBindApi qqBindApi = null;
    private PlayerQqGroupRemarkApi playerQqGroupRemarkApi = null;

    private PlayerQqInGroupApi playerQqInGroupApi = null;

    private final @NotNull Object lock = new Object();

    private final @NotNull Gpt gpt;
    private final @NotNull TaskScheduler taskScheduler;

    private final static String PATH_BOT_ID = "bot-id";
    private final static String PATH_MAIN_GROUP_ID = "main-group-id";

    private final static String PATH_AUDIT_GROUP_ID = "audit-group-id";

    private final static String MESSAGE_PREFIX_SEND_TO_GROUP = "#";

    private final @NotNull HashSet<Long> auditSendImageQq = new HashSet<>();
    private final @NotNull HashSet<Long> passAuditQq = new HashSet<>();

    private final @NotNull BlockingQueue<Runnable> messageSends;

    private MyScheduledTask myScheduledTask = null;

    public QqGroupAccess() {
        this.gpt = new Gpt();
        this.taskScheduler = UniversalScheduler.getScheduler(this);
        this.messageSends = new LinkedBlockingQueue<>();
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

    private void onMainGroupAtMessage(@NotNull At at, @NotNull Member member, @NotNull String name) {
        final long target = at.getTarget();

        if (this.qqBindApi == null) return;

        final QqBindApi.BindInfo bindInfo;

        try {
            bindInfo = this.qqBindApi.queryByQq(target);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if (bindInfo == null || bindInfo.uuid() == null) return;

        final Player player = getServer().getPlayer(bindInfo.uuid());

        if (player == null) return;
        if (!player.isOnline()) return;

        this.taskScheduler.runTask(() -> {
            player.sendTitlePart(TitlePart.TITLE, Component.text()
                    .append(Component.text(name).color(NamedTextColor.DARK_AQUA).decorate(TextDecoration.BOLD))
                    .append(Component.text(" 在群里@你").color(NamedTextColor.GOLD))
                    .build());

            player.sendTitlePart(TitlePart.SUBTITLE, Component.text("他可能找你有事").color(NamedTextColor.GREEN));
        });

    }


    private void transportGroupMessage(@NotNull Group group, @NotNull Member member, @NotNull MessageChain messageChain) {

        if (this.qqBindApi == null) return;

        // 转发到游戏内

        final QqBindApi.BindInfo bindInfo;
        try {
            bindInfo = this.qqBindApi.queryByQq(member.getId());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // QQ没有被绑定
        if (bindInfo == null || bindInfo.uuid() == null) return;

        final OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(bindInfo.uuid());

        // 获取游戏名
        final String name = offlinePlayer.getName();

        // 无法获取游戏名，应该是没进过服务器
        if (name == null) return;

        // 太久不上线了
        final long current = System.currentTimeMillis();
        if (current - offlinePlayer.getFirstPlayed() > 7 * 24 * 60 * 60 * 1000L) return;

        final StringBuilder builder = new StringBuilder();

        for (final SingleMessage singleMessage : messageChain) {
            if (singleMessage instanceof final At at) { // AT某人
                final String display = at.getDisplay(group);
                builder.append(display);
                this.onMainGroupAtMessage(at, member, name);
            } else if (singleMessage instanceof final AtAll atAll) { // AT全体
                builder.append(atAll.contentToString());
            } else if (singleMessage instanceof final PlainText plainText) { // 纯文本
                builder.append(plainText.getContent());
            } else if (singleMessage instanceof final Image image) { // 图片
                builder.append(image.contentToString());
            } else if (singleMessage instanceof final Face face) { // 表情
                builder.append(face.contentToString());
            } else if (singleMessage instanceof final VipFace vipFace) { // VIP表情
                builder.append(vipFace.contentToString());
            }
        }

        this.taskScheduler.runTask(() -> getServer().broadcast(Component.text()
                .append(Component.text("<").color(NamedTextColor.GOLD))
                .append(Component.text(name))
                .append(Component.text("> ").color(NamedTextColor.GOLD))
                .append(Component.text(builder.toString()))
                .build()));

    }

    private void onMainGroupMessage(@NotNull GroupMessageEvent event) {
        final Member sender = event.getSender();
        final String messageStr = event.getMessage().contentToString();
        final Group group = event.getGroup();

        final MessageChain message = event.getMessage();

        if (message.size() == 3) {
            final SingleMessage singleMessage = message.get(1);
            final SingleMessage singleMessage1 = message.get(2);

            if (singleMessage instanceof final At at) {
                if (at.getTarget() == event.getBot().getId()) {
                    if (singleMessage1 instanceof final PlainText plainText) {
                        this.taskScheduler.runTaskAsynchronously(() -> {
                            String resp;

                            try {
                                resp = this.gpt.request(plainText.getContent(), sender.getId());
                            } catch (IOException e) {
                                e.printStackTrace();
                                resp = e.toString();
                            }

                            group.sendMessage(new MessageChainBuilder()
                                    .append(new QuoteReply(message))
                                    .append(new At(sender.getId()))
                                    .append(new PlainText(" "))
                                    .append(new PlainText(resp))
                                    .build());
                        });
                    }
                }
            }
        }

        if (this.playerQqGroupRemarkApi != null) {
            this.playerQqGroupRemarkApi.updateRemarkByGroupMessage(sender.getId(), sender.getNameCard());
        }

        this.transportGroupMessage(group, sender, message);

        if (this.qqBindApi != null) {
            final List<String> reply = this.qqBindApi.onMainGroupMessage(sender.getId(), messageStr);
            if (reply != null) {
                for (final String s : reply) {
                    final Runnable runnable = () -> group.sendMessage(new MessageChainBuilder()
                            .append(new QuoteReply(event.getMessage()))
                            .append(new At(sender.getId()))
                            .append(new PlainText(" "))
                            .append(new PlainText(s))
                            .build());
                    if (!messageSends.offer(runnable)) runnable.run();
                }
            }
        }
    }

    private void onAuditGroupMessage(@NotNull GroupMessageEvent event) {
        final Group group = event.getGroup();
        final MessageChain message = event.getMessage();


        // 包含At
        for (SingleMessage singleMessage1 : message) {
            if (!(singleMessage1 instanceof final At at)) continue;
            if (at.getTarget() != event.getBot().getId()) continue;

            final boolean removed;
            synchronized (this.auditSendImageQq) {
                removed = this.auditSendImageQq.remove(event.getSender().getId());
            }

            if (removed) {

                final Runnable runnable = () -> group.sendMessage(new MessageChainBuilder()
                        .append(new At(event.getSender().getId()))
                        .append(" 已将入群方式通过私信发送给你啦~\n如果没有收到，可以联系管理员~")
                        .build());

                if (!messageSends.offer(runnable)) runnable.run();

                synchronized (this.passAuditQq) {
                    this.passAuditQq.add(event.getSender().getId());
                }

                final Member sender = event.getSender();

                final Runnable runnable1 = () -> sender.sendMessage("""
                        你好呀！我是PaperCard机器人喵喵~
                        恭喜你已经通过了审核~
                        我们的QQ主群为：%d
                        期待你的加入~""".formatted(this.getMainGroupId()));

                if (!messageSends.offer(runnable1)) runnable1.run();

            } else {

                final Runnable runnable = () ->
                        group.sendMessage(new MessageChainBuilder()
                                .append(new QuoteReply(message))
                                .append(new At(event.getSender().getId()))
                                .append(" 先发送三连截图到群里，再@我噢~")
                                .build());

                if (!messageSends.offer(runnable)) runnable.run();
            }

            return;
        }

        if (message.size() < 2) return;

        final SingleMessage singleMessage = message.get(1);

        if (singleMessage instanceof final Image image) {
            // 发送一张图片
            final ImageType imageType = image.getImageType();
            getLogger().info("DEBUG: ImageType: %s".formatted(imageType.name()));

            synchronized (this.auditSendImageQq) {
                this.auditSendImageQq.add(event.getSender().getId());
            }

        }
    }

    private void onFriendMessage() {

        GlobalEventChannel.INSTANCE.subscribeAlways(FriendMessageEvent.class, event -> {
            final Friend sender = event.getSender();
            final String message = event.getMessage().contentToString();

            this.taskScheduler.runTaskAsynchronously(() -> {
                final String resp;

                try {
                    resp = this.gpt.request(message, sender.getId());
                } catch (IOException e) {
                    sender.sendMessage(e.toString());
                    return;
                }

                sender.sendMessage(resp);
            });
        });
    }

    private void onGroupMessage() {
        GlobalEventChannel.INSTANCE.subscribeAlways(GroupMessageEvent.class, event -> {
            final long botId = this.getBotId();
            if (event.getBot().getId() != botId) return;

            if (event.getGroup().getId() == this.getMainGroupId()) this.onMainGroupMessage(event);
            if (event.getGroup().getId() == this.getAuditGroupId()) this.onAuditGroupMessage(event);

        });
    }

    private @NotNull String getLeaveMemberName(long qq) {
        if (qqBindApi == null) return "无法访问QQ绑定API！";


        final QqBindApi.BindInfo bindInfo;
        try {
            bindInfo = qqBindApi.queryByQq(qq);
        } catch (Exception e) {
            e.printStackTrace();
            return e.toString();
        }

        if (bindInfo == null || bindInfo.uuid() == null) {
            return "未绑定";
        }


        final OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(bindInfo.uuid());

        final String name1 = offlinePlayer.getName();

        if (name1 != null) return name1;

        return offlinePlayer.getUniqueId().toString();
    }

    private void onMemberLeave() {
        GlobalEventChannel.INSTANCE.subscribeAlways(MemberLeaveEvent.class, event -> {
            if (event.getBot().getId() != this.getBotId()) return;

            if (event.getGroupId() != this.getMainGroupId()) return;

            if (this.playerQqInGroupApi != null)
                this.playerQqInGroupApi.onMemberQuitGroup(event.getMember().getId());


            final Group group = event.getGroup();
            final Member member = event.getMember();

            final Runnable runnable = () -> {
                final long id = member.getId();
                final String name = getLeaveMemberName(id);


                group.sendMessage("%s (%d) 离开了群聊，无白名单\n游戏名：%s".formatted(member.getNick(), member.getId(), name));
            };

            if (!messageSends.offer(runnable)) runnable.run();

        });
    }

    private void onMemberJoin() {
        GlobalEventChannel.INSTANCE.subscribeAlways(MemberJoinEvent.class, event -> {
            if (event.getBot().getId() != this.getBotId()) return;

            if (event.getGroupId() == getAuditGroupId()) { // 进入审核群

                final Runnable runnable = () -> {
                    final String msg = """
                            \n欢迎来到PaperCard服务器审核群~
                            -
                            你只需要完成以下一件事情就可以游玩本服务器：
                            给我们的【宣传视频】三连支持，并发送【三连截图】到本群中，然后@我就可以啦
                            -
                            PS: 作为一个公益服，宣传不易，您的三连就是对我们最大的支持~
                            -
                            了解服务器的更多信息，请查看游玩手册：https://docs.qq.com/doc/DV0VmWXVSVVBadWVW
                            -
                            当前B站宣传视频：https://www.bilibili.com/video/BV1wP41187Yd
                            -
                            目前服务器MC版本为：%s。
                            -
                            如果你没有自己的客户端的话，可下载QQ群文件里的整合包
                            -
                            在进入主群之前请勿退出审核群，否则就无法发送通知给你啦~
                            -
                            有其它疑问可以查看游玩手册、群公告或咨询管理员~""".formatted(getServer().getMinecraftVersion());

                    final Group group = event.getGroup();
                    group.sendMessage(new MessageChainBuilder()
                            .append(new At(event.getMember().getId()))
                            .append(msg)
                            .build());
                };

                boolean offer = messageSends.offer(runnable);

                if (!offer) runnable.run();

                return;
            }

            if (event.getGroupId() == getMainGroupId()) { // 进入主群

                if (event.getGroupId() != this.getMainGroupId()) return;
                if (this.playerQqInGroupApi != null) {
                    this.playerQqInGroupApi.onMemberJoinGroup(event.getMember().getId());
                }

                final Runnable runnable = () -> {
                    final String msg = """
                            \n欢迎新伙伴入裙~
                            请查看群公告【新人必看】（服务器地址在这里噢）
                            祝您游戏愉快~""";

                    final Group group = event.getGroup();

                    group.sendMessage(new MessageChainBuilder()
                            .append(new At(event.getMember().getId()))
                            .append(msg)
                            .build());

                };

                final boolean offer = messageSends.offer(runnable);
                if (!offer) runnable.run();

                // 如果在审核群里，提示退出审核群
                final GroupAccess auditGroupAccess;
                try {
                    auditGroupAccess = createAuditGroupAccess();
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }

                auditGroupAccess.sendAtMessage(event.getMember().getId(), "恭喜你已经进入主群，现在可以退出审核群啦~");
            }
        });
    }

    private void onBotLogin() {

        GlobalEventChannel.INSTANCE.subscribeAlways(BotOnlineEvent.class, event -> {
            if (event.getBot().getId() != this.getBotId()) return;

            final GroupAccess mainGroupAccess;
            try {
                mainGroupAccess = createMainGroupAccess();
            } catch (Exception e) {
                getLogger().warning(e.toString());
                return;
            }

            mainGroupAccess.sendNormalMessage("QQ机器人登录成功，服务器已经启动啦~");

        });
    }

    private void onJoinRequest() {
        GlobalEventChannel.INSTANCE.subscribeAlways(MemberJoinRequestEvent.class, event -> {
            if (event.getBot().getId() != this.getBotId()) return;
            if (event.getGroupId() != this.getMainGroupId()) return;

            final Group group = event.getGroup();
            if (group == null) {
                getLogger().info("DEBUG: group == null!");
                return;
            }

            // 进入主群的申请

            final long fromId = event.getFromId();
            final String fromNick = event.getFromNick();
            final Long invitorId = event.getInvitorId();

            getLogger().info("入群申请 {fromId: %d, fromNick: %s, invitorId: %s}".formatted(
                    fromId, fromNick, invitorId
            ));

            // 查询QQ绑定
            if (this.qqBindApi != null) {
                try {
                    final QqBindApi.BindInfo bindInfo = this.qqBindApi.queryByQq(fromId);

                    if (bindInfo != null && bindInfo.uuid() != null) {


                        final Runnable runnable = () -> {
                            String name = getServer().getOfflinePlayer(bindInfo.uuid()).getName();
                            if (name == null) name = bindInfo.uuid().toString();

                            group.sendMessage("""
                                    自动同意老玩家入群：
                                    游戏名：%s
                                    QQ: %s (%d)
                                    """.formatted(
                                    name, fromNick, fromId
                            ));
                        };

                        final boolean offer = messageSends.offer(runnable);
                        if (!offer) runnable.run();

                        event.accept();
                        return;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    group.sendMessage(e.toString());
                }
            }

            // 查询过审表
            final boolean contains;
            synchronized (this.passAuditQq) {
                contains = this.passAuditQq.contains(fromId);
            }

            if (contains) {
                final Runnable runnable = () -> group.sendMessage("""
                        自动同意过审玩家入群：
                        QQ: %s (%d)""".formatted(fromNick, fromId));

                if (!messageSends.offer(runnable)) runnable.run();

                event.accept();
                return;
            }


            final Runnable runnable = () -> group.sendMessage("""
                    无法自动处理的入群申请：
                    FromId: %d
                    FromNick: %s
                    InvitorId: %s"""
                    .formatted(
                            fromId,
                            fromNick,
                            invitorId
                    )
            );

            if (!messageSends.offer(runnable)) runnable.run();
        });
    }

    @Override
    public void onEnable() {

        this.getServer().getPluginManager().registerEvents(this, this);

        // 注册命令
        final PluginCommand command = this.getCommand("qq-group-access");
        final MainCommand mainCommand = new MainCommand(this);
        assert command != null;
        command.setTabCompleter(mainCommand);
        command.setExecutor(mainCommand);

        // 保存配置文件
        this.setMainGroupId(this.getMainGroupId());
        this.setAuditGroupId(this.getAuditGroupId());
        this.setBotId(this.getBotId());
        this.saveConfig();

        // 获取其它插件接口
        this.getQqBindApi();
        this.getPlayerQqGroupRemarkApi();
        this.getPlayerQqInGroupApi();

        // 好友消息的处理
        this.onFriendMessage();

        // 群消息的处理
        this.onGroupMessage();

        // 退群事件的处理
        this.onMemberLeave();

        // 入群请求事件的处理
        this.onJoinRequest();

        // 入群事件的处理
        this.onMemberJoin();

        // 机器人登录事件的处理
        this.onBotLogin();


        final Random random = new Random();

        if (this.myScheduledTask == null) {
            this.myScheduledTask = this.taskScheduler.runTaskTimerAsynchronously(() -> {
                final Runnable runnable = messageSends.poll();
                if (runnable == null) return;

                final long l = random.nextLong(200);

                try {
                    Thread.sleep(l);
                } catch (InterruptedException e) {
                    getLogger().warning(e.toString());
                    return;
                }

                runnable.run();
            }, 20, 20);

        }

    }

    @Override
    public void onDisable() {
        this.saveConfig();

        if (this.myScheduledTask != null) {
            this.myScheduledTask.cancel();
            this.myScheduledTask = null;
        }
        this.taskScheduler.cancelTasks();
    }


    @Override
    public @NotNull GroupAccess createMainGroupAccess() throws Exception {
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

            return new GroupAccessImpl(group);
        }
    }

    @Override
    public @NotNull GroupAccess createAuditGroupAccess() throws Exception {
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

            return new GroupAccessImpl(group);

        }
    }

    @EventHandler
    public void onChat(@NotNull AsyncChatEvent event) {
        final Component message = event.message();
        if (!(message instanceof final TextComponent textComponent)) return;

        final String content = textComponent.content();

        if (!content.startsWith(MESSAGE_PREFIX_SEND_TO_GROUP)) return;

        if (this.getBotId() == 0) return;
        if (this.getMainGroupId() == 0) return;


        final GroupAccess mainGroupAccess;
        try {
            mainGroupAccess = createMainGroupAccess();
        } catch (Exception e) {
            getLogger().warning(e.toString());
            return;
        }

        mainGroupAccess.sendNormalMessage("<%s> %s".formatted(event.getPlayer().getName(), content));
    }

    @NotNull Permission addPermission(@NotNull String name) {
        final Permission permission = new Permission(name);
        this.getServer().getPluginManager().addPermission(permission);
        return permission;
    }

    class GroupAccessImpl implements GroupAccess {

        private final @NotNull Group group;

        GroupAccessImpl(@NotNull Group group) {
            this.group = group;
        }

        @Override
        public boolean hasMember(long qq) {
            final NormalMember normalMember = this.group.get(qq);
            return normalMember != null;
        }

        @Override
        public void setGroupMemberRemark(long qq, @NotNull String remark) throws Exception {
            final NormalMember normalMember = this.group.get(qq);
            if (normalMember == null) throw new Exception("QQ群[%d]里没有该成员：%d".formatted(this.group.getId(), qq));

            try {
                normalMember.setNameCard(remark);
            } catch (PermissionDeniedException e) {
                throw new Exception(e);
            }
        }

        @Override
        public void sendNormalMessage(@NotNull String message) {
            final boolean offer = messageSends.offer(() -> group.sendMessage(message));
            if (!offer) {
                getLogger().warning("发送消息到群失败！");
            }

        }

        @Override
        public void sendAtMessage(long qq, @NotNull String message) {
            final boolean offer = messageSends.offer(() -> group.sendMessage(new MessageChainBuilder()
                    .append(new At(qq))
                    .append(new PlainText(" "))
                    .append(new PlainText(message))
                    .build()));

            if (!offer) {
                getLogger().warning("发送消息到群失败！");
            }

        }
    }
}
