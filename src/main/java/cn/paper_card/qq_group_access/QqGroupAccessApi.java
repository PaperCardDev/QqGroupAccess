package cn.paper_card.qq_group_access;

import org.jetbrains.annotations.NotNull;

public interface QqGroupAccessApi {
    @SuppressWarnings("unused")
    interface GroupAccess {
        boolean hasMember(long qq) throws Exception;

        void setGroupMemberRemark(long qq, @NotNull String remark) throws Exception;

        void sendNormalMessage(@NotNull String message);

        void sendAtMessage(long qq, @NotNull String message);

        void setMute(long qq, int seconds) throws Exception;
    }

    @SuppressWarnings("unused")
    long getMainGroupId();

    long getAuditGroupId();

    @SuppressWarnings("unused")
    @NotNull GroupAccess createMainGroupAccess() throws Exception;

    @SuppressWarnings("unused")
    @NotNull GroupAccess createAuditGroupAccess() throws Exception;
}
