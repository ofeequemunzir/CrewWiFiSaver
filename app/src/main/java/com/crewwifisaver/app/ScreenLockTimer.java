package com.crewwifisaver.app;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

public class ScreenLockTimer {
    private final Handler handler =
        new Handler(Looper.getMainLooper());

    private final Runnable onTimeout;
    private Runnable pending;
    private long deadline = 0;

    public ScreenLockTimer(Runnable onTimeout) {
        this.onTimeout = onTimeout;
    }

    public void start(int seconds) {
        cancel();
        if (seconds <= 0) return;

        deadline = SystemClock.elapsedRealtime()
            + seconds * 1000L;

        pending = () -> {
            pending = null;
            deadline = 0;
            onTimeout.run();
        };

        handler.postAtTime(
            pending,
            SystemClock.uptimeMillis()
                + seconds * 1000L
        );
    }

    public void cancel() {
        if (pending != null) {
            handler.removeCallbacks(pending);
            pending = null;
        }
        deadline = 0;
    }

    public long remainingSeconds() {
        if (deadline == 0) return 0;
        return Math.max(0,
            (deadline - SystemClock.elapsedRealtime()
                + 999) / 1000);
    }
}
