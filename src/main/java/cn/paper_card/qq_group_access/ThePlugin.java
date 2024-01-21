package cn.paper_card.qq_group_access;

import cn.paper_card.accept_player_manuals.AcceptPlayerManualsApi;
import cn.paper_card.bilibili_bind.api.BilibiliBindApi;
import cn.paper_card.chat_gpt.api.ChatGptApi;
import cn.paper_card.group_root_command.GroupRootCommandApi;
import cn.paper_card.little_skin_login.api.LittleSkinLoginApi;
import cn.paper_card.paper_card_auth.api.PaperCardAuthApi;
import cn.paper_card.player_last_quit.api.PlayerLastQuitApi2;
import cn.paper_card.player_last_quit.api.QuitInfo;
import cn.paper_card.player_qq_group_remark.PlayerQqGroupRemarkApi;
import cn.paper_card.player_qq_in_group.PlayerQqInGroupApi;
import cn.paper_card.qq_bind.api.BindInfo;
import cn.paper_card.qq_bind.api.QqBindApi;
import cn.paper_card.qq_group_access.api.GroupAccess;
import cn.paper_card.qq_group_access.api.QqGroupAccessApi;
import cn.paper_card.qq_group_chat_sync.api.QqGroupChatSyncApi;
import cn.paper_card.smurf.api.SmurfApi;
import cn.paper_card.smurf.api.SmurfInfo;
import cn.paper_card.sponsorship.api.QqGroupMessageSender;
import cn.paper_card.sponsorship.api.SponsorshipApi2;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.Member;
import net.mamoe.mirai.contact.NormalMember;
import net.mamoe.mirai.data.UserProfile;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.events.*;
import net.mamoe.mirai.message.MessageReceipt;
import net.mamoe.mirai.message.data.*;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public final class ThePlugin extends JavaPlugin {

    private ChatGptApi chatGptApi = null;

    private QqBindApi qqBindApi = null;

    private BilibiliBindApi bilibiliBindApi = null;

    private LittleSkinLoginApi littleSkinLoginApi = null;

    private PaperCardAuthApi paperCardAuthApi = null;

    private PlayerQqGroupRemarkApi playerQqGroupRemarkApi = null;

    private PlayerQqInGroupApi playerQqInGroupApi = null;

    private GroupRootCommandApi groupRootCommandApi = null;

    private AcceptPlayerManualsApi acceptPlayerManualsApi = null;

    private QqGroupChatSyncApi qqGroupChatSyncApi = null;

    private PlayerLastQuitApi2 playerLastQuitApi = null;

    private SmurfApi smurfApi = null;

    private final @NotNull Object lock = new Object();

    private final @NotNull TaskScheduler taskScheduler;

    private final static String PATH_BOT_ID = "bot-id";

    private final static String PATH_OWNER_ID = "owner-id";
    private final static String PATH_MAIN_GROUP_ID = "main-group-id";

    private final static String PATH_AUDIT_GROUP_ID = "audit-group-id";

    private final @NotNull HashSet<Long> auditSendImageQq = new HashSet<>();
    private final @NotNull HashSet<Long> passAuditQq = new HashSet<>();

    private final @NotNull BlockingQueue<Runnable> messageSends;

    private final @NotNull ConcurrentHashMap<Integer, UUID> groupSyncMessages;

    private final @NotNull HashMap<Long, Long> leaveTimes;

    private MyScheduledTask myScheduledTask = null;

    public ThePlugin() {
        this.taskScheduler = UniversalScheduler.getScheduler(this);
        this.messageSends = new LinkedBlockingQueue<>();
        this.leaveTimes = new HashMap<>();
        this.groupSyncMessages = new ConcurrentHashMap<>();
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

    private void getGroupRootCommandApi() {
        if (this.groupRootCommandApi == null) {
            final Plugin plugin = this.getServer().getPluginManager().getPlugin("GroupRootCommand");
            if (plugin instanceof final GroupRootCommandApi api) {
                this.groupRootCommandApi = api;
            }
        }
    }

    private void getAcceptPlayerManualsApi() {
        if (this.acceptPlayerManualsApi == null) {
            final Plugin plugin = this.getServer().getPluginManager().getPlugin("AcceptPlayerManuals");
            if (plugin instanceof final AcceptPlayerManualsApi api) {
                this.acceptPlayerManualsApi = api;
            }
        }
    }

    @NotNull TaskScheduler getTaskScheduler() {
        return this.taskScheduler;
    }

    void setBotId(long id) {
        this.getConfig().set(PATH_BOT_ID, id);
    }

    long getBotId() {
        return this.getConfig().getLong(PATH_BOT_ID, 0);
    }

    long getOwnerId() {
        return this.getConfig().getLong(PATH_OWNER_ID, 0);
    }

    void setOwnerId(long v) {
        this.getConfig().set(PATH_OWNER_ID, v);
    }

    void setMainGroupId(long id) {
        this.getConfig().set(PATH_MAIN_GROUP_ID, id);
    }


    long getMainGroupId() {
        return this.getConfig().getLong(PATH_MAIN_GROUP_ID, 0);
    }

    void setAuditGroupId(long id) {
        this.getConfig().set(PATH_AUDIT_GROUP_ID, id);
    }


    long getAuditGroupId() {
        return this.getConfig().getLong(PATH_AUDIT_GROUP_ID, 0);
    }

    private void onNewFriendRequestEvent() {
        GlobalEventChannel.INSTANCE.subscribeAlways(NewFriendRequestEvent.class, event -> {
            final long ownerId = this.getOwnerId();
            if (ownerId == 0) return;

            final long fromId = event.getFromId();

            final Bot bot = event.getBot();

            getLogger().info("好友申请 {bot: %s(%d), fromId: %d}".formatted(
                    bot.getNick(), bot.getId(), fromId
            ));

            // 自动同意来来自主人的好友申请
            if (fromId == ownerId) {
                getLogger().info("自动同意来自OWNER的好友申请");
                event.accept();
            }
        });
    }

    private void onBotInvitedJoinGroupRequestEvent() {
        GlobalEventChannel.INSTANCE.subscribeAlways(BotInvitedJoinGroupRequestEvent.class, event -> {
            final long invitorId = event.getInvitorId();
            final long groupId = event.getGroupId();
            final Bot bot = event.getBot();

            this.getLogger().info("被邀请加入群 {bot %s(%d), friend: %d, group: %s(%d)}".formatted(
                    bot.getNick(), bot.getId(), invitorId, event.getGroupName(), event.getGroupId()
            ));

            final long auditGroupId = this.getAuditGroupId();
            final long mainGroupId = this.getMainGroupId();

            if (auditGroupId == groupId) {
                this.getLogger().info("自动同意加入主群");
                event.accept();
            } else if (mainGroupId == groupId) {
                this.getLogger().info("自动同意加入审核群");
                event.accept();
            }
        });
    }

    private void onBotJoinGroupEvent() {
        GlobalEventChannel.INSTANCE.subscribeAlways(BotJoinGroupEvent.class, event -> {
            final long groupId = event.getGroupId();
            final long mainGroupId = this.getMainGroupId();
            final long auditGroupId = this.getAuditGroupId();

            if (groupId == mainGroupId || groupId == auditGroupId) {
                final Runnable run = () -> {
                    event.getGroup().sendMessage(new PlainText("喵~"));
                    final NormalMember botAsMember = event.getGroup().getBotAsMember();
                    botAsMember.setNameCard("喵喵~");
                };

                if (!messageSends.offer(run)) run.run();
            }
        });
    }

    private void onGroupTempMessageEvent() {

        GlobalEventChannel.INSTANCE.subscribeAlways(GroupTempMessageEvent.class, event -> {
            final Bot bot = event.getBot();
            if (bot.getId() != this.getBotId()) return;

            final Group group = event.getGroup();
            if (group.getId() != this.getAuditGroupId()) return;

            final NormalMember sender = event.getSender();

            final boolean contains;
            synchronized (this.passAuditQq) {
                contains = this.passAuditQq.contains(sender.getId());
            }

            if (!contains) return;

            final MessageChain message = event.getMessage();
            final String s = message.contentToString();
            if ("入群方式".equals(s)) {

                final Runnable runnable = () -> sender.sendMessage("""
                        你好呀！恭喜你已经通过了审核~
                        我们的QQ主群为：%d
                        期待你的加入~
                        申请入群会直接同意噢~
                        如果申请之后一分钟内没有入群，
                        可能是出现了一点故障，
                        联系审核群其它管理员就好啦~""".formatted(this.getMainGroupId()));

                if (!messageSends.offer(runnable)) runnable.run();

            }
        });
    }


    private void transportGroupMessage(@NotNull String senderName, long senderQq, @NotNull Group group, @NotNull MessageChain messageChain) {
        // 转发到游戏内
        final QqGroupChatSyncApi api = this.qqGroupChatSyncApi;

        if (api == null) return;

        final StringBuilder builder = new StringBuilder();

        final LinkedList<At> ats = new LinkedList<>();
        final LinkedList<AtAll> atAllList = new LinkedList<>();
        final LinkedList<QuoteReply> quoteReplies = new LinkedList<>();

        for (final SingleMessage singleMessage : messageChain) {
            if (singleMessage instanceof final At at) { // AT某人
                final String display = at.getDisplay(group);
                builder.append(display);
                ats.add(at);
            } else if (singleMessage instanceof final AtAll atAll) { // AT全体
                builder.append(atAll.contentToString());
                atAllList.add(atAll);
            } else if (singleMessage instanceof final PlainText plainText) { // 纯文本
                builder.append(plainText.getContent());
            } else if (singleMessage instanceof final Image image) { // 图片
                builder.append(image.contentToString());
            } else if (singleMessage instanceof final Face face) { // 表情
                builder.append(face.contentToString());
            } else if (singleMessage instanceof final VipFace vipFace) { // VIP表情
                builder.append(vipFace.contentToString());
            } else if (singleMessage instanceof QuoteReply quoteReply) {
                quoteReplies.add(quoteReply);
            }

        }

        final String content = builder.toString();

        //
        {
            final String reply = api.onGroupMessage(senderQq, senderName, builder.toString());
            if (reply != null) {
                final Runnable run = () -> group.sendMessage(reply);
                if (!messageSends.offer(run)) run.run();
            }
        }

        // 处理AT
        for (final At at : ats) {
            final String reply = api.onAtMessage(senderQq, senderName, at.getTarget(), content);
            if (reply != null) {
                final Runnable run = () -> group.sendMessage(reply);
                if (!messageSends.offer(run)) run.run();
            }
        }

        // 处理AT全体
        for (AtAll ignored : atAllList) {
            final String reply = api.onAtAllMessage(senderQq, senderName, content);
            if (reply != null) {
                final Runnable run = () -> group.sendMessage(reply);
                if (!messageSends.offer(run)) run.run();
            }
        }

        // 处理引用回复
        for (QuoteReply quoteReply : quoteReplies) {
            final MessageSource source = quoteReply.getSource();
//                final long targetId = source.getTargetId(); // 群号
            final long botId = source.getBotId();
            final long fromId = source.getFromId();

            if (fromId == botId) { // 回复了机器人的消息

                // 判断是否回复机器人的游戏同步消息
                final int[] ids = source.getIds();
                if (ids.length == 1) {
                    final int id = ids[0];

                    final UUID uuid = groupSyncMessages.get(id);
                    if (uuid != null) {
//                        getSLF4JLogger().info("DEBUG: 回复的是机器人的同步消息");
                        final String reply = api.onReplySyncMessage(senderQq, senderName, uuid, content);
                        if (reply != null) {
                            final Runnable run = () -> group.sendMessage(reply);
                            if (!messageSends.offer(run)) run.run();
                        }
                    } else {
                        getSLF4JLogger().info("DEBUG: 回复的是机器人的非同步消息");
                    }

                } else {
                    getSLF4JLogger().warn("ids.length: %d".formatted(ids.length));
                }
            }
        }
    }

    private @NotNull String parseMessageForCommand(@NotNull MessageChain chain) {
        final StringBuilder builder = new StringBuilder();
        for (final SingleMessage singleMessage : chain) {
            if (singleMessage instanceof final PlainText plainText) {
                builder.append(plainText.getContent());
            } else if (singleMessage instanceof final At at) {
                builder.append(at.contentToString());
            }
        }
        return builder.toString();
    }

    private void executeMainGroupCommand(boolean isAdmin, @NotNull String message, @NotNull MessageChain chain, @NotNull Group group, @NotNull Member member) {
        if (this.groupRootCommandApi == null) return;


        final @Nullable String[] strings = isAdmin ?
                this.groupRootCommandApi.executeAdminMainGroupCommand(message, member.getId(), member.getNameCard(),
                        group.getId(), group.getName()) :
                this.groupRootCommandApi.executeMemberMainGroupCommand(message, member.getId(), member.getNameCard(),
                        group.getId(), group.getName());


        if (strings == null) return;

        for (final String reply : strings) {
            if (reply == null || reply.isEmpty()) continue;

            final Runnable runnable = () -> group.sendMessage(new MessageChainBuilder()
                    .append(new QuoteReply(chain))
                    .append(new At(member.getId()))
                    .append(" ")
                    .append(reply)
                    .build());

            if (!messageSends.offer(runnable)) runnable.run();

        }
    }


    private void onMainGroupMessage(@NotNull GroupMessageEvent event) {
        final Member sender = event.getSender();
        final String messageStr = event.getMessage().contentToString();
        final Group group = event.getGroup();

        final MessageChain message = event.getMessage();

        if (this.playerQqGroupRemarkApi != null) {
            this.playerQqGroupRemarkApi.updateRemarkByGroupMessage(sender.getId(), sender.getNameCard());
        }

        final String senderName = event.getSenderName();
        final long senderQq = sender.getId();

        // QQ群消息 -> 游戏内
        this.transportGroupMessage(senderName, senderQq, group, message);


        // QQ绑定验证码相关
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

        // LittleSkin绑定验证码相关
        if (this.littleSkinLoginApi != null) {
            final @Nullable String reply = this.littleSkinLoginApi.onMainGroupMessage(messageStr, sender.getId());
            if (reply != null) {
                final Runnable runnable = () -> group.sendMessage(new MessageChainBuilder()
                        .append(new QuoteReply(event.getMessage()))
                        .append(new At(sender.getId()))
                        .append(new PlainText(" "))
                        .append(new PlainText(reply))
                        .build());

                if (!messageSends.offer(runnable)) runnable.run();
            }
        }

        // 离线正版验证确认相关
        if (this.paperCardAuthApi != null) {
            final String reply = this.paperCardAuthApi.onGroupMessage(sender.getId(), messageStr);
            if (reply != null) {
                final Runnable runnable = () -> group.sendMessage(new MessageChainBuilder()
                        .append(new QuoteReply(event.getMessage()))
                        .append(new At(sender.getId()))
                        .append(new PlainText(" "))
                        .append(new PlainText(reply))
                        .build());

                if (!messageSends.offer(runnable)) runnable.run();
            }
        }

        // bilibili
        if (this.bilibiliBindApi != null) {
            final String reply = this.bilibiliBindApi.onMainGroupMessage(messageStr, sender.getId());
            if (reply != null) {
                final Runnable runnable = () -> group.sendMessage(new MessageChainBuilder()
                        .append(new QuoteReply(event.getMessage()))
                        .append(new At(sender.getId()))
                        .append(new PlainText(" "))
                        .append(new PlainText(reply))
                        .build());
                if (!messageSends.offer(runnable)) runnable.run();
            }
        }

        // 处理QQ群根命令
        final String commandLine = parseMessageForCommand(message);

        // 管理员命令
        if (sender.getPermission().getLevel() > 0)
            executeMainGroupCommand(true, commandLine, message, group, sender);

        // 普通成员命令
        executeMainGroupCommand(false, commandLine, message, group, sender);

        // 玩家同意协议相关
        if (this.acceptPlayerManualsApi != null) {
            final @Nullable String[] reply = this.acceptPlayerManualsApi.onMainGroupMessage(messageStr, sender.getId());
            if (reply != null) {
                for (final String s : reply) {
                    if (s == null) continue;

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


        // 包含关键字
        final String key = "已三连";
        final String contentToString = message.contentToString();

        if (contentToString.startsWith(key)) {
            final boolean removed;
            synchronized (this.auditSendImageQq) {
                removed = this.auditSendImageQq.remove(event.getSender().getId());
            }

            if (removed) {

                synchronized (this.passAuditQq) {
                    this.passAuditQq.add(event.getSender().getId());
                }

                final Runnable runnable = () -> group.sendMessage(new MessageChainBuilder()
                        .append(new QuoteReply(message))
                        .append(new At(event.getSender().getId()))
                        .append(" ")
                        .append("""
                                已颁发入群令牌，QQ主群为：%d
                                请在两分钟内申请加入~
                                您的QQ: %s(%d)""".formatted(getMainGroupId(), event.getSenderName(), event.getSender().getId()))
                        .build());

                if (!messageSends.offer(runnable)) runnable.run();

            } else {
                final Runnable runnable = () ->
                        group.sendMessage(new MessageChainBuilder()
                                .append(new QuoteReply(message))
                                .append(new At(event.getSender().getId()))
                                .append(" 先发送三连截图到群里，再发消息“%s”，注意一下【先后顺序】噢".formatted(key))
                                .build());

                if (!messageSends.offer(runnable)) runnable.run();
            }
        }

        if (message.size() < 2) return;
        final SingleMessage singleMessage = message.get(1);

        if (singleMessage instanceof final Image image) {
            // 发送一张图片
            final ImageType imageType = image.getImageType();

            getSLF4JLogger().info("ImageType: " + imageType.name());

            synchronized (this.auditSendImageQq) {
                this.auditSendImageQq.add(event.getSender().getId());
            }
        }
    }

    private void onFriendMessage() {

        GlobalEventChannel.INSTANCE.subscribeAlways(FriendMessageEvent.class, event -> {
        });
    }

    private void onGroupMessage() {
        GlobalEventChannel.INSTANCE.subscribeAlways(GroupMessageEvent.class, event -> {

            if (this.chatGptApi != null) {
                final Group group = event.getGroup();
                if (group.getId() == this.getMainGroupId()) { // 主群
                    final MessageChain message = event.getMessage();
                    // 检查是不是AT自己
                    boolean atMe = false;
                    final StringBuilder stringBuilder = new StringBuilder();
                    for (SingleMessage singleMessage : message) {
                        if (singleMessage instanceof At at) {
                            if (at.getTarget() == event.getBot().getId()) {
                                atMe = true;
                            }
                        } else if (singleMessage instanceof PlainText text) {
                            stringBuilder.append(text.getContent());
                        }
                    }

                    boolean reply;
                    if (atMe) {
                        reply = true;
                    } else {
                        reply = new Random().nextDouble() < 0.01;
                    }

                    final String messageStr = stringBuilder.toString();
                    if (messageStr.isEmpty()) {
                        reply = false;
                    }

                    if (reply) {
                        final Runnable runnable = () -> {
                            try {
                                final String answer;
                                getSLF4JLogger().info("request answer for: " + messageStr);
                                answer = this.chatGptApi.requestAnswer(messageStr, event.getSender().getId());
                                final MessageChainBuilder append = new MessageChainBuilder()
                                        .append(new QuoteReply(message))
                                        .append(new PlainText(answer));

                                group.sendMessage(append.build());
                            } catch (Exception e) {
                                getSLF4JLogger().error("", e);
                                group.sendMessage(new MessageChainBuilder()
                                        .append(new QuoteReply(message))
                                        .append(new PlainText(e.toString()))
                                        .build());
                            }
                        };

                        if (!messageSends.offer(runnable)) runnable.run();

                    }
                }
            }

            final long botId = this.getBotId();
            if (event.getBot().getId() != botId) return;

            if (event.getGroup().getId() == this.getMainGroupId()) this.onMainGroupMessage(event);
            if (event.getGroup().getId() == this.getAuditGroupId()) this.onAuditGroupMessage(event);

        });
    }

    private void onMemberLeave() {
        GlobalEventChannel.INSTANCE.subscribeAlways(MemberLeaveEvent.class, event -> {
            if (event.getBot().getId() != this.getBotId()) return;

            if (event.getGroupId() != this.getMainGroupId()) return;

            // 退出主群
            final Group group = event.getGroup();
            final Member member = event.getMember();

            final long qq = member.getId();

            if (this.playerQqInGroupApi != null)
                this.playerQqInGroupApi.onMemberQuitGroup(qq);


            String name; // 游戏名

            if (this.qqBindApi == null) {
                name = "无法访问QQ绑定API！";
            } else {
                final BindInfo bindInfo;
                try {
                    bindInfo = this.qqBindApi.getBindService().queryByQq(qq);

                    if (bindInfo != null && bindInfo.uuid() != null) {
                        final OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(bindInfo.uuid());

                        name = offlinePlayer.getName();
                        if (name == null) name = offlinePlayer.getUniqueId().toString();

                    } else {
                        name = "未绑定QQ";
                    }
                } catch (Exception e) {
                    this.getSLF4JLogger().error("qq bind service -> query by qq", e);
                    name = e.toString();
                }
            }

            final String name1 = name;

            final Runnable runnable = () -> group.sendMessage("%s (%d) 离开了群聊\n游戏名：%s".formatted(member.getNick(), member.getId(),
                    name1));

            if (!messageSends.offer(runnable)) runnable.run();

            // 保存退出时间
            synchronized (this.leaveTimes) {
                this.leaveTimes.put(qq, System.currentTimeMillis());
            }
        });

        GlobalEventChannel.INSTANCE.subscribeAlways(MemberLeaveEvent.Kick.class, event -> {
            if (event.getBot().getId() != this.getBotId()) return;

            if (event.getGroupId() != this.getMainGroupId()) return;

            final NormalMember member = event.getMember();
            final NormalMember operator = event.getOperator();

            final String opName;
            final String opId;

            if (operator != null) {
                String n = operator.getNameCard();
                if (n.isEmpty()) n = operator.getNick();

                opName = n;
                opId = "%d".formatted(operator.getId());
            } else {
                opName = "null";
                opId = "null";
            }

            final Runnable runnable1 = () -> {
                String name = member.getNameCard();
                if (name.isEmpty()) name = member.getNick();

                final long id = member.getId();

                event.getGroup().sendMessage("%s(%d) 被 %s(%s) 踢出了群聊".formatted(
                        name, id, opName, opId
                ));
            };

            if (!messageSends.offer(runnable1)) runnable1.run();
        });
    }

    private boolean checkPlayerJoinGroup(long qq, MemberJoinEvent event, int qLevel) {
        if (this.qqBindApi == null) return false;

        // 看看是否老玩家入群
        final BindInfo bindInfo;

        try {
            bindInfo = this.qqBindApi.getBindService().queryByQq(qq);
        } catch (Exception e) {
            this.getSLF4JLogger().error("qq bind service -> query by qq", e);
            final Runnable runnable = () -> event.getGroup().sendMessage(e.toString());
            if (!messageSends.offer(runnable)) runnable.run();
            return false;
        }

        if (bindInfo == null) return false;

        final UUID uuid = bindInfo.uuid();

        if (uuid == null) return false;

        final OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(uuid);
        String name = offlinePlayer.getName();
        if (name == null) name = uuid.toString();

        final Runnable runnable = sendMessageOldPlayerJoin(event, qLevel, name);

        if (!messageSends.offer(runnable)) runnable.run();

        return true;
    }

    @NotNull
    private static Runnable sendMessageOldPlayerJoin(MemberJoinEvent event, int qLevel, String name) {
        return () -> {
            final String msg = """
                    \n欢迎回到PaperCard~
                    您的游戏名: %s
                    您的QQ等级: %d
                    请查看群公告【新人必看】（服务器地址在这里噢）
                    祝您游戏愉快~""".formatted(name, qLevel);

            final Group group = event.getGroup();

            group.sendMessage(new MessageChainBuilder()
                    .append(new At(event.getMember().getId()))
                    .append(msg)
                    .build());
        };
    }

    private void onMemberJoin() {
        GlobalEventChannel.INSTANCE.subscribeAlways(MemberJoinEvent.class, event -> {
            if (event.getBot().getId() != this.getBotId()) return;

            if (event.getGroupId() == getAuditGroupId()) { // 进入审核群

                final Runnable runnable = () -> {
                    final String msg = """
                            \n欢迎来到PaperCard服务器审核群，
                            主群进入方式在置顶群公告了噢~
                            """;

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

                final long joinQq = event.getMember().getId();

                if (this.playerQqInGroupApi != null) {
                    this.playerQqInGroupApi.onMemberJoinGroup(joinQq);
                }

                // 重过审集合中移出
                final boolean removed;
                synchronized (this.passAuditQq) {
                    removed = this.passAuditQq.remove(joinQq);
                }

                final int level;
                final UserProfile userProfile = event.getMember().queryProfile();
                level = userProfile.getQLevel();

                // 检查是否老玩家入群
                if (this.checkPlayerJoinGroup(joinQq, event, level)) return;

                final Runnable runnable = () -> {

                    final String msg = """
                            \n欢迎新伙伴入裙~
                            您的QQ等级为: %d
                            请查看群公告【新人必看】（服务器地址在这里噢）
                            祝您游戏愉快~""".formatted(level);

                    final Group group = event.getGroup();

                    group.sendMessage(new MessageChainBuilder()
                            .append(new At(joinQq))
                            .append(msg)
                            .build());

                };
                if (!messageSends.offer(runnable)) runnable.run();

                // 检查令牌
                final Runnable runnable1 = () -> {
                    final String msg;
                    if (removed) {
                        msg = "已回收你的入群令牌";
                    } else {
                        msg = "未检测到你的入群令牌，请先加入审核群%d获取令牌！".formatted(this.getAuditGroupId());
                    }

                    event.getGroup().sendMessage(new MessageChainBuilder()
                            .append(new At(joinQq))
                            .append(" ")
                            .append(msg)
                            .build());
                };
                if (!messageSends.offer(runnable1)) runnable1.run();

                // 如果在审核群里，提示退出审核群
                final GroupAccess auditGroupAccess;
                try {
                    auditGroupAccess = createAuditGroupAccess();
                } catch (Exception e) {
                    handleException(e);
                    return;
                }

                try {
                    auditGroupAccess.sendAtMessage(event.getMember().getId(), "恭喜你已经进入主群，现在可以退出审核群啦~");
                } catch (Exception e) {
                    handleException(e);
                }
            }
        });
    }

    private void notifyLastQuitByQqGroup(@NotNull GroupAccess mainGroup, long cur) {
        final PlayerLastQuitApi2 playerLastQuitApi1 = this.playerLastQuitApi;
        final QqBindApi qqBindApi1 = this.qqBindApi;
        final SmurfApi smurfApi1 = this.smurfApi;

        if (playerLastQuitApi1 == null || qqBindApi1 == null) {
            try {
                mainGroup.sendNormalMessage("QQ机器人登录成功，服务器已经启动啦~");
            } catch (Exception e) {
                getSLF4JLogger().error("", e);
            }
            return;
        }

        final List<QuitInfo> list;
        try {
            list = playerLastQuitApi1.queryTimeAfter(cur - 5 * 60 * 1000L);
        } catch (Exception e) {
            getSLF4JLogger().error("", e);
            return;
        }

        final HashSet<Long> qqs = new HashSet<>();

        // 获取玩家的QQ号
        for (QuitInfo quitInfo : list) {
            
            // 已经在线了
            final Player player = getServer().getPlayer(quitInfo.uuid());
            if (player != null && player.isOnline()) continue;

            // 查询QQ绑定
            final BindInfo qqBind;

            try {
                qqBind = qqBindApi1.getBindService().queryByUuid(quitInfo.uuid());
            } catch (Exception e) {
                this.getSLF4JLogger().error("", e);
                continue;
            }

            if (qqBind != null) qqs.add(qqBind.qq());

            if (smurfApi1 != null) {
                try {
                    final SmurfInfo smurfInfo = smurfApi1.getSmurfService().queryBySmurfUuid(quitInfo.uuid());
                    if (smurfInfo != null) {
                        final BindInfo b = qqBindApi1.getBindService().queryByUuid(smurfInfo.mainUuid());
                        if (b != null) qqs.add(b.qq());
                    }
                } catch (Exception e) {
                    getSLF4JLogger().error("", e);
                }
            }
        }

        try {
            mainGroup.sendAtMessage(qqs.stream().toList(), "\n\n服务器已经启动啦~");
        } catch (Exception e) {
            getSLF4JLogger().error("", e);
        }
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

            // 通知玩家上线
            this.notifyLastQuitByQqGroup(mainGroupAccess, System.currentTimeMillis());

        });
    }

    private void onJoinRequest() {

        GlobalEventChannel.INSTANCE.subscribeAlways(MemberJoinRequestEvent.class, event -> {
            getLogger().info("DEBUG: 入群申请事件");

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

            final Long leveTime;
            synchronized (this.leaveTimes) {
                leveTime = this.leaveTimes.get(fromId);
            }

            if (leveTime != null && System.currentTimeMillis() < leveTime + 24 * 60 * 60 * 1000L) {
                final String msg = """
                        不自动处理的入群申请，因为在短时间内离开了群聊
                        QQ: %s(%s)""".formatted(fromNick, fromId);

                final Runnable runnable = () -> group.sendMessage(msg);

                if (!messageSends.offer(runnable)) runnable.run();
                return;
            }


            // 查询QQ绑定
            if (this.qqBindApi != null) {
                try {
                    final BindInfo bindInfo = this.qqBindApi.getBindService().queryByQq(fromId);

                    if (bindInfo != null && bindInfo.uuid() != null) {

                        final OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(bindInfo.uuid());
                        String name = offlinePlayer.getName();
                        if (name == null) name = bindInfo.uuid().toString();
                        final String name1 = name;

                        final boolean banned = offlinePlayer.isBanned();

                        final Runnable runnable = () -> {

                            if (banned) {
                                group.sendMessage("""
                                        被封禁玩家申请入群，请手动处理
                                        游戏名：%s
                                        QQ: %s (%s)""".formatted(
                                        name1, fromNick, fromId
                                ));

                            } else {
                                group.sendMessage("""
                                        自动同意老玩家入群：
                                        游戏名：%s
                                        QQ: %s (%d)""".formatted(
                                        name1, fromNick, fromId
                                ));
                            }

                        };

                        final boolean offer = messageSends.offer(runnable);
                        if (!offer) runnable.run();

                        if (!banned) {
                            event.accept();
                        }

                        return;
                    }

                } catch (Exception e) {
                    handleException(e);
                    final Runnable run = () -> group.sendMessage(e.toString());
                    if (!messageSends.offer(run)) run.run();
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

            // 是否在审核群中
            try {
                final GroupAccess auditGroupAccess = createAuditGroupAccess();
                final boolean hasMember = auditGroupAccess.hasMember(fromId);
                if (hasMember) {

                    event.reject(false, "请先通过审核！");
                    auditGroupAccess.sendAtMessage(fromId, "请先发送三连截图到群里，再@我，得到入群令牌后，再申请加入主群！");

                    final Runnable runnable = () -> {
                        final String msg = """
                                已自动拒绝无令牌的入群申请：
                                %s (%d)""".formatted(fromNick, fromId);
                        group.sendMessage(msg);
                    };

                    if (!messageSends.offer(runnable)) runnable.run();
                    return;
                }
            } catch (Exception e) {
                handleException(e);
                final Runnable run = () -> group.sendMessage(e.toString());
                if (!messageSends.offer(run)) run.run();
            }

            final Runnable runnable = () -> group.sendMessage("""
                    无入群令牌，不会自动处理入群申请：
                    %s (%d)
                    InvitorId: %s"""
                    .formatted(
                            fromNick,
                            fromId,
                            invitorId
                    )
            );

            if (!messageSends.offer(runnable)) runnable.run();
        });
    }

    @Override
    public void onLoad() {
        QqGroupAccessImpl qqGroupAccess = new QqGroupAccessImpl(this);

        this.getSLF4JLogger().info("注册%s...".formatted(PlayerQqInGroupApi.QqGroupAccess.class.getSimpleName()));
        this.getServer().getServicesManager().register(QqGroupAccessApi.class, qqGroupAccess, this, ServicePriority.Highest);
    }

    @NotNull Group getMainGroup() throws Exception {
        final long botId = getBotId();
        if (botId <= 0) throw new Exception("未配置QQ机器人ID");

        final long mainGroupId = getMainGroupId();
        if (mainGroupId <= 0) throw new Exception("未配置QQ主群ID！");

        final Bot instance = Bot.findInstance(botId);
        if (instance == null) throw new Exception("找不到QQ机器人：%d".formatted(botId));

        if (!instance.isOnline()) throw new Exception("QQ机器人[%d]不在线！".formatted(botId));

        final Group group = instance.getGroup(mainGroupId);

        if (group == null)
            throw new Exception("QQ机器人[%d]无法访问QQ群[%d]".formatted(botId, mainGroupId));

        return group;
    }


    @Override
    public void onEnable() {

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
        this.setOwnerId(this.getOwnerId());
        this.saveConfig();

        // 获取其它插件接口
        this.qqBindApi = this.getServer().getServicesManager().load(QqBindApi.class);
        this.littleSkinLoginApi = this.getServer().getServicesManager().load(LittleSkinLoginApi.class);
        this.paperCardAuthApi = this.getServer().getServicesManager().load(PaperCardAuthApi.class);
        this.playerLastQuitApi = this.getServer().getServicesManager().load(PlayerLastQuitApi2.class);
        this.smurfApi = this.getServer().getServicesManager().load(SmurfApi.class);

        this.bilibiliBindApi = this.getServer().getServicesManager().load(BilibiliBindApi.class);
        if (this.bilibiliBindApi != null) getSLF4JLogger().info("已连接到" + BilibiliBindApi.class.getSimpleName());

        this.qqGroupChatSyncApi = this.getServer().getServicesManager().load(QqGroupChatSyncApi.class);
        if (this.qqGroupChatSyncApi != null) {
            getSLF4JLogger().info("已连接到" + QqGroupChatSyncApi.class.getSimpleName());

            this.qqGroupChatSyncApi.setMessageSender((uuid, name, content) -> {

                final Runnable runnable = () -> {
                    final Group mainGroup;

                    try {
                        mainGroup = getMainGroup();
                    } catch (Exception e) {
                        handleException(e);
                        return;
                    }

                    final MessageReceipt<Group> receipt = mainGroup.sendMessage(new PlainText(content));


                    final OnlineMessageSource.Outgoing source = receipt.getSource();

                    final int[] ids = source.getIds();

                    if (ids.length == 1) {
                        final int id = ids[0];
                        // 记录下来
                        groupSyncMessages.put(id, uuid);
                    } else {
                        getSLF4JLogger().warn("ids.length: %d".formatted(ids.length));
                    }
                };

                if (!messageSends.offer(runnable)) runnable.run();

            });
        }

        SponsorshipApi2 sponsorshipApi = this.getServer().getServicesManager().load(SponsorshipApi2.class);
        if (sponsorshipApi != null) {
            getSLF4JLogger().info("已连接到" + SponsorshipApi2.class.getSimpleName());
            sponsorshipApi.setQqGroupMessageSender(new QqGroupMessageSender() {

                @Override
                public void sendNormal(@NotNull String s) throws Exception {

                    final Group group = getMainGroup();

                    final Runnable run = () -> group.sendMessage(s);

                    if (!messageSends.offer(run)) run.run();
                }

                @Override
                public void sendAt(@Nullable String s, long l, @Nullable String s1) throws Exception {
                    final Group group = getMainGroup();

                    final Runnable run = () -> {
                        final MessageChainBuilder builder = new MessageChainBuilder();
                        if (s != null) builder.append(s);
                        builder.append(new At(l));
                        if (s1 != null) builder.append(s1);

                        group.sendMessage(builder.build());
                    };

                    if (!messageSends.offer(run)) run.run();
                }
            });
        }

        // 设置QQ群号
        if (this.qqBindApi != null) {
            final long mainGroupId = this.getMainGroupId();
            this.qqBindApi.setGroupId(mainGroupId);
            this.getLogger().info("已连接QqBindApi并设置QQ群号：" + mainGroupId);
        }

        try {
            this.chatGptApi = this.getServer().getServicesManager().load(ChatGptApi.class);
            if (this.chatGptApi == null) {
                getSLF4JLogger().warn("无法连接到" + ChatGptApi.class.getSimpleName());
            } else {
                getSLF4JLogger().info("已连接到" + ChatGptApi.class.getSimpleName());
            }
        } catch (NoClassDefFoundError e) {
            this.getSLF4JLogger().warn("无法连接到ChatGPT插件：" + e);
            this.chatGptApi = null;
        }

        this.getPlayerQqGroupRemarkApi();
        this.getPlayerQqInGroupApi();
        this.getGroupRootCommandApi();
        this.getAcceptPlayerManualsApi();

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

        this.onNewFriendRequestEvent();
        this.onBotInvitedJoinGroupRequestEvent();
        this.onBotJoinGroupEvent();

        this.onGroupTempMessageEvent();


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

        // 事件监听
        new OnQuit(this);
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


    @NotNull GroupAccess createMainGroupAccess() throws Exception {
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

            return new GroupAccessImpl(group, messageSends);
        }
    }


    @NotNull GroupAccess createAuditGroupAccess() throws Exception {
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

            return new GroupAccessImpl(group, messageSends);

        }
    }

    @NotNull
    Permission addPermission(@NotNull String name) {
        final Permission permission = new Permission(name);
        this.getServer().getPluginManager().addPermission(permission);
        return permission;
    }

    void handleException(@NotNull Throwable e) {
        getSLF4JLogger().error("", e);
    }

    @NotNull ConcurrentHashMap<Integer, UUID> getGroupSyncMessages() {
        return this.groupSyncMessages;
    }
}
