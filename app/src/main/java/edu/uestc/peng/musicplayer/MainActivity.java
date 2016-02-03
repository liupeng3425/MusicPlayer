package edu.uestc.peng.musicplayer;

import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
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

public class MainActivity extends BaseActivity implements View.OnClickListener {

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


    private Cursor cursor;

    BroadcastReceiver changeMusicBroadcastReceiver;

    IntentFilter intentFilter;
    private Handler handler;
    private Runnable updateThread;


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

        tvSongName.setOnClickListener(this);

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


        if (cursor != null && cursor.getCount() > 0) {
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

        bindService();
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
                musicService.playAnother(position);
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

                musicService.seekTo(seekBar.getProgress());
                btnPlayStop.setImageResource(R.drawable.pause);// TODO: 2016/2/2  
            }
        });

        intentFilter = new IntentFilter(Constants.ACTION_CHANGE_MUSIC);

        changeMusicBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case Constants.ACTION_CHANGE_MUSIC:
                        tvCurTime.setText(Constants.INIT_TIME);
                        seekBar.setProgress(0);
                        setTextViews();
                        break;
                }

            }
        };

        registerReceiver(changeMusicBroadcastReceiver, intentFilter);

        handler = new Handler();
        updateThread = new Runnable() {
            @Override
            public void run() {
                if (musicService != null && musicService.isPlaying()) {
                    seekBar.setProgress((int) musicService.getCurrentPosition());
                    tvCurTime.setText(String.format("%02d:%02d", musicService.getCurrentPosition() / 1000 / 60,
                            musicService.getCurrentPosition() / 1000 % 60));
                }
                handler.postDelayed(this, 500);
            }
        };
        handler.post(updateThread);

    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.e("MainActivity", "onStop");
        handler.removeCallbacks(updateThread);
        unbindService();
        unregisterReceiver(changeMusicBroadcastReceiver);
    }

//    @Override
//    protected void onResume() {
//        super.onResume();
//        registerReceiver(changeMusicBroadcastReceiver, intentFilter);
//        bindService();
//    }


    @Override
    protected void onRestart() {
        super.onRestart();
        Log.e("MainActivity", "onRestart");
        setTextViews();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // 创建退出对话框
                AlertDialog isExit = new AlertDialog.Builder(this).create();
                // 设置对话框标题
                isExit.setTitle("系统提示");
                // 设置对话框消息
                isExit.setMessage("确定要退出吗");
                // 添加选择按钮并注册监听
                isExit.setButton(DialogInterface.BUTTON_POSITIVE, "确定", listener);
                isExit.setButton(DialogInterface.BUTTON_NEGATIVE, "取消", listener);
                // 显示对话框
                isExit.show();
        }
        return super.onKeyDown(keyCode, event);
    }

    DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case AlertDialog.BUTTON_POSITIVE:
                    handler.removeCallbacks(updateThread);
                    musicService.stopSelf();
                    unbindService();
                    finish();
                    break;
                case AlertDialog.BUTTON_NEGATIVE:
                    break;
                default:
                    break;
            }
        }
    };


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add("退出");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        handler.removeCallbacks(updateThread);
        musicService.stopSelf();
        unbindService();
        finish();
        return super.onOptionsItemSelected(item);
    }

    //获取当前播放歌曲演唱者及歌名
    public String getInfoByPosition(Cursor c, int position) {
        c.moveToPosition(position);
        int titleColumn = c.getColumnIndex(MediaStore.Audio.Media.TITLE);
        int artistColumn = c.getColumnIndex(MediaStore.Audio.Media.ARTIST);
        int durationColumn = c.getColumnIndex(MediaStore.Audio.Media.DURATION);
        return c.getString(artistColumn) + " - " + c.getString(titleColumn) + " - " +
                String.format("%02d:%02d", c.getLong(durationColumn) / 1000 / 60, c.getLong(durationColumn) / 1000 % 60);
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
        seekBar.setMax((int) getDurationByPosition(cursor, musicService.getCURRENT_PLAY()));
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
                musicService.playNext();
                btnPlayStop.setImageResource(R.drawable.pause);
                break;
            case R.id.btnPre:
                musicService.playPre();
                btnPlayStop.setImageResource(R.drawable.pause);
                break;

            /**
             * 播放/暂停 按钮的事件处理。
             */
            case R.id.btnPlayStop:
                playStop();
                break;
            case R.id.tvSongName:
                Intent intent = new Intent(MainActivity.this, LrcActivity.class);
                startActivity(intent);
                break;
        }
    }

    /**
     * 如果是在暂停状态，继续播放，设置定时器，将 isPause 设置为 false，设置图标为 暂停。
     * 最后一种情况，刚打开播放器直接点击播放按钮。
     */
    private void playStop() {
        musicService.playOrPause();
        if (musicService.isPause()) {
            btnPlayStop.setImageResource(R.drawable.play);
        } else {
            btnPlayStop.setImageResource(R.drawable.pause);
        }
    }

}
