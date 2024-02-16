package cn.paper_card.qq_group_access;

import cn.paper_card.player_coins.api.PlayerCoinsApi;
import cn.paper_card.qq_bind.api.BindInfo;
import cn.paper_card.qq_bind.api.QqBindApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class MemberSignService {

    private final @NotNull ThePlugin plugin;

    private final static long coins = 6;

    MemberSignService(@NotNull ThePlugin plugin) {
        this.plugin = plugin;
    }

    @Nullable String onSign(long qq) {
        final QqBindApi api = plugin.getQqBindApi();
        if (api == null) return "QqBindApi不可用！";

        final BindInfo info;

        try {
            info = api.getBindService().queryByQq(qq);
        } catch (Exception e) {
            plugin.handleException(e);
            return e.toString();
        }

        if (info == null) {
            return """
                    你的QQ还没绑定正版账号噢
                    无法通过打卡获得硬币
                    请先进行绑定，如果不知道怎么操作
                    请咨询管理员~""";
        }

        final PlayerCoinsApi coinsApi = plugin.getPlayerCoinsApi();
        if (coinsApi == null) return "PlayerCoinsApi不可用！";

        try {
            coinsApi.addCoins(info.uuid(), coins, "群打卡，QQ: %d".formatted(qq));
        } catch (Exception e) {
            plugin.handleException(e);
            return e.toString();
        }

        return """
                打卡成功，你已获得%d枚硬币~
                游戏名：%s""".formatted(coins, info.name());
    }
}