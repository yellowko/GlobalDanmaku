package com.yellowko.GlobalDanmaku;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by admin on 2018/5/30.
 */

public class FloatingVideoService extends Service {
    public static boolean isStarted = false;

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;

    private MediaPlayer mediaPlayer;
    private View displayView=null;
    String  realVideoLink;
    private Intent intentFloatingService = new Intent("com.yellowko.GlobalDanmaku.FloatingService.Reciver");
    private Intent intentMainactivity = new Intent("com.yellowko.GlobalDanmaku.MainActivity.Reciver");
    private int screenWidth = 1920;
    private int screenHeight = 1080;

    @Override
    public void onCreate() {
        super.onCreate();
        isStarted = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        layoutParams = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        layoutParams.format = PixelFormat.RGBA_8888;
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE| WindowManager.LayoutParams.FLAG_FULLSCREEN ;
        layoutParams.width = 800;
        layoutParams.height = 450;
        layoutParams.x = 300;
        layoutParams.y = 300;

        mediaPlayer = new MediaPlayer();
    }

    @Override
    public void onDestroy(){
        handler.removeCallbacks(timer1s);
        releaseMediaPlayer();
        windowManager.removeViewImmediate(displayView);
        windowManager.removeViewImmediate(mMediaController);
        isStarted = false;
        super.onDestroy();
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        realVideoLink=intent.getStringExtra("realVideoLink");
        int floatingVideoSize=intent.getIntExtra("floatingVideoSize",800);
        if(displayView==null){

            screenWidth = intent.getIntExtra("height",1920);
            screenHeight = intent.getIntExtra("width",1080);
            if(screenWidth*9/16>screenHeight){
                screenWidth=screenHeight*16/9;
            }
            else {
                screenHeight=screenWidth*9/16;
            }
            showFloatingWindow();

        }
        else if(floatingVideoSize!=layoutParams.width){
            layoutParams.width=floatingVideoSize;
            layoutParams.height=floatingVideoSize*9/16;
            displayView.setLayoutParams(layoutParams);
            windowManager.updateViewLayout(mMediaController, layoutParams);
            windowManager.updateViewLayout(displayView, layoutParams);

        }
        else{
            mediaPlayer.reset();
            openVideoFromLink(realVideoLink);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void showFloatingWindow() {
        if (Settings.canDrawOverlays(this)) {
            //通过LayoutInflater来找到未载入界面的组件
            LayoutInflater layoutInflater = LayoutInflater.from(this);
            displayView = layoutInflater.inflate(R.layout.video_display, null);
            displayView.setOnTouchListener(new FloatingOnTouchListener());
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            SurfaceView surfaceView = displayView.findViewById(R.id.video_display_surfaceview);
            final SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    mediaPlayer.setDisplay(surfaceHolder);
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {

                }
            });
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mediaAllTime =mediaPlayer.getDuration()/1000;
                    mseekbar.setMax(mediaAllTime);
                    mediaNowTime=0;
                    mtext_now_time.setText(s2time(mediaNowTime));
                    mtext_all_time.setText(s2time(mediaAllTime));
                    intentFloatingService.putExtra("seekms", mediaNowTime*1000);
                    intentFloatingService.putExtra("modify", true);
                    sendBroadcast(intentFloatingService);
                    mMediaController.setVisibility(View.GONE);
                    handler.removeCallbacks(timer1s);
                    mediaPlayer.start();
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    handler.removeCallbacks(timer1s);
                    intentMainactivity.putExtra("operation",1);
                    sendBroadcast(intentMainactivity);
                }
            });
            openVideoFromLink(realVideoLink);
            windowManager.addView(displayView, layoutParams);
            mMediaController=layoutInflater.inflate(R.layout.media_controller, null);
            mMediaController.setOnTouchListener(new FloatingOnTouchListener());

            createMediaController();
        }
    }


    private void openVideoFromLink(String VideoLink){
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0");
            headers.put("referer", "https://www.bilibili.com");
            mediaPlayer.setDataSource(FloatingVideoService.this, Uri.parse(VideoLink),headers);
            mediaPlayer.prepareAsync();
        }
        catch (IOException e) {
            Toast.makeText(FloatingVideoService.this, "无法打开视频源", Toast.LENGTH_LONG).show();
        }
    }

    private View mMediaController;
    private ImageButton mbtn_close;
    private ImageButton mbtn_hide_show;
    private ImageButton mbtn_full_screen;
    private ImageButton mbtn_pause_start;
    private TextView mtext_now_time;
    private SeekBar mseekbar;
    private TextView mtext_all_time;
    private void createMediaController(){

        mMediaController.setVisibility(View.GONE);
        mbtn_close = (ImageButton) mMediaController.findViewById(R.id.btn_close);
        mbtn_hide_show = (ImageButton) mMediaController.findViewById(R.id.btn_hide_show);
        mbtn_full_screen = (ImageButton) mMediaController.findViewById(R.id.btn_full_screen);
        mbtn_pause_start = (ImageButton) mMediaController.findViewById(R.id.btn_pause_start);
        mtext_now_time=(TextView)mMediaController.findViewById(R.id.text_now_time);
        mseekbar = (SeekBar) mMediaController.findViewById(R.id.sb_alpha);
        mtext_all_time=(TextView)mMediaController.findViewById(R.id.text_all_time);

        View.OnClickListener mMediaControllerListener=new ControllerOnClickListener();
        mMediaController.setOnTouchListener(new ControllerOnTouchListener());
        mbtn_close.setOnClickListener(mMediaControllerListener);
        mbtn_hide_show.setOnClickListener(mMediaControllerListener);
        mbtn_full_screen.setOnClickListener(mMediaControllerListener);
        mbtn_pause_start.setOnClickListener(mMediaControllerListener);
        mtext_now_time.setOnClickListener(mMediaControllerListener);

        if(mediaAllTime !=0)
            mseekbar.setMax(mediaAllTime);
        mseekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                //mediaNowTime=seekBar.getProgress();
                mtext_now_time.setText(s2time(seekBar.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mediaPlayer.pause();
                handler.removeCallbacks(timer1s);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mediaNowTime=seekBar.getProgress();
                intentFloatingService.putExtra("seekms", mediaNowTime*1000);
                intentFloatingService.putExtra("modify", true);
                sendBroadcast(intentFloatingService);
                mediaPlayer.seekTo(mediaNowTime*1000);
                mediaPlayer.start();
                handler.postDelayed(timer1s, 1000);
            }
        });

        mtext_all_time.setOnClickListener(mMediaControllerListener);

        windowManager.addView(mMediaController, layoutParams);
    }

    private class FloatingOnTouchListener implements View.OnTouchListener {
        private int x;
        private int y;
        private int downX;
        private int downY;
        private boolean isTouching=false;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX= x = (int) event.getRawX();
                    downY= y = (int) event.getRawY();
                    return false;
                case MotionEvent.ACTION_MOVE:
                    int nowX = (int) event.getRawX();
                    int nowY = (int) event.getRawY();
                    if(!isTouching){
                        if(nowX - downX > 50|| downX-nowX > 50||nowY -downY > 50||downY - nowY> 50){
                            isTouching=true;
                        }
                    }
                    else {
                        int movedX = nowX - x;
                        int movedY = nowY - y;
                        x = nowX;
                        y = nowY;
                        layoutParams.x = layoutParams.x + movedX;
                        layoutParams.y = layoutParams.y + movedY;
                        windowManager.updateViewLayout(mMediaController, layoutParams);
                        windowManager.updateViewLayout(displayView, layoutParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if(isTouching){//判断为拖拽行为，消耗掉点击
                        isTouching=false;
                        return true;
                    }
                    else{
                        mMediaController.setVisibility(View.VISIBLE);
                        mediaNowTime=mediaPlayer.getCurrentPosition()/1000;
                        mtext_now_time.setText(s2time(mediaNowTime));
                        handler.postDelayed(timer1s, 1000);
                        return false;
                    }
                default:
                    return false;
            }
        }
    }

    int mediaAllTime =0;
    int mediaNowTime =0;
    Boolean danmakuShow = true;
    Boolean full_screen = false;
    private class ControllerOnClickListener implements View.OnClickListener {
        private int oldWidth;
        private int oldHeight;

        @Override
        public void onClick(View view) {
            if (view == mbtn_close) {
                stopSelf();
            }
            else if (view == mbtn_hide_show){
                danmakuShow=!danmakuShow;
                intentFloatingService.putExtra("modify", false);
                intentFloatingService.putExtra("danmakuShow", danmakuShow);
                sendBroadcast(intentFloatingService);
                if(danmakuShow){
                    mbtn_hide_show.setImageDrawable(getDrawable(R.drawable.ic_check_box_black_24dp));
                }
                else{
                    mbtn_hide_show.setImageDrawable(getDrawable(R.drawable.ic_check_box_outline_blank_black_24dp));
                }
            }
            else if (view == mbtn_full_screen){
                if(full_screen){
                    mbtn_full_screen.setImageDrawable(getDrawable(R.drawable.ic_fullscreen_black_24dp));
                    layoutParams.width=oldWidth;
                    layoutParams.height=oldHeight;
                    layoutParams.screenOrientation= ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
                    displayView.setLayoutParams(layoutParams);
                    windowManager.updateViewLayout(mMediaController, layoutParams);
                    windowManager.updateViewLayout(displayView, layoutParams);
                }
                else {
                    mbtn_full_screen.setImageDrawable(getDrawable(R.drawable.ic_fullscreen_exit_black_24dp));
                    oldWidth=layoutParams.width;
                    oldHeight=layoutParams.height;
                    layoutParams.height = screenHeight;
                    layoutParams.width = screenWidth;
                    layoutParams.screenOrientation= ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
                    displayView.setLayoutParams(layoutParams);
                    windowManager.updateViewLayout(mMediaController, layoutParams);
                    windowManager.updateViewLayout(displayView, layoutParams);
                }
                full_screen=!full_screen;
            }
            else if(view == mbtn_pause_start){
                if(mediaPlayer.isPlaying()){
                    mediaPlayer.pause();
                    mbtn_pause_start.setImageDrawable(getDrawable(R.drawable.ic_play_arrow_black_24dp));
                }
                else{
                    mediaPlayer.start();
                    mbtn_pause_start.setImageDrawable(getDrawable(R.drawable.ic_pause_black_24dp));
                }

            }
        }
    }

    private class ControllerOnTouchListener implements View.OnTouchListener {
        private int x;
        private int y;
        private int downX;
        private int downY;
        private boolean isTouching=false;
        private int downMediaPlayerCurrentPosition;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX= x = (int) event.getRawX();
                    downY= y = (int) event.getRawY();
                    return false;
                case MotionEvent.ACTION_MOVE:
                    int nowX = (int) event.getRawX();
                    if(!isTouching){
                        //确定为滑动行为
                        if(nowX - downX > 50|| downX-nowX > 50) {
                            isTouching=true;
                            handler.removeCallbacks(timer1s);
                            mediaPlayer.pause();
                            downMediaPlayerCurrentPosition=mediaPlayer.getCurrentPosition();
                        }
                    }
                    else {
                        //根据滑动长度改变视频进度条
                        int nowMediaPlayerCurrentPosition=downMediaPlayerCurrentPosition+(nowX - downX)*45;
                        mediaPlayer.seekTo(nowMediaPlayerCurrentPosition);
                        mseekbar.setProgress(nowMediaPlayerCurrentPosition/1000);
                        mtext_now_time.setText(s2time(nowMediaPlayerCurrentPosition/1000));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    //判断为拖拽行为，消耗掉点击，并释放滑动
                    if(isTouching){
                        mediaNowTime=mediaPlayer.getCurrentPosition()/1000;
                        intentFloatingService.putExtra("seekms", mediaNowTime*1000);
                        intentFloatingService.putExtra("modify", true);
                        sendBroadcast(intentFloatingService);
                        mediaPlayer.start();
                        handler.postDelayed(timer1s, 1000);
                        isTouching=false;
                        return true;
                    }
                    else{//点击行为
                        mMediaController.setVisibility(View.GONE);
                        handler.removeCallbacks(timer1s);
                        return false;
                    }
                default:
                    return false;
            }
        }
    }

    private String s2time(int seconds){
        return seconds/60+":"+String.format("%02d", seconds%60);
    }

    Handler handler=new Handler();
    Runnable timer1s=new Runnable() {
        @Override
        public void run() {
            handler.postDelayed(this, 1000);
            mediaNowTime++;
            mtext_now_time.setText(s2time(mediaNowTime));
            mseekbar.setProgress(mediaNowTime);
        }
    };

    private void releaseMediaPlayer() {
        // If the media player is not null, then it may be currently playing a sound.
        if (mediaPlayer != null) {
            // Regardless of the current state of the media player, release its resources
            // because we no longer need it.
            mediaPlayer.release();

            // Set the media player back to null. For our code, we've decided that
            // setting the media player to null is an easy way to tell that the media player
            // is not configured to play an audio file at the moment.
            mediaPlayer = null;
        }
    }

}

