package edu.uestc.peng.musicplayer;

import android.content.BroadcastReceiver;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lrc);
        bindService();
        lrcView = (LrcView) findViewById(R.id.lrcView);

        lrcView.setListener(new ILrcView.LrcViewListener() {

            public void onLrcSeeked(int newPosition, LrcRow row) {
                if (musicService != null) {
                    musicService.seekTo((int) row.time);
                }
            }
        });

        lrcView.setLoadingTipText("Loading...");

        String musicPath = musicService.getMusicPathByPosition(musicService.getCURRENT_PLAY());
        String lrc = musicPath.substring(0, musicPath.lastIndexOf('.')) + ".lrc";

        File lrcFile = new File(lrc);
        if (lrcFile.exists()) {
            StringBuffer stringBuffer = new StringBuffer();

            char[] chars = new char[1024];
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(lrcFile)));
                int lenth = -1;
                while ((lenth = in.read(chars)) != -1) {
                    stringBuffer.append(chars, 0, lenth);
                }
                in.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            ILrcBuilder builder = new DefaultLrcBuilder();
            List<LrcRow> rows = builder.getLrcRows(lrc);

            lrcView.setLrc(rows);
            beginLrcPlay();
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
