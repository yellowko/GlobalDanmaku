package com.yellowko.GlobalDanmaku;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.yellowko.GlobalDanmaku.BiliDanmukuParser;
import com.yellowko.GlobalDanmaku.R;

import org.java_websocket.handshake.ServerHandshake;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.zip.Inflater;

import master.flame.danmaku.controller.IDanmakuView;
import master.flame.danmaku.danmaku.loader.ILoader;
import master.flame.danmaku.danmaku.loader.IllegalDataException;
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.BaseCacheStuffer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.model.android.SpannedCacheStuffer;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.parser.IDataSource;
import master.flame.danmaku.danmaku.util.IOUtils;

import java.util.Map;

/**
 * Created by dongzhong on 2018/5/30.
 */

public class FloatingService extends Service {
    public static boolean isStarted = false;

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;

    private Button button;
    private View displayView;

    private IDanmakuView mDanmakuView;
    private BaseDanmakuParser mParser;
    private DanmakuContext mContext;
    private boolean showName=false;
    private  String danmakuText;
    private  String danmakuLink;
    private InputStream danmakuInputStream;
    boolean isLive;

    private JWebSocketClient client=null;
    private int roomid;
    URI uri = URI.create("wss://broadcastlv.chat.bilibili.com:2245/sub");
    Gson gson = new Gson();
    ByteBuffer heatbeat=generatePacket(2,"");
    /**
     * 字节数组转16进制
     * @param bytes 需要转换的byte数组
     * @return  转换后的Hex字符串
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuffer sb = new StringBuffer();
        for(int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(bytes[i] & 0xFF);
            if(hex.length() < 2){
                sb.append(0);
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    private byte[] getStrFromGZip(byte[] data) {
        //定义byte数组用来放置解压后的数据
        byte[] output = new byte[0];
        Inflater decompresser = new Inflater();
        decompresser.reset();
        //设置当前输入解压
        decompresser.setInput(data, 0, data.length);
        ByteArrayOutputStream o = new ByteArrayOutputStream(data.length);
        try {
            byte[] buf = new byte[1024];
            while (!decompresser.finished()) {
                int i = decompresser.inflate(buf);
                o.write(buf, 0, i);
            }
            output = o.toByteArray();
        } catch (Exception e) {
            output = data;
            e.printStackTrace();
        } finally {
            try {
                o.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        decompresser.end();
        return output;
    }


    public ByteBuffer encodeKey(String key) {
        try {
            return ByteBuffer.wrap(key.getBytes("utf-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return ByteBuffer.wrap(key.getBytes());
    }

    /**
     * byte 数组转byteBuffer
     * @param byteArray
     */
    public static ByteBuffer byte2Byffer(byte[] byteArray) {

        //初始化一个和byte长度一样的buffer
        ByteBuffer buffer=ByteBuffer.allocate(byteArray.length);
        // 数组放到buffer中
        buffer.put(byteArray);
        //重置 limit 和postion 值 否则 buffer 读取数据不对
        buffer.flip();
        return buffer;
    }

