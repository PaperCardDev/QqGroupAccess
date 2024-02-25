package cn.paper_card.qq_group_access;

import net.mamoe.mirai.message.data.MessageChain;
import net.mamoe.mirai.message.data.PlainText;
import net.mamoe.mirai.message.data.SingleMessage;
import org.jetbrains.annotations.NotNull;

class MyUtil {
    static String getAllPainTexts(@NotNull MessageChain messages) {
        final StringBuilder sb = new StringBuilder();
        for (SingleMessage message : messages) {
            if (message instanceof PlainText pt) {
                sb.append(pt.getContent());
            }
        }
        return sb.toString();
    }

    static String minutesAndSeconds(long ms) {
        long secs = ms / 1000L;
        final long minutes = secs / 60;
        secs %= 60;

        final StringBuilder sb = new StringBuilder();
        if (minutes != 0) {
            sb.append(minutes);
            sb.append('分');
        }

        if (secs != 0 || minutes == 0) {
            sb.append(secs);
            sb.append('秒');
        }
        return sb.toString();
    }
}