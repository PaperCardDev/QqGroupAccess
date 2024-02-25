package cn.paper_card.qq_group_access;

import cn.paper_card.qq_bind.api.BindInfo;
import cn.paper_card.qq_bind.api.QqBindApi;
import cn.paper_card.qq_group_member_info.api.QqGroupMemberInfoApi;
import cn.paper_card.qq_group_member_info.api.QqGroupMemberInfoService;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.Member;
import net.mamoe.mirai.contact.NormalMember;
import net.mamoe.mirai.data.UserProfile;
import net.mamoe.mirai.event.events.MemberJoinEvent;
import net.mamoe.mirai.event.events.MemberJoinRequestEvent;
import net.mamoe.mirai.event.events.MemberLeaveEvent;
import net.mamoe.mirai.message.data.At;
import net.mamoe.mirai.message.data.MessageChainBuilder;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

class MainGroupHandler {

    private final @NotNull ThePlugin plugin;

    private final @NotNull HashMap<Long, Long> leaveTimes;

    MainGroupHandler(@NotNull ThePlugin plugin) {
        this.plugin = plugin;
        this.leaveTimes = new HashMap<>();
    }

    private boolean offerFail(@NotNull Runnable runnable) {
        return this.plugin.getMessageSender().offerFail(runnable);
    }

    void onJoinRequest(@NotNull MemberJoinRequestEvent event) {

        this.plugin.getSLF4JLogger().info("DEBUG: 入群申请事件");

        final Group group = event.getGroup();
        if (group == null) return;

        // 进入主群的申请

        final long fromId = event.getFromId();
        final String fromNick = event.getFromNick();
        final Long invitorId = event.getInvitorId();

        this.plugin.getSLF4JLogger().info("入群申请 {fromId: %d, fromNick: %s, invitorId: %s}".formatted(
                fromId, fromNick, invitorId
        ));

        // 检查是否频繁退群入群
        final Long leveTime;
        synchronized (this.leaveTimes) {
            leveTime = this.leaveTimes.get(fromId);
        }

        if (leveTime != null) {
            final long ms = System.currentTimeMillis() - leveTime;
            if (ms < 60 * 60 * 1000L && ms > 0) {
                final String msg = """
                        不自动处理的入群申请，因为在%s前离开了群聊
                        QQ：%s (%s)""".formatted(
                        MyUtil.minutesAndSeconds(ms),
                        fromNick,
                        fromId
                );

                final Runnable runnable = () -> group.sendMessage(msg);

                if (offerFail(runnable)) runnable.run();
                return;
            }
        }

        // 查询QQ绑定，自动同意老玩家
        final QqBindApi api = this.plugin.getQqBindApi();
        if (api != null) {
            try {
                final BindInfo bindInfo = api.getBindService().queryByQq(fromId);

                if (bindInfo != null) {
                    event.accept();

                    final OfflinePlayer offlinePlayer = this.plugin.getServer().getOfflinePlayer(bindInfo.uuid());
                    String name = offlinePlayer.getName();
                    if (name == null) name = bindInfo.uuid().toString();
                    final String name1 = name;

                    final Runnable runnable = () -> group.sendMessage("""
                            自动同意老玩家入群：
                            游戏名：%s
                            QQ：%s (%d)""".formatted(
                            name1, fromNick, fromId
                    ));

                    if (offerFail(runnable)) runnable.run();

                    return;
                }
            } catch (Exception e) {
                this.plugin.getSLF4JLogger().error("", e);
                final Runnable run = () -> group.sendMessage(e.toString());
                if (offerFail(run)) run.run();
            }
        }

        // 查询入群令牌
        final boolean hasToken = this.plugin.getAuditGroupHandler().hasToken(fromId);

        if (hasToken) {
            final Runnable runnable = () -> group.sendMessage("""
                    自动同意过审玩家入群：
                    QQ：%s (%d)""".formatted(fromNick, fromId));

            if (offerFail(runnable)) runnable.run();

            event.accept();
            return;
        }

        // 是否在审核群中
        final Group auditGroup = event.getBot().getGroup(this.plugin.getConfigManager().getAuditGroupId());
        if (auditGroup != null) {
            final NormalMember member = auditGroup.get(fromId);
            if (member != null) {
                event.reject(false, "请先通过审核！");

                this.plugin.getAuditGroupHandler().onJoinReqMainFail(auditGroup, fromId);

                final Runnable runnable = () -> {
                    final String msg = """
                            已自动拒绝无令牌的入群申请：
                            %s (%d)""".formatted(fromNick, fromId);
                    group.sendMessage(msg);
                };
                if (offerFail(runnable)) runnable.run();
            }
        }


        // 其它情况
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

        if (offerFail(runnable)) runnable.run();
    }

