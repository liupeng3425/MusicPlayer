package edu.uestc.peng.musicplayer;

/**
 * 使用 Mediaplayer 类来播放音乐。
 * <p/>
 * 首先搜索了存储卡根目录下的音乐文件（仅 .mp3 格式，在 musicFilter 类中修改即可增加更多的音乐格式），使用了 File Filter 来筛选文件。
 * 将文件名字和绝对路径分别存放在 songNameList 和 pathList 中，并利用这些来创建 ArrayAdapter。
 * <p/>
 * 用 ListView 显示歌曲列表。
 * <p/>
 * SeekBar 显示播放进度，同时具有跳转功能。
 * <p/>
 * 使用 ImageButton 实现了 播放/暂停、下一曲、上一曲 的按钮，实现了 播放/暂停 按钮的图片切换。
 * <p/>
 * 需要的改进的地方：不能搜索其他目录的歌曲、手机上没有歌曲时程序不能启动，闪退。
 * <p/>
 * 希望可以使用系统的数据库，查询到歌曲的信息，而不是显示文件名。
 */

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telecom.ConnectionService;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, ServiceConnection {

    /**
     * 歌曲名字的列表
     */
    public List<String> songNameList;

    /**
     * 下一曲，上一曲，播放/暂停按钮
     */
    private ImageButton btnNext, btnPre, btnPlayStop;

    /**
     * 进度条
     */
    private SeekBar seekBar;

    /**
     * 文本框：歌曲名，总时间，当前时间
     */
    private TextView tvSongName, tvTotalTime, tvCurTime;

    /**
     * 歌曲列表视图
     */
    private ListView listView;

    /**
     * 歌曲名字适配器
     */
    private ArrayAdapter<String> nameAdapter;

    private MusicService musicService = null;

    private Cursor cursor;

//    private Timer timer;

//    private Handler handler = new Handler() {
//        @Override
//        public void handleMessage(Message msg) {
//            super.handleMessage(msg);
//            switch (msg.what) {
//                case Constants.UPDATE_SEEK_BAR:
//                    seekBar.setMax((int) getDurationByPosition(cursor, CURRENT_PLAY));
//                    seekBar.setProgress((int) musicService.getCurrentPosition());
//                    break;
//                case Constants.UPDATE_TV_CURRENT_TIME:
//                    tvCurTime.setText(String.format("%02d:%02d", musicService.getCurrentPosition() / 60000, musicService.getCurrentPosition() / 1000 % 60));
//                    break;
//                case Constants.UPDATE_TEXT_VIEWS:
//                    setTextViews();
//                    break;
//                default:
//                    break;
//            }
//        }
//    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /**
         * 初始化部件
         */
        btnNext = (ImageButton) findViewById(R.id.btnNext);
        btnPlayStop = (ImageButton) findViewById(R.id.btnPlayStop);
        btnPre = (ImageButton) findViewById(R.id.btnPre);

        listView = (ListView) findViewById(R.id.listView);
        seekBar = (SeekBar) findViewById(R.id.seekBar);

        tvCurTime = (TextView) findViewById(R.id.tvCurTime);
        tvSongName = (TextView) findViewById(R.id.tvSongName);
        tvTotalTime = (TextView) findViewById(R.id.tvTotalTime);

        btnNext.setOnClickListener(this);
        btnPlayStop.setOnClickListener(this);
        btnPre.setOnClickListener(this);

        songNameList = new ArrayList<>();

        final String[] mCursorCols = new String[]{
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


        if (cursor != null) {
            for (int i = 0; i < cursor.getCount(); i++) {
                songNameList.add(getInfoByPosition(cursor, i));
            }
        } else {
            Toast.makeText(this, Constants.NO_MUSIC_FOUND, Toast.LENGTH_LONG).show();
            finish();
        }


        /**
         * 构造函数创建 nameAdapter
         */
        nameAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, songNameList);

        /**
         * 设置 listView 的适配器为 nameAdapter
         */
        listView.setAdapter(nameAdapter);


        /**
         * player 默认加载第一首歌，adapter 的序号从 0 开始
         *
         * 同时也启动了服务。
         */
        startMusicService(cursor, 0, Constants.ACTION_SET_PLAYER);

    }

    @Override
    protected void onStart() {
        super.onStart();

        /**
         * 设置 listView 列表项目的点击事件监听器
         *
         * 点击某个歌曲，立即停止所有的播放任务，从头开始播放点击的歌曲。
         */
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                setTextViews();
                startMusicService(cursor, position, Constants.ACTION_PLAY_ANOTHER);
                btnPlayStop.setImageResource(R.drawable.pause);
            }

        });

        /**
         * 设置 seekBar 的滑动时间侦听器。
         *
         * 在滑动的过程中，实时显示将要跳转到的时间点。onProgressChanged 方法中，新增了一个 fromUser 的参数，可以利用其判断改变是否来自用户。
         * 在开始点击的时候，显示点击位置的时间点。
         * 在点击结束时，mediaPlayer 跳转到指定时间点。如果当前是在播放状态则继续播放，如果在暂停状态，只是跳转，不播放。
         */
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurTime.setText(String.format("%02d:%02d",
                            progress / 60000,
                            progress / 1000 % 60));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                tvCurTime.setText(String.format("%02d:%02d",
                        seekBar.getProgress() / 60000,
                        seekBar.getProgress() / 1000 % 60));
            }

            @Override
            public void onStopTrackingTouch(final SeekBar seekBar) {
                tvCurTime.setText(String.format("%02d:%02d",
                        seekBar.getProgress() / 60000,
                        seekBar.getProgress() / 1000 % 60));

                startMusicService(cursor, seekBar.getProgress(), Constants.ACTION_SEEK_TO);
                btnPlayStop.setImageResource(R.drawable.pause);
            }
        });


    }

    private void startMusicService(Cursor cursor, int position, String action) {
        ComponentName componentName = new ComponentName(this, MusicService.class);
        Intent intent = new Intent(action);
        intent.setComponent(componentName);
        switch (action) {
            case Constants.ACTION_SEEK_TO:
                intent.putExtra("position", position);
                break;
            case Constants.ACTION_SET_PLAYER:
                intent.putExtra("musicPath", getMusicPathByPosition(cursor, position));
                intent.putExtra("position", position);
                break;
            case Constants.ACTION_PLAY_ANOTHER:
                intent.putExtra("musicPath", getMusicPathByPosition(cursor, position));
                intent.putExtra("position", position);
                break;
            default:
                break;
        }
        if (musicService == null)
            bindService(intent, this, BIND_AUTO_CREATE);
        else
            startService(intent);
    }

    private void startMusicService(String action) {
        ComponentName componentName = new ComponentName(this, MusicService.class);
        Intent intent = new Intent(action);
        intent.setComponent(componentName);
        if (musicService == null)
            bindService(intent, this, BIND_AUTO_CREATE);
        else
            startService(intent);
    }

    //根据位置来获取歌曲位置
    public String getMusicPathByPosition(Cursor c, int position) {
        c.moveToPosition(position);
        int dataColumn = c.getColumnIndex(MediaStore.Audio.Media.DATA);
        return c.getString(dataColumn);
    }

    //获取当前播放歌曲演唱者及歌名
    public String getInfoByPosition(Cursor c, int position) {
        c.moveToPosition(position);
        int titleColumn = c.getColumnIndex(MediaStore.Audio.Media.TITLE);
        int artistColumn = c.getColumnIndex(MediaStore.Audio.Media.ARTIST);
        return c.getString(artistColumn) + " - " + c.getString(titleColumn);
    }

    public long getDurationByPosition(Cursor cursor, int position) {
        cursor.moveToPosition(position);
        int durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
        return cursor.getLong(durationColumn);
    }

    private void setTextViews() {
        tvSongName.setText(getInfoByPosition(cursor, musicService.getCURRENT_PLAY()));
        tvTotalTime.setText(String.format("%02d:%02d",
                getDurationByPosition(cursor, musicService.getCURRENT_PLAY()) / 60 / 1000,
                getDurationByPosition(cursor, musicService.getCURRENT_PLAY()) / 1000 % 60));
    }

    /**
     * 按钮的点击事件监听器。
     *
     * @param v view
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            /**
             * 点击下一首或者上一首的按钮时，开始播放上一曲或者下一曲。
             */
            case R.id.btnNext:
                startMusicService(Constants.ACTION_PLAY_NEXT);
                break;
            case R.id.btnPre:
                startMusicService(Constants.ACTION_PLAY_PRE);
                break;

            /**
             * 播放/暂停 按钮的事件处理。
             */
            case R.id.btnPlayStop:
                playStop();
                break;
        }
    }

    /**
     * 如果是在暂停状态，继续播放，设置定时器，将 isPause 设置为 false，设置图标为 暂停。
     * 如果正在播放，暂停，取消定时器，isPause 设置为 true，设置图标为 播放。
     * 最后一种情况，刚打开播放器直接点击播放按钮。
     * 由于初始化的时候，已经默认加载了第一首歌，直接开始播放第一首歌。
     */
    private void playStop() {
        startMusicService(Constants.ACTION_PLAY_OR_PAUSE);

        if (!musicService.isPause()) {
            btnPlayStop.setImageResource(R.drawable.play);
        } else {
            btnPlayStop.setImageResource(R.drawable.pause);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.e("MusicPlayer", "onServiceConnected");
        musicService = ((MusicService.MusicBinder) service).getService();

        final Handler handler = new Handler();
        Runnable updateThread = new Runnable() {
            @Override
            public void run() {
                seekBar.setMax((int) getDurationByPosition(cursor, musicService.getCURRENT_PLAY()));
                seekBar.setProgress((int) musicService.getCurrentPosition());
                tvCurTime.setText(String.format("%02d:%02d", musicService.getCurrentPosition() / 1000 / 60,
                        musicService.getCurrentPosition() / 1000 % 60));
                setTextViews();
                handler.postDelayed(this, 500);
            }
        };

        handler.postDelayed(updateThread, 500);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

}
