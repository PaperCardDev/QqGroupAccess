package cn.paper_card.qq_group_access;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface QqGroupAccessApi {

    @SuppressWarnings("unused")
    interface GroupMember {

        long getQq(); // QQ 号码

        String getNick(); // 昵称

        int getJoinTime(); // 入群时间

        int getActiveLevel(); // 活跃等级

        String getSpecialTitle(); // 群头衔

        int getPermissionLevel(); // 权限等级

        void kick(String message) throws Exception;
    }

    @SuppressWarnings("unused")
    interface GroupAccess {
        boolean hasMember(long qq) throws Exception;

        void setGroupMemberRemark(long qq, @NotNull String remark) throws Exception;

        void sendNormalMessage(@NotNull String message);

        void sendAtMessage(long qq, @NotNull String message);

        void setMute(long qq, int seconds) throws Exception;

        @NotNull List<GroupMember> getAllMembers();
    }

    @SuppressWarnings("unused")
    long getMainGroupId();

    long getAuditGroupId();

    @SuppressWarnings("unused")
    @NotNull GroupAccess createMainGroupAccess() throws Exception;

    @SuppressWarnings("unused")
    @NotNull GroupAccess createAuditGroupAccess() throws Exception;
}