    public void initJWebSocket (int iroomid) {
        roomid = iroomid;
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0");

        client = new JWebSocketClient(uri,headers) {
            @Override
            public void onOpen(ServerHandshake handshakedata){
                super.onOpen(handshakedata);
                ByteBuffer join = _joinRoom(roomid);
                //ByteBuffer在写完后切换到读之前要调用flip(),把limit放到position，把position置0
                join.flip();
                client.send(join);
                mHandler.postDelayed(heartBeatRunnable, HEART_BEAT_RATE);//开启心跳检测
            }
            @Override
            public void onError(Exception ex){
                super.onError(ex);
                ex.printStackTrace();
            }
            @Override
            public void onMessage( ByteBuffer bytes ) {
//                byte[] cbytes=new byte[compressBytes.limit()];
//                compressBytes.get(cbytes);
//                ByteBuffer bytes=encodeKey(getStrFromGZip(cbytes));
                //二进制流长度
                super.onMessage(bytes);
                int len= bytes.limit();
                int packetLen, headerLen;
                for (int offset = 0; offset < len;) {
                    packetLen = bytes.getInt(offset);
                    headerLen = bytes.getShort(offset + 4);
                    int ver = bytes.getShort(offset + 6);
                    int op=bytes.getInt(offset + 8);
                    int num=bytes.getInt(offset + 12);
                    Log.e("onMessage",packetLen+" "+headerLen+" "+ver+" "+op+" "+ num);
                    //判断Zlib压缩
                    if (ver == 2) {
                        //Zlib解压
                        byte[] recData = new byte[packetLen - headerLen];
                        bytes.position(offset + headerLen);
                        bytes.get(recData, 0, packetLen - headerLen);
                        byte[] data = getStrFromGZip(recData);
                        //Zlib解压后继续拆包
                        ByteBuffer bytes1= byte2Byffer(data);
                        for(int offset1=0;offset1<bytes1.limit();) {
                            int packetLen1 = bytes1.getInt(offset1);
                            int headerLen1 = bytes1.getShort(offset + 4);
                            Log.d("length",String.valueOf(packetLen1 - headerLen1));
                            byte[] danmakuData=new byte[packetLen1 - headerLen1];
                            bytes1.position(offset1 + headerLen1);
                            bytes1.get(danmakuData, 0, packetLen1 - headerLen1);

                            int ver1 = bytes1.getShort(offset1 + 6);
                            int op1=bytes1.getInt(offset1 + 8);
                            int num1=bytes1.getInt(offset1 + 12);
                            //判断是否为弹幕包
                            if(op1==5) {
                                String danmaku = new String(danmakuData);
                                try {
//                                    Log.d("websocket", danmaku);
                                    JsonObject body = JsonParser.parseString(danmaku).getAsJsonObject();
                                    if (body.get("cmd").getAsString().equals("DANMU_MSG")) {
                                        Log.e("ver1,op1",packetLen1+" "+headerLen1+" "+ver1+" "+op1+" "+ num1);
                                        Log.i("Danmu", body.get("info").getAsJsonArray().get(2).getAsJsonArray().get(1).getAsString() + ':' + body.get("info").getAsJsonArray().get(1).getAsString());
                                        if (showName)
                                            danmakuText = body.get("info").getAsJsonArray().get(2).getAsJsonArray().get(1).getAsString() + ':' + body.get("info").getAsJsonArray().get(1).getAsString();
                                        else
                                            danmakuText = body.get("info").getAsJsonArray().get(1).getAsString();
                                        addDanmaku(danmakuText, true, body.get("info").getAsJsonArray().get(0).getAsJsonArray().get(3).getAsInt());
                                    }
                                } catch (JsonSyntaxException e) {
                                    e.printStackTrace();
                                }
                            }
                            offset1+=packetLen1;
                        }
                    }
                    offset += packetLen;
                }

            }
        };
        connect();
    }

    /**
     * 发送加入间包
     */
    private ByteBuffer _joinRoom(int rid) {
        String packet =new String("{\"roomid\":"+ rid+",\"protover\": 2,\"platform\": \"web\",\"clientver\": \"1.8.5\",\"type\" : 2}");
        return generatePacket(7, packet);
    }

    /**
     * 生成对应的消息包
     * @param {Number} action 2是心跳包/7是加入房间
     * @param {String} payload
     */

