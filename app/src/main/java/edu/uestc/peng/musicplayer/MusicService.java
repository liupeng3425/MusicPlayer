package edu.uestc.peng.musicplayer;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;

import java.io.IOException;
import java.util.logging.Handler;

public class MusicService extends Service {

    private MediaPlayer mediaPlayer = new MediaPlayer();
    private MusicBinder musicBinder = new MusicBinder();

    private boolean isPause = true;
    private Cursor cursor;

    private int CURRENT_PLAY = 0;

    public boolean isPause() {
        return isPause;
    }

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    public MusicService() {
    }

    public long getCurrentPosition() {
        return mediaPlayer.getCurrentPosition();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        String[] mCursorCols = new String[]{
                "audio._id AS _id", // index must match IDCOLIDX below
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.DURATION
        };

        /**
         * 过滤时长 >10000 ms.
         */

        cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                mCursorCols,
                "duration > 10000", null, null);

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                playNext();
            }
        });

        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                sendUpdateBroadcast(Constants.ACTION_CHANGE_MUSIC);
                if (!isPause) {
                    mp.start();
                }
            }
        });

        setPlayer(CURRENT_PLAY);
    }

    private void sendUpdateBroadcast(String action) {
        Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    public int getCURRENT_PLAY() {
        return CURRENT_PLAY;
    }

    public boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }

    public void playNext() {
        CURRENT_PLAY = (CURRENT_PLAY + 1) % cursor.getCount();
        isPause = false;
        setPlayer(CURRENT_PLAY);
    }

    public void playPre() {
        CURRENT_PLAY--;
        isPause = false;
        if (CURRENT_PLAY < 0) CURRENT_PLAY = cursor.getCount() - 1;
        setPlayer(CURRENT_PLAY);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    public void seekTo(int position) {
        isPause = false;
        mediaPlayer.seekTo(position);
        mediaPlayer.start();
    }

    public void playAnother(int position) {
        mediaPlayer.reset();
        CURRENT_PLAY = position;
        setPlayer(position);
        isPause = false;
    }

    public void playOrPause() {
        if (mediaPlayer.isPlaying()) {
            isPause = true;
            mediaPlayer.pause();
        } else {
            isPause = false;
            mediaPlayer.start();
        }
    }

    /**
     * 设置播放器的资源路径，使播放器完全做好播放的准备。
     * 同时设置歌曲名文本框和总时间文本框。
     */
    public void setPlayer(int position) {
        mediaPlayer.reset();

        String musicPath = getMusicPathByPosition(position);
        try {
            mediaPlayer.setDataSource(musicPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //根据位置来获取歌曲位置
    public String getMusicPathByPosition(int position) {
        cursor.moveToPosition(position);
        int dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
        return cursor.getString(dataColumn);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mediaPlayer.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return musicBinder;
    }
}
