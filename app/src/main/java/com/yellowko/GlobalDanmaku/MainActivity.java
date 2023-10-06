package com.yellowko.GlobalDanmaku;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    Intent i = null;
    Intent FloatingVideoIntent = null;
    Switch msw_danmaku;
    EditText medittext;
    Button maddVideo;
    Switch msw_showName;
    Switch msw_autoNext;
    Switch msw_allowedClipBoard;
    SeekBar msb_alpha;
    SeekBar msb_floatingVideoSize;

    int videoIndex = 0;
    int width = 1080;
    int height = 1920;
    float alpha = 0.5f;
    boolean isDanmakuOn = false;
    boolean showName = false;
    String danmakuLink;
    String realVideoLink;
    boolean isLive = true;
    Boolean autoNext = false;
    boolean allowedClipboard = false;
    VideoList mvideoList = new VideoList();

    int floatingVideoSize = 800;

    int roomid = 0;
    String videoId = "";
    String lastVideoId = "";

    VideoClass mvideoClass;
    private Pattern roomidPattern = Pattern.compile("^[0-9]*$");
    private Pattern videoidPattern = Pattern.compile("(^(av|AV)?[0-9]*$|^BV[0-9|a-z|A-Z]+$)");

    private Pattern avPattern=Pattern.compile("^(av|AV)(?=[0-9]*$)");
    private Pattern bvPattern=Pattern.compile("(^BV[0-9|a-z|A-Z]+$)");

    Pattern biliRoomidPattern = Pattern.compile("(?<=live.bilibili.com/)[0-9]+");
    Pattern biliVideoidPattern = Pattern.compile("(?<=((www|m).bilibili.com/video|b23.tv)/)(av[0-9]+|BV[0-9|a-z|A-Z]+)");
    Pattern videourlPattern = Pattern.compile("(?<=video_url: ')[\\s|\\S]*?(?=')");
    Pattern videopartPattern = Pattern.compile("(?<=\"videos\":)[0-9]*?(?=,)");
    Pattern httpPattern = Pattern.compile("https?");
    Pattern cidPattern = Pattern.compile("(?<=\"cid\":)[0-9]*?(?=,)");
    Pattern authorPattern = Pattern.compile("(?<=name=\"author\" content=\")[\\s|\\S]*?(?=\")");
    Pattern videoNamePattern = Pattern.compile("(?<=name=\"title\" content=\")[\\s|\\S]*?(?=\")");
    Pattern picNamePattern = Pattern.compile("(?<=itemprop=\"image\" content=\")[\\s|\\S]*?.jpg");

    ClipData deeplink;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        deeplink = intent.getClipData();
    }

    @Override
    protected void onStart() {
        super.onStart();
        String linktext;
        //获取b站分享中的直播间号
        if (deeplink != null) {//从intent得到的数据
            //获取 ClipDescription
            String mMimetype = deeplink.getDescription().getMimeType(0);
            if (mMimetype.equals("text/uri-list")) {
                //TODO 解析Chrome分享的或直接复制的链接，目前解出来为空，类似于content://com.android.chrome.FileProvider/BlockedFile_525863828996375
                linktext = deeplink.getItemAt(0).coerceToText(this).toString();
            } else if (mMimetype.equals("text/plain")) {
                linktext = deeplink.getItemAt(0).getText().toString();
            } else {
                linktext = "";
            }
            deeplink = null;
        } else {//TODO 从剪贴板读取数据
            if (allowedClipboard) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                // 获取剪贴板的剪贴数据集
                ClipData clipData = clipboard.getPrimaryClip();

                if (clipData != null) {
                    // 从数据集中获取（粘贴）第一条文本数据
                    CharSequence temp = clipData.getItemAt(0).getText();
                    if (temp != null)
                        linktext = temp.toString();
                    else
                        linktext = "";
                    //清空剪贴板
                    //API>28 clipboard.clearPrimaryClip();
                    //clipboard.setPrimaryClip(null);
                } else
                    linktext = "";

            } else
                linktext = "";
        }
        if (!linktext.equals("")) {
            Matcher biliRoomidMather = biliRoomidPattern.matcher(linktext);
            Matcher biliVideoidMatcher = biliVideoidPattern.matcher(linktext);
            if (biliRoomidMather.find()) {
                isLive = true;
                roomid = Integer.parseInt(biliRoomidMather.group(0));
                medittext.setText(biliRoomidMather.group(0));
            } else if (biliVideoidMatcher.find()) {
                isLive = false;
                videoId = biliVideoidMatcher.group(0);
                medittext.setText(videoId);
            } else {
                Toast.makeText(MainActivity.this, R.string.roomIdIsError, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        medittext = findViewById(R.id.editText);
        FloatingVideoIntent = new Intent(MainActivity.this, FloatingVideoService.class);
        i = new Intent(MainActivity.this, FloatingService.class);
        DisplayMetrics metric = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metric);
        width = metric.widthPixels;     // 屏幕宽度（像素）
        height = metric.heightPixels;   // 屏幕高度（像素）

        getConfig();

        //动态注册广播接收器
        msgReceiver = new MsgReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.yellowko.GlobalDanmaku.MainActivity.Reciver");
        registerReceiver(msgReceiver, intentFilter);


        deeplink = getIntent().getClipData();
        setSupportActionBar(toolbar);

        maddVideo = findViewById(R.id.addVideo);
        maddVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CharSequence roomIdText = medittext.getText();
                if (TextUtils.isEmpty(roomIdText)) {
                    Toast.makeText(MainActivity.this, R.string.roomIdIsEmpty, Toast.LENGTH_SHORT).show();
                    isDanmakuOn = false;
                    msw_danmaku.setChecked(isDanmakuOn);
                } else {
                    String tempId = roomIdText.toString();
                    //addCardView();
                    if (isIdTrue(tempId) && !lastVideoId.equals(tempId) && !isLive) {
                        lastVideoId = videoId;
                        videoId = tempId;
                        Thread getLinkThread = new Thread(new getlink());
                        getLinkThread.start();
                    }
                }
            }
        });

        msb_alpha = findViewById(R.id.sb_alpha);
        msb_alpha.setProgress((int) (alpha * 10));
        msb_alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                alpha = (float) seekBar.getProgress() / 10;
                if (isDanmakuOn) {
                    i.putExtra("alpha", alpha);
                    if (FloatingService.isStarted)
                        startService(i);
                }
            }
        });


        msb_floatingVideoSize = findViewById(R.id.sb_floatingVideoSize);
        msb_floatingVideoSize.setProgress((int) ((floatingVideoSize - 480) / 64));
        msb_floatingVideoSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                floatingVideoSize = seekBar.getProgress() * 64 + 480;
                if (floatingVideoSize > 1080)
                    floatingVideoSize = 1080;
                if (isDanmakuOn) {
                    FloatingVideoIntent.putExtra("floatingVideoSize", floatingVideoSize);
                    if (FloatingVideoService.isStarted)
                        startService(FloatingVideoIntent);
                }
            }
        });

        msw_danmaku = findViewById(R.id.sw_danmaku);
        msw_danmaku.setChecked(isDanmakuOn);
        if (isDanmakuOn) {
            TextView mTextView = (TextView) findViewById(R.id.textView);
            mTextView.setText(R.string.danmu_swon);
        } else {
            TextView mTextView = (TextView) findViewById(R.id.textView);
            mTextView.setText(R.string.danmu_swoff);
        }
        msw_danmaku.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView.isChecked()) {
                    TextView mTextView = (TextView) findViewById(R.id.textView);
                    mTextView.setText(R.string.danmu_swon);
                    if (!Settings.canDrawOverlays(MainActivity.this)) {
                        Toast.makeText(MainActivity.this, "当前无权限，请授权", Toast.LENGTH_SHORT);
                        startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), 0);
                        isDanmakuOn = false;
                        msw_danmaku.setChecked(isDanmakuOn);
                    } else {
                        CharSequence roomIdText = medittext.getText();
                        if (TextUtils.isEmpty(roomIdText)) {
                            Toast.makeText(MainActivity.this, R.string.roomIdIsEmpty, Toast.LENGTH_SHORT).show();
                            isDanmakuOn = false;
                            msw_danmaku.setChecked(isDanmakuOn);
                        } else {
                            if (isIdTrue(roomIdText.toString())) {

                                Toast.makeText(MainActivity.this, "打开了", Toast.LENGTH_SHORT).show();
                                if (isLive) {
                                    roomid = Integer.parseInt(roomIdText.toString());
                                    if (FloatingService.isStarted)
                                        stopService(i);
                                    i.putExtra("roomid", roomid);
                                    i.putExtra("videoId", videoId);
                                    i.putExtra("danmakuLink", danmakuLink);
                                    i.putExtra("alpha", alpha);
                                    i.putExtra("showName", showName);
                                    i.putExtra("isLive", isLive);
                                    startService(i);
                                } else {
                                    if (FloatingVideoService.isStarted)
                                        stopService(FloatingVideoIntent);
                                    if (!startBiliVideo(videoIndex)) {
                                        isDanmakuOn = false;
                                        msw_danmaku.setChecked(isDanmakuOn);
                                        Toast.makeText(MainActivity.this, "视频请先添加到列表", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                }
                                isDanmakuOn = true;
                            } else {
                                isDanmakuOn = false;
                                msw_danmaku.setChecked(isDanmakuOn);
                                Toast.makeText(MainActivity.this, R.string.roomIdIsError, Toast.LENGTH_SHORT).show();
                            }
                        }

                    }

                } else {
                    Toast.makeText(MainActivity.this, "关闭了", Toast.LENGTH_SHORT).show();
                    TextView mTextView = (TextView) findViewById(R.id.textView);
                    mTextView.setText(R.string.danmu_swoff);
                    stopService(i);
                    stopService(FloatingVideoIntent);
                    isDanmakuOn = false;
                }
            }
        });


        msw_autoNext = findViewById(R.id.sw_autoNext);
        msw_autoNext.setChecked(autoNext);
        msw_autoNext.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                autoNext = isChecked;
            }
        });

        msw_allowedClipBoard = findViewById(R.id.sw_allowedClipBoard);
        msw_allowedClipBoard.setChecked(allowedClipboard);
        msw_allowedClipBoard.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                allowedClipboard = isChecked;
            }
        });

        msw_showName = findViewById(R.id.sw_showName);
        msw_showName.setChecked(showName);
        msw_showName.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                showName = isChecked;
                if (isDanmakuOn) {
                    i.putExtra("showName", showName);
                    startService(i);
                }
            }
        });

        int tempVideoListSize = mvideoList.size();
        for (int i = 0; i < tempVideoListSize; i++) {
            addCardView((VideoClass) mvideoList.get(i));
        }
    }

    @Override
    protected void onPause() {
        saveConfig();
        super.onPause();
    }

    private Boolean isIdTrue(String id) {
        Matcher matcher = roomidPattern.matcher(id);
        Matcher matcherVideo = videoidPattern.matcher(id);
        if (matcher.find()) {
            isLive = true;
            return true;
        } else if (matcherVideo.find()) {
            isLive = false;
            return true;
        } else
            return false;
    }

    private static final int[] mixinKeyEncTab = new int[]{
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
            33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
            61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
            36, 20, 34, 44, 52
    };

    public static String getMixinKey(String imgKey, String subKey) {
        String s = imgKey + subKey;
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            key.append(s.charAt(mixinKeyEncTab[i]));
        }
        return key.toString();
    }

    public static String md5(String content) {
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("MD5").digest(content.getBytes("UTF-8"));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("NoSuchAlgorithmException", e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UnsupportedEncodingException", e);
        }

        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            if ((b & 0xFF) < 0x10) {
                hex.append("0");
            }
            hex.append(Integer.toHexString(b & 0xFF));
        }
        return hex.toString();
    }

    class getlink implements Runnable {
        public String httpGet(String path) {
            try {
                URL url = new URL(path);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(2000);
                connection.setRequestMethod("GET");
                //获得结果码
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    //请求成功 获得返回的流
                    InputStream is = connection.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int len = -1;
                    byte[] buffer = new byte[1024]; //1kb
                    while ((len = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    is.close();
                    return new String(baos.toByteArray());
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (ProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return new String();
        }


        public String getVideoLink(String BVID, int cid) {
            JsonObject navJson = JsonParser.parseString(httpGet("https://api.bilibili.com/x/web-interface/nav?")).getAsJsonObject();
            String img_url = navJson.getAsJsonObject("data").getAsJsonObject("wbi_img").get("img_url").getAsString();
            String sub_url = navJson.getAsJsonObject("data").getAsJsonObject("wbi_img").get("sub_url").getAsString();
            String mixinKey = getMixinKey(img_url, sub_url);
            String param = "bvid=" + BVID + "&cid=" + cid + "&wts=" + System.currentTimeMillis() / 1000;
            String s = param + mixinKey;
            String wbiSign = md5(s);
            System.out.println(wbiSign);
            return "https://api.bilibili.com/x/player/wbi/playurl?" + param + "&w_rid=" + wbiSign;
        }

        public void run() {
            String path = "https://api.bilibili.com/x/web-interface/view?bvid=1x84y1e7sQ";//"https://m.bilibili.com/video/"+ videoId;

            //判断AV和BV
            if(avPattern.matcher(videoId).find())
                path = "https://api.bilibili.com/x/web-interface/view?aid="+avPattern.matcher(videoId).replaceAll("");
            else if(bvPattern.matcher(videoId).find())
                path = "https://api.bilibili.com/x/web-interface/view?bvid="+videoId;

            //获取视频链接
            String content = httpGet(path);
            if (content != null) {
                JsonObject videoBaseInfo = JsonParser.parseString(content).getAsJsonObject();
                int cid = videoBaseInfo.getAsJsonObject("data").get("cid").getAsInt();
                String BVID = videoBaseInfo.getAsJsonObject("data").get("bvid").getAsString();
                String picLink = videoBaseInfo.getAsJsonObject("data").get("pic").getAsString();

                JsonObject videoStream =JsonParser.parseString(httpGet(getVideoLink(BVID, cid))).getAsJsonObject();
                realVideoLink = videoStream.getAsJsonObject("data").getAsJsonArray("durl").get(0).getAsJsonObject().get("url").getAsString();;
                if (realVideoLink != null) {
                    //不带http标,补一个
                    if (!httpPattern.matcher(realVideoLink).find()) {
                        realVideoLink = "https:" + realVideoLink;
                    }else{
                        httpPattern.matcher(realVideoLink).replaceAll("https");
                    }
                    Matcher matcher =httpPattern.matcher(picLink);
                    if (!matcher.find()) {
                        picLink = "https:" + picLink;
                    }else{
                        picLink=matcher.replaceAll("https");
                    }


                    mvideoClass = new VideoClass();
                    mvideoClass.videoLink = realVideoLink;
                    mvideoClass.vid=BVID;
                    mvideoClass.cid=String.valueOf(cid);
                    mvideoClass.danmakuLink = "https://comment.bilibili.com/" + cid + ".xml";
                    mvideoClass.author = videoBaseInfo.getAsJsonObject("data").getAsJsonObject("owner").get("name").getAsString();
                    mvideoClass.videoName = videoBaseInfo.getAsJsonObject("data").get("title").getAsString();
                    mvideoClass.picLink=picLink;

                    Message msg = new Message();
                    handle.sendMessage(msg);
                    Looper.prepare();
                    Toast.makeText(MainActivity.this, "已添加视频", Toast.LENGTH_SHORT).show();
                    Looper.loop();

                } else {
                    Looper.prepare();
                    Toast.makeText(MainActivity.this, "无法匹配到视频源地址", Toast.LENGTH_SHORT).show();
                    Looper.loop();
                    Log.e("getlink", "无法匹配到视频源地址");
                }
            } else {
                Looper.prepare();
                Toast.makeText(MainActivity.this, "网络异常，无法获取视频网页", Toast.LENGTH_SHORT).show();
                Looper.loop();
                Log.e("getlink", "网络异常，无法获取视频网页");    //请求失败
            }
        }
    }

//获取图片
    class getpic implements Runnable {
        private String picLink1;
        private VideoClass videoClass;

        public getpic(VideoClass iVideoClass) {
            picLink1 = iVideoClass.picLink;
            videoClass=iVideoClass;
        }

        public void run() {
            try {
                URL url = new URL(picLink1);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(2000);
                connection.setRequestMethod("GET");
                //获得结果码
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    InputStream is =connection.getInputStream();
                    videoClass.pic=BitmapFactory.decodeStream(is);

                    runOnUiThread(new Runnable() {
                    @Override
                     public void run() {
                            ConstraintLayout mcardCons = (ConstraintLayout) videoClass.cardView.getChildAt(0);
                            ImageView mpic = (ImageView) mcardCons.getChildAt(0);
                            mpic.setImageBitmap(videoClass.pic);
                        }});

                    is.close();
                } else {
                    Looper.prepare();
                    Toast.makeText(MainActivity.this, "网络异常，无法获取图片", Toast.LENGTH_SHORT).show();
                    Looper.loop();
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (ProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void getConfig() {
        SharedPreferences pres = MainActivity.this.getSharedPreferences("cofig", Context.MODE_PRIVATE);
        isDanmakuOn = pres.getBoolean("isDanmakuOn", false);
        alpha = pres.getFloat("alpha", 0.5f);
        showName = pres.getBoolean("showName", false);
        floatingVideoSize = pres.getInt("floatingVideoSize", 800);
        autoNext = pres.getBoolean("autoNext", false);
        allowedClipboard = pres.getBoolean("allowedClipboard", false);
        getVideoList(pres);
    }

    private void saveConfig() {
        SharedPreferences pres = MainActivity.this.getSharedPreferences("cofig", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pres.edit();
        editor.clear();
        editor.putBoolean("isDanmakuOn", isDanmakuOn);
        editor.putFloat("alpha", alpha);
        editor.putBoolean("showName", showName);
        editor.putInt("floatingVideoSize", floatingVideoSize);
        editor.putBoolean("autoNext", autoNext);
        editor.putBoolean("allowedClipboard", allowedClipboard);
        saveVideoList(editor);
        editor.commit();
    }

    private void saveVideoList(SharedPreferences.Editor editor) {
        int tempSize = mvideoList.size();
        VideoClass temp;
        editor.putInt("videoListSize", tempSize);
        for (int i = 0; i < tempSize; i++) {
            //只存储文字信息
            temp = (VideoClass) mvideoList.get(i);
            editor.putString("videoLink" + i, temp.videoLink);
            editor.putString("danmakuLink" + i, temp.danmakuLink);
            editor.putString("author" + i, temp.author);
            editor.putString("videoName" + i, temp.videoName);
            editor.putString("vid" + i, temp.vid);
            editor.putString("cid" + i, temp.cid);
            editor.putString("picLink" + i, temp.picLink);
        }
    }

    private void getVideoList(SharedPreferences pres) {
        int videoListSize = pres.getInt("videoListSize", 0);
        for (int i = 0; i < videoListSize; i++) {
            VideoClass temp = new VideoClass();
            temp.videoLink = pres.getString("videoLink" + i, "");
            temp.danmakuLink = pres.getString("danmakuLink" + i, "");
            temp.author = pres.getString("author" + i, "");
            temp.videoName = pres.getString("videoName" + i, "");
            temp.vid = pres.getString("vid" + i, "");
            temp.cid = pres.getString("cid" + i, "");
            temp.picLink = pres.getString("picLink" + i, "");
            mvideoList.add(temp);
        }
    }

    private Boolean startBiliVideo(int index) {
        VideoClass mvideoClass = new VideoClass();
        try {
            mvideoClass = (VideoClass) mvideoList.get(index);
            //TODO 更新videoLink
            FloatingVideoIntent.putExtra("realVideoLink", mvideoClass.videoLink);
            FloatingVideoIntent.putExtra("floatingVideoSize", floatingVideoSize);
            FloatingVideoIntent.putExtra("width", width);
            FloatingVideoIntent.putExtra("height", height);
            startService(FloatingVideoIntent);

            if (FloatingService.isStarted)
                stopService(i);
            i.putExtra("roomid", roomid);
            i.putExtra("videoId", videoId);
            i.putExtra("danmakuLink", mvideoClass.danmakuLink);
            i.putExtra("alpha", alpha);
            i.putExtra("showName", showName);
            i.putExtra("isLive", isLive);
            startService(i);
            return true;
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    Handler handle = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
                addCardView(mvideoClass);
                mvideoList.add(mvideoClass);
                 }

    };

    private CardView addCardView(VideoClass videoClass) {
        LinearLayout cardViewLayout = (LinearLayout) findViewById(R.id.cardViewLayout);

        LayoutInflater layoutInflater = LayoutInflater.from(this);
        CardView mcardView = layoutInflater.inflate(R.layout.msgcard_display, null).findViewById(R.id.thisCardView);
        videoClass.cardView = mcardView;
        ConstraintLayout mcardCons = (ConstraintLayout) mcardView.getChildAt(0);

        //加载图片
        Thread getLinkThread = new Thread(new getpic(videoClass));
        getLinkThread.start();
        TextView mauthor = (TextView) mcardCons.getChildAt(1);
        TextView mvideoName = (TextView) mcardCons.getChildAt(2);
        ImageButton mimageButton = (ImageButton) mcardCons.getChildAt(3);
        mauthor.setText(videoClass.author);
        mvideoName.setText(videoClass.videoName);
        mimageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CardView thisCardView = (CardView) v.getParent().getParent();
                int i = 0;
                for (; i < mvideoList.size(); i++) {
                    VideoClass tempVideoClass = (VideoClass) mvideoList.get(i);
                    if (tempVideoClass.cardView == thisCardView) {
                        break;
                    }
                }
                removeCardView(thisCardView);
                mvideoList.remove(i);
            }
        });
        cardViewLayout.addView(mcardView);
        return mcardView;
    }


    private void removeCardView(CardView cardView) {
        LinearLayout cardViewLayout = (LinearLayout) findViewById(R.id.cardViewLayout);
        cardViewLayout.removeView(cardView);

    }


    /**
     * 广播接收器,从FloatingVideoService接收弹幕时间
     *
     * @author len
     */

    private MsgReceiver msgReceiver;

    public class MsgReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int operation = intent.getIntExtra("operation", 0);
            switch (operation) {
                case 1: {
                    nextVideo();
                    break;
                }
                default:
                    break;
            }
        }

    }

    private void nextVideo() {
        if (mvideoList.size() > 0 && autoNext) {
            VideoClass tempVideoClass = (VideoClass) mvideoList.get(0);
            removeCardView(tempVideoClass.cardView);
            mvideoList.remove(0);
            startBiliVideo(0);
        }
    }

}