    private ByteBuffer generatePacket (int action, String payload) {
        byte[] packet = payload.getBytes();
        ByteBuffer buff = ByteBuffer.allocate(packet.length + 16);
        buff.putInt(packet.length + 16);
        buff.putShort((short)16);
        buff.putShort((short)1);
        buff.putInt(action);
        buff.putInt(1);
        buff.put(packet,0,packet.length);
        return buff;
    }
    /**
     * 发送心跳包，表明连接激活
     */
    private static final long HEART_BEAT_RATE = 30 * 1000;//每隔10秒进行一次对长连接的心跳检测
    private Handler mHandler = new Handler();
    private Runnable heartBeatRunnable = new Runnable() {
        @Override
        public void run() {
            heatbeat.flip();
            client.send(heatbeat);
            //定时对长连接进行心跳检测
            mHandler.postDelayed(this, HEART_BEAT_RATE);
        }
    };
    /**
     * 连接websocket
     */
    private void connect() {
        new Thread() {
            @Override
            public void run() {
                try {
                    //connectBlocking多出一个等待操作，会先连接再发送，否则未连接发送会报错
                    client.connectBlocking();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();

    }


    /**
     * 断开连接
     */
    public void closeConnect() {
        try {
            if (null != client) {
                client.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            client = null;
        }
    }

//---------------------------------------------------------------------------------------------------------------------------------------

    /*
    开一个加载图片的线程
     */
    private BaseCacheStuffer.Proxy mCacheStufferAdapter = new BaseCacheStuffer.Proxy() {

        private Drawable mDrawable;
        @Override
        public void prepareDrawing(final BaseDanmaku danmaku, boolean fromWorkerThread) {
            if (danmaku.text instanceof Spanned) { // 根据你的条件检查是否需要需要更新弹幕
                // FIXME 这里只是简单启个线程来加载远程url图片，请使用你自己的异步线程池，最好加上你的缓存池
                new Thread() {

                    @Override
                    public void run() {
                        String url = "https://www.bilibili.com/favicon.ico";
                        InputStream inputStream = null;
                        Drawable drawable = mDrawable;
                        if(drawable == null) {
                            try {
                                URLConnection urlConnection = new URL(url).openConnection();
                                inputStream = urlConnection.getInputStream();
                                drawable = BitmapDrawable.createFromStream(inputStream, "bitmap");
                                mDrawable = drawable;
                            } catch (MalformedURLException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                IOUtils.closeQuietly(inputStream);
                            }
                        }
                        if (drawable != null) {
                            drawable.setBounds(0, 0, 100, 100);
                            SpannableStringBuilder spannable = createSpannable(drawable);
                            danmaku.text = spannable;
                            if(mDanmakuView != null) {
                                mDanmakuView.invalidateDanmaku(danmaku, false);
                            }
                            return;
                        }
                    }
                }.start();
            }
        }

        @Override
        public void releaseResource(BaseDanmaku danmaku) {
            // TODO 重要:清理含有ImageSpan的text中的一些占用内存的资源 例如drawable
        }
    };

    /**
     * 绘制背景(自定义弹幕样式)
     */
    private static class BackgroundCacheStuffer extends SpannedCacheStuffer {
        // 通过扩展SimpleTextCacheStuffer或SpannedCacheStuffer个性化你的弹幕样式
        final Paint paint = new Paint();

        @Override
        public void measure(BaseDanmaku danmaku, TextPaint paint, boolean fromWorkerThread) {
            danmaku.padding = 10;  // 在背景绘制模式下增加padding
            super.measure(danmaku, paint, fromWorkerThread);
        }

        @Override
        public void drawBackground(BaseDanmaku danmaku, Canvas canvas, float left, float top) {
            paint.setColor(0x8125309b);
            canvas.drawRect(left + 2, top + 2, left + danmaku.paintWidth - 2, top + danmaku.paintHeight - 2, paint);
        }

        @Override
        public void drawStroke(BaseDanmaku danmaku, String lineText, Canvas canvas, float left, float top, Paint paint) {
            // 禁用描边绘制
        }
    }
    /*
    创建一个关于文件流的弹幕解析器
     */
    private BaseDanmakuParser createParser(InputStream stream) {

        if (stream == null) {
            return new BaseDanmakuParser() {

                @Override
                protected Danmakus parse() {
                    return new Danmakus();
                }
            };
        }
        //创建弹幕加载器
        ILoader loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI);
        //尝试加载弹幕文件流
        try {
            loader.load(stream);
        } catch (IllegalDataException e) {
            e.printStackTrace();
        }
        BaseDanmakuParser parser = new BiliDanmukuParser();
        IDataSource<?> dataSource = loader.getDataSource();
        parser.load(dataSource);
        return parser;

    }

    public void findViews(View v) {
        // 设置最大显示行数
        HashMap<Integer, Integer> maxLinesPair = new HashMap<Integer, Integer>();
        maxLinesPair.put(BaseDanmaku.TYPE_SCROLL_RL, null); // 滚动弹幕最大显示5行
        // 设置是否禁止重叠
        HashMap<Integer, Boolean> overlappingEnablePair = new HashMap<Integer, Boolean>();
        overlappingEnablePair.put(BaseDanmaku.TYPE_SCROLL_RL, true);
        overlappingEnablePair.put(BaseDanmaku.TYPE_FIX_TOP, true);

        mDanmakuView = v.findViewById(R.id.sv_danmaku);
        /*
        弹幕参数初始化
         */
        mContext = DanmakuContext.create();
        mContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3).setDuplicateMergingEnabled(false).setScrollSpeedFactor(1.0f).setScaleTextSize(1.2f)
                .setCacheStuffer(new SpannedCacheStuffer(), mCacheStufferAdapter) // 图文混排使用SpannedCacheStuffer
//        .setCacheStuffer(new BackgroundCacheStuffer(), mCacheStufferAdapter)  // 绘制背景使用BackgroundCacheStuffer
                .setMaximumLines(maxLinesPair)
                .preventOverlapping(overlappingEnablePair).setDanmakuMargin(40);

        if (mDanmakuView != null) {
            if(isLive){
                mParser = createParser(null);//this.getResources().openRawResource(R.raw.comments));//打开res/raw/comment.xml
            }
            else{
                try {
                    Thread getLinkThread = new Thread(new getDanmakuXml());
                    getLinkThread.start();
                    getLinkThread.join();
                }
                catch (Exception  e) {
                    e.printStackTrace();
                }
                mParser = createParser(danmakuInputStream);
            }
            mDanmakuView.setCallback(new master.flame.danmaku.controller.DrawHandler.Callback() {
                @Override
                public void updateTimer(DanmakuTimer timer) {
                }

                @Override
                public void drawingFinished() {

                }

                @Override
                public void danmakuShown(BaseDanmaku danmaku) {
//                    Log.d("DFM", "danmakuShown(): text=" + danmaku.text);
                }

                @Override
                public void prepared() {
                    mDanmakuView.start();
                }
            });
            mDanmakuView.prepare(mParser, mContext);
            mDanmakuView.showFPS(false);
            mDanmakuView.enableDanmakuDrawingCache(true);
        }
    }

    /*
    添加一条弹幕
     */
    private void addDanmaku(String text,boolean islive,int textColor) {
        BaseDanmaku danmaku = mContext.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL);
        if (danmaku == null || mDanmakuView == null) {
            return;
        }
        danmaku.text = text;
        danmaku.padding = 5;
        danmaku.priority = 0;  // 可能会被各种过滤器过滤并隐藏显示
        danmaku.isLive = islive;
        danmaku.setTime(mDanmakuView.getCurrentTime() + 1200);
        danmaku.textSize = 25f * (mParser.getDisplayer().getDensity() - 0.6f);
        danmaku.textColor = textColor;
        danmaku.textShadowColor = Color.GRAY;
        // danmaku.underlineColor = Color.GREEN;
        //danmaku.borderColor = Color.GREEN;
        mDanmakuView.addDanmaku(danmaku);

    }

    private void addDanmaKuShowTextAndImage(boolean islive) {
        BaseDanmaku danmaku = mContext.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL);
        Drawable drawable = getDrawable(R.drawable.ic_launcher);
        drawable.setBounds(0, 0, 100, 100);
        SpannableStringBuilder spannable = createSpannable(drawable);
        danmaku.text = spannable;
        danmaku.padding = 5;
        danmaku.priority = 1;  // 一定会显示, 一般用于本机发送的弹幕
        danmaku.isLive = islive;
        danmaku.setTime(mDanmakuView.getCurrentTime() + 1200);
        danmaku.textSize = 25f * (mParser.getDisplayer().getDensity() - 0.6f);
        danmaku.textColor = Color.RED;
        danmaku.textShadowColor = 0; // 重要：如果有图文混排，最好不要设置描边(设textShadowColor=0)，否则会进行两次复杂的绘制导致运行效率降低
        danmaku.underlineColor = Color.GREEN;
        mDanmakuView.addDanmaku(danmaku);
    }

