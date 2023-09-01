package cn.paper_card.qq_group_access;

import org.jetbrains.annotations.NotNull;

public interface QqGroupAccessApi {
    @SuppressWarnings("unused")
    interface GroupAccess {
        boolean hasMember(long qq) throws Exception;

        void setGroupMemberRemark(long qq, @NotNull String remark) throws Exception;

        void sendNormalMessage(@NotNull String message);

        void sendAtMessage(long qq, @NotNull String message);
    }

    @SuppressWarnings("unused")
    long getMainGroupId();

    long getAuditGroupId();

    @SuppressWarnings("unused")
    @NotNull GroupAccess getMainGroupAccess() throws Exception;

    @SuppressWarnings("unused")
    @NotNull GroupAccess getAuditGroupAccess() throws Exception;
}
