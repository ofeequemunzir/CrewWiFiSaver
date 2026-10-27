package com.crewwifisaver.app;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import java.net.*;
import java.io.*;
import java.util.Locale;

public class MonitorService extends Service {
 public static final String CHANNEL="crew_monitor", ACTION_STOP="com.crewwifisaver.app.STOP", ACTION_LOGOUT="com.crewwifisaver.app.LOGOUT";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private long previousBytes=-1, previousTime=-1, idleStart=-1;
 private boolean busy=false, logoutAttempted=false;
 private final Runnable tick=new Runnable(){public void run(){sample();handler.postDelayed(this,10000);}};
 public void onCreate(){super.onCreate();NotificationChannel ch=new NotificationChannel(CHANNEL,"Crew Wi-Fi monitoring",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(ch);startForeground(12,notification("Starting monitoring"));handler.post(tick);}
 public int onStartCommand(Intent intent,int flags,int startId){if(intent!=null && ACTION_STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}if(intent!=null && ACTION_LOGOUT.equals(intent.getAction()))logout();return START_STICKY;}
 private Notification notification(String detail){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);Intent out=new Intent(this,MonitorService.class).setAction(ACTION_LOGOUT);PendingIntent lo=PendingIntent.getService(this,2,out,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("Crew WiFi Saver").setContentText(detail).setContentIntent(pi).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Log out now",lo).setOngoing(true).build();}
 private void report(String state,String detail,double rate){getSharedPreferences("prefs",MODE_PRIVATE).edit().putString("state",state).putString("detail",detail).putFloat("rate",(float)rate).apply();getSystemService(NotificationManager.class).notify(12,notification(detail));sendBroadcast(new Intent("com.crewwifisaver.app.UPDATE").setPackage(getPackageName()));}
 private Network wifi(){ConnectivityManager cm=getSystemService(ConnectivityManager.class);for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))return n;}return null;}
 private void sample(){Network network=wifi();if(network==null){idleStart=-1;previousBytes=-1;report("paused","Wi-Fi unavailable; monitoring paused",0);return;}
 long now=SystemClock.elapsedRealtime(),rx=TrafficStats.getTotalRxBytes(),tx=TrafficStats.getTotalTxBytes();if(rx<0||tx<0){report("unknown","Device traffic counters unavailable",0);return;}long bytes=rx+tx;double rate=0;if(previousBytes>=0&&now>previousTime){rate=Math.max(0,bytes-previousBytes)*60000.0/1024/(now-previousTime);}else{previousBytes=bytes;previousTime=now;report("monitoring","Sampling total device traffic (approximate)",0);return;}previousBytes=bytes;previousTime=now;
 android.content.SharedPreferences p=getSharedPreferences("prefs",MODE_PRIVATE);int threshold=p.getInt("traffic",50),mins=p.getInt("minutes",15);boolean enabled=p.getBoolean("auto",true);if(rate>=threshold){idleStart=-1;logoutAttempted=false;}else if(idleStart<0)idleStart=now;
 long elapsed=idleStart<0?0:now-idleStart;long remain=Math.max(0,mins*60000L-elapsed);String detail=String.format(Locale.US,"%.1f KB/min total · idle %d:%02d remaining",rate,remain/60000,(remain/1000)%60);
 if(!enabled){idleStart=-1;detail="Automatic logout paused · "+String.format(Locale.US,"%.1f KB/min total",rate);}report("monitoring",detail,rate);
 if(enabled&&idleStart>=0&&elapsed>=mins*60000L&&!logoutAttempted){logoutAttempted=true;logout();}}
 public void logout(){if(busy)return;Network n=wifi();if(n==null){report("unknown","Cannot log out: ship Wi-Fi unavailable",0);return;}busy=true;report("logging_out","Requesting logout.com over Wi-Fi",0);new Thread(()->{String result="";try{HttpURLConnection conn=(HttpURLConnection)n.openConnection(new URL("http://logout.com/"));conn.setConnectTimeout(9000);conn.setReadTimeout(9000);conn.setInstanceFollowRedirects(true);conn.setUseCaches(false);int code=conn.getResponseCode();result="Logout request returned HTTP "+code+"; session unverified";conn.disconnect();}catch(Exception e){result="Logout request failed; session unverified";}final String message=result;handler.post(()->{busy=false;idleStart=-1;report("unknown",message,0);});}).start();}
 public void onDestroy(){handler.removeCallbacks(tick);super.onDestroy();}public IBinder onBind(Intent i){return null;}
}