    private SpannableStringBuilder createSpannable(Drawable drawable) {
        String text = "bitmap";
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(text);
        ImageSpan span = new ImageSpan(drawable);//ImageSpan.ALIGN_BOTTOM);
        spannableStringBuilder.setSpan(span, 0, text.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        spannableStringBuilder.append("图文混排");
        spannableStringBuilder.setSpan(new BackgroundColorSpan(Color.parseColor("#8A2233B1")), 0, spannableStringBuilder.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        return spannableStringBuilder;
    }
    /*
    配置变更时(在manifast里写了)会触发
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            mDanmakuView.getConfig().setDanmakuMargin(20);
        } else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mDanmakuView.getConfig().setDanmakuMargin(40);
        }
    }


//----------------------------------------------------------------------------------------------------------------------------------------------------------------------------

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
        //无法触摸，触摸透过，无焦点
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.x = 0;
        layoutParams.y = 0;
    }

    @Override
    public void onDestroy() {
        if (mDanmakuView != null) {
            // dont forget release!
            mDanmakuView.release();
            mDanmakuView = null;
        }
        if(!isLive){
            unregisterReceiver(msgReceiver);
        }
        closeConnect();
        mHandler.removeCallbacks(heartBeatRunnable);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mDanmakuView == null) {
            if(isLive=intent.getBooleanExtra("isLive",true)){
                showFloatingWindow(intent.getIntExtra("roomid",13946381));
            }
            else {
                danmakuLink=intent.getStringExtra("danmakuLink");
                showFloatingWindow(intent.getStringExtra("videoId"));
                //动态注册广播接收器
                msgReceiver = new MsgReceiver();
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction("com.yellowko.GlobalDanmaku.FloatingService.Reciver");
                registerReceiver(msgReceiver, intentFilter);

            }
        }
        displayView.setAlpha(intent.getFloatExtra("alpha",0.5f));
        showName=intent.getBooleanExtra("showName",false);
        return super.onStartCommand(intent, flags, startId);
    }

    //启动bilibili直播间弹幕
    private void showFloatingWindow(int id) {
        if (Settings.canDrawOverlays(this)) {
            LayoutInflater layoutInflater = LayoutInflater.from(this);
            displayView = layoutInflater.inflate(R.layout.popupwindow, null);
            findViews(displayView);
            initJWebSocket(id);
            windowManager.addView(displayView, layoutParams);
        }
    }

    //启动bilibili视频对应弹幕
    private void showFloatingWindow(String id) {
        if (Settings.canDrawOverlays(this)) {
            LayoutInflater layoutInflater = LayoutInflater.from(this);
            displayView = layoutInflater.inflate(R.layout.popupwindow, null);
            findViews(displayView);
            windowManager.addView(displayView, layoutParams);
        }
    }

    //--------------------------------------------------------------------------------------------------------------------------


    class getDanmakuXml implements Runnable{
        public void run() {
            try {
                URL url = new URL(danmakuLink);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setRequestMethod("GET");
                //获得结果码
                int responseCode = connection.getResponseCode();
                if(responseCode ==200){
                    //请求成功 获得返回的流

                    InputStream iss = connection.getInputStream();
                    ByteArrayOutputStream swapStream = new ByteArrayOutputStream();
                    byte[] buff = new byte[100]; //buff用于存放循环读取的临时数据
                    int rc = 0;
                    while ((rc = iss.read(buff, 0, 100)) > 0) {
                        swapStream.write(buff, 0, rc);
                    }
                    byte[] in_b = swapStream.toByteArray(); //in_b为转换之后的结果
                    byte[] decompressBytes =decompress(in_b);
                    danmakuInputStream = new ByteArrayInputStream(decompressBytes);

                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (ProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    /*
    deflate解压
     */
    public static byte[] decompress(byte[] data) {
        byte[] output;

        Inflater decompresser = new Inflater(true);//这个true是关键
        decompresser.reset();
        decompresser.setInput(data);

        ByteArrayOutputStream o = new ByteArrayOutputStream(data.length);
        try {
            byte[] buf = new byte[1024];
            while (!decompresser.finished()) {
                int i = decompresser.inflate(buf);
                o.write(buf, 0, i);
            }
            output = o.toByteArray();
        } catch (Exception e) {
            output = data;
            e.printStackTrace();
        } finally {
            try {
                o.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        decompresser.end();
        return output;
    }

    /**
     * 广播接收器,从FloatingVideoService接收弹幕时间
     * @author len
     *
     */

    private MsgReceiver msgReceiver;
    public class MsgReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int seekms=intent.getIntExtra("seekms",0);
            Boolean modify=intent.getBooleanExtra("modify",false);
            Boolean danmakushow=intent.getBooleanExtra("danmakuShow",true);
            if(mDanmakuView!=null&&modify)
                mDanmakuView.seekTo(new Long((long)seekms));
            if(danmakushow){
                mDanmakuView.show();
            }
            else {
                mDanmakuView.hide();
            }
        }

    }

}
