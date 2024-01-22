package cn.paper_card.qq_group_access;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

class ConfigManager {

    private final static String PATH_BOT_ID = "bot-id";

    private final static String PATH_OWNER_ID = "owner-id";
    private final static String PATH_MAIN_GROUP_ID = "main-group-id";

    private final static String PATH_AUDIT_GROUP_ID = "audit-group-id";

    private final static String PATH_SEND_MESSAGE_ON_LOGIN = "send-message-on-login";

    private final @NotNull ThePlugin plugin;

    ConfigManager(@NotNull ThePlugin plugin) {
        this.plugin = plugin;
    }

    @NotNull FileConfiguration getConfig() {
        return this.plugin.getConfig();
    }

    void setBotId(long id) {
        this.getConfig().set(PATH_BOT_ID, id);
    }

    long getBotId() {
        return this.getConfig().getLong(PATH_BOT_ID, 0);
    }

    long getOwnerId() {
        return this.getConfig().getLong(PATH_OWNER_ID, 0);
    }

    void setOwnerId(long v) {
        this.getConfig().set(PATH_OWNER_ID, v);
    }

    void setMainGroupId(long id) {
        this.getConfig().set(PATH_MAIN_GROUP_ID, id);
    }


    long getMainGroupId() {
        return this.getConfig().getLong(PATH_MAIN_GROUP_ID, 0);
    }

    void setAuditGroupId(long id) {
        this.getConfig().set(PATH_AUDIT_GROUP_ID, id);
    }


    long getAuditGroupId() {
        return this.getConfig().getLong(PATH_AUDIT_GROUP_ID, 0);
    }

    boolean isSendMessageOnLogin() {
        return this.getConfig().getBoolean(PATH_SEND_MESSAGE_ON_LOGIN, true);
    }

    void setSendMessageOnLogin(boolean v) {
        this.getConfig().set(PATH_SEND_MESSAGE_ON_LOGIN, v);
    }


    void setDefaults() {
        this.setMainGroupId(this.getMainGroupId());
        this.setAuditGroupId(this.getAuditGroupId());
        this.setBotId(this.getBotId());
        this.setOwnerId(this.getOwnerId());
        this.setSendMessageOnLogin(this.isSendMessageOnLogin());
    }

    void save() {
        this.plugin.saveConfig();
    }

    void reload() {
        this.plugin.reloadConfig();
    }
}
