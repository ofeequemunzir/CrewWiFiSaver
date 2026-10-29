package com.crewwifisaver.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class CrewWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_LOCK = "com.crewwifisaver.app.WIDGET_LOCK";

    private static final String ACTION_TOGGLE =
            "com.crewwifisaver.app.WIDGET_TOGGLE";

    @Override
    public void onUpdate(Context context,
                         AppWidgetManager manager,
                         int[] widgetIds) {
        for (int id : widgetIds) {
            updateWidget(context, manager, id);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_LOCK.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE);
            boolean enabled = !prefs.getBoolean("screen_lock", true);
            prefs.edit().putBoolean("screen_lock", enabled)
                    .putBoolean("auto", enabled).apply();

            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            onUpdate(context, manager, manager.getAppWidgetIds(
                    new ComponentName(context, CrewWidgetProvider.class)));
            return;
        }

        if (ACTION_TOGGLE.equals(intent.getAction())) {
            SharedPreferences prefs =
                    context.getSharedPreferences("prefs", Context.MODE_PRIVATE);

            String status = prefs.getString("connection_status", "unknown");

            if ("connected".equals(status)) {
                Intent logout = new Intent(context, MonitorService.class);
                logout.setAction(MonitorService.ACTION_LOGOUT);
                try {
                    context.startForegroundService(logout);
                } catch (Exception e) {
                    android.widget.Toast.makeText(context,
                            "Unable to start logout",
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            } else {
                Intent login = new Intent(context, MainActivity.class);
                login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(login);
            }

            AppWidgetManager manager =
                    AppWidgetManager.getInstance(context);

            int[] ids = manager.getAppWidgetIds(
                    new ComponentName(context, CrewWidgetProvider.class));

            onUpdate(context, manager, ids);
        }
    }

    private void updateWidget(Context context,
                              AppWidgetManager manager,
                              int id) {

        RemoteViews views = new RemoteViews(
                context.getPackageName(), R.layout.crew_widget);

        SharedPreferences prefs =
                context.getSharedPreferences("prefs", Context.MODE_PRIVATE);

        boolean connected = "connected".equals(
                prefs.getString("connection_status", "unknown"));

        views.setTextViewText(R.id.widget_wifi,
                connected ? "◉" : "○");

        views.setTextViewText(R.id.widget_status,
                connected ? "Connected" : "Disconnected");

        boolean lockEnabled = prefs.getBoolean("screen_lock", true);
        views.setTextViewText(R.id.widget_lock_toggle,
                lockEnabled ? "Logout on lock: ON" : "Logout on lock: OFF");

        Intent lock = new Intent(context, CrewWidgetProvider.class);
        lock.setAction(ACTION_LOCK);
        PendingIntent lockPending = PendingIntent.getBroadcast(
                context, 1, lock,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_lock_toggle, lockPending);

        Intent toggle = new Intent(context, CrewWidgetProvider.class);
        toggle.setAction(ACTION_TOGGLE);

        PendingIntent pending = PendingIntent.getBroadcast(
                context, 0, toggle,
                PendingIntent.FLAG_UPDATE_CURRENT |
                PendingIntent.FLAG_IMMUTABLE);

        views.setOnClickPendingIntent(R.id.widget_wifi, pending);

        manager.updateAppWidget(id, views);
    }
}
