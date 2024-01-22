package cn.paper_card.qq_group_access;

import cn.paper_card.qq_group_access.api.GroupMember;
import net.mamoe.mirai.contact.NormalMember;
import org.jetbrains.annotations.NotNull;

class GroupMemberImpl implements GroupMember {

    private final @NotNull NormalMember member;

    GroupMemberImpl(@NotNull NormalMember member) {
        this.member = member;
    }

    @Override
    public long getQq() {
        return this.member.getId();
    }

    @Override
    public String getNick() {
        return this.member.getNick();
    }

    @Override
    public String getNameCard() {
        return this.member.getNameCard();
    }

    @Override
    public int getJoinTime() {
        return this.member.getJoinTimestamp();
    }

    @Override
    public int getActiveLevel() {
        return this.member.getActive().getTemperature();
    }

    @Override
    public String getSpecialTitle() {
        return this.member.getSpecialTitle();
    }

    @Override
    public int getPermissionLevel() {
        return this.member.getPermission().getLevel();
    }

    @Override
    public int getQLevel() {
        return this.member.queryProfile().getQLevel();
    }

    @Override
    public void kick(String message) throws Exception {
        try {
            this.member.kick(message);
        } catch (RuntimeException e) {
            throw new Exception(e);
        }
    }
}