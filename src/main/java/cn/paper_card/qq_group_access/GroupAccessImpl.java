package cn.paper_card.qq_group_access;

import cn.paper_card.qq_group_access.api.GroupAccess;
import cn.paper_card.qq_group_access.api.GroupMember;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.NormalMember;
import net.mamoe.mirai.contact.PermissionDeniedException;
import net.mamoe.mirai.message.data.At;
import net.mamoe.mirai.message.data.MessageChain;
import net.mamoe.mirai.message.data.MessageChainBuilder;
import net.mamoe.mirai.message.data.PlainText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

class GroupAccessImpl implements GroupAccess {

    private final @NotNull Group group;

    private final @NotNull MessageSender messageSender;

    GroupAccessImpl(@NotNull Group group, @NotNull MessageSender sender) {
        this.group = group;
        this.messageSender = sender;
    }

    @Override
    public long getId() {
        return this.group.getId();
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
        final Runnable runnable = () -> group.sendMessage(message);
        if (this.messageSender.offerFail(runnable)) runnable.run();
    }

    @Override
    public void sendAtMessage(long qq, @NotNull String message) {
        final Runnable runnable = () -> {
            final MessageChain chain = new MessageChainBuilder()
                    .append(new At(qq))
                    .append(new PlainText(" "))
                    .append(new PlainText(message))
                    .build();
            group.sendMessage(chain);
        };
        if (this.messageSender.offerFail(runnable)) runnable.run();
    }

    @Override
    public void sendAtMessage(@NotNull List<Long> qqs, @NotNull String message) {
        final Runnable runnable = () -> {
            final MessageChainBuilder builder = new MessageChainBuilder();

            for (Long qq : qqs) {
                builder.append(new At(qq));
                builder.append(" ");
            }

            builder.append(new PlainText(message));

            final MessageChain build = builder.build();

            group.sendMessage(build);
        };

        if (this.messageSender.offerFail(runnable)) runnable.run();
    }

    @Override
    public void setMute(long qq, int seconds) throws Exception {
        final NormalMember normalMember = group.get(qq);
        if (normalMember == null) throw new Exception("QQ%d不在群里！".formatted(qq));
        normalMember.mute(seconds);
    }

    @Override
    public @Nullable GroupMember getMember(long qq) {
        final NormalMember nm = this.group.get(qq);
        if (nm == null) return null;

        return new GroupMemberImpl(nm);
    }

    @Override
    public @NotNull List<GroupMember> getAllMembers() {
        final LinkedList<GroupMember> list = new LinkedList<>();
        for (final NormalMember member : group.getMembers()) {
            list.add(new GroupMemberImpl(member));
        }
        return list;
    }
}