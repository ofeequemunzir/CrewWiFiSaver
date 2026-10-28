package com.crewwifisaver.app;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import java.net.*;
import java.io.*;
import java.util.Locale;

public class MonitorService extends Service {
    private ScreenLockReceiver screenReceiver;
    private ScreenLockTimer screenTimer;

    private void setupScreenMonitoring() {
        screenTimer = new ScreenLockTimer(() ->
            logout()
        );

        screenReceiver = new ScreenLockReceiver(
            new ScreenLockReceiver.Listener() {
                @Override
                public void onScreenOff() {
                    int seconds = getSharedPreferences(
                        "prefs", MODE_PRIVATE
                    ).getInt("lock_seconds", 30);

                    if (getSharedPreferences("prefs", MODE_PRIVATE)
        .getBoolean("auto", true)) {
    LogoutAlarm.schedule(MonitorService.this, seconds);
} else {
    LogoutAlarm.cancel(MonitorService.this);
}
                    report("monitoring",
                        "Screen off - countdown started", 0);
                }

                @Override
                public void onScreenOn() {
                    LogoutAlarm.cancel(MonitorService.this);
                    report("monitoring",
                        "Screen on - countdown cancelled", 0);
                }
            }
        );

        android.content.IntentFilter filter =
            new android.content.IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, filter);
    }

 public static final String CHANNEL="crew_monitor", ACTION_STOP="com.crewwifisaver.app.STOP", ACTION_LOGOUT="com.crewwifisaver.app.LOGOUT";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private long previousBytes=-1, previousTime=-1, idleStart=-1;
 private boolean busy=false, logoutAttempted=false;
 private final Runnable tick=new Runnable(){public void run(){sample();handler.postDelayed(this,10000);}};
 public void onCreate(){super.onCreate();NotificationChannel ch=new NotificationChannel(CHANNEL,"Crew Wi-Fi monitoring",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(ch);startForeground(12,notification("Starting monitoring"));setupScreenMonitoring();handler.post(tick);}
 public int onStartCommand(Intent intent,int flags,int startId){if(intent!=null && ACTION_STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}if(intent!=null && ACTION_LOGOUT.equals(intent.getAction()))logout();
if(intent!=null && "com.crewwifisaver.app.AUTO_LOGOUT".equals(intent.getAction()))logout();return START_STICKY;}
 private Notification notification(String detail){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);Intent out=new Intent(this,MonitorService.class).setAction(ACTION_LOGOUT);PendingIntent lo=PendingIntent.getService(this,2,out,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("Crew WiFi Saver").setContentText(detail).setContentIntent(pi).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Log out now",lo).setOngoing(true).build();}
 private void report(String state,String detail,double rate){getSharedPreferences("prefs",MODE_PRIVATE).edit().putString("state",state).putString("detail",detail).putFloat("rate",(float)rate).apply();getSystemService(NotificationManager.class).notify(12,notification(detail));sendBroadcast(new Intent("com.crewwifisaver.app.UPDATE").setPackage(getPackageName()));}
 private Network wifi(){ConnectivityManager cm=getSystemService(ConnectivityManager.class);for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))return n;}return null;}
 private void sample(){return;} private void oldTrafficSample(){Network network=wifi();if(network==null){idleStart=-1;previousBytes=-1;report("paused","Wi-Fi unavailable; monitoring paused",0);return;}
 long now=SystemClock.elapsedRealtime(),rx=TrafficStats.getTotalRxBytes(),tx=TrafficStats.getTotalTxBytes();if(rx<0||tx<0){report("unknown","Device traffic counters unavailable",0);return;}long bytes=rx+tx;double rate=0;if(previousBytes>=0&&now>previousTime){rate=Math.max(0,bytes-previousBytes)*60000.0/1024/(now-previousTime);}else{previousBytes=bytes;previousTime=now;report("monitoring","Sampling total device traffic (approximate)",0);return;}previousBytes=bytes;previousTime=now;
 android.content.SharedPreferences p=getSharedPreferences("prefs",MODE_PRIVATE);int threshold=p.getInt("traffic",50),mins=p.getInt("minutes",15);boolean enabled=p.getBoolean("auto",true);if(rate>=threshold){idleStart=-1;logoutAttempted=false;}else if(idleStart<0)idleStart=now;
 long elapsed=idleStart<0?0:now-idleStart;long remain=Math.max(0,mins*60000L-elapsed);String detail=String.format(Locale.US,"%.1f KB/min total · idle %d:%02d remaining",rate,remain/60000,(remain/1000)%60);
 if(!enabled){idleStart=-1;detail="Automatic logout paused · "+String.format(Locale.US,"%.1f KB/min total",rate);}report("monitoring",detail,rate);
 if(enabled&&idleStart>=0&&elapsed>=mins*60000L&&!logoutAttempted){logout();}}
 public void logout(){
    if (logoutAttempted) return;
    logoutAttempted = true;
getSharedPreferences("prefs", MODE_PRIVATE)
    .edit()
    .putLong("last_logout_attempt", System.currentTimeMillis())
    .apply();
    report("unknown", "Sending background logout request...", 0);

    new Thread(() -> {
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL url =
                new java.net.URL("http://logout.com/");
            conn = (java.net.HttpURLConnection)
                url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setUseCaches(false);

            int code = conn.getResponseCode();
java.net.URL currentUrl = url;
int redirects = 0;

while (code >= 300 && code < 400 && redirects < 10) {
    String location = conn.getHeaderField("Location");
    if (location == null) break;

    currentUrl = new java.net.URL(currentUrl, location);
    String protocol = currentUrl.getProtocol();
    if (!protocol.equals("http") && !protocol.equals("https")) {
        throw new java.io.IOException("Unsupported redirect");
    }

    conn.disconnect();
    conn = (java.net.HttpURLConnection) currentUrl.openConnection();
    conn.setRequestMethod("GET");
    conn.setInstanceFollowRedirects(false);
    conn.setConnectTimeout(10000);
    conn.setReadTimeout(10000);
    conn.setUseCaches(false);

    code = conn.getResponseCode();
    redirects++;
}

try (java.io.InputStream input = conn.getInputStream()) {
    byte[] buffer = new byte[4096];
    while (input.read(buffer) != -1) {}
} catch (java.io.IOException ignored) {
    // The portal may disconnect the network during logout.
}

            report("unknown",
                "Logout HTTP " + code +
                " - verify paid session ended", 0);
        } catch (Exception e) {
            report("unknown",
                "Logout request failed: " +
                e.getMessage(), 0);
        } finally {
            if (conn != null) conn.disconnect();
            logoutAttempted = false;
        }
    }).start();
}
public void onDestroy(){LogoutAlarm.cancel(this);handler.removeCallbacks(tick);if(screenTimer!=null)LogoutAlarm.cancel(MonitorService.this);if(screenReceiver!=null)unregisterReceiver(screenReceiver);super.onDestroy();}public IBinder onBind(Intent i){return null;}
}
