package cn.paper_card.qq_group_access;

import cn.paper_card.bilibili_bind.api.BilibiliBindApi;
import cn.paper_card.chat_gpt.api.ChatGptApi;
import cn.paper_card.group_root_command.GroupRootCommandApi;
import cn.paper_card.little_skin_login.api.LittleSkinLoginApi;
import cn.paper_card.paper_card_auth.api.PaperCardAuthApi;
import cn.paper_card.player_coins.api.PlayerCoinsApi;
import cn.paper_card.player_last_quit.api.PlayerLastQuitApi2;
import cn.paper_card.player_last_quit.api.QuitInfo;
import cn.paper_card.qq_bind.api.BindInfo;
import cn.paper_card.qq_bind.api.QqBindApi;
import cn.paper_card.qq_group_access.api.GroupAccess;
import cn.paper_card.qq_group_access.api.QqGroupAccessApi;
import cn.paper_card.qq_group_chat_sync.api.QqGroupChatSyncApi;
import cn.paper_card.qq_group_member_info.api.QqGroupMemberInfo;
import cn.paper_card.qq_group_member_info.api.QqGroupMemberInfoApi;
import cn.paper_card.qq_group_member_info.api.QqGroupMemberInfoService;
import cn.paper_card.smurf.api.SmurfApi;
import cn.paper_card.smurf.api.SmurfInfo;
import cn.paper_card.sponsorship.api.QqGroupMessageSender;
import cn.paper_card.sponsorship.api.SponsorshipApi2;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.Member;
import net.mamoe.mirai.contact.NormalMember;
import net.mamoe.mirai.contact.UserOrBot;
import net.mamoe.mirai.data.UserProfile;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.events.*;
import net.mamoe.mirai.message.MessageReceipt;
import net.mamoe.mirai.message.data.*;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ThePlugin extends JavaPlugin {

    private ChatGptApi chatGptApi = null;

    private QqBindApi qqBindApi = null;

    private BilibiliBindApi bilibiliBindApi = null;

    private LittleSkinLoginApi littleSkinLoginApi = null;

    private PaperCardAuthApi paperCardAuthApi = null;

    private GroupRootCommandApi groupRootCommandApi = null;

    private QqGroupChatSyncApi qqGroupChatSyncApi = null;

    private PlayerLastQuitApi2 playerLastQuitApi = null;

    private SmurfApi smurfApi = null;

    private QqGroupMemberInfoApi qqGroupMemberInfoApi = null;

    private PlayerCoinsApi playerCoinsApi = null;

    private final @NotNull Object lock = new Object();

    private final @NotNull TaskScheduler taskScheduler;

    private final @NotNull ConfigManager configManager;

    private final @NotNull MessageSender messageSender;

    private final @NotNull ConcurrentHashMap<Integer, UUID> groupSyncMessages;

    private final @NotNull AuditGroupHandler auditGroupHandler;

    private final @NotNull MainGroupHandler mainGroupHandler;

    private boolean chatImageMod = true;

    public ThePlugin() {
        this.taskScheduler = UniversalScheduler.getScheduler(this);
        this.groupSyncMessages = new ConcurrentHashMap<>();
        this.configManager = new ConfigManager(this);
        this.chatImageMod = true;
        this.messageSender = new MessageSender(this);
        this.auditGroupHandler = new AuditGroupHandler(this);
        this.mainGroupHandler = new MainGroupHandler(this);
    }

    private void getGroupRootCommandApi() {
        if (this.groupRootCommandApi == null) {
            final Plugin plugin = this.getServer().getPluginManager().getPlugin("GroupRootCommand");
            if (plugin instanceof final GroupRootCommandApi api) {
                this.groupRootCommandApi = api;
            }
        }
    }

    @NotNull MessageSender getMessageSender() {
        return this.messageSender;
    }

    @NotNull AuditGroupHandler getAuditGroupHandler() {
        return this.auditGroupHandler;
    }

    @NotNull TaskScheduler getTaskScheduler() {
        return this.taskScheduler;
    }

    private boolean offerFail(@NotNull Runnable runnable) {
        return this.messageSender.offerFail(runnable);
    }


    private void onNewFriendRequestEvent() {
        GlobalEventChannel.INSTANCE.subscribeAlways(NewFriendRequestEvent.class, event -> {
            final long ownerId = this.configManager.getOwnerId();
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

            final long auditGroupId = this.configManager.getAuditGroupId();
            final long mainGroupId = this.configManager.getMainGroupId();

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
            final long mainGroupId = this.configManager.getMainGroupId();
            final long auditGroupId = this.configManager.getAuditGroupId();

            if (groupId == mainGroupId || groupId == auditGroupId) {
                final Runnable run = () -> {
                    event.getGroup().sendMessage(new PlainText("喵~"));
                    final NormalMember botAsMember = event.getGroup().getBotAsMember();
                    botAsMember.setNameCard("喵喵~");
                };

                if (offerFail(run)) run.run();
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

                final String str = image.contentToString();

                if (this.chatImageMod) {
                    final String imageUrl = Image.queryUrl(image);
                    String name = str;
                    name = name.replaceAll("[\\[\\]]", "");
                    builder.append("[[CICode,url=%s, name=%s, alt=请安装ChatImage模组来预览图片]]".formatted(
                            imageUrl, name
                    ));
                } else {
                    builder.append(str);
                }

            } else if (singleMessage instanceof final Face face) { // 表情
                builder.append(face.contentToString());
            } else if (singleMessage instanceof final VipFace vipFace) { // VIP表情
                builder.append(vipFace.contentToString());
            } else if (singleMessage instanceof QuoteReply quoteReply) {
                quoteReplies.add(quoteReply);
            } else {
                if (!(singleMessage instanceof MessageSource)) {
                    final String name = singleMessage.getClass().getSimpleName();
                    builder.append("[不支持的消息类型：%s]".formatted(name));
                }
            }
        }

        final String content = builder.toString();

        //
        {
            final String reply = api.onGroupMessage(senderQq, senderName, builder.toString());
            if (reply != null) {
                final Runnable run = () -> group.sendMessage(reply);
                if (offerFail(run)) run.run();
            }
        }

        // 处理AT
        for (final At at : ats) {
            final String reply = api.onAtMessage(senderQq, senderName, at.getTarget(), content);
            if (reply != null) {
                final Runnable run = () -> group.sendMessage(reply);
                if (offerFail(run)) run.run();
            }
        }

        // 处理AT全体
        for (AtAll ignored : atAllList) {
            final String reply = api.onAtAllMessage(senderQq, senderName, content);
            if (reply != null) {
                final Runnable run = () -> group.sendMessage(reply);
                if (offerFail(run)) run.run();
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
                            if (offerFail(run)) run.run();
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

            if (offerFail(runnable)) runnable.run();

        }
    }


    private void onMainGroupMessage(@NotNull GroupMessageEvent event) {
        final Member sender = event.getSender();
        final String messageStr = event.getMessage().contentToString();
        final Group group = event.getGroup();

        final MessageChain message = event.getMessage();

        { // 更新在群状态和群名片的数据
            final QqGroupMemberInfoApi api = this.qqGroupMemberInfoApi;
            if (api != null) {
                try {
                    final String reply = this.qqGroupMemberInfoApi.onMainGroupMessage(sender.getId(), sender.getNameCard(), messageStr);
                    if (reply != null) {
                        final Runnable run = () -> group.sendMessage(new MessageChainBuilder()
                                .append(new QuoteReply(event.getMessage()))
                                .append(new At(sender.getId()))
                                .append(" ")
                                .append(new PlainText(reply))
                                .build());

                        if (offerFail(run)) run.run();
                    }
                } catch (Exception e) {
                    getSLF4JLogger().error("", e);
                }
            }
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
                    if (offerFail(runnable)) runnable.run();
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

                if (offerFail(runnable)) runnable.run();
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

                if (offerFail(runnable)) runnable.run();
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
                if (offerFail(runnable)) runnable.run();
            }
        }

        // 处理QQ群根命令
        final String commandLine = parseMessageForCommand(message);

        // 管理员命令
        if (sender.getPermission().getLevel() > 0)
            executeMainGroupCommand(true, commandLine, message, group, sender);

        // 普通成员命令
        executeMainGroupCommand(false, commandLine, message, group, sender);
    }

    private void onFriendMessage() {

        GlobalEventChannel.INSTANCE.subscribeAlways(FriendMessageEvent.class, event -> {
        });
    }

    private void onGroupMessage() {
        GlobalEventChannel.INSTANCE.subscribeAlways(GroupMessageEvent.class, event -> {

            if (this.chatGptApi != null) {
                final Group group = event.getGroup();
                if (group.getId() == this.configManager.getMainGroupId()) { // 主群
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

                        if (offerFail(runnable)) runnable.run();

                    }
                }
            }

            final long botId = this.configManager.getBotId();
            if (event.getBot().getId() != botId) return;

            final Group group = event.getGroup();
            final long groupId = group.getId();

            if (groupId == this.configManager.getMainGroupId()) this.onMainGroupMessage(event);

            if (groupId == this.configManager.getAuditGroupId())
                this.auditGroupHandler.onMessage(event);
        });
    }

    private void onMemberLeave() {
        GlobalEventChannel.INSTANCE.subscribeAlways(MemberLeaveEvent.class, event -> {
            if (event.getBot().getId() != this.configManager.getBotId()) return;

            if (event.getGroupId() == this.configManager.getMainGroupId())
                this.mainGroupHandler.onLeave(event);
        });

        GlobalEventChannel.INSTANCE.subscribeAlways(MemberLeaveEvent.Kick.class, event -> {
            if (event.getBot().getId() != this.configManager.getBotId()) return;

            if (event.getGroupId() != this.configManager.getMainGroupId()) return;

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

            if (offerFail(runnable1)) runnable1.run();
        });
    }


    void recordQqInfoWhenJoin(@NotNull NormalMember member, boolean inGroup, @Nullable UserProfile userProfile) {
        final QqGroupMemberInfoApi api = this.qqGroupMemberInfoApi;
        if (api == null) return;

        this.taskScheduler.runTaskAsynchronously(() -> {
            // 记录信息
            final UserProfile p = userProfile != null ? userProfile : member.queryProfile();

            final QqGroupMemberInfoService s = api.getQqGroupMemberInfoService();

            try {
                final boolean added = s.addOrUpdateByQq(new QqGroupMemberInfo(
                        member.getId(),
                        member.getNick(),
                        member.getNameCard(),
                        System.currentTimeMillis(),
                        inGroup,
                        p.getQLevel()
                ));

                getSLF4JLogger().info("%s (%d) 入群，添加记录：%s".formatted(member.getNameCard(), member.getId(), added));
            } catch (Exception e) {
                handleException(e);
            }
        });
    }

    private void onMemberJoin() {
        GlobalEventChannel.INSTANCE.subscribeAlways(MemberJoinEvent.class, event -> {
            if (event.getBot().getId() != this.configManager.getBotId()) return;

            final long groupId = event.getGroupId();

            if (groupId == this.configManager.getAuditGroupId()) { // 进入审核群
                this.auditGroupHandler.onJoin(event);
                return;
            }

            if (groupId == this.configManager.getMainGroupId()) { // 进入主群
                this.mainGroupHandler.onJoin(event);
            }
        });
    }

    private void notifyLastQuitByQqGroup(@NotNull GroupAccess mainGroup) {
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
            list = playerLastQuitApi1.queryLatest(10 * 60 * 1000L);
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
            if (event.getBot().getId() != this.configManager.getBotId()) return;

            if (this.configManager.isSendMessageOnLogin()) {
                final GroupAccess mainGroupAccess;
                try {
                    mainGroupAccess = createMainGroupAccess();
                } catch (Exception e) {
                    getLogger().warning(e.toString());
                    return;
                }

                // 通知玩家上线
                this.notifyLastQuitByQqGroup(mainGroupAccess);
            }
        });
    }

    private void onJoinRequest() {
        GlobalEventChannel.INSTANCE.subscribeAlways(MemberJoinRequestEvent.class, event -> {
            final long groupId = event.getGroupId();
            final long mainGroupId = this.configManager.getMainGroupId();
            if (mainGroupId == groupId)
                this.mainGroupHandler.onJoinRequest(event);
        });
    }

    private void onSignIn() {
        GlobalEventChannel.INSTANCE.subscribeAlways(SignEvent.class, event -> {
            getSLF4JLogger().info("DEBUG: " + event.toString());

            final UserOrBot user = event.getUser();

            getSLF4JLogger().info("DEBUG: " + user.getClass().getName());

            final Bot bot = event.getBot();

            // Event: SignEvent(bot=2797664401, group=860768366, member=1658813364, sign=今日已打卡 )

            if (bot.getId() != this.configManager.getBotId()) return;

            if (!(user instanceof final NormalMember member)) return;

            final Group group = member.getGroup();
            if (group.getId() != this.configManager.getMainGroupId()) return;

            final String reply = new MemberSignService(this).onSign(member.getId());

            if (reply == null) return;

            final Runnable r = () -> group.sendMessage(new MessageChainBuilder()
                    .append(new At(member.getId()))
                    .append(" ")
                    .append(reply)
                    .build());

            if (offerFail(r)) r.run();
        });
    }

    @Override
    public void onLoad() {
        QqGroupAccessImpl qqGroupAccess = new QqGroupAccessImpl(this);

        this.getSLF4JLogger().info("注册%s...".formatted(QqGroupAccessApi.class.getSimpleName()));
        this.getServer().getServicesManager().register(QqGroupAccessApi.class, qqGroupAccess, this, ServicePriority.Highest);
    }

    @NotNull Group getMainGroup() throws Exception {
        final long botId = this.configManager.getBotId();
        if (botId <= 0) throw new Exception("未配置QQ机器人ID");

        final long mainGroupId = this.configManager.getMainGroupId();
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
        this.configManager.setDefaults();
        this.configManager.save();


        // 获取其它插件接口
        this.qqBindApi = this.getServer().getServicesManager().load(QqBindApi.class);
        this.littleSkinLoginApi = this.getServer().getServicesManager().load(LittleSkinLoginApi.class);
        this.paperCardAuthApi = this.getServer().getServicesManager().load(PaperCardAuthApi.class);
        this.playerLastQuitApi = this.getServer().getServicesManager().load(PlayerLastQuitApi2.class);
        this.smurfApi = this.getServer().getServicesManager().load(SmurfApi.class);
        this.qqGroupMemberInfoApi = this.getServer().getServicesManager().load(QqGroupMemberInfoApi.class);
        this.playerCoinsApi = this.getServer().getServicesManager().load(PlayerCoinsApi.class);

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

                if (offerFail(runnable)) runnable.run();

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

                    if (offerFail(run)) run.run();
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

                    if (offerFail(run)) run.run();
                }
            });
        }

        // 设置QQ群号
        if (this.qqBindApi != null) {
            final long mainGroupId = this.configManager.getMainGroupId();
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

        this.getGroupRootCommandApi();

        this.onSignIn();

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

        // 事件监听
        new OnQuit(this);

        this.messageSender.init();
    }

    @Override
    public void onDisable() {
        this.configManager.save();

        this.messageSender.destroy();
        this.taskScheduler.cancelTasks();

        this.getServer().getServicesManager().unregisterAll(this);
    }

    @NotNull GroupAccess createMainGroupAccess() throws Exception {
        final long mainGroupId = this.configManager.getMainGroupId();
        final long botId = this.configManager.getBotId();

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

            return new GroupAccessImpl(group, this.messageSender);
        }
    }


    @NotNull GroupAccess createAuditGroupAccess() throws Exception {
        final long auditGroupId = this.configManager.getAuditGroupId();
        final long botId = this.configManager.getBotId();

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

            return new GroupAccessImpl(group, this.messageSender);

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

    @Nullable QqGroupMemberInfoApi getQqGroupMemberInfoApi() {
        return this.qqGroupMemberInfoApi;
    }

    @NotNull ConfigManager getConfigManager() {
        return this.configManager;
    }

    void sendError(@NotNull CommandSender sender, @NotNull String error) {
        final TextComponent.Builder t = Component.text();
        this.appendPrefix(t);
        t.appendSpace();
        t.append(Component.text(error).color(NamedTextColor.RED));
        sender.sendMessage(t.build());
    }

    void sendInfo(@NotNull CommandSender sender, @NotNull String info) {
        final TextComponent.Builder t = Component.text();
        this.appendPrefix(t);
        t.appendSpace();
        t.append(Component.text(info).color(NamedTextColor.GREEN));
        sender.sendMessage(t.build());
    }

    void sendException(@NotNull CommandSender sender, @NotNull Throwable e) {
        final TextComponent.Builder text = Component.text();

        this.appendPrefix(text);
        text.append(Component.text(" ==== 异常信息 ====").color(NamedTextColor.DARK_RED));

        for (Throwable t = e; t != null; t = t.getCause()) {
            text.appendNewline();
            text.append(Component.text(t.toString()).color(NamedTextColor.RED));
        }
        sender.sendMessage(text.build());
    }

    void appendPrefix(@NotNull TextComponent.Builder text) {
        text.append(Component.text("[").color(NamedTextColor.GRAY));
        text.append(Component.text(this.getName()).color(NamedTextColor.DARK_AQUA));
        text.append(Component.text("]").color(NamedTextColor.GRAY));
    }

    @Nullable QqBindApi getQqBindApi() {
        return this.qqBindApi;
    }

    @Nullable PlayerCoinsApi getPlayerCoinsApi() {
        return this.playerCoinsApi;
    }
}
