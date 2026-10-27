package com.crewwifisaver.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScreenLockReceiver extends BroadcastReceiver {

    public interface Listener {
        void onScreenOff();
        void onScreenOn();
    }

    private final Listener listener;

    public ScreenLockReceiver(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        switch (intent.getAction()) {
            case Intent.ACTION_SCREEN_OFF:
                listener.onScreenOff();
                break;

            case Intent.ACTION_SCREEN_ON:
                listener.onScreenOn();
                break;
        }
    }
}
