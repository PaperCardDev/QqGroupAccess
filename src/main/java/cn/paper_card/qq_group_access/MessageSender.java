package cn.paper_card.qq_group_access;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

class MessageSender implements Runnable {
    private final @NotNull BlockingQueue<Runnable> messageSends;

    private final @NotNull Random random;

    private MyScheduledTask myScheduledTask = null;

    private final @NotNull ThePlugin plugin;

    MessageSender(@NotNull ThePlugin plugin) {
        this.plugin = plugin;
        this.messageSends = new LinkedBlockingQueue<>();
        this.random = new Random();
    }

    void init() {
        if (this.myScheduledTask == null) {
            this.myScheduledTask = this.plugin.getTaskScheduler().runTaskTimerAsynchronously(this, 20, 20);
        }
    }

    void destroy() {
        final MyScheduledTask t = this.myScheduledTask;
        this.myScheduledTask = null;
        if (t != null) t.cancel();
        this.messageSends.clear();
    }

    boolean offerFail(@NotNull Runnable runnable) {
        return !this.messageSends.offer(runnable);
    }

    @Override
    public void run() {
        final Runnable runnable = this.messageSends.poll();
        if (runnable == null) return;

        final long l = this.random.nextLong(200);

        try {
            Thread.sleep(l);
        } catch (InterruptedException e) {
            this.plugin.getSLF4JLogger().warn("", e);
            return;
        }

        runnable.run();
    }
}
