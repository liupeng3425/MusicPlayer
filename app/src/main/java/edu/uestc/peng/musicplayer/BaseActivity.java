package edu.uestc.peng.musicplayer;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class BaseActivity extends AppCompatActivity implements ServiceConnection {

    protected MusicService musicService = null;
    private Intent intent;
    private boolean isBind = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ComponentName componentName = new ComponentName(this, MusicService.class);
        intent = new Intent();
        intent.setComponent(componentName);
        startService(intent);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.e("MusicPlayer", "onServiceConnected");
        musicService = ((MusicService.MusicBinder) service).getService();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        isBind = false;
        musicService = null;
    }

    public void bindService() {
        if (!isBind) {
            bindService(intent, this, BIND_AUTO_CREATE);
            isBind = true;
        }
    }

    public void unbindService() {
        if (isBind) {
            unbindService(this);
            isBind = false;
        }
    }
}
