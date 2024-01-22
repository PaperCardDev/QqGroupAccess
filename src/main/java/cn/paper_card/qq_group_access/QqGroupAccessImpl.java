package cn.paper_card.qq_group_access;

import cn.paper_card.qq_group_access.api.GroupAccess;
import cn.paper_card.qq_group_access.api.QqGroupAccessApi;
import org.jetbrains.annotations.NotNull;

class QqGroupAccessImpl implements QqGroupAccessApi {

    private final @NotNull ThePlugin plugin;

    QqGroupAccessImpl(@NotNull ThePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public long getMainGroupId() {
        return this.plugin.getConfigManager().getMainGroupId();
    }

    @Override
    public long getAuditGroupId() {
        return this.plugin.getConfigManager().getAuditGroupId();
    }

    @Override
    public @NotNull GroupAccess createMainGroupAccess() throws Exception {
        return this.plugin.createMainGroupAccess();
    }

    @Override
    public @NotNull GroupAccess createAuditGroupAccess() throws Exception {
        return this.plugin.createAuditGroupAccess();
    }
}