    @NotNull
    private static Runnable sendMessageOldPlayerJoin(MemberJoinEvent event, int qLevel, String name, boolean hasToken) {
        return () -> {
            final StringBuilder sb = new StringBuilder();
            sb.append("\n欢迎回到PaperCard~");
            sb.append("\n您的游戏名：");
            sb.append(name);
            sb.append(name);
            sb.append("\n您的QQ等级：");
            sb.append(qLevel);
            sb.append("\n请查看群公告【新人必看】（服务器地址在这里噢）");

            if (hasToken) {
                sb.append("\n已回收您的入群令牌");
            }
            sb.append("\n祝您游戏愉快~");


            final Group group = event.getGroup();

            group.sendMessage(new MessageChainBuilder()
                    .append(new At(event.getMember().getId()))
                    .append(sb.toString())
                    .build());
        };
    }

    private boolean checkPlayerJoinGroup(long qq, MemberJoinEvent event, int qLevel, boolean hasToken) {
        final QqBindApi api = this.plugin.getQqBindApi();

        if (api == null) return false;

        // 看看是否老玩家入群
        final BindInfo bindInfo;

        try {
            bindInfo = api.getBindService().queryByQq(qq);
        } catch (Exception e) {
            this.plugin.getSLF4JLogger().error("qq bind service -> query by qq", e);
            final Runnable runnable = () -> event.getGroup().sendMessage(e.toString());
            if (offerFail(runnable)) runnable.run();
            return false;
        }

        if (bindInfo == null) return false;

        final Runnable runnable = sendMessageOldPlayerJoin(event, qLevel, bindInfo.name(), hasToken);

        if (offerFail(runnable)) runnable.run();

        return true;
    }

    void onJoin(@NotNull MemberJoinEvent event) {
        final Group mainGroup = event.getGroup();
        final NormalMember member = event.getMember();
        final long memberId = member.getId();

        final AuditGroupHandler auditGroupHandler = this.plugin.getAuditGroupHandler();

        // 如果在审核群里，提示退出审核群
        auditGroupHandler.onJoinMain(mainGroup, memberId);

        // 移除令牌
        final boolean hasToken = auditGroupHandler.removeToken(memberId);

        final int qqLevel;
        final UserProfile userProfile = event.getMember().queryProfile();
        qqLevel = userProfile.getQLevel();

        // 记录群成员信息
        this.plugin.recordQqInfoWhenJoin(event.getMember(), true, userProfile);

        // 检查是否老玩家入群
        if (this.checkPlayerJoinGroup(memberId, event, qqLevel, hasToken)) return;

        // 新玩家入群
        final Runnable runnable = () -> {

            final StringBuilder sb = new StringBuilder();
            sb.append('\n');
            sb.append("欢迎新玩家入裙~");
            sb.append("\n您的QQ等级为：");
            sb.append(qqLevel);
            sb.append("\n请查看群公告【新人必看】（服务器地址在这里噢）");
            if (hasToken) {
                sb.append("\n已回收您的入群令牌，祝您游戏愉快~");
            } else {
                sb.append("\n未检测到您的入群令牌！");
            }
            final Group group = event.getGroup();

            group.sendMessage(new MessageChainBuilder()
                    .append(new At(memberId))
                    .append(sb.toString())
                    .build());

        };
        if (offerFail(runnable)) runnable.run();
    }

    void onLeave(@NotNull MemberLeaveEvent event) {
        // 退出主群
        final Group group = event.getGroup();
        final Member member = event.getMember();

        final long qq = member.getId();

        // 保存退出时间
        synchronized (this.leaveTimes) {
            this.leaveTimes.put(qq, System.currentTimeMillis());
        }

        // 更新信息
        final QqGroupMemberInfoApi infoApi = this.plugin.getQqGroupMemberInfoApi();
        if (infoApi != null) {
            final QqGroupMemberInfoService service = infoApi.getQqGroupMemberInfoService();
            try {
                final boolean updated = service.updateInGroup(qq, false);
                this.plugin.getSLF4JLogger().info("%s (%d) 退出主群，信息更新：%s".formatted(member.getNameCard(), member.getId(), updated));
            } catch (Exception e) {
                this.plugin.getSLF4JLogger().error("", e);
            }
        }


        final QqBindApi qqBindApi = this.plugin.getQqBindApi();

        String name;
        if (qqBindApi == null) {
            name = "无法访问QQ绑定API！";
        } else {
            final BindInfo bindInfo;
            try {
                bindInfo = qqBindApi.getBindService().queryByQq(qq);
                if (bindInfo == null) {
                    name = "未绑定游戏角色";
                } else {
                    name = bindInfo.name();
                }
            } catch (Exception e) {
                this.plugin.getSLF4JLogger().error("qq bind service -> query by qq", e);
                name = e.toString();
            }
        }

        final String finalName = name;
        final Runnable runnable = () -> group.sendMessage("%s (%d) 离开了群聊\n游戏名：%s".formatted(member.getNick(), member.getId(),
                finalName));

        if (offerFail(runnable)) runnable.run();
    }
}
