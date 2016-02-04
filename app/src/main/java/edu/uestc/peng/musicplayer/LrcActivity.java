package edu.uestc.peng.musicplayer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import edu.uestc.peng.musicplayer.view.DefaultLrcBuilder;
import edu.uestc.peng.musicplayer.view.ILrcBuilder;
import edu.uestc.peng.musicplayer.view.ILrcView;
import edu.uestc.peng.musicplayer.view.LrcRow;
import edu.uestc.peng.musicplayer.view.LrcView;

public class LrcActivity extends BaseActivity {

    private LrcView lrcView;
    private int mPlayTimerDuration = 1000;
    private Timer mTimer;
    private TimerTask mTask;
    private BroadcastReceiver broadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lrc);
        lrcView = (LrcView) findViewById(R.id.lrcView);

        lrcView.setListener(new ILrcView.LrcViewListener() {

            public void onLrcSeeked(int newPosition, LrcRow row) {
                if (musicService != null) {
                    musicService.seekTo((int) row.time);
                }
            }
        });

        lrcView.setLoadingTipText("Loading...");
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                stopLrcPlay();
                lrcView.setLrc(null);
                readLrc(getLrcPath());
            }
        };

        registerReceiver(broadcastReceiver, new IntentFilter(Constants.ACTION_CHANGE_MUSIC));

        bindService();
    }

    private String getLrcPath() {
        String musicPath = musicService.getMusicPathByPosition(musicService.getCURRENT_PLAY());
        String lrc = musicPath.substring(0, musicPath.lastIndexOf('.')) + ".lrc";
        return lrc;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        super.onServiceConnected(name, service);

        readLrc(getLrcPath());
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(broadcastReceiver);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        registerReceiver(broadcastReceiver, new IntentFilter(Constants.ACTION_CHANGE_MUSIC));
    }

    private boolean readLrc(String lrcPath) {
        File lrcFile = new File(lrcPath);
        if (lrcFile.exists()) {
            StringBuilder stringBuilder = new StringBuilder();
            char[] chars = new char[1024];
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(lrcFile)));
                int length = -1;
                while ((length = in.read(chars)) != -1) {
                    stringBuilder.append(chars, 0, length);
                }
                in.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            ILrcBuilder builder = new DefaultLrcBuilder();
            List<LrcRow> rows = builder.getLrcRows(stringBuilder.toString());

            lrcView.setLrc(rows);
            beginLrcPlay();
            return true;
        } else {
            return false;
        }
    }

    private void beginLrcPlay() {
        if (mTimer == null) {
            mTimer = new Timer();
            mTask = new LrcTask();
            mTimer.scheduleAtFixedRate(mTask, 0, mPlayTimerDuration);
        }
    }

    public void stopLrcPlay() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    class LrcTask extends TimerTask {

        long beginTime = -1;

        @Override
        public void run() {
            if (beginTime == -1) {
                beginTime = System.currentTimeMillis();
            }

            final long timePassed = musicService.getCurrentPosition();
            LrcActivity.this.runOnUiThread(new Runnable() {

                public void run() {
                    lrcView.seekLrcToTime(timePassed);
                }
            });

        }
    }

    ;
}
