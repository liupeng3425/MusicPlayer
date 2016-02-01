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
                // TODO: 2016/1/30
                playNext();
            }
        });

        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.start();
            }
        });
    }

    private void sendUpdateBroadcast(String action){
        Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    public int getCURRENT_PLAY() {
        return CURRENT_PLAY;
    }

    private void playNext() {
        CURRENT_PLAY = (CURRENT_PLAY + 1) % cursor.getCount();
        cursor.moveToPosition(CURRENT_PLAY);
        int musicPathIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
        String musicPath = cursor.getString(musicPathIndex);
        setPlayer(musicPath);
    }

    private void playPre() {
        CURRENT_PLAY--;
        if (CURRENT_PLAY < 0) CURRENT_PLAY = cursor.getCount() - 1;
        cursor.moveToPrevious();
        int musicPathIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
        String musicPath = cursor.getString(musicPathIndex);
        setPlayer(musicPath);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        switch (intent.getAction()) {
            case Constants.ACTION_PLAY_OR_PAUSE:
                playOrPause();
                break;
            case Constants.ACTION_PLAY_ANOTHER:
                playAnother(intent.getStringExtra("musicPath"));
                CURRENT_PLAY = intent.getIntExtra("position", 0);
                break;
            case Constants.ACTION_SET_PLAYER:
                setPlayer(intent.getStringExtra("musicPath"));
                CURRENT_PLAY = intent.getIntExtra("position", 0);
                break;
            case Constants.ACTION_PREPARE:
                break;
            case Constants.ACTION_SEEK_TO:
                seekTo(intent.getIntExtra("position", 0));
                break;
            case Constants.ACTION_PLAY_NEXT:
                playNext();
                break;
            case Constants.ACTION_PLAY_PRE:
                playPre();
                break;
            default:
                break;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void seekTo(int position) {
        mediaPlayer.seekTo(position);
        isPause = false;
    }

    private void playAnother(String musicPath) {
        mediaPlayer.reset();
        setPlayer(musicPath);
        isPause = false;
    }

    private void playOrPause() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPause = true;
        } else {
            mediaPlayer.start();
            isPause = false;
        }
    }

    /**
     * 设置播放器的资源路径，使播放器完全做好播放的准备。
     * 同时设置歌曲名文本框和总时间文本框。
     *
     * @param musicPath 歌曲的绝对路径
     */
    public void setPlayer(String musicPath) {
        mediaPlayer.reset();

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
        sendUpdateBroadcast(Constants.ACTION_CHANGE_MUSIC);
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
