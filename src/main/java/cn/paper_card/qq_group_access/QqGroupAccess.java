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

    }

    @Override
    public void onDisable() {
    }

    @Override
    public long getMainGroupId() {
        return 860768366L;
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
