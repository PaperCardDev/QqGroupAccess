package cn.paper_card.qq_group_access;

import cn.paper_card.player_qq_bind.QqBindApi;
import cn.paper_card.player_qq_group_remark.PlayerQqGroupRemarkApi;
import cn.paper_card.player_qq_in_group.PlayerQqInGroupApi;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.Member;
import net.mamoe.mirai.contact.NormalMember;
import net.mamoe.mirai.contact.PermissionDeniedException;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.MemberJoinEvent;
import net.mamoe.mirai.event.events.MemberLeaveEvent;
import net.mamoe.mirai.message.data.At;
import net.mamoe.mirai.message.data.MessageChainBuilder;
import net.mamoe.mirai.message.data.PlainText;
import net.mamoe.mirai.message.data.QuoteReply;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("unused")
public final class QqGroupAccess extends JavaPlugin implements QqGroupAccessApi {

    private QqBindApi qqBindApi = null;
    private PlayerQqGroupRemarkApi playerQqGroupRemarkApi = null;

    private PlayerQqInGroupApi playerQqInGroupApi = null;

    private final @NotNull Object lock = new Object();


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

    @Override
    public void onEnable() {

        this.getQqBindApi();
        this.getPlayerQqGroupRemarkApi();
        this.getPlayerQqInGroupApi();

        GlobalEventChannel.INSTANCE.subscribeAlways(GroupMessageEvent.class, event -> {
            final Group group = event.getGroup();
            if (group.getId() != this.getMainGroupId()) return;

            final Member sender = event.getSender();
            final String messageStr = event.getMessage().contentToString();

            if (this.playerQqGroupRemarkApi != null) {
                this.playerQqGroupRemarkApi.updateRemarkByGroupMessage(sender.getId(), sender.getNameCard());
            }

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
            if (event.getGroupId() != this.getMainGroupId()) return;
            if (this.playerQqInGroupApi != null) {
                this.playerQqInGroupApi.onMemberJoinGroup(event.getMember().getId());
            }
        });

        GlobalEventChannel.INSTANCE.subscribeAlways(MemberLeaveEvent.class, event -> {
            if (event.getGroupId() != this.getMainGroupId()) return;
            if (this.playerQqInGroupApi != null) {
                this.playerQqInGroupApi.onMemberQuitGroup(event.getMember().getId());
            }
        });


        GlobalEventChannel.INSTANCE.subscribeAlways(MemberJoinEvent.class, event -> {

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

    }

    @Override
    public void onDisable() {
    }

    @Override
    public long getMainGroupId() {
        return 860768366L;
    }

    @Override
    public long getAuditGroupId() {
        return 747760104L;
    }

    @Override
    public @NotNull GroupAccess getMainGroupAccess() throws Exception {
        final long mainGroupId = this.getMainGroupId();
        synchronized (this.lock) {
            final List<Bot> instances = Bot.getInstances();
            if (instances.size() == 0) throw new Exception("没有任何一个QQ机器人！");

            for (final Bot instance : Bot.getInstances()) {
                if (instance == null) continue;
                if (!instance.isOnline()) continue;
                final Group group = instance.getGroup(mainGroupId);
                if (group == null) continue;

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

            throw new Exception("没有任何一个机器人可以访问QQ群：%d".formatted(mainGroupId));
        }
    }
}
