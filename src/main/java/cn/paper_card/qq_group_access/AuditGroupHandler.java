package cn.paper_card.qq_group_access;

import cn.paper_card.qq_bind.api.BindInfo;
import cn.paper_card.qq_bind.api.QqBindApi;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.MemberJoinEvent;
import net.mamoe.mirai.message.data.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;

class AuditGroupHandler {
    record Info(
            Image image,
            long sendTime
    ) {
    }

    private final static String KEY = "已三连";

    private final static long CONFIRM_TIME_OUT = 60 * 1000L;

    private final HashMap<Long, Info> lastSendImages;

    private final HashSet<Long> joinTokens;

    private final @NotNull ThePlugin plugin;

    AuditGroupHandler(@NotNull ThePlugin plugin) {
        this.plugin = plugin;
        this.lastSendImages = new HashMap<>();
        this.joinTokens = new HashSet<>();
    }

    private boolean offerFail(@NotNull Runnable runnable) {
        return this.plugin.getMessageSender().offerFail(runnable);
    }

    boolean hasToken(long qq) {
        synchronized (this.joinTokens) {
            return this.joinTokens.contains(qq);
        }
    }

    boolean removeToken(long qq) {
        synchronized (this.joinTokens) {
            return this.joinTokens.remove(qq);
        }
    }

    void sendErrorGroup(@NotNull Group group, @NotNull String error) {
        final Runnable runnable = () -> group.sendMessage("[错误] " + error);
        if (this.plugin.getMessageSender().offerFail(runnable)) runnable.run();
    }

    @Nullable BindInfo hasBind(long qq) throws Exception {
        final QqBindApi api = this.plugin.getQqBindApi();
        if (api == null) return null;
        return api.getBindService().queryByQq(qq);
    }

    void onJoinMain(@NotNull Group mainGroup, long qq) {

        final Runnable runnable = () -> {

            final Group group = mainGroup.getBot().getGroup(this.plugin.getConfigManager().getAuditGroupId());

            if (group == null) return;

            group.sendMessage(new MessageChainBuilder()
                    .append(new At(qq))
                    .append(" 恭喜您已经进入主群，现在可以退出审核群啦~")
                    .build());
        };

        if (offerFail(runnable)) runnable.run();
    }

    void onJoin(@NotNull MemberJoinEvent event) {

        final long qq = event.getMember().getId();
        final Group group = event.getGroup();


        // 判断是否为老玩家
        this.plugin.getTaskScheduler().runTaskAsynchronously(() -> {
            final String groupName = group.getName();

            BindInfo bindInfo;

            try {
                bindInfo = this.hasBind(qq);
            } catch (Exception e) {
                this.plugin.getSLF4JLogger().error("", e);
                this.sendErrorGroup(group, e.toString());
                bindInfo = null;
            }

            // 老玩家
            if (bindInfo != null) {

                // 添加令牌
                synchronized (this.joinTokens) {
                    this.joinTokens.add(qq);
                }

                final String msg = """
                        \n欢迎回到%s，
                        您的游戏名：%s，
                        请直接加入主群：%d""".formatted(
                        groupName,
                        bindInfo.name(),
                        this.plugin.getConfigManager().getMainGroupId()
                );

                final Runnable runnable = () -> group.sendMessage(new MessageChainBuilder()
                        .append(new At(qq))
                        .append(msg)
                        .build());

                if (offerFail(runnable)) runnable.run();
            } else {
                // 新玩家
                final Runnable runnable = () -> {
                    final String msg = """
                            \n欢迎来到%s，
                            主群进入方式在置顶群公告噢~""".formatted(groupName);

                    group.sendMessage(new MessageChainBuilder()
                            .append(new At(qq))
                            .append(msg)
                            .build());
                };

                if (offerFail(runnable)) runnable.run();
            }
        });

        this.plugin.recordQqInfoWhenJoin(event.getMember(), false, null);
    }

    void onMessage(@NotNull GroupMessageEvent event) {
        final Group group = event.getGroup();
        final MessageChain message = event.getMessage();

        // 包含关键字
        final String contentToString = MyUtil.getAllPainTexts(message);

        final long senderQq = event.getSender().getId();

        if (contentToString.contains(KEY)) {
            final Info lastSend;
            synchronized (this.lastSendImages) {
                lastSend = this.lastSendImages.remove(senderQq);
            }

            final long cur = System.currentTimeMillis();
            if (lastSend != null && cur - CONFIRM_TIME_OUT < lastSend.sendTime()) {
                // 添加令牌
                synchronized (this.joinTokens) {
                    this.joinTokens.add(senderQq);
                }

                final Runnable runnable = () -> group.sendMessage(new MessageChainBuilder()
                        .append(new QuoteReply(message))
                        .append(new At(event.getSender().getId()))
                        .append(" ")
                        .append("""
                                已颁发入群令牌，QQ主群为：%d
                                请尽快申请加入~
                                您的QQ: %s (%d)""".formatted(
                                this.plugin.getConfigManager().getMainGroupId(),
                                event.getSenderName(),
                                senderQq)
                        )
                        .build());

                if (offerFail(runnable)) runnable.run();

            } else {
                final Runnable runnable = () ->
                        group.sendMessage(new MessageChainBuilder()
                                .append(new QuoteReply(message))
                                .append(new At(event.getSender().getId()))
                                .append(" 先发送三连截图到群里，再发消息“%s”，注意一下【先后顺序】噢".formatted(KEY))
                                .build());

                if (offerFail(runnable)) runnable.run();
            }
        }

        // 处理图片
        if (message.size() < 2) return;
        final SingleMessage singleMessage = message.get(1);


        if (!(singleMessage instanceof final Image image)) return;
        if (image.isEmoji()) return;
        // 发送一张图片

        // 记录
        synchronized (this.lastSendImages) {
            this.lastSendImages.put(senderQq, new Info(image, System.currentTimeMillis()));
        }

        // 提示
        final Runnable runnable = () ->
                group.sendMessage(new MessageChainBuilder()
                        .append(new QuoteReply(message))
                        .append(new At(senderQq))
                        .append("""
                                \n如要确认提交三连截图，请发送消息“%s”；
                                如果截图错误，请发送新的截图；
                                确认提交后，截图将会被永久保存，并颁发主群的入群令牌。
                                忽略此消息则取消提交。""".formatted(KEY))
                        .build());
        if (offerFail(runnable)) runnable.run();
    }

    void onJoinReqMainFail(@NotNull Group auditGroup, long fromId) {
        final Runnable runnable = () -> auditGroup.sendMessage(new MessageChainBuilder()
                .append(new At(fromId))
                .append("\n请先发送三连截图到群里，再发送消息“%s”\n得到入群令牌后，再申请加入主群！".formatted(KEY))
                .build());
        if (offerFail(runnable)) runnable.run();
    }
}
