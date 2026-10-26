package com.crewwifisaver.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.TrafficStats;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import android.content.Intent;
import android.provider.Settings;
import android.text.format.Formatter;
import java.util.Locale;

public class MainActivity extends Activity {
 private final Handler handler = new Handler(Looper.getMainLooper());
 private TextView readout, status;
 private long lastBytes = -1;
 private long lastTime = -1;
 private boolean running = false;
 private final Runnable sample = new Runnable() {
  @Override public void run() {
   if (!running) return;
   long rx=TrafficStats.getTotalRxBytes(), tx=TrafficStats.getTotalTxBytes();
   long now=android.os.SystemClock.elapsedRealtime();
   if(rx==TrafficStats.UNSUPPORTED || tx==TrafficStats.UNSUPPORTED){ readout.setText("Traffic counters unavailable"); }
   else {
    long bytes=rx+tx;
    if(lastBytes>=0 && now>lastTime) {
     double kbmin=Math.max(0,bytes-lastBytes)/1024.0*60000.0/(now-lastTime);
     readout.setText(String.format(Locale.US,"%.1f KB/min",kbmin));
    }
    lastBytes=bytes;lastTime=now;
   }
   handler.postDelayed(this, 2000);
  }
 };
 @Override public void onCreate(Bundle b) { super.onCreate(b);
  LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);root.setPadding(36,56,36,24);root.setBackgroundColor(Color.WHITE);
  TextView title=new TextView(this);title.setText("Crew WiFi Saver");title.setTextSize(26);title.setTextColor(Color.rgb(20,40,50));root.addView(title);
  TextView explanation=new TextView(this);explanation.setText("TEST MODE — measures total device traffic while this screen is open. Does not log out or consume your credentials.");explanation.setPadding(0,20,0,20);root.addView(explanation);
  readout=new TextView(this);readout.setText("Waiting for first sample...");readout.setTextSize(29);root.addView(readout);
  status=new TextView(this);status.setText("Traffic includes all device network interfaces, not only ship Wi-Fi. Baseline testing only.");status.setPadding(0,15,0,30);root.addView(status);
  Button wifi=new Button(this);wifi.setText("Open Wi-Fi settings / choose ship network");wifi.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));root.addView(wifi);
  TextView note=new TextView(this);note.setText("To test: connect to ship Wi-Fi, log in normally, keep this screen open, and compare readings when idle versus downloading. Screen-off monitoring and auto logout are not implemented yet.");note.setPadding(0,30,0,0);root.addView(note);
  setContentView(root);
 }
 @Override protected void onResume(){super.onResume();running=true;lastBytes=-1;lastTime=-1;handler.post(sample);}
 @Override protected void onPause(){running=false;handler.removeCallbacks(sample);super.onPause();}
}
