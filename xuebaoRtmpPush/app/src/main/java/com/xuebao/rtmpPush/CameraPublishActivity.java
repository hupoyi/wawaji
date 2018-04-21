/*
 * CameraPublishActivity.java
 * CameraPublishActivity
 * 
 * Github: https://github.com/daniulive/SmarterStreaming
 * 
 * Created by DaniuLive on 2015/09/20.
 * Copyright © 2014~2016 DaniuLive. All rights reserved.
 */

package com.xuebao.rtmpPush;

import com.daniulive.smartpublisher.RecorderManager;
import com.daniulive.smartpublisher.SmartPublisherJni.WATERMARK;
import com.daniulive.smartpublisher.SmartPublisherJniV2;
import com.eventhandle.NTSmartEventCallbackV2;
import com.eventhandle.NTSmartEventID;
//import com.voiceengine.NTAudioRecord;	//for audio capture..
import com.voiceengine.NTAudioRecordV2;
import com.voiceengine.NTAudioRecordV2Callback;
import com.voiceengine.NTAudioUtils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.hardware.Camera.AutoFocusCallback;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import android_serialport_api.SerialPort;
import android_serialport_api.ComPort;
import socks.*;
import update.SilentInstall;

@SuppressWarnings("deprecation")
public class CameraPublishActivity extends FragmentActivity {
    public enum MessageType {
        msgCheckTime,//检查系统时间是否已正确。正确之后才能开始预览并推流，否则会因为设置时间导致预览被卡住而死机
        msgOnTimeOK,//时间已准备就绪
        msgDelayPush,//延迟开始推。
        msgCheckWawajiReady,//检查娃娃机是否就绪
        msgCheckPreview,//5秒钟的预览有效性检查
        msgNetworkData,//收到应用服务器过来的数据
        msgConfigData,//收到配置变更后的数据
        msgOnUpdate,//收到更新命令--准备开始更新程序
        msgComData,//收到串口过来的数据
        msgUDiskMount,//收到U盘插入的消息。会读取里面的txt来配置本机--实际并没有使用场景
        msgQueryWawajiState,//查询娃娃机是否已处于空闲状态。 此命令是预览停止后，准备重启娃娃机时开始查询
        msgMyFireHeartBeat,//调试信息。。心跳
        msgWaitIP,//等待IP
        msgIpGOT, //IP已得到。开始检查时间
        msgDebugTxt,
        msgCheckWawaNowState, //检查娃娃机当前状态的循环
        msgUDiskUnMount, //U盘拔掉消息
        msgUpdateFreeSpace,
        msgApplyCamparam,//点击摄像头的对比度设置按钮
        msgRestoreCamparam,//点击恢复默认按钮
        msgComRawDataPrint  //听说会收不到串口数据。我加这个消息，在串口收到任何数据时，即打印在屏幕上
    }

    ;

    private static String TAG = "CameraPublishActivity";

    //NTAudioRecord audioRecord_ = null;	//for audio capture

    NTAudioRecordV2 audioRecord_ = null;

    NTAudioRecordV2Callback audioRecordCallback_ = null;

    private long publisherHandleBack = 0;

    private long publisherHandleFront = 0;

    private long publisherHandleCurrent = 0;//单路推流的时候这个对象去推

    private SmartPublisherJniV2 libPublisher = null;

    /* 推流分辨率选择
     * 0: 640*480
	 * 1: 320*240
	 * 2: 176*144
	 * 3: 1280*720
	 * */
    private Spinner resolutionSelector;

    /* video软编码profile设置
     * 1: baseline profile
     * 2: main profile
     * 3: high profile
	 * */
    private Spinner swVideoEncoderProfileSelector;

    private Spinner swVideoEncoderSpeedSelector;

    private Button btnHWencoder;

    private Button btnStartPush;

    private SurfaceView mSurfaceViewFront = null;
    private SurfaceHolder mSurfaceHolderFront = null;

    private SurfaceView mSurfaceViewBack = null;
    private SurfaceHolder mSurfaceHolderBack = null;

    private Camera mCameraFront = null;
    private AutoFocusCallback myAutoFocusCallbackFront = null;

    private Camera mCameraBack = null;
    private AutoFocusCallback myAutoFocusCallbackBack = null;

    private boolean mPreviewRunningFront = false;
    private boolean mPreviewRunningBack = false;

    private boolean isPushing = false;
    private boolean isRecording = false;

    private String txt = "当前状态";

    private static final int FRONT = 1;        //前置摄像头标记
    private static final int BACK = 2;        //后置摄像头标记

    private int curFrontCameraIndex = -1;
    private int curBackCameraIndex = -1;

    public static ComPort mComPort;
    public SockAPP sendThread;//应用服务器
    public SockConfig confiThread;//配置服务器
    MyTCServer lis_server = null;//本机监听端口。接受局域网配置工具命令

    private WifiManager wifiManager;
    WifiAutoConnectManager wifiauto;
    private Context myContext;

    enum PushState {UNKNOWN, OK, FAILED, CLOSE};

    PushState pst_front = PushState.UNKNOWN;
    PushState pst_back = PushState.UNKNOWN;

    boolean isTimeReady = false;//安卓系统时间初始化会引起摄像头预览卡住，从而推流失败。我们不方便给客户烧录旧系统去补救这个。所以只能推流端将摄像头在时间就绪以后初始化--add 2018.2.2
    boolean isWawajiReady = false;//检测娃娃机就绪以后才开始跟他要ip，设置IP，什么之类的。w娃娃机就绪以后，应用服务器开始心跳--add 2018.2.2

    //检查摄像头预览是否正常的变量。初始为true 每隔5秒检查是否是true。如果是设置成false。 如果检查是false 则表明预览已中断可以重启。当需要重启时，检查娃娃机是否有人在玩，没人则closesocket 重启。 有人则等待本局游戏结束，发送完成后，重启。
    //add 2018.2.2
    boolean isFrontCameraPreviewOK = true;
    boolean isBackCameraPreviewOK = true;

    boolean isShouldRebootSystem = false;//检测到推流预览停止时，这变量置为真.此时立刻查询娃娃机状态，如果娃娃机是空闲的，直接关掉socket。重启。

    int timeWaitCount = 20;//等待时间就绪的次数。我们只等2分钟。也就是20次。
    int wawajiCurrentState = -1;//娃娃机当前状态

    String sdCardPath;//20180308 存储sd卡路径
    String fronDirName = "/xuebaoRecFront";
    String backDirName = "/xuebaoRecBack";
    CheckSpaceThread checkSpaceThread = null;

    //int queryStateTimeoutTime = 0;//娃娃机状态查询超时的次数
    //新做的tab控件
    //列表控件相关
    private String[] titles = new String[]{"日志", "色彩调整"};
    private TabLayout mTabLayout;
    private ViewPager mViewPager;
    private FragmentAdapter adapter;
    //ViewPage选项卡页面列表
    private List<Fragment> mFragments;
    private List<String> mTitles;

    private int trySetMACCount = 0;//20180421 当收到心跳的mac为空时，尝试给串口发mac和本机ip。最多重试3次。已重试的次数存储在这


    static {
        System.loadLibrary("SmartPublisher");
    }

    BroadcastReceiver mSdcardReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String path = intent.getData().getPath();
            if (intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED)) {
                Toast.makeText(context, "path1111:" + intent.getData().getPath(), Toast.LENGTH_SHORT).show();
                Message message = Message.obtain();
                message.what = MessageType.msgUDiskMount.ordinal();
                message.obj = path;
                if (mHandler != null) mHandler.sendMessage(message);

            } else if (intent.getAction().equals(Intent.ACTION_MEDIA_REMOVED)) {

                Message message = Message.obtain();
                message.what = MessageType.msgUDiskUnMount.ordinal();
                message.obj = path;
                if (mHandler != null) mHandler.sendMessage(message);

                Log.e("123", "remove ACTION_MEDIA_REMOVED111111111" + path);
            } else if (intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED)) {

                Message message = Message.obtain();
                message.what = MessageType.msgUDiskUnMount.ordinal();
                message.obj = path;
                if (mHandler != null) mHandler.sendMessage(message);

                Log.e("123", "remove ACTION_MEDIA_REMOVED222222222222" + path);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);    //屏幕常亮
        setContentView(R.layout.activity_main);
        myContext = this.getApplicationContext();

        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        wifiauto = new WifiAutoConnectManager(wifiManager);

        pst_front = PushState.UNKNOWN;
        pst_back = PushState.UNKNOWN;

        //接受U盘挂载事件
        IntentFilter filter = null;
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);   //接受外媒挂载过滤器
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);   //接受外媒挂载过滤器
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);   //接受外媒挂载过滤器
        filter.addDataScheme("file");

        registerReceiver(mSdcardReceiver, filter, "android.permission.READ_EXTERNAL_STORAGE", null);

        VideoConfig.instance = new VideoConfig();
        VideoConfig.instance.LoadConfig(this, mHandler);

        isShouldRebootSystem = false;
        isTimeReady = false;
        isWawajiReady =  false;//note 调试模式下 先让娃娃机就绪 否则连接不了应用服务器
        timeWaitCount = 20;
        //queryStateTimeoutTime = 0;

        //串口对象
        if (mComPort == null) {
            mComPort = new ComPort(mHandler);
        }
        mComPort.Start();

        initUI();

        UpdateConfigToUI();

        if (getLocalIpAddress().equals("")) {
            mHandler.sendEmptyMessage(MessageType.msgWaitIP.ordinal());//网卡尚未就绪 IP地址没有获取。等待IP就绪
        } else {
            mHandler.sendEmptyMessage(MessageType.msgIpGOT.ordinal());
        }

        mHandler.sendEmptyMessage(MessageType.msgCheckWawajiReady.ordinal());//循环检查娃娃机是否就绪

        List<String> ss = getAllExternalSdcardPath();
        if( ss.size() <=0 )
        {
            sdCardPath = "";
            initRecordUI( sdCardPath, 0);
        }
        else
            {
                sdCardPath = ss.get(0);
                int frontCount = GetRecFileList( sdCardPath + fronDirName );
                int backCount = GetRecFileList( sdCardPath + backDirName );

                //检查可用空间 和已有文件大小是否满足要求。不满足，则置空。因为会频繁触发文件检查 这是不允许的
                if( frontCount + backCount <200 && getSDFreesSpace(sdCardPath)<300)
                {
                    Log.e(TAG, "U盘即使删除文件也无法满足临界要求。不存储");
                    Toast.makeText(getApplicationContext(), "U盘即使删除文件也无法满足临界要求。不存储", Toast.LENGTH_SHORT).show();
                    sdCardPath= "";
                    initRecordUI("",0);
                }
                else
                    initRecordUI(ss.get(0), frontCount + backCount);
            }

        if (checkSpaceThread == null) {
            outputInfo("开始空间检查", false);
            checkSpaceThread = new CheckSpaceThread(mHandler, sdCardPath);//空循环等待 没事
            checkSpaceThread.start();
        }else
            {
                checkSpaceThread.Check( sdCardPath );
            }
    }

    private int GetRecFileList(String recDirPath)
    {
        if ( recDirPath == null )
        {
            Log.i(TAG, "recDirPath is null");
            return 0;
        }


        if ( recDirPath.isEmpty() )
        {
            Log.i(TAG, "recDirPath is empty");
            return 0;
        }


        File recDirFile = null;

        try
        {
            recDirFile = new File(recDirPath);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return 0;
        }

        if ( !recDirFile.exists() )
        {
            Log.e("Tag", "rec dir is not exist, path:" + recDirPath);
            return 0;
        }

        if ( !recDirFile.isDirectory() )
        {
            Log.e(TAG, recDirPath + " is not dir");
            return 0;
        }


        File[] files = recDirFile.listFiles();
        if ( files == null )
        {
            return 0;
        }

        List<String>  fileList = new ArrayList<String>();

        try
        {
            for ( int i =0; i < files.length; ++i )
            {

                File recFile = files[i];
                if ( recFile == null )
                {
                    continue;
                }

                //Log.i(Tag, "recfile:" + recFile.getAbsolutePath());

                if ( !recFile.isFile() )
                {
                    continue;
                }

                if ( !recFile.exists() )
                {
                    continue;
                }

                String name = recFile.getName();
                if ( name == null )
                {
                    continue;
                }

                if ( name.isEmpty() )
                {
                    continue;
                }

                if ( name.endsWith(".mp4") )
                {
                    fileList.add(recFile.getAbsolutePath());
                }
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        return fileList.size();
    }

    //in MB
    long getSDFreesSpace(String sdP)
    {
        if( sdP.equals("") == true)
            return 0;

        StatFs sf = new StatFs(sdP);
        long blockSize = sf.getBlockSize();
        long blockCount = sf.getBlockCount();
        long availCount = sf.getAvailableBlocks();

        return (availCount*blockSize>>20);
    }

    void initRecordUI(String uPath, int total) {

        if( uPath.equals("") == true)
        {
            TextView tviapptitle = findViewById(R.id.devSpace);
            tviapptitle.setText("录像功能无法使用。原因:没有插入U盘或外置SD卡.或插入的U盘不满足存储临界条件");
        }
        else {
                StatFs sf = new StatFs(uPath);
                long blockSize = sf.getBlockSize();
                long blockCount = sf.getBlockCount();
                long availCount = sf.getAvailableBlocks();
                Log.d(TAG, "block大小:"+ blockSize+",block数目:"+ blockCount+",总大小:"+blockSize*blockCount/1024+"KB");
                Log.d(TAG, "可用的block数目：:"+ availCount+",剩余空间:"+ (availCount*blockSize>>20)+"MB");

                TextView tviapptitle = findViewById(R.id.devSpace);
                tviapptitle.setText( "已有录像个数:" + total + " 剩余可用空间: " +  (availCount*blockSize>>20)+" MB" + "盘符路径" + uPath);
            }
    }

    public static List<String> getAllExternalSdcardPath() {
        List<String> PathList = new ArrayList<String>();

        //PathList.add("/sdcard/daniulive");
        //return PathList;

        String firstPath = Environment.getExternalStorageDirectory().getPath();
        Log.d(TAG,"getAllExternalSdcardPath , firstPath = "+firstPath);

        try {
            // 运行mount命令，获取命令的输出，得到系统中挂载的所有目录
            Runtime runtime = Runtime.getRuntime();
            Process proc = runtime.exec("mount");
            InputStream is = proc.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            String line;
            BufferedReader br = new BufferedReader(isr);
            while ((line = br.readLine()) != null) {
                // 将常见的linux分区过滤掉
                if (line.contains("proc") || line.contains("tmpfs") || line.contains("media") || line.contains("asec") || line.contains("secure") || line.contains("system") || line.contains("cache")
                        || line.contains("sys") || line.contains("data") || line.contains("shell") || line.contains("root") || line.contains("acct") || line.contains("misc") || line.contains("obb")) {
                    continue;
                }

                // 下面这些分区是我们需要的
                if (line.contains("fat") || line.contains("fuse") || (line.contains("ntfs"))){
                    // 将mount命令获取的列表分割，items[0]为设备名，items[1]为挂载路径
                    String items[] = line.split(" ");
                    if (items != null && items.length > 1){
                        String path = items[1].toLowerCase(Locale.getDefault());
                        // 添加一些判断，确保是sd卡，如果是otg等挂载方式，可以具体分析并添加判断条件
                        if (path != null && !PathList.contains(path))
                        {
                            if(  path.contains("usb_storage") || path.contains("external_storage"))
                            {
                                PathList.add(items[1]);
                                Log.e(TAG,"USB1 PATH:" + path);
                            } else
                            {
                                Log.e(TAG,"ohter PATH:" + path);
                            }
                        }
                    }
                }
            }
        } catch (Exception e){
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return PathList;
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "activity destory!");

        if (confiThread != null) {
            Log.e("app退出", "配置线程终止");
            confiThread.StopNow();
            confiThread = null;
        }

        if (sendThread != null) {
            Log.e("app退出", "应用线程终止");
            sendThread.StopNow();
            sendThread = null;
        }

        if (lis_server != null) {
            Log.e("app退出", "监听线程终止");
            lis_server.StopNow();
            lis_server = null;
        }

        if (isPushing || isRecording) {
            if (audioRecord_ != null) {
                Log.i(TAG, "surfaceDestroyed, call StopRecording..");

                //audioRecord_.StopRecording();
                //audioRecord_ = null;

                audioRecord_.Stop();

                if (audioRecordCallback_ != null) {
                    audioRecord_.RemoveCallback(audioRecordCallback_);
                    audioRecordCallback_ = null;
                }

                audioRecord_ = null;
            }

            stopPush();
            stopRecorder();

            isPushing = false;
            isRecording = false;

            if (publisherHandleFront != 0) {
                if (libPublisher != null) {
                    libPublisher.SmartPublisherClose(publisherHandleFront);
                    publisherHandleFront = 0;
                }
            }

            if (publisherHandleBack != 0) {
                if (libPublisher != null) {
                    libPublisher.SmartPublisherClose(publisherHandleBack);
                    publisherHandleBack = 0;
                }
            }

            if (publisherHandleCurrent != 0) {
                if (libPublisher != null) {
                    libPublisher.SmartPublisherClose(publisherHandleCurrent);
                    publisherHandleCurrent = 0;
                }
            }

            if( checkSpaceThread != null)
            {
                checkSpaceThread.StopNow();
                checkSpaceThread = null;
            }
        }

        super.onDestroy();
        finish();
        System.exit(0);
    }

    void initUI() {
        TextView tviapptitle = findViewById(R.id.id_app_title);
        tviapptitle.setText(APKVersionCodeUtils.getVerName(this) + Integer.toString(VideoConfig.instance.appVersion));

        //DHCP checkbox的逻辑
        CheckBox cbDHCP = findViewById(R.id.checkBoxAutoGet);
        cbDHCP.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                if (isChecked) {
                    findViewById(R.id.my_ip_addr).setEnabled(false);
                    findViewById(R.id.my_netgate_tip).setVisibility(View.INVISIBLE);
                    findViewById(R.id.my_gate_addr).setVisibility(View.INVISIBLE);
                    findViewById(R.id.my_netmask_tip).setVisibility(View.INVISIBLE);
                    findViewById(R.id.my_netmask_addr).setVisibility(View.INVISIBLE);
                    VideoConfig.instance.using_dhcp = true;
                    EditText eti_my_ip_addr = findViewById(R.id.my_ip_addr);
                    eti_my_ip_addr.setText(getLocalIpAddress());

                } else {
                    findViewById(R.id.my_ip_addr).setEnabled(true);
                    findViewById(R.id.my_netgate_tip).setVisibility(View.VISIBLE);
                    findViewById(R.id.my_gate_addr).setVisibility(View.VISIBLE);
                    findViewById(R.id.my_netmask_tip).setVisibility(View.VISIBLE);
                    findViewById(R.id.my_netmask_addr).setVisibility(View.VISIBLE);
                    VideoConfig.instance.using_dhcp = false;
                }
            }
        });

        //是否使用预设视频配置
        CheckBox cbPrefernce = findViewById(R.id.checkUsePrefence);
        cbPrefernce.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                if (isChecked) {
                    findViewById(R.id.resolutionSelctor).setVisibility(View.VISIBLE);
                    findViewById(R.id.custum_w_tip).setVisibility(View.INVISIBLE);
                    findViewById(R.id.custum_wideo_w).setVisibility(View.INVISIBLE);
                    findViewById(R.id.custum_h_tip).setVisibility(View.INVISIBLE);
                    findViewById(R.id.custum_wideo_h).setVisibility(View.INVISIBLE);

                } else {
                    findViewById(R.id.resolutionSelctor).setVisibility(View.INVISIBLE);
                    findViewById(R.id.custum_w_tip).setVisibility(View.VISIBLE);
                    findViewById(R.id.custum_wideo_w).setVisibility(View.VISIBLE);
                    findViewById(R.id.custum_h_tip).setVisibility(View.VISIBLE);
                    findViewById(R.id.custum_wideo_h).setVisibility(View.VISIBLE);
                }
            }
        });

        //分辨率配置
        resolutionSelector = (Spinner) findViewById(R.id.resolutionSelctor);
        final String[] resolutionSel = new String[]{"960*720", "640*480", "640*360", "352*288", "320*240"};
        ArrayAdapter<String> adapterResolution = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, resolutionSel);
        adapterResolution.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionSelector.setAdapter(adapterResolution);
        resolutionSelector.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isPushing || isRecording) {
                    return;
                }

                SwitchResolution(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        //软编码配置
        swVideoEncoderProfileSelector = (Spinner) findViewById(R.id.swVideoEncoderProfileSelector);
        final String[] profileSel = new String[]{"BaseLineProfile", "MainProfile", "HighProfile"};
        ArrayAdapter<String> adapterProfile = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, profileSel);
        adapterProfile.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        swVideoEncoderProfileSelector.setAdapter(adapterProfile);
        swVideoEncoderProfileSelector.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isPushing || isRecording) {
                    return;
                }

                VideoConfig.instance.sw_video_encoder_profile = position + 1;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        //软编码关键帧数
        swVideoEncoderSpeedSelector = (Spinner) findViewById(R.id.sw_video_encoder_speed_selctor);
        final String[] video_encoder_speed_Sel = new String[]{"6", "5", "4", "3", "2", "1"};
        ArrayAdapter<String> adapterVideoEncoderSpeed = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, video_encoder_speed_Sel);
        adapterVideoEncoderSpeed.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        swVideoEncoderSpeedSelector.setAdapter(adapterVideoEncoderSpeed);
        swVideoEncoderSpeedSelector.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                VideoConfig.instance.sw_video_encoder_speed = 6 - position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        btnHWencoder = (Button) findViewById(R.id.button_hwencoder);
        btnHWencoder.setOnClickListener(new ButtonHardwareEncoderListener());

        TextView timac = findViewById(R.id.id_mac);
        timac.setText("MAC: " + VideoConfig.instance.getMac());

        btnStartPush = (Button) findViewById(R.id.button_start_push);
        btnStartPush.setOnClickListener(new ButtonStartPushListener());

        //摄像头部分
        mSurfaceViewFront = (SurfaceView) this.findViewById(R.id.surface_front);
        mSurfaceHolderFront = mSurfaceViewFront.getHolder();
        mSurfaceHolderFront.addCallback(new NT_SP_SurfaceHolderCallback(FRONT));
        mSurfaceHolderFront.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        //自动聚焦变量回调
        myAutoFocusCallbackFront = new AutoFocusCallback() {
            public void onAutoFocus(boolean success, Camera camera) {
                if (success)//success表示对焦成功
                {
                    Log.i(TAG, "Front onAutoFocus succeed...");
                } else {
                    Log.i(TAG, "Front onAutoFocus failed...");
                }
            }
        };

        mSurfaceViewBack = (SurfaceView) this.findViewById(R.id.surface_back);
        mSurfaceHolderBack = mSurfaceViewBack.getHolder();
        mSurfaceHolderBack.addCallback(new NT_SP_SurfaceHolderCallback(BACK));
        mSurfaceHolderBack.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        //自动聚焦变量回调
        myAutoFocusCallbackBack = new AutoFocusCallback() {
            public void onAutoFocus(boolean success, Camera camera) {
                if (success)//success表示对焦成功
                {
                    Log.i(TAG, "Back onAutoFocus succeed...");
                } else {
                    Log.i(TAG, "Back onAutoFocus failed...");
                }
            }
        };

        //是否启用远程配置的逻辑
        CheckBox cbEnableCongfig = findViewById(R.id.enableConfServer);
        cbEnableCongfig.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                if (isChecked) {
                    //启用界面 调用开启配置线程的接口
                    findViewById(R.id.config_server_ip).setEnabled(true);
                    findViewById(R.id.config_server_port).setEnabled(true);

                    VideoConfig.instance.enableConfigServer = true;
                } else {

                    //停用界面 关闭配置线程
                    findViewById(R.id.config_server_ip).setEnabled(false);
                    findViewById(R.id.config_server_port).setEnabled(false);

                    VideoConfig.instance.enableConfigServer = false;
                }
            }
        });

        //是否开启录像功能
        CheckBox cbRecord = findViewById(R.id.checkRecord);
        cbRecord.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                if (isChecked) {
                    VideoConfig.instance.is_need_local_recorder = true;
                } else {
                    VideoConfig.instance.is_need_local_recorder = false;
                }
            }
        });

        //包含声音
        CheckBox cbIncludeAudio = findViewById(R.id.cbIncludeAudio);
        cbIncludeAudio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                if (isChecked) {
                    VideoConfig.instance.containAudio = true;
                } else {
                    VideoConfig.instance.containAudio = false;
                }
            }
        });

        //只推一路模式
        CheckBox cbSwitchToOne = findViewById(R.id.checkSwitchToOne);
        cbSwitchToOne.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                if (isChecked) {
                    VideoConfig.instance.swtichToOne = true;
                    findViewById(R.id.curWay).setVisibility(View.VISIBLE);
                    TextView tv = findViewById(R.id.curWay);
                    tv.setText("当前:" + VideoConfig.instance.curPushWay);
                } else {
                    VideoConfig.instance.swtichToOne = false;
                    findViewById(R.id.curWay).setVisibility(View.INVISIBLE);

                }
            }
        });

        //日志的tab
        mViewPager = (ViewPager) findViewById(R.id.viewpager);
        mTabLayout = (TabLayout) findViewById(R.id.tablayout);

        mTitles = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            mTitles.add(titles[i]);
        }
        mFragments = new ArrayList<>();
        mFragments.add(FragmentLogTxt.newInstance(0));
        mFragments.add(FragmentCamSet.newInstance(1, mHandler));

        adapter = new FragmentAdapter(getSupportFragmentManager(), mFragments, mTitles);
        mViewPager.setAdapter(adapter);//给ViewPager设置适配器
        mTabLayout.setupWithViewPager(mViewPager);//将TabLayout和ViewPager关联起来
    }

    byte[] strIPtob(String sip) {
        String[] ipb = sip.split("\\.");
        byte[] b = new byte[4];
        b[0] = (byte) Integer.parseInt(ipb[0]);
        b[1] = (byte) Integer.parseInt(ipb[1]);
        b[2] = (byte) Integer.parseInt(ipb[2]);
        b[3] = (byte) Integer.parseInt(ipb[3]);
        return b;
    }

    int g_packget_id = 0;

    void send_com_data(int... params) {
        byte send_buf[] = new byte[8 + params.length];
        send_buf[0] = (byte) 0xfe;
        send_buf[1] = (byte) (g_packget_id);
        send_buf[2] = (byte) (g_packget_id >> 8);
        send_buf[3] = (byte) ~send_buf[0];
        send_buf[4] = (byte) ~send_buf[1];
        send_buf[5] = (byte) ~send_buf[2];
        send_buf[6] = (byte) (8 + params.length);
        for (int i = 0; i < params.length; i++) {
            send_buf[7 + i] = (byte) (params[i]);
        }

        int sum = 0;
        for (int i = 6; i < (8 + params.length - 1); i++) {
            sum += (send_buf[i] & 0xff);
        }

        send_buf[8 + params.length - 1] = (byte) (sum % 100);
        mComPort.SendData(send_buf, send_buf.length);
        g_packget_id++;
    }

    byte[] make_cmd(int... params) {
        byte send_buf[] = new byte[8 + params.length];
        send_buf[0] = (byte) 0xfe;
        send_buf[1] = (byte) (g_packget_id);
        send_buf[2] = (byte) (g_packget_id >> 8);
        send_buf[3] = (byte) ~send_buf[0];
        send_buf[4] = (byte) ~send_buf[1];
        send_buf[5] = (byte) ~send_buf[2];
        send_buf[6] = (byte) (8 + params.length);
        for (int i = 0; i < params.length; i++) {
            send_buf[7 + i] = (byte) (params[i]);
        }

        int sum = 0;
        for (int i = 6; i < (8 + params.length - 1); i++) {
            sum += (send_buf[i] & 0xff);
        }

        send_buf[8 + params.length - 1] = (byte) (sum % 100);

        g_packget_id++;
        return send_buf;
    }

    void SaveConfigHostInfoToCom() {
        if (VideoConfig.instance.enableConfigServer == false)//不启用远程配置的时候，给娃娃机传0
        {
            send_com_data(0x40, 0, 0, 0, 0, 0, 0);
            return;
        }

        String[] ipb = VideoConfig.instance.configHost.split("\\.");
        if (ipb.length < 4)
            return;

        byte[] b = new byte[6];
        b[0] = (byte) Integer.parseInt(ipb[0]);
        b[1] = (byte) Integer.parseInt(ipb[1]);
        b[2] = (byte) Integer.parseInt(ipb[2]);
        b[3] = (byte) Integer.parseInt(ipb[3]);

        b[4] = (byte) (VideoConfig.instance.GetConfigPort() / 256);
        b[5] = (byte) (VideoConfig.instance.GetConfigPort() % 256);

        send_com_data(0x40, b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    void ComParamSet(boolean includeMAC, boolean includedLocalIP, boolean includeduserID) {

        //给娃娃机发送本机的MAC
        if (includeMAC) {
            byte msg_content[] = new byte[21];
            msg_content[0] = (byte) 0xfe;
            msg_content[1] = (byte) (0);
            msg_content[2] = (byte) (0);
            msg_content[3] = (byte) ~msg_content[0];
            msg_content[4] = (byte) ~msg_content[1];
            msg_content[5] = (byte) ~msg_content[2];
            msg_content[6] = (byte) (msg_content.length);
            msg_content[7] = (byte) 0x3f;
            String strMAC = VideoConfig.instance.getMac();
            System.arraycopy(strMAC.getBytes(), 0, msg_content, 8, strMAC.getBytes().length);
            int total_c = 0;
            for (int i = 6; i < msg_content.length - 1; i++) {
                total_c += (msg_content[i] & 0xff);
            }
            msg_content[msg_content.length - 1] = (byte) (total_c % 100);
            mComPort.SendData(msg_content, msg_content.length);
            String sss = SockAPP.bytesToHexString(msg_content);
            outputInfo("MaC发往串口" + sss, false);
        }

        //ip
        if (includedLocalIP) {
            byte msg_content[] = new byte[13];
            msg_content[0] = (byte) 0xfe;
            msg_content[1] = (byte) (0);
            msg_content[2] = (byte) (0);
            msg_content[3] = (byte) ~msg_content[0];
            msg_content[4] = (byte) ~msg_content[1];
            msg_content[5] = (byte) ~msg_content[2];
            msg_content[6] = (byte) (msg_content.length);
            msg_content[7] = (byte) 0x39;

            if (VideoConfig.instance.hostIP.equals(""))
                VideoConfig.instance.hostIP = getLocalIpAddress();

            byte bip[] = strIPtob(VideoConfig.instance.hostIP);
            System.arraycopy(bip, 0, msg_content, 8, bip.length);

            int total_c = 0;
            for (int i = 6; i < msg_content.length - 1; i++) {
                total_c += (msg_content[i] & 0xff);
            }
            msg_content[msg_content.length - 1] = (byte) (total_c % 100);
            mComPort.SendData(msg_content, msg_content.length);
        }

        //userid
        if (includeduserID) {
            byte msg_content[] = new byte[25];
            msg_content[0] = (byte) 0xfe;
            msg_content[1] = (byte) (0);
            msg_content[2] = (byte) (0);
            msg_content[3] = (byte) ~msg_content[0];
            msg_content[4] = (byte) ~msg_content[1];
            msg_content[5] = (byte) ~msg_content[2];
            msg_content[6] = (byte) (msg_content.length);
            msg_content[7] = (byte) 0x3a;

            int psw_len = VideoConfig.instance.userID.length() > 16 ? 16 : VideoConfig.instance.userID.length();
            System.arraycopy(VideoConfig.instance.userID.getBytes(), 0, msg_content, 8, psw_len);

            int total_c = 0;
            for (int i = 6; i < msg_content.length - 1; i++) {
                total_c += (msg_content[i] & 0xff);
            }
            msg_content[msg_content.length - 1] = (byte) (total_c % 100);
            mComPort.SendData(msg_content, msg_content.length);
        }
    }

    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf
                        .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        String ipAddress = inetAddress.getHostAddress().toString();
                        if (!ipAddress.contains("::"))
                            return inetAddress.getHostAddress().toString();
                    } else
                        continue;
                }
            }
        } catch (SocketException ex) {
            Log.e("adsf", ex.toString());
        }
        return "";
    }

    //更新配置到UI
    void UpdateConfigToUI() {
        //本机名称
        EditText eti_myname = findViewById(R.id.id_my_name1);
        eti_myname.setText(VideoConfig.instance.machine_name);

        //是否自动获取IP
        if (VideoConfig.instance.using_dhcp == false) {
            CheckBox cbDHCP = findViewById(R.id.checkBoxAutoGet);
            cbDHCP.setChecked(false);

            //设置本机为静态IP 并且设置IP地址
            findViewById(R.id.my_ip_addr).setEnabled(true);
            EditText eti = (EditText) findViewById(R.id.my_ip_addr);
            eti.setText(VideoConfig.instance.hostIP);

            findViewById(R.id.my_netgate_tip).setVisibility(View.VISIBLE);
            findViewById(R.id.my_gate_addr).setVisibility(View.VISIBLE);
            eti = (EditText) findViewById(R.id.my_gate_addr);
            eti.setText(VideoConfig.instance.gateIP);

            findViewById(R.id.my_netmask_tip).setVisibility(View.VISIBLE);
            findViewById(R.id.my_netmask_addr).setVisibility(View.VISIBLE);
            eti = (EditText) findViewById(R.id.my_netmask_addr);
            eti.setText(VideoConfig.instance.maskIP);

        } else {
            CheckBox cbDHCP = findViewById(R.id.checkBoxAutoGet);
            cbDHCP.setChecked(true);

            //设置为动态获取IP
            findViewById(R.id.my_ip_addr).setEnabled(false);
            EditText eti_my_ip_addr = findViewById(R.id.my_ip_addr);
            VideoConfig.instance.hostIP = getLocalIpAddress();
            eti_my_ip_addr.setText(VideoConfig.instance.hostIP);

            findViewById(R.id.my_netgate_tip).setVisibility(View.INVISIBLE);
            findViewById(R.id.my_gate_addr).setVisibility(View.INVISIBLE);
            findViewById(R.id.my_netmask_tip).setVisibility(View.INVISIBLE);
            findViewById(R.id.my_netmask_addr).setVisibility(View.INVISIBLE);
        }

        //是否使用预设分辨率
        CheckBox cbPrefernce = findViewById(R.id.checkUsePrefence);
        if (VideoConfig.instance.GetResolutionIndex() != -1) {
            cbPrefernce.setChecked(true);
            findViewById(R.id.resolutionSelctor).setVisibility(View.VISIBLE);
            findViewById(R.id.custum_w_tip).setVisibility(View.INVISIBLE);
            findViewById(R.id.custum_wideo_w).setVisibility(View.INVISIBLE);
            findViewById(R.id.custum_h_tip).setVisibility(View.INVISIBLE);
            findViewById(R.id.custum_wideo_h).setVisibility(View.INVISIBLE);
            resolutionSelector.setSelection(VideoConfig.instance.GetResolutionIndex());
        } else {
            cbPrefernce.setChecked(false);
            findViewById(R.id.resolutionSelctor).setVisibility(View.INVISIBLE);
            findViewById(R.id.custum_w_tip).setVisibility(View.VISIBLE);
            findViewById(R.id.custum_wideo_w).setVisibility(View.VISIBLE);
            EditText eti = (EditText) findViewById(R.id.custum_wideo_w);
            eti.setText(Integer.toString(VideoConfig.instance.GetVideoWidth()));

            findViewById(R.id.custum_h_tip).setVisibility(View.VISIBLE);
            findViewById(R.id.custum_wideo_h).setVisibility(View.VISIBLE);
            eti = (EditText) findViewById(R.id.custum_wideo_h);
            eti.setText(Integer.toString(VideoConfig.instance.GetVideoHeight()));
        }

        //软硬编码按钮
        if (VideoConfig.instance.is_hardware_encoder) {
            btnHWencoder.setText("当前硬编码");
            //显示软编码选项
            findViewById(R.id.swVideoEncoderProfileSelector).setVisibility(View.INVISIBLE);
            findViewById(R.id.speed_tip).setVisibility(View.INVISIBLE);
            findViewById(R.id.sw_video_encoder_speed_selctor).setVisibility(View.INVISIBLE);
        } else {
            btnHWencoder.setText("当前软编码");
            //显示软编码选项
            findViewById(R.id.swVideoEncoderProfileSelector).setVisibility(View.VISIBLE);
            swVideoEncoderProfileSelector.setSelection(VideoConfig.instance.sw_video_encoder_profile - 1);

            findViewById(R.id.speed_tip).setVisibility(View.VISIBLE);
            findViewById(R.id.sw_video_encoder_speed_selctor).setVisibility(View.VISIBLE);
            swVideoEncoderSpeedSelector.setSelection(6 - VideoConfig.instance.sw_video_encoder_speed);
        }

        //帧率
        EditText eti = findViewById(R.id.push_rate);
        eti.setText(Integer.toString(VideoConfig.instance.GetFPS()));

        //推流地址1
        EditText eti_url1 = findViewById(R.id.cam1_url_edit);
        eti_url1.setText(VideoConfig.instance.url1);

        //推流地址2
        EditText eti_url2 = findViewById(R.id.cam2_url_edit);
        eti_url2.setText(VideoConfig.instance.url2);

        //应用服务器IP
        EditText eti_serverip = findViewById(R.id.server_ip);
        eti_serverip.setText(VideoConfig.instance.destHost);

        //应用服务器端口
        EditText eti_server_port = findViewById(R.id.server_port);
        eti_server_port.setText(Integer.toString(VideoConfig.instance.GetAppPort()));

        //配置服务器ip
        EditText eti_configserverip = findViewById(R.id.config_server_ip);
        eti_configserverip.setText(VideoConfig.instance.configHost);

        //配置服务器端口
        EditText eti_configserver_port = findViewById(R.id.config_server_port);
        eti_configserver_port.setText(Integer.toString(VideoConfig.instance.GetConfigPort()));

        if (VideoConfig.instance.enableConfigServer == false) {
            eti_configserverip.setEnabled(false);
            eti_configserver_port.setEnabled(false);

            CheckBox cbEnableConfig = findViewById(R.id.enableConfServer);
            cbEnableConfig.setChecked(false);

        } else {
            CheckBox cbEnableConfig = findViewById(R.id.enableConfServer);
            cbEnableConfig.setChecked(true);

            eti_configserverip.setEnabled(true);
            eti_configserver_port.setEnabled(true);
        }

        //录像选项
        CheckBox cbRecord = findViewById(R.id.checkRecord);
        if (VideoConfig.instance.is_need_local_recorder == true)
        {
            cbRecord.setChecked(true);
        }
        else {
            cbRecord.setChecked(false);
        }

        CheckBox cbSwitchToOne = findViewById(R.id.checkSwitchToOne);
        if (VideoConfig.instance.swtichToOne == true)
        {
            cbSwitchToOne.setChecked(true);
            TextView tee = findViewById(R.id.curWay);
            tee.setVisibility(View.VISIBLE);
            tee.setText("当前:" + VideoConfig.instance.curPushWay);
        }
        else {
            cbSwitchToOne.setChecked(false);
            TextView tee = findViewById(R.id.curWay);
            tee.setVisibility(View.INVISIBLE);
            tee.setText("当前:" + VideoConfig.instance.curPushWay);
        }

        CheckBox cbIncludeAudio = findViewById(R.id.cbIncludeAudio);
        if( VideoConfig.instance.containAudio == true)
        {
            cbIncludeAudio.setChecked(true);
        }
        else
            {
                cbIncludeAudio.setChecked(false);
            }

        EditText eti_userID = findViewById(R.id.id_userid);
        eti_userID.setText(VideoConfig.instance.userID);

        updateCamUI();//更新tablayout里面的值
    }

    //更新亮度饱和度对比到界面
    void updateCamUI()
    {
        FragmentCamSet fcam = (FragmentCamSet) mFragments.get(1);
        fcam.ConfigToUI();
    }

    //将亮度饱和度对比度应用到摄像头
    public void ApplyCam3Params()
    {
        if( mCameraFront != null)
        {
            Camera.Parameters parameters;
            try {
                parameters = mCameraFront.getParameters();
                parameters.set("staturation", VideoConfig.instance.staturation);
                parameters.set("contrast", VideoConfig.instance.contrast);
                parameters.set("brightness", VideoConfig.instance.brightness);

                parameters.flatten();
                mCameraFront.setParameters(parameters);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        if( mCameraBack != null)
        {
            Camera.Parameters parameters;
            try {
                parameters = mCameraBack.getParameters();
                parameters.set("staturation", VideoConfig.instance.staturation);
                parameters.set("contrast", VideoConfig.instance.contrast);
                parameters.set("brightness", VideoConfig.instance.brightness);

                parameters.flatten();
                mCameraBack.setParameters(parameters);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    boolean SaveConfigFromUI() {
        //保存--本机名称
        EditText eti_myname = findViewById(R.id.id_my_name1);
        VideoConfig.instance.machine_name = eti_myname.getText().toString().trim();

        //dhcp
        CheckBox cbDHCP = findViewById(R.id.checkBoxAutoGet);
        if (cbDHCP.isChecked()) {
            VideoConfig.instance.using_dhcp = true;
            EditText eti_my_ip_addr = findViewById(R.id.my_ip_addr);
            VideoConfig.instance.hostIP = getLocalIpAddress();
            eti_my_ip_addr.setText(VideoConfig.instance.hostIP);
        } else {
            VideoConfig.instance.using_dhcp = false;

            EditText eti_my_ip_addr = findViewById(R.id.my_ip_addr);
            VideoConfig.instance.hostIP = eti_my_ip_addr.getText().toString().trim();

            ComParamSet(false, true, false);

            EditText eti_my_gate = findViewById(R.id.my_gate_addr);
            VideoConfig.instance.gateIP = eti_my_gate.getText().toString().trim();

            EditText eti_my_mask = findViewById(R.id.my_netmask_addr);
            VideoConfig.instance.maskIP = eti_my_mask.getText().toString().trim();
        }

        //推流分辨率 查看是否使用预设
        //是否使用预设分辨率
        CheckBox cbPrefernce = findViewById(R.id.checkUsePrefence);
        if (cbPrefernce.isChecked()) {
            VideoConfig.instance.SetResolutionIndex(resolutionSelector.getSelectedItemPosition());
        } else//不使用
        {
            VideoConfig.instance.SetResolutionIndex(-1);

            EditText eti_my_video_w = findViewById(R.id.custum_wideo_w);
            String ss = eti_my_video_w.getText().toString().trim();
            VideoConfig.instance.SetVideoWidth(Integer.parseInt(eti_my_video_w.getText().toString().trim()));

            EditText eti_my_video_h = findViewById(R.id.custum_wideo_h);
            VideoConfig.instance.SetVideoHeight(Integer.parseInt(eti_my_video_h.getText().toString().trim()));
        }

        //is_hardware_encoder已经实时更改了
        if (VideoConfig.instance.is_hardware_encoder) {

        } else//软编码
        {
            //软编码配置
            VideoConfig.instance.sw_video_encoder_profile = swVideoEncoderProfileSelector.getSelectedItemPosition() + 1;

            //软编码关键帧数
            VideoConfig.instance.sw_video_encoder_speed = 6 - swVideoEncoderSpeedSelector.getSelectedItemPosition();
        }

        //帧率
        EditText eti = findViewById(R.id.push_rate);
        String strFPS = eti.getText().toString().trim();
        VideoConfig.instance.SetFPS(Integer.parseInt(strFPS));

        //保存--推流地址
        EditText cam_front_url = (EditText) findViewById(R.id.cam1_url_edit);
        EditText cam_back_url = (EditText) findViewById(R.id.cam2_url_edit);

        VideoConfig.instance.url1 = cam_front_url.getText().toString().trim();
        VideoConfig.instance.url2 = cam_back_url.getText().toString().trim();

        EditText eti_serverip = findViewById(R.id.server_ip);
        VideoConfig.instance.destHost = eti_serverip.getText().toString().trim();

        EditText eti_server_port = findViewById(R.id.server_port);
        String strPort = eti_server_port.getText().toString().trim();
        VideoConfig.instance.SetAppPort(Integer.parseInt(strPort));

        EditText eti_config_serverip = findViewById(R.id.config_server_ip);
        VideoConfig.instance.configHost = eti_config_serverip.getText().toString().trim();

        EditText eti_config_server_port = findViewById(R.id.config_server_port);
        String streti_config_serveripPort = eti_config_server_port.getText().toString().trim();
        VideoConfig.instance.SetConfigPort(Integer.parseInt(streti_config_serveripPort));

        EditText eti_userID = findViewById(R.id.id_userid);
        VideoConfig.instance.userID = eti_userID.getText().toString().trim();
        ComParamSet(false, false, true);

        boolean is_applyok = true;
        if (VideoConfig.instance.GetResolutionIndex() == -1)//查看自定义的配置是否合法 不合法不推。
        {
            Camera front_camera = GetCameraObj(FRONT);
            if (front_camera != null && mSurfaceHolderFront != null) {
                front_camera.stopPreview();
                is_applyok = initCamera(FRONT, mSurfaceHolderFront);
            }

            Camera back_camera = GetCameraObj(BACK);
            if (back_camera != null && mSurfaceHolderBack != null) {
                back_camera.stopPreview();
                if (is_applyok)
                    is_applyok = initCamera(BACK, mSurfaceHolderBack);
            }

            //当两个摄像头都不存在 并且不是预设分辨率时 视为失败。不要问为什么。和小林商量的时候确定的
            if (front_camera == null && back_camera == null && VideoConfig.instance.GetResolutionIndex() == -1)
                is_applyok = false;

            if (is_applyok == false) {
                RestoreConfigAndUpdateVideoUI();
                Toast.makeText(getApplicationContext(), "无效的视频配置，已恢复原状!", Toast.LENGTH_SHORT).show();
                Log.e("asdfasdf", "无效的视频配置，已恢复原状");
                //	return false;
            }
        }

        //是否启用录像
        CheckBox chRecorder = findViewById(R.id.checkRecord);
        if (chRecorder.isChecked()) {
            VideoConfig.instance.is_need_local_recorder = true;
        } else {
            VideoConfig.instance.is_need_local_recorder = false;
        }

        CheckBox cbIncludeAudio = findViewById(R.id.cbIncludeAudio);
        if( cbIncludeAudio.isChecked() )
        {
            VideoConfig.instance.containAudio = true;
        }
        else
        {
            VideoConfig.instance.containAudio = false;
        }

        //是否只推一路
        CheckBox chSwitchToOne = findViewById(R.id.checkSwitchToOne);
        if (chSwitchToOne.isChecked()) {
            VideoConfig.instance.swtichToOne = true;
        } else {
            VideoConfig.instance.swtichToOne = false;
        }

        return true;
    }

    private void outputInfo(String strTxt, boolean append ) {

        FragmentLogTxt fc = (FragmentLogTxt) mFragments.get(0);
        fc.outputInfo( strTxt , append);
        /*TextView et = (TextView) findViewById(R.id.txtlog);
        String str_conten = et.getText().toString();
        if (et.getLineCount() >= 20)
            et.setText(strTxt);
        else {
            str_conten += "\r\n";
            str_conten += strTxt;
            et.setText(str_conten);
        }*/
    }

    public Handler mHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            //outputInfo("Handle:" + msg.what);
            if (msg.what >= MessageType.values().length)
                return;

            MessageType mt = MessageType.values()[msg.what];
            //outputInfo("Enum is" + mt.toString());

            switch (mt) {
                case msgWaitIP: {
                    if (getLocalIpAddress().equals("")) {
                        Toast.makeText(getApplicationContext(), "IP未就绪。等待IP", Toast.LENGTH_SHORT).show();
                        mHandler.sendEmptyMessageDelayed(MessageType.msgWaitIP.ordinal(), 3000);
                    } else {
                        mHandler.sendEmptyMessage(MessageType.msgIpGOT.ordinal());
                    }
                }
                break;
                case msgIpGOT: {
                    outputInfo("IP已就绪。配置线程运行。开始检查时间", false);
                    //Ip已获取。更新界面
                    VideoConfig.instance.hostIP = getLocalIpAddress();

                    findViewById(R.id.my_ip_addr).setEnabled(true);
                    EditText eti = (EditText) findViewById(R.id.my_ip_addr);
                    eti.setText(VideoConfig.instance.hostIP);

                    //监听本地局域网端口
                    lis_server = new MyTCServer();
                    lis_server.init();

                    //连接配置服务器
                    confiThread = new SockConfig();
                    {
                        if (VideoConfig.instance.enableConfigServer == true)
                            confiThread.StartWokring(mHandler, VideoConfig.instance.configHost, VideoConfig.instance.GetConfigPort());
                        else
                            confiThread.StartWokring(mHandler, "", 0);
                    }

                    //开始检查时间
                    mHandler.sendEmptyMessage(MessageType.msgCheckTime.ordinal());

                    //IP已获取到，给娃娃机设置本机IP
                    if (isWawajiReady) {
                        ComParamSet(true, true, true);//给串口发 MAC 本机IP 密码

                        //连接应用服务器
                        if (sendThread == null) {
                            outputInfo("时间就绪之-开始连接应用服务器.", false);
                            sendThread = new SockAPP();//空循环等待 没事
                            sendThread.StartWokring(mHandler, VideoConfig.instance.destHost, VideoConfig.instance.GetAppPort());
                        }

                        //本地无配置服务器地址配置 先跟串口要
                        if (VideoConfig.instance.configHost.equals("") || VideoConfig.instance.GetConfigPort() == 0) {
                            send_com_data(0x3c);//跟串口要IP和端口 要到以后 如果合法 它会自己开始连接并心跳
                        }
                    }
                }
                break;
                case msgCheckTime://空循环检查时间是否准备就绪 才能开始推
                {
                    Calendar c = Calendar.getInstance();
                    int year = c.get(Calendar.YEAR);
                    if (year < 2018) {
                        isTimeReady = false;
                        timeWaitCount--;
                        Toast.makeText(getApplicationContext(), "时间未就绪。等待.否则预览会卡死.剩余" + timeWaitCount + "次后强行推流", Toast.LENGTH_SHORT).show();
                        if (timeWaitCount <= 0) {
                            isTimeReady = true;
                            //延迟2秒后开始预览
                            mHandler.sendEmptyMessageDelayed(MessageType.msgOnTimeOK.ordinal(), 2000);
                        } else
                            mHandler.sendEmptyMessageDelayed(MessageType.msgCheckTime.ordinal(), 6000);

                    } else {
                        isTimeReady = true;
                        //延迟2秒后开始预览
                        mHandler.sendEmptyMessageDelayed(MessageType.msgOnTimeOK.ordinal(), 2000);//因为联网取到时间以后会导致预览卡顿。（安卓系统的问题。已通过开相机拔插网线验证）所以，如果是先开机，后网络可用的情况，直接重启。不然就会因为bug推流失败
                    }
                }
                break;
                case msgOnTimeOK: {
                    outputInfo("时间已就绪.", false);

                    //调用摄像头 初始化预览
                    Camera front_camera = GetCameraObj(FRONT);
                    if (front_camera != null && mSurfaceHolderFront != null) {
                        front_camera.stopPreview();
                        initCamera(FRONT, mSurfaceHolderFront);
                    }
                    if(front_camera != null)
                    {
                        Camera.Parameters parameters;
                        try {
                            parameters = front_camera.getParameters();

                            VideoConfig.instance.defaultStaturation = Integer.parseInt( parameters.get("staturation" ) );
                            VideoConfig.instance.defaultContrast = Integer.parseInt( parameters.get("contrast" ) );
                            VideoConfig.instance.defaultBrightness = Integer.parseInt( parameters.get("brightness" ) );

                            parameters.flatten();
                            front_camera.setParameters(parameters);
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }

                    Camera back_camera = GetCameraObj(BACK);

                    if (back_camera != null && mSurfaceHolderBack != null) {
                        back_camera.stopPreview();
                        initCamera(BACK, mSurfaceHolderBack);
                    }

                    if(back_camera != null)
                    {
                        Camera.Parameters parameters;
                        try {
                            parameters = back_camera.getParameters();
                            VideoConfig.instance.defaultStaturation = Integer.parseInt( parameters.get("staturation" ) );
                            VideoConfig.instance.defaultContrast = Integer.parseInt( parameters.get("contrast" ) );
                            VideoConfig.instance.defaultBrightness = Integer.parseInt( parameters.get("brightness" ) );

                            parameters.flatten();
                            back_camera.setParameters(parameters);
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }

                    if( VideoConfig.instance.usingCustomConfig == false )
                    {
                        VideoConfig.instance.staturation = VideoConfig.instance.defaultStaturation;
                        VideoConfig.instance.contrast = VideoConfig.instance.defaultContrast;
                        VideoConfig.instance.brightness =  VideoConfig.instance.defaultBrightness;
                    }

                    //先更新界面
                    updateCamUI();



                    //延迟开始推流
                    mHandler.sendEmptyMessageDelayed(MessageType.msgDelayPush.ordinal(), 2000);
                }
                break;
                case msgDelayPush: {
                    //时间就绪。这时候预览已经开始了。可以开启检查线程
                    mHandler.sendEmptyMessage(MessageType.msgCheckPreview.ordinal());//每隔5秒 就判断isFrontCameraPreviewOK isBackCameraPreviewOK 是否是真。如果是真，则

                    UpdateConfigToUI();//有可能拿到了新的应用服务器端口地址。所以更新UI

                    if (libPublisher == null) {
                        libPublisher = new SmartPublisherJniV2();
                    }

                    //将界面配置好的配置服务器地址传输到串口保存
                    SaveConfigHostInfoToCom();

                    //开始推流
                    UIClickStartPush();
                }
                break;
                case msgCheckWawajiReady://每隔一秒给娃娃机发状态查询指令 看看它是否就绪
                {
                    if (isWawajiReady == false) {
                        send_com_data(0x34);
                        mHandler.sendEmptyMessageDelayed(MessageType.msgCheckWawajiReady.ordinal(), 1000);
                    }
                }
                break;
                case msgNetworkData://收到socket过来的消息
                {
                    int msg_len = msg.arg1;
                    byte test_data[] = (byte[]) (msg.obj);

                    int net_cmd = (test_data[7] & 0xff);
                    if (net_cmd == 0x88)//收到要求重启命令
                    {
                        if (sendThread != null) {
                            sendThread.StopNow();
                            sendThread = null;
                        }

                        Log.e(TAG, "收到重启指令，立刻重启");

                        Intent intent = new Intent();
                        intent.setAction("ACTION_RK_REBOOT");
                        sendBroadcast(intent, null);

                    }else if(net_cmd == 0x90)
                    {
                        outputInfo( "立收到要求切流命令", false);

                        //收到命令 执行切流
                        if(VideoConfig.instance.curPushWay == 1)
                            VideoConfig.instance.curPushWay = 2;
                        else if( VideoConfig.instance.curPushWay ==2)
                            VideoConfig.instance.curPushWay = 1;


                        if( VideoConfig.instance.curPushWay == 1 && mCameraFront == null)
                        {
                            VideoConfig.instance.curPushWay = 2;
                            Toast.makeText(getApplicationContext(), "前置不存在。拒绝切换到前置", Toast.LENGTH_SHORT).show();
                        }

                        if( VideoConfig.instance.curPushWay == 2 && mCameraBack == null)
                        {
                            VideoConfig.instance.curPushWay = 1;
                            Toast.makeText(getApplicationContext(), "后置不存在。拒绝切换到后置", Toast.LENGTH_SHORT).show();
                        }

                        TextView tee = findViewById(R.id.curWay);
                        tee.setText("当前:" + VideoConfig.instance.curPushWay);

                        byte[] abc = make_cmd(0x91,  VideoConfig.instance.curPushWay);//4位空的预留 0x89命令码 前置状态  后置状态 4位预留。 其中：状态为:00 正常 1 使用中掉线 2摄像头缺失
                        if (sendThread != null) {
                            sendThread.sendMsg(abc);
                        }
                    }
                    else if(net_cmd == 0x99)//todo remove for debug use.不接娃娃机时的临时实现。用来模仿游戏结束的。此处用来停止录像。意思是接收到游戏结束后停止录像
                    {
                        outputInfo("结束，停止录像", false);
                        stopRecorder();
                        isRecording = false;

                        //更新界面
                        if( sdCardPath.equals("") == false)
                        {
                            int frontCount = GetRecFileList( sdCardPath + fronDirName );
                            int backCount = GetRecFileList( sdCardPath + backDirName );
                            initRecordUI(sdCardPath, frontCount + backCount);
                        }
                    }
                    else//其他命令 转发给串口
                    {
                        String sock_data = ComPort.bytes2HexString(test_data, msg_len);
                        outputInfo("收到网络数据:" + sock_data + "发往串口", false);
                        //检查如果不是配置IP之类的东西 就往串口发

                        //往串口发
                        if (isShouldRebootSystem == true && net_cmd == 0x31)//系统因为摄像头断流的原因要重启。析出开局指令不转发
                        {
                            outputInfo("检测到设备需要重启。不转发开局指令", false);
                        } else
                            mComPort.SendData(test_data, msg_len);

                        if( net_cmd == 0x31)//开局指令 检查是否需要录像
                        {
                            outputInfo("开局，开始录像", false);

                            if( checkSpaceThread != null)
                            {
                                checkSpaceThread.Check( sdCardPath );
                            }

                            if( VideoConfig.instance.is_need_local_recorder)
                                BeginRecord();
                        }
                    }
                }
                break;
                case msgMyFireHeartBeat: {
                    //心跳调试
                    outputInfo("发送心跳消息", false);
                }
                break;
                case msgComRawDataPrint:
                    {
                        String ss = msg.obj.toString();
                        outputInfo(ss, false);
                    }
                    break;
                case msgConfigData: {
                    //收到配置口过来的数据
                    outputInfo("应用更改.", false);
                    UpdateConfigToUI();
                    UIClickStopPush();

                    SaveConfigHostInfoToCom();

                    if (sendThread != null)
                        sendThread.ApplyNewServer(VideoConfig.instance.destHost, VideoConfig.instance.GetAppPort());

                    if (confiThread != null) {
                        if (VideoConfig.instance.enableConfigServer == true)
                            confiThread.ApplyNewServer(VideoConfig.instance.configHost, VideoConfig.instance.GetConfigPort());
                        else
                            confiThread.ApplyNewServer("", 0);
                    }

                    boolean is_applyok = true;
                    Camera front_camera = GetCameraObj(FRONT);
                    if (front_camera != null && mSurfaceHolderFront != null) {
                        front_camera.stopPreview();
                        is_applyok = initCamera(FRONT, mSurfaceHolderFront);
                    }

                    Camera back_camera = GetCameraObj(BACK);
                    if (back_camera != null && mSurfaceHolderBack != null) {
                        back_camera.stopPreview();
                        if (is_applyok)
                            is_applyok = initCamera(BACK, mSurfaceHolderBack);
                    }

                    //当两个摄像头都不存在 并且是预设分辨率时 视为失败。不要问为什么。和小林商量的时候确定的
                    if (front_camera == null && back_camera == null && VideoConfig.instance.GetResolutionIndex() == -1)
                        is_applyok = false;

                    Socket ssa = (Socket) msg.obj;
                    if (is_applyok == false) {
                        RestoreConfigAndUpdateVideoUI();
                        Toast.makeText(getApplicationContext(), "无效的视频配置，已恢复原状!", Toast.LENGTH_SHORT).show();
                        UIClickStartPush();

                        try {
                            if (ssa != null && ssa.isConnected()) {
                                String s = "{\"result\":\"failed\"}";//将结果发回发送端
                                OutputStream outputStream = ssa.getOutputStream();
                                outputStream.write(s.getBytes(), 0, s.getBytes().length);
                                outputStream.flush();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e("返回结果", "失败");
                        }
                    } else {
                        try {
                            if (ssa != null && ssa.isConnected()) {
                                String s = "{\"result\":\"ok\"}";//将结果发回发送端
                                OutputStream outputStream = ssa.getOutputStream();
                                outputStream.write(s.getBytes(), 0, s.getBytes().length);
                                outputStream.flush();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e("返回结果", "失败");
                        }
                        UIClickStartPush();
                    }
                }
                break;
                case msgOnUpdate://收到更新命令
                {
                    String jsonStr = (String) (msg.obj);
                    try {
                        JSONObject jsonObject = new JSONObject(jsonStr);
                        String url = jsonObject.getString("url");

                        int versionCode = 0;
                        if (jsonObject.has("versionCode"))
                            versionCode = jsonObject.getInt("versionCode");

                        Log.e("收到更新命令", "url" + url + " 当前版本:" + VideoConfig.instance.appVersion);
                        SilentInstall upobj = new SilentInstall(getApplicationContext());
                        upobj.startUpdate(url);
                    } catch (JSONException jse) {
                        jse.printStackTrace();
                    }
                }
                break;
                case msgComData: //串口过来的消息
                {
                    byte test_data[] = (byte[]) (msg.obj);
                    int data_len = msg.arg1;

                    //处理从串口过来的消息。如果是心跳 不管。如果不是 转发给服务器
                    String com_data = ComPort.bytes2HexString(test_data, data_len);
                    //if( check_com_data( test_data, data_len ) == false )
                    {
                       // Log.e(TAG, "串口 长度:"+ data_len +" 数据:" + com_data);
                    }

                    outputInfo("串口数据 长度:" + data_len + " data " + com_data, false);

                    int cmd_value = test_data[7]&0xff;
                    if( cmd_value == 0x33)
                    {
                        outputInfo("结束，停止录像", false);
                        stopRecorder();
                        isRecording = false;

                        //更新界面
                        if( sdCardPath.equals("") == false)
                        {
                            int frontCount = GetRecFileList( sdCardPath + fronDirName );
                            int backCount = GetRecFileList( sdCardPath + backDirName );
                            initRecordUI(sdCardPath, frontCount + backCount);
                        }
                    }

                    if(cmd_value == 0x35)
                    {
                        //检查mac是否是全空 是的话 mac ip 发给串口--add 20180420.防止主板重启而安卓板不重启的情况.
                        if (trySetMACCount<3 &&
                                data_len >= 21
                                && test_data[8] == 0
                                && test_data[9] == 0
                                && test_data[10] == 0
                                && test_data[11] == 0
                                && test_data[12] == 0
                                && test_data[13] == 0
                                &&test_data[14] == 0
                                && test_data[15] == 0
                                && test_data[16] == 0
                                && test_data[17] == 0
                                && test_data[18] == 0
                                && test_data[19] == 0)
                        {
                            trySetMACCount ++;
                            ComParamSet(true, true, false);
                        }

                        if(sendThread != null)
                            sendThread.heartBeat();

                        Message me1 = Message.obtain();//心跳消息
                        me1.what = CameraPublishActivity.MessageType.msgMyFireHeartBeat.ordinal();
                        if (mHandler != null) mHandler.sendMessage(me1);
                    }

                    if (cmd_value ==   0x35 || cmd_value == 0x34) {
                        //如果是正常的 0X34  0x35我要透传
                        if (cmd_value ==  0x34 ) {
                            //queryStateTimeoutTime = 0;
                            if (sendThread != null) {
                                sendThread.sendMsg(test_data);
                                outputInfo(" 发到服务器", true);
                            }
                        }

                        if (cmd_value ==  0x34 && test_data[6] == (byte) 0x0e && isShouldRebootSystem == true) {
                            wawajiCurrentState = test_data[8] & 0xff;
                            //fe 00 00 01 ff ff 0e 34 num1 num2 num3 num4 num5 [校验位1] Num1表示机台状态0，1，2是正常状态，其它看 ** [通知]故障上报 **
                            if (test_data[8] == 1 || test_data[8] == 2)//要求重启的时候，娃娃机正在有人玩.啥事也不做。等待
                            {

                            } else {

                                if (sendThread != null) {
                                    sendThread.StopNow();
                                    sendThread = null;
                                }

                                Log.e(TAG, "娃娃机已满足重启要求。立刻重启");

                                Intent intent = new Intent();
                                intent.setAction("ACTION_RK_REBOOT");
                                sendBroadcast(intent, null);
                            }
                        }

                        if (isWawajiReady == false)//娃娃机就绪。检查是否需要跟娃娃机获取应用服务器端口 如果不用。则直接生成连接应用服务器的对象
                        {
                            isWawajiReady = true;
                            outputInfo("娃娃机已就绪.", false);

                            //连接应用服务器
                            if (sendThread == null) {
                                outputInfo("娃机就绪之-开始连接应用服务器.", false);
                                sendThread = new SockAPP();//空循环等待 没事
                                sendThread.StartWokring(mHandler, VideoConfig.instance.destHost, VideoConfig.instance.GetAppPort());
                            }

                            //本地无配置服务器地址配置 先跟串口要
                            if (VideoConfig.instance.configHost.equals("") || VideoConfig.instance.GetConfigPort() == 0) {
                                send_com_data(0x3c);//跟串口要IP和端口 要到以后 如果合法 它会自己开始连接并心跳
                            }

                            //先发MAC。然后检查ip是否可用。如果有，也要发给他。
                            ComParamSet(true, false, true);//给串口发 MAC 密码
                            if (getLocalIpAddress().equals("") == false) {
                                ComParamSet(false, true, false);//给串口发本机IP
                            }
                        }

                        //wawaji is alive
                    } else if (cmd_value ==   0x42 && data_len > 14)//串口过来的配置服务器IP地址和端口//有一次出现收到0x42但是数据长度不够15位，导致我访问越界这是什么鬼,并且你的校验和是对的？//2018.2.1 add fix add data_len>14
                    {
                        //只要不是空 就重新开启sendThread
                        int a = test_data[8] & 0xff;
                        int b = test_data[9] & 0xff;
                        int c = test_data[10] & 0xff;
                        int d = test_data[11] & 0xff;

                        int e = test_data[12] & 0xff;
                        int f = test_data[13] & 0xff;
                        int nPort = e * 256 + f;

                        String s_ip = String.format("%d.%d.%d.%d", a, b, c, d);
                        outputInfo("收到串口配置IP地址" + s_ip + "端口" + nPort, false);
                        Log.e("收到串口配置IP地址", s_ip + "端口" + nPort);

                        //if( s_ip.equals("0.0.0.0") == false && nPort != 0)
                        //{
                        VideoConfig.instance.configHost = s_ip;
                        VideoConfig.instance.SetConfigPort(nPort);

                        //配置服务器IP
                        EditText eti_serverip = findViewById(R.id.config_server_ip);
                        eti_serverip.setText(VideoConfig.instance.configHost);

                        //配置服务器端口
                        EditText eti_server_port = findViewById(R.id.config_server_port);
                        eti_server_port.setText(Integer.toString(VideoConfig.instance.GetConfigPort()));

                        if (s_ip.equals("0.0.0.0") == false && nPort != 0) {
                            VideoConfig.instance.enableConfigServer = true;
                            CheckBox cbEnableConfig = findViewById(R.id.enableConfServer);
                            cbEnableConfig.setChecked(true);
                        } else {
                            VideoConfig.instance.enableConfigServer = false;
                            CheckBox cbEnableConfig = findViewById(R.id.enableConfServer);
                            cbEnableConfig.setChecked(false);
                        }

                        if (confiThread != null)
                            confiThread.ApplyNewServer(VideoConfig.instance.configHost, VideoConfig.instance.GetConfigPort());

                        VideoConfig.instance.SaveConfig(getApplicationContext());
                    } else {//透传给服务器
                        if (sendThread != null) {
                            sendThread.sendMsg(test_data);
                            outputInfo(" 发到服务器", true);
                        }
                    }
                    break;
                }
                case msgUDiskMount://插U盘
                {
                    String UPath = (String) (msg.obj);
                    // 获取sd卡的对应的存储目录
                    //获取指定文件对应的输入流
                    try {

                        sdCardPath = UPath;
                        int frontCount = GetRecFileList( sdCardPath + fronDirName );
                        int backCount = GetRecFileList( sdCardPath + backDirName );

                        //检查可用空间 和已有文件大小是否满足要求。不满足，则置空。因为会频繁触发文件检查 这是不允许的
                        if( frontCount + backCount <200 && getSDFreesSpace(sdCardPath)<300)
                        {
                            Log.e(TAG, "U盘即使删除文件也无法满足临界要求。不存储");
                            Toast.makeText(getApplicationContext(), "U盘即使删除文件也无法满足临界要求。不存储", Toast.LENGTH_SHORT).show();
                            sdCardPath= "";
                            initRecordUI("",0);
                        }
                        else
                            initRecordUI(sdCardPath, frontCount + backCount);

                        FileInputStream fis = new FileInputStream(UPath + "/config.txt");
                        //将指定输入流包装成 BufferedReader
                        BufferedReader br = new BufferedReader(new InputStreamReader(fis, "GBK"));

                        StringBuilder sb = new StringBuilder("");
                        String line = null;
                        //循环读取文件内容
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }

                        //关闭资源
                        br.close();
                        Log.e("file content", sb.toString());

                        try {
                            JSONObject jsonOBJ = new JSONObject(sb.toString());
                            if (jsonOBJ.has("wifiSSID")) {
                                String wifiSSID = jsonOBJ.getString("wifiSSID");

                                String wifiPassword = "";
                                if (jsonOBJ.has("wifiPassword"))
                                    wifiPassword = jsonOBJ.getString("wifiPassword");

                                //启用wifi
                                if (!wifiManager.isWifiEnabled())
                                    wifiManager.setWifiEnabled(true);

                                //连接特定的wifi
                                int ntype = wifiPassword.equals("") ? 1 : 3;
                                WifiAutoConnectManager.WifiCipherType ntr = wifiPassword.equals("") ?
                                        WifiAutoConnectManager.WifiCipherType.WIFICIPHER_NOPASS : WifiAutoConnectManager.WifiCipherType.WIFICIPHER_WPA;

                                Log.e("连接wifi", "ssid" + wifiSSID + " pwd " + wifiPassword + "type" + ntype);
                                //WifiUtil.createWifiInfo(wifiSSID, wifiPassword, ntype, wifiManager);

                                wifiauto.connect(wifiSSID, wifiPassword, ntr);
                            }

                            boolean apply_ret = VideoConfig.instance.ApplyConfig(sb.toString(), null);

                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.e("u盘配置文件错误", "Json file Error.");
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
                case msgUDiskUnMount:
                    {
                        if( isRecording )
                            stopRecorder();

                        sdCardPath = "";

                        initRecordUI( sdCardPath, 0);
                    }
                    break;
                case msgUpdateFreeSpace:
                    {
                        //更新界面
                        if( sdCardPath.equals("") == false)
                        {
                            int frontCount = GetRecFileList( sdCardPath + fronDirName );
                            int backCount = GetRecFileList( sdCardPath + backDirName );
                            initRecordUI(sdCardPath, frontCount + backCount);
                        }
                    }
                    break;
                case msgCheckPreview: {
                    //if( isWawajiReady == false)
                    //Log.i(TAG, "CheckingPreview");

                    if (mCameraFront != null) {
                        outputInfo("isFrontCameraPreviewOK" + isFrontCameraPreviewOK, false);

                        if (isFrontCameraPreviewOK)
                            isFrontCameraPreviewOK = false;
                        else if (isFrontCameraPreviewOK == false) {
                            if (isShouldRebootSystem == false) {
                                isShouldRebootSystem = true;
                                wawajiCurrentState = -1;
                                mHandler.sendEmptyMessageDelayed(MessageType.msgCheckWawaNowState.ordinal(), 3000);

                                int backCamValue = 0;
                                if (mCameraBack == null) backCamValue = 2;
                                else if (isBackCameraPreviewOK == false)
                                    backCamValue = 1;

                                byte[] abc = make_cmd(0x89, 01, backCamValue, 0, 0, 0, 0);//4位空的预留 0x89命令码 前置状态  后置状态 4位预留。 其中：状态为:00 正常 1 使用中掉线 2摄像头缺失
                                if (sendThread != null) {
                                    sendThread.sendMsg(abc);
                                }

                                outputInfo("前置摄像头有效性错误。设备需要重启。", false);
                                Log.e(TAG, "前置摄像头有效性错误。设备需要重启。");
                                mHandler.sendEmptyMessage(MessageType.msgQueryWawajiState.ordinal());
                            }
                        }
                    }

                    if (mCameraBack != null) {
                        outputInfo("isBackCameraPreviewOK" + isBackCameraPreviewOK, false);

                        if (isBackCameraPreviewOK)
                            isBackCameraPreviewOK = false;
                        else if (isBackCameraPreviewOK == false) {
                            if (isShouldRebootSystem == false) {
                                isShouldRebootSystem = true;
                                wawajiCurrentState = -1;
                                mHandler.sendEmptyMessageDelayed(MessageType.msgCheckWawaNowState.ordinal(), 3000);

                                int frontCamValue = 0;
                                if (mCameraFront == null) frontCamValue = 2;
                                else if (isFrontCameraPreviewOK == false)
                                    frontCamValue = 1;

                                byte[] abc = make_cmd(0x89, frontCamValue, 01, 0, 0, 0, 0);//4位空的预留
                                if (sendThread != null) {
                                    sendThread.sendMsg(abc);
                                }

                                outputInfo("后置摄像头有效性错误。设备需要重启。", false);
                                Log.e(TAG, "后置摄像头有效性错误。设备需要重启。");
                                mHandler.sendEmptyMessage(MessageType.msgQueryWawajiState.ordinal());
                            }
                        }
                    }

                    mHandler.sendEmptyMessageDelayed(MessageType.msgCheckPreview.ordinal(), 5000);
                }
                break;
                case msgCheckWawaNowState: {
                    if (wawajiCurrentState != 1 && wawajiCurrentState != 2) {
                        Log.e(TAG, "娃娃机状态已满足重启要求，立刻重启");

                        Intent intent = new Intent();
                        intent.setAction("ACTION_RK_REBOOT");
                        sendBroadcast(intent, null);
                    }
                }
                break;
                case msgQueryWawajiState: {
                    //queryStateTimeoutTime++;
                    send_com_data(0x34);
                    mHandler.sendEmptyMessageDelayed(MessageType.msgQueryWawajiState.ordinal(), 1000);
						/*if( queryStateTimeoutTime >= 10)
						{
							Log.e(TAG,"状态查询超时10次，立刻重启" );

							Intent intent=new Intent();
							intent.setAction("ACTION_RK_REBOOT");
							sendBroadcast(intent,null);
						}*/
                }
                break;
                case msgApplyCamparam:
                    {
                        ApplyCam3Params();
                        VideoConfig.instance.usingCustomConfig = true;
                    }
                    break;
                case msgRestoreCamparam:
                    {
                        VideoConfig.instance.usingCustomConfig = false;
                        VideoConfig.instance.staturation =  VideoConfig.instance.defaultStaturation;
                        VideoConfig.instance.contrast =  VideoConfig.instance.defaultContrast;
                        VideoConfig.instance.brightness =  VideoConfig.instance.defaultBrightness;
                        ApplyCam3Params();
                        updateCamUI();
                    }
                    break;
            }
        }
    };



    void SwitchResolution(int position) {
        if (isTimeReady == false) return;
        //分辨率配置
        //"960*720", "640*480","640*360", "352*288","320*240"
        Log.i(TAG, "Current Resolution position: " + position);

        VideoConfig.instance.SetResolutionIndex(position);

        switch (position) {
            case 0: {
                VideoConfig.instance.SetVideoWidth(960);
                VideoConfig.instance.SetVideoHeight(720);
            }
            break;
            case 1:
                VideoConfig.instance.SetVideoWidth(640);
                VideoConfig.instance.SetVideoHeight(480);
                break;
            case 2:
                VideoConfig.instance.SetVideoWidth(640);
                VideoConfig.instance.SetVideoHeight(360);
                break;
            case 3:
                VideoConfig.instance.SetVideoWidth(352);
                VideoConfig.instance.SetVideoHeight(288);
                break;
            case 4:
                VideoConfig.instance.SetVideoWidth(320);
                VideoConfig.instance.SetVideoHeight(240);
                break;
            case 5: {
                VideoConfig.instance.SetVideoWidth(555);
                VideoConfig.instance.SetVideoHeight(555);
            }
            break;
            default:
                VideoConfig.instance.SetVideoWidth(640);
                VideoConfig.instance.SetVideoHeight(360);
        }

        boolean is_applyok = true;
        Camera front_camera = GetCameraObj(FRONT);
        if (front_camera != null && mSurfaceHolderFront != null) {
            front_camera.stopPreview();
            is_applyok = initCamera(FRONT, mSurfaceHolderFront);
        }

        Camera back_camera = GetCameraObj(BACK);
        if (back_camera != null && mSurfaceHolderBack != null) {
            back_camera.stopPreview();
            if (is_applyok != false)
                is_applyok = initCamera(BACK, mSurfaceHolderBack);
        }

        //当两个摄像头都不存在 并且是预设分辨率时 视为失败。不要问为什么。和小林商量的时候确定的
        if (front_camera == null && back_camera == null && VideoConfig.instance.GetResolutionIndex() == -1)
            is_applyok = false;

        if (is_applyok == false) {
            Toast.makeText(getApplicationContext(), "错误的配置,回退到正确的配置", Toast.LENGTH_SHORT).show();

            RestoreConfigAndUpdateVideoUI();
        }
    }

    void RestoreConfigAndUpdateVideoUI() {
        VideoConfig.instance.RestoreLastVideoSizeAndIndex(this);

        if (VideoConfig.instance.GetResolutionIndex() != -1) {
            CheckBox cbPrefernce = findViewById(R.id.checkUsePrefence);
            cbPrefernce.setChecked(true);

            findViewById(R.id.resolutionSelctor).setVisibility(View.VISIBLE);
            findViewById(R.id.custum_w_tip).setVisibility(View.INVISIBLE);
            findViewById(R.id.custum_wideo_w).setVisibility(View.INVISIBLE);
            findViewById(R.id.custum_h_tip).setVisibility(View.INVISIBLE);
            findViewById(R.id.custum_wideo_h).setVisibility(View.INVISIBLE);
            resolutionSelector.setSelection(VideoConfig.instance.GetResolutionIndex());
        } else {
            CheckBox cbPrefernce = findViewById(R.id.checkUsePrefence);
            cbPrefernce.setChecked(false);
            findViewById(R.id.resolutionSelctor).setVisibility(View.INVISIBLE);
            findViewById(R.id.custum_w_tip).setVisibility(View.VISIBLE);
            findViewById(R.id.custum_wideo_w).setVisibility(View.VISIBLE);
            EditText eti = (EditText) findViewById(R.id.custum_wideo_w);
            eti.setText(Integer.toString(VideoConfig.instance.GetVideoWidth()));

            findViewById(R.id.custum_h_tip).setVisibility(View.VISIBLE);
            findViewById(R.id.custum_wideo_h).setVisibility(View.VISIBLE);
            eti = (EditText) findViewById(R.id.custum_wideo_h);
            eti.setText(Integer.toString(VideoConfig.instance.GetVideoHeight()));
        }
    }

    //Configure recorder related function.

    void ConfigRecorderFuntion(String rec, long handle, boolean isNeedLocalRecorder) {
        if (libPublisher != null) {
            if (isNeedLocalRecorder) {
                if (rec != null && !rec.isEmpty()) {
                    int ret = libPublisher.SmartPublisherCreateFileDirectory(rec);
                    if (0 == ret) {
                        if (0 != libPublisher.SmartPublisherSetRecorderDirectory(handle, rec)) {
                            Log.e(TAG, "Set recoder dir failed , path:" + rec);
                            return;
                        }

                        if (0 != libPublisher.SmartPublisherSetRecorder(handle, 1)) {
                            Log.e(TAG, "SmartPublisherSetRecoder failed.");
                            return;
                        }

                        if (0 != libPublisher.SmartPublisherSetRecorderFileMaxSize(handle, 200)) {
                            Log.e(TAG, "SmartPublisherSetRecoderFileMaxSize failed.");
                            return;
                        }

                    } else {
                        Log.e(TAG, "Create recoder dir failed, path:" + rec);
                    }
                }
            } else {
                if (0 != libPublisher.SmartPublisherSetRecorder(handle, 0)) {
                    Log.e(TAG, "SmartPublisherSetRecoder failed.");
                    return;
                }
            }
        }
    }



    class ButtonHardwareEncoderListener implements OnClickListener {
        public void onClick(View v) {
            VideoConfig.instance.is_hardware_encoder = !VideoConfig.instance.is_hardware_encoder;

            if (VideoConfig.instance.is_hardware_encoder) {
                btnHWencoder.setText("当前硬编码");
                //显示软编码选项
                findViewById(R.id.swVideoEncoderProfileSelector).setVisibility(View.INVISIBLE);
                findViewById(R.id.speed_tip).setVisibility(View.INVISIBLE);
                findViewById(R.id.sw_video_encoder_speed_selctor).setVisibility(View.INVISIBLE);
            } else {
                btnHWencoder.setText("当前软编码");
                //显示软编码选项
                findViewById(R.id.swVideoEncoderProfileSelector).setVisibility(View.VISIBLE);
                findViewById(R.id.speed_tip).setVisibility(View.VISIBLE);
                findViewById(R.id.sw_video_encoder_speed_selctor).setVisibility(View.VISIBLE);
            }

        }
    }

    void NotifyStreamResult(int CameraType, PushState nowPS)//当推流状态变化时，通知服务器端
    {
        if (CameraType == 0) {
            if (nowPS == pst_front)
                return;

            pst_front = nowPS;
        } else if (CameraType == 1) {
            if (nowPS == pst_back)
                return;

            pst_back = nowPS;
        }

        byte msg_content[] = new byte[22];
        msg_content[0] = (byte) 0xfe;
        msg_content[1] = (byte) (0);
        msg_content[2] = (byte) (0);
        msg_content[3] = (byte) ~msg_content[0];
        msg_content[4] = (byte) ~msg_content[1];
        msg_content[5] = (byte) ~msg_content[2];
        msg_content[6] = (byte) (msg_content.length);
        msg_content[7] = (byte) 0xa0;

        System.arraycopy(VideoConfig.instance.getMac().getBytes(), 0, msg_content, 8, VideoConfig.instance.getMac().getBytes().length);

        if (CameraType == 0 && nowPS == PushState.FAILED) {
            msg_content[20] = 0x00;//
        } else if (CameraType == 0 && nowPS == PushState.OK) {
            msg_content[20] = 0x01;//
        } else if (CameraType == 0 && nowPS == PushState.CLOSE) {
            msg_content[20] = 0x02;//
        } else if (CameraType == 1 && nowPS == PushState.FAILED) {
            msg_content[20] = 0x10;//
        } else if (CameraType == 1 && nowPS == PushState.OK) {
            msg_content[20] = 0x11;//
        } else if (CameraType == 1 && nowPS == PushState.CLOSE) {
            msg_content[20] = 0x12;//
        }

        int total_c = 0;
        for (int i = 6; i < msg_content.length - 1; i++) {
            total_c += (msg_content[i] & 0xff);
        }
        msg_content[msg_content.length - 1] = (byte) (total_c % 100);

        if (sendThread != null) sendThread.sendMsg(msg_content);
    }

    class EventHandeV2 implements NTSmartEventCallbackV2 {
        @Override
        public void onNTSmartEventCallbackV2(long handle, int id, long param1, long param2, String param3, String param4, Object param5) {

            Log.d(TAG, "EventHandeV2: handle=" + handle + " id:" + id);

            switch (id) {
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_STARTED:
                    txt = "开始。。";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_CONNECTING:
                    txt = "连接中。。";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_CONNECTION_FAILED:
                    txt = "连接失败。。";
                    if (handle == publisherHandleFront) {
                        VideoConfig.instance.videoPushState_1 = false;
                        NotifyStreamResult(0, PushState.FAILED);
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam1_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    else if(handle == publisherHandleCurrent)
                    {
                        VideoConfig.instance.videoPushState_1 = false;
                        NotifyStreamResult(0, PushState.FAILED);
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam1_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    else if (handle == publisherHandleBack) {
                        NotifyStreamResult(1, PushState.FAILED);
                        VideoConfig.instance.videoPushState_2 = false;
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam2_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_CONNECTED:
                    txt = "连接成功。。";
                    if (handle == publisherHandleFront) {
                        NotifyStreamResult(0, PushState.OK);
                        VideoConfig.instance.videoPushState_1 = true;
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam1_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(0, 255, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    else if(handle == publisherHandleCurrent)
                    {
                        NotifyStreamResult(0, PushState.OK);
                        VideoConfig.instance.videoPushState_1 = true;
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam1_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(0, 255, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    else if (handle == publisherHandleBack) {
                        NotifyStreamResult(1, PushState.OK);
                        VideoConfig.instance.videoPushState_2 = true;
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam2_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(0, 255, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_DISCONNECTED:
                    txt = "连接断开。。";
                    if (handle == publisherHandleFront) {
                        VideoConfig.instance.videoPushState_1 = false;
                        NotifyStreamResult(0, PushState.FAILED);
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam1_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    else if( handle == publisherHandleCurrent)
                    {
                        VideoConfig.instance.videoPushState_1 = false;
                        NotifyStreamResult(0, PushState.FAILED);
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam1_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    else if (handle == publisherHandleBack) {
                        NotifyStreamResult(1, PushState.FAILED);
                        VideoConfig.instance.videoPushState_2 = false;
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam2_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_STOP:
                    txt = "关闭。。";
                    if (handle == publisherHandleFront) {
                        NotifyStreamResult(0, PushState.CLOSE);
                        VideoConfig.instance.videoPushState_1 = false;
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam1_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    else if(handle == publisherHandleCurrent)
                    {
                        NotifyStreamResult(0, PushState.CLOSE);
                        VideoConfig.instance.videoPushState_1 = false;
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam1_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    else if (handle == publisherHandleBack) {
                        NotifyStreamResult(1, PushState.CLOSE);
                        VideoConfig.instance.videoPushState_2 = false;
                        CameraPublishActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tvFr = findViewById(R.id.cam2_url_tip);
                                if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                                getWindow().getDecorView().postInvalidate();
                            }
                        });
                    }
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_RECORDER_START_NEW_FILE:
                    Log.i(TAG, "开始一个新的录像文件 : " + param3);
                    txt = "开始一个新的录像文件。。";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_ONE_RECORDER_FILE_FINISHED:
                    Log.i(TAG, "已生成一个录像文件 : " + param3);
                    txt = "已生成一个录像文件。。";
                    break;

                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_SEND_DELAY:
                    Log.i(TAG, "发送时延: " + param1 + " 帧数:" + param2);
                    txt = "收到发送时延..";
                    break;

                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_CAPTURE_IMAGE:
                    Log.i(TAG, "快照: " + param1 + " 路径：" + param3);

                    if (param1 == 0) {
                        txt = "截取快照成功。.";
                    } else {
                        txt = "截取快照失败。.";
                    }
                    break;
            }

            String str = "当前回调状态：" + txt;

            Log.d(TAG, str);
        }
    }

    private void ConfigControlEnable(boolean isEnable) {
        btnHWencoder.setEnabled(isEnable);

        findViewById(R.id.id_my_name1).setEnabled(isEnable);
        findViewById(R.id.checkBoxAutoGet).setEnabled(isEnable);//dhcp
        if (isEnable) {
            if (VideoConfig.instance.using_dhcp == false) {
                findViewById(R.id.my_ip_addr).setEnabled(true);
            } else {
                findViewById(R.id.my_ip_addr).setEnabled(false);
            }
        } else {
            findViewById(R.id.my_ip_addr).setEnabled(false);
        }

        findViewById(R.id.enableConfServer).setEnabled(isEnable);
        if (isEnable) {
            if (VideoConfig.instance.enableConfigServer == false) {
                findViewById(R.id.config_server_ip).setEnabled(false);
                findViewById(R.id.config_server_port).setEnabled(false);
            } else {
                findViewById(R.id.config_server_ip).setEnabled(true);
                findViewById(R.id.config_server_port).setEnabled(true);
            }
        } else {
            findViewById(R.id.config_server_ip).setEnabled(false);
            findViewById(R.id.config_server_port).setEnabled(false);
        }

        findViewById(R.id.my_gate_addr).setEnabled(isEnable);
        findViewById(R.id.my_netmask_addr).setEnabled(isEnable);

        findViewById(R.id.checkUsePrefence).setEnabled(isEnable);
        findViewById(R.id.resolutionSelctor).setEnabled(isEnable);
        findViewById(R.id.custum_wideo_w).setEnabled(isEnable);
        findViewById(R.id.custum_wideo_h).setEnabled(isEnable);

        findViewById(R.id.swVideoEncoderProfileSelector).setEnabled(isEnable);
        findViewById(R.id.sw_video_encoder_speed_selctor).setEnabled(isEnable);
        findViewById(R.id.push_rate).setEnabled(isEnable);
        findViewById(R.id.cam1_url_edit).setEnabled(isEnable);
        findViewById(R.id.cam2_url_edit).setEnabled(isEnable);

        findViewById(R.id.server_ip).setEnabled(isEnable);
        findViewById(R.id.server_port).setEnabled(isEnable);


        findViewById(R.id.checkRecord).setEnabled(isEnable);
        findViewById(R.id.checkSwitchToOne).setEnabled(isEnable);
        findViewById(R.id.cbIncludeAudio).setEnabled(isEnable);
    }

    private void SetConfig(long handle) {
        if (libPublisher == null)
            return;

        if (handle == 0)
            return;

        int iMute = 0;
        if( VideoConfig.instance.containAudio == true)
            iMute = 0;
        else
            iMute = 1;

        //非镜像
        libPublisher.SmartPublisherSetMirror(handle, 0);

        //静音
        libPublisher.SmartPublisherSetMute(handle,iMute);

        //设置码率
        if (VideoConfig.instance.is_hardware_encoder) {
            int hwHWKbps = setHardwareEncoderKbps(VideoConfig.instance.GetVideoWidth(), VideoConfig.instance.GetVideoHeight());

            Log.i(TAG, "hwHWKbps: " + hwHWKbps);

            int isSupportHWEncoder = libPublisher.SetSmartPublisherVideoHWEncoder(handle, hwHWKbps);

            if (isSupportHWEncoder == 0) {
                Log.i(TAG, "Great, it supports hardware encoder!");
            }
        }

        //硬编码
        if (VideoConfig.instance.is_hardware_encoder) {
            libPublisher.SmartPublisherSetFPS(handle, 20);
        }

        libPublisher.SetSmartPublisherEventCallbackV2(handle, new EventHandeV2());

        //音频-set AAC encoder
        libPublisher.SmartPublisherSetAudioCodecType(handle, 1);
        //音频-噪音抑制
        libPublisher.SmartPublisherSetNoiseSuppression(handle, 1);
        //音频编码
        libPublisher.SmartPublisherSetAGC(handle, 0);

        libPublisher.SmartPublisherSetSWVideoEncoderProfile(handle, VideoConfig.instance.sw_video_encoder_profile);
        libPublisher.SmartPublisherSetSWVideoEncoderSpeed(handle, VideoConfig.instance.sw_video_encoder_speed);

        libPublisher.SmartPublisherSaveImageFlag(handle, 0);
        libPublisher.SmartPublisherSetClippingMode(handle, 0);
    }

    private void InitAndSetConfig() {

        int inCludeAudio = 0;
        if( VideoConfig.instance.containAudio == true)
            inCludeAudio = 1;
        else
            inCludeAudio = 0;

        if(VideoConfig.instance.swtichToOne == false)
        {
            Camera front_cam = GetCameraObj(FRONT);
            if(front_cam != null)
            {
                publisherHandleFront = libPublisher.SmartPublisherOpen(myContext,inCludeAudio, /*video_opt*/1,
                        VideoConfig.instance.GetVideoWidth(), VideoConfig.instance.GetVideoHeight());

                if (publisherHandleFront != 0) {
                    SetConfig(publisherHandleFront);
                    Log.e("前置摄像头", "ID" + publisherHandleFront);
                }
            }

            Camera back_cam = GetCameraObj(BACK);
            if( back_cam != null)
            {
                publisherHandleBack = libPublisher.SmartPublisherOpen(myContext, inCludeAudio, /*video_opt*/1,
                        VideoConfig.instance.GetVideoWidth(), VideoConfig.instance.GetVideoHeight());

                if (publisherHandleBack != 0) {
                    SetConfig(publisherHandleBack);
                    Log.e("后置摄像头", "ID" + publisherHandleBack);
                }
            }
        }else
            {
                publisherHandleCurrent = libPublisher.SmartPublisherOpen(myContext, inCludeAudio, /*video_opt*/1,
                        VideoConfig.instance.GetVideoWidth(), VideoConfig.instance.GetVideoHeight());

                if (publisherHandleCurrent != 0) {
                    SetConfig(publisherHandleCurrent);
                    Log.e("摄像头", "ID" + publisherHandleCurrent);
                }
            }

    }

    class NTAudioRecordV2CallbackImpl implements NTAudioRecordV2Callback
    {
        @Override
        public void onNTAudioRecordV2Frame(ByteBuffer data, int size, int sampleRate, int channel, int per_channel_sample_number)
        {

    		/* Log.e(TAG, "onNTAudioRecordV2Frame size=" + size + " sampleRate=" + sampleRate + " channel=" + channel
    				 + " per_channel_sample_number=" + per_channel_sample_number);*/

            if ( publisherHandleFront != 0 )
            {
                libPublisher.SmartPublisherOnPCMData(publisherHandleFront, data, size, sampleRate, channel, per_channel_sample_number);
            }

            if ( publisherHandleBack != 0 )
            {
                libPublisher.SmartPublisherOnPCMData(publisherHandleBack, data, size, sampleRate, channel, per_channel_sample_number);
            }

            if( publisherHandleCurrent != 0)
                libPublisher.SmartPublisherOnPCMData(publisherHandleCurrent, data, size, sampleRate, channel, per_channel_sample_number);

        }
    }

    void CheckInitAudioRecorder()
    {
        if ( audioRecord_ == null )
        {
            //audioRecord_ = new NTAudioRecord(this, 1);

            audioRecord_ = new NTAudioRecordV2(this);
        }

        if( audioRecord_ != null )
        {
            Log.i(TAG, "CheckInitAudioRecorder call audioRecord_.start()+++...");

            audioRecordCallback_ = new NTAudioRecordV2CallbackImpl();

            audioRecord_.AddCallback(audioRecordCallback_);

            audioRecord_.Start();

            Log.i(TAG, "CheckInitAudioRecorder call audioRecord_.start()---...");


            //Log.i(TAG, "onCreate, call executeAudioRecordMethod..");
            // auido_ret: 0 ok, other failed
            //int auido_ret= audioRecord_.executeAudioRecordMethod();
            //Log.i(TAG, "onCreate, call executeAudioRecordMethod.. auido_ret=" + auido_ret);
        }
    }

    void UIClickStartPush() {
        if (getLocalIpAddress().equals(""))
            return;

        if (libPublisher == null)
            return;

        outputInfo("开推.", false);

        Log.i(TAG, "onClick start push..");

        VideoConfig.instance.SaveConfig(this);

        if ( !isRecording )
        {
            InitAndSetConfig();
        }

        //应用摄像头参数
        ApplyCam3Params();

        if( VideoConfig.instance.swtichToOne == false)//只有启用双路推流时走这里单路推流需要另外处理
        {
            isPushing = true;

            VideoConfig.instance.videoPushState_1 = false;
            VideoConfig.instance.videoPushState_2 = false;

            Camera front_cam = GetCameraObj(FRONT);
            if (front_cam != null) {

                if (libPublisher.SmartPublisherSetURL(publisherHandleFront, VideoConfig.instance.url1) != 0) {
                    Log.e(TAG, "Failed to set publish stream URL..");
                    outputInfo("前置推流地址应用失败.", false);
                }

                int startRet = libPublisher.SmartPublisherStartPublisher(publisherHandleFront);
                if (startRet != 0) {
                    isPushing = false;
                    Log.e(TAG, "Failed to start push stream..");
                    TextView tvFr = findViewById(R.id.cam1_url_tip);
                    if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                }
            }

            if ( !isRecording )
            {
                Log.e(TAG, "CheckInitAudioRecorder");
                CheckInitAudioRecorder();	//enable pure video publisher..
            }

            Camera back_cam = GetCameraObj(BACK);
            if (back_cam != null) {

                if (libPublisher.SmartPublisherSetURL(publisherHandleBack, VideoConfig.instance.url2) != 0) {
                    Log.e(TAG, "Failed to set publish stream URL..");
                }
                int startRet = libPublisher.SmartPublisherStartPublisher(publisherHandleBack);
                if (startRet != 0) {
                    isPushing = false;
                    Log.e(TAG, "Failed to start push stream back..");
                    TextView tvFr = findViewById(R.id.cam2_url_tip);
                    if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                }
            }

            if (!isRecording && isPushing == true) {
                ConfigControlEnable(false);
                btnStartPush.setText(" 停止推送 ");
            } else if (isPushing == false) {
                ConfigControlEnable(true);
                btnStartPush.setText(" 推送");
                outputInfo("推送失败。检查推流URL,或摄像头是否已插好", false);
            }
        }else //单路推流
            {
                isPushing = true;

                VideoConfig.instance.videoPushState_1 = false;
                VideoConfig.instance.videoPushState_2 = false;

                if ( !isRecording )
                {
                    Log.e(TAG, "CheckInitAudioRecorder");
                    CheckInitAudioRecorder();	//enable pure video publisher..
                }

                Camera front_cam = GetCameraObj(FRONT);
                Camera back_cam = GetCameraObj(BACK);
                if( front_cam == null && back_cam == null)
                {
                    isPushing = false;
                    ConfigControlEnable(true);
                    btnStartPush.setText("推送");
                    outputInfo("推送失败。检查推流URL,或摄像头是否已插好", false);
                    Toast.makeText(getApplicationContext(), "都没摄像头。不推", Toast.LENGTH_SHORT).show();

                    return;
                }

                if( front_cam == null && VideoConfig.instance.curPushWay == 1) VideoConfig.instance.curPushWay = 2;

                if(back_cam == null && VideoConfig.instance.curPushWay == 2) VideoConfig.instance.curPushWay = 1;

                    if (libPublisher.SmartPublisherSetURL(publisherHandleCurrent, VideoConfig.instance.url1) != 0) {
                        isPushing = false;
                        ConfigControlEnable(true);

                        Log.e(TAG, "Failed to set publish stream URL..");
                        outputInfo("推送失败。检查推流URL,或摄像头是否已插好", false);

                        btnStartPush.setText(" 推送");
                        TextView tvFr = findViewById(R.id.cam1_url_tip);
                        if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                        return;
                    }

                    int startRet = libPublisher.SmartPublisherStartPublisher(publisherHandleCurrent);
                    if (startRet != 0) {
                        isPushing = false;
                        ConfigControlEnable(true);

                        Log.e(TAG, "Failed to start push stream..");
                        outputInfo("推送失败。检查推流URL,或摄像头是否已插好", false);

                        btnStartPush.setText(" 推送");
                        TextView tvFr = findViewById(R.id.cam1_url_tip);
                        if (tvFr != null) tvFr.setTextColor(Color.rgb(255, 0, 0));
                    }

                if ( isPushing == true ) {

                    TextView tv = findViewById(R.id.curWay);
                    tv.setText("当前:" + VideoConfig.instance.curPushWay);

                    ConfigControlEnable(false);
                    btnStartPush.setText(" 停止推送 ");
                }
            }

    }

    void UIClickStopPush() {
        outputInfo("停推.", false);
        stopPush();

        if (!isRecording) {
            ConfigControlEnable(true);
        }

        btnStartPush.setText(" 推送");
        isPushing = false;

        return;
    }

    class ButtonStartPushListener implements OnClickListener {
        public void onClick(View v) {
            if (isPushing) {
                UIClickStopPush();
                return;
            } else {

                boolean previewOK = SaveConfigFromUI();

                SaveConfigHostInfoToCom();

                //检查是否需要重连服务器
                if (sendThread != null)
                    sendThread.ApplyNewServer(VideoConfig.instance.destHost, VideoConfig.instance.GetAppPort());

                //检查是否需要连接配置服务器
                if (confiThread != null) {
                    if (VideoConfig.instance.enableConfigServer == true)
                        confiThread.ApplyNewServer(VideoConfig.instance.configHost, VideoConfig.instance.GetConfigPort());
                    else
                        confiThread.ApplyNewServer("", 0);
                }

                if (previewOK) {
                    UIClickStartPush();
                }
            }
        }
    }

    private void stopPush() {
        if (!isRecording) {
            if (audioRecord_ != null) {
                Log.i(TAG, "stopPush, call audioRecord_.StopRecording..");

                audioRecord_.Stop();

                if (audioRecordCallback_ != null) {
                    audioRecord_.RemoveCallback(audioRecordCallback_);
                    audioRecordCallback_ = null;
                }

                audioRecord_ = null;
            }
        }

        if(VideoConfig.instance.swtichToOne == false)
        {
            if (libPublisher != null && publisherHandleFront != 0) {
                libPublisher.SmartPublisherStopPublisher(publisherHandleFront);
            }

            if (!isRecording) {
                if (publisherHandleFront != 0) {
                    if (libPublisher != null) {
                        libPublisher.SmartPublisherClose(publisherHandleFront);
                        publisherHandleFront = 0;
                    }
                }
            }

            if (libPublisher != null && publisherHandleBack != 0) {
                libPublisher.SmartPublisherStopPublisher(publisherHandleBack);
            }

            if (!isRecording) {
                if (publisherHandleBack != 0) {
                    if (libPublisher != null) {
                        libPublisher.SmartPublisherClose(publisherHandleBack);
                        publisherHandleBack = 0;
                    }
                }
            }
        }
        else {
                if (libPublisher != null && publisherHandleCurrent != 0) {
                    libPublisher.SmartPublisherStopPublisher(publisherHandleCurrent);
                }

                if (!isRecording) {
                    if (publisherHandleCurrent != 0) {
                        if (libPublisher != null) {
                            libPublisher.SmartPublisherClose(publisherHandleCurrent);
                            publisherHandleCurrent = 0;
                        }
                    }
                }
            }
    }

    void BeginRecord()
    {
        if (isRecording) {
            stopRecorder();
            isRecording = false;
        }

        Log.i(TAG, "onClick start recorder..");

        if (libPublisher == null)
            return;

        if( sdCardPath.equals("") == true)
            return;

        isRecording = true;

        if (!isPushing) {
            InitAndSetConfig();
        }

        if( VideoConfig.instance.swtichToOne == false)
        {
            if( mCameraFront != null && publisherHandleFront != 0)
            {
                ConfigRecorderFuntion(sdCardPath + fronDirName, publisherHandleFront, true);
            }

            if( mCameraBack != null && publisherHandleBack != 0)
            {
                ConfigRecorderFuntion(sdCardPath + backDirName, publisherHandleBack, true);
            }

            int recordCount = 0;
            int startRet = 0;
            if( mCameraFront != null )
            {
                recordCount = 1;
                libPublisher.SmartPublisherStartRecorder(publisherHandleFront);
                if (startRet != 0) {
                    isRecording = false;

                    Log.e(TAG, "Failed to start front cam recorder.");
                    return;
                }
            }

            //因为业务需求 只录一路
            if( mCameraBack != null)
            {
                if( recordCount == 0)
                {
                    startRet = libPublisher.SmartPublisherStartRecorder(publisherHandleBack);
                    if (startRet != 0) {
                        isRecording = false;

                        Log.e(TAG, "Failed to start back cam recorder .");
                        return;
                    }
                }
            }
        }else
            {
                if(  publisherHandleCurrent != 0)
                {
                    ConfigRecorderFuntion(sdCardPath + fronDirName, publisherHandleCurrent, true);
                   int  startRet = libPublisher.SmartPublisherStartRecorder(publisherHandleCurrent);
                    if (startRet != 0) {
                        isRecording = false;

                        Log.e(TAG, "Failed to start front cam recorder.");
                        return;
                    }
                }
            }

        if ( !isPushing )
        {
            CheckInitAudioRecorder();	//enable pure video publisher..
        }


    }

    private void stopRecorder() {

        isRecording = false;

        if (!isPushing) {
            if (audioRecord_ != null) {
                Log.i(TAG, "stopRecorder, call audioRecord_.StopRecording..");

                audioRecord_.Stop();

                if (audioRecordCallback_ != null) {
                    audioRecord_.RemoveCallback(audioRecordCallback_);
                    audioRecordCallback_ = null;
                }

                audioRecord_ = null;
            }
        }

        if( VideoConfig.instance.swtichToOne == false)
        {
            if (libPublisher != null && publisherHandleFront != 0) {
                libPublisher.SmartPublisherStopRecorder(publisherHandleFront);
            }

            if (!isPushing) {
                if (publisherHandleFront != 0) {
                    if (libPublisher != null) {
                        libPublisher.SmartPublisherClose(publisherHandleFront);
                        publisherHandleFront = 0;
                    }
                }
            }

            if (libPublisher != null && publisherHandleBack != 0) {
                libPublisher.SmartPublisherStopRecorder(publisherHandleBack);
            }

            if (!isPushing) {
                if (publisherHandleBack != 0) {
                    if (libPublisher != null) {
                        libPublisher.SmartPublisherClose(publisherHandleBack);
                        publisherHandleBack = 0;
                    }
                }
            }
        }else
            {
                if (libPublisher != null && publisherHandleCurrent != 0) {
                    libPublisher.SmartPublisherStopRecorder(publisherHandleCurrent);
                }

                if (!isPushing) {
                    if (publisherHandleCurrent != 0) {
                        if (libPublisher != null) {
                            libPublisher.SmartPublisherClose(publisherHandleCurrent);
                            publisherHandleCurrent = 0;
                        }
                    }
                }
            }
    }

    private void SetCameraFPS(Camera.Parameters parameters) {
        if (parameters == null)
            return;

        int[] findRange = null;

        int defFPS = 20 * 1000;

        List<int[]> fpsList = parameters.getSupportedPreviewFpsRange();
        if (fpsList != null && fpsList.size() > 0) {
            for (int i = 0; i < fpsList.size(); ++i) {
                int[] range = fpsList.get(i);
                if (range != null
                        && Camera.Parameters.PREVIEW_FPS_MIN_INDEX < range.length
                        && Camera.Parameters.PREVIEW_FPS_MAX_INDEX < range.length) {
                    Log.i(TAG, "Camera index:" + i + " support min fps:" + range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]);

                    Log.i(TAG, "Camera index:" + i + " support max fps:" + range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);

                    if (findRange == null) {
                        if (defFPS <= range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]) {
                            findRange = range;

                            Log.i(TAG, "Camera found appropriate fps, min fps:" + range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]
                                    + " ,max fps:" + range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
                        }
                    }
                }
            }
        }

        if (findRange != null) {
            parameters.setPreviewFpsRange(findRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX], findRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
        }
    }

    /*it will call when surfaceChanged*/
    private boolean initCamera(int camera_type, SurfaceHolder holder) {
        Log.i(TAG, "initCa11mera..");

        if (isTimeReady == false)
            return false;

        Camera camera = GetCameraObj(camera_type);
        if (camera == null) {
            Log.e(TAG, "initCa111mera camera is null, type=" + camera_type);
            //return false;
        }

        int cameraIndex = GetCameraIndex(camera_type);
        if (-1 == cameraIndex) {
            Log.e(TAG, "initCam11era cameraIndex is -1, type=" + camera_type);
            //return false;
        }

        if (FRONT == camera_type && camera != null) {
            if (mPreviewRunningFront) {
                camera.stopPreview();
            }
        } else if (BACK == camera_type && camera != null) {
            if (mPreviewRunningBack) {
                camera.stopPreview();
            }
        }

        Camera.Parameters parameters;
        try {
            parameters = camera.getParameters();
            //List<Size> ss = parameters.getSupportedPictureSizes();
            //VideoConfig.instance.videoSizes = ss;

            //for(int i = 0; i< ss.size(); i++)
            //{
            //	Log.e("sadfasdf","camTYpe:" + camera_type + "w:" + ss.get(i).width + " h:" + ss.get(i).height);
            //}
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }

        parameters.setPreviewSize(VideoConfig.instance.GetVideoWidth(), VideoConfig.instance.GetVideoHeight());
        parameters.setPictureFormat(PixelFormat.JPEG);
        parameters.setPreviewFormat(PixelFormat.YCbCr_420_SP);

        SetCameraFPS(parameters);

        if (camera != null) camera.setDisplayOrientation(90);

        Log.e("Cmeraaaa", "apply w:" + VideoConfig.instance.GetVideoWidth() + "h " + VideoConfig.instance.GetVideoHeight());
        try {
            if (camera != null) camera.setParameters(parameters);
        } catch (Exception ex) {
            Log.e("*******", "Apply Camera Config failed.");
            return false;
        }

        int bufferSize = (((VideoConfig.instance.GetVideoWidth() | 0xf) + 1) * VideoConfig.instance.GetVideoHeight() * ImageFormat.getBitsPerPixel(parameters.getPreviewFormat())) / 8;

        if (camera != null) camera.addCallbackBuffer(new byte[bufferSize]);

        if (camera != null)
            camera.setPreviewCallbackWithBuffer(new NT_SP_CameraPreviewCallback(camera_type));

        try {
            if (camera != null) camera.setPreviewDisplay(holder);
        } catch (Exception ex) {
            // TODO Auto-generated catch block
            if (null != camera) {
                camera.release();
                camera = null;
                SetCameraObj(camera_type, null);
            }
            ex.printStackTrace();

            return false;
        }

        if (camera != null) {
            try {
                camera.startPreview();
            } catch (Exception ea) {
                ea.printStackTrace();
            }
        }

        if (FRONT == camera_type && camera != null) {
            camera.autoFocus(myAutoFocusCallbackFront);
            mPreviewRunningFront = true;
        } else if (BACK == camera_type && camera != null) {
            camera.autoFocus(myAutoFocusCallbackBack);
            mPreviewRunningBack = true;
        }

        return true;
    }

    int GetCameraIndex(int type) {
        if (FRONT == type) {
            return curFrontCameraIndex;
        } else if (BACK == type) {
            return curBackCameraIndex;
        } else {
            Log.i(TAG, "GetCameraIndex type error, type=" + type);
            return -1;
        }
    }

    Camera GetCameraObj(int type) {
        if (FRONT == type) {
            return mCameraFront;
        } else if (BACK == type) {
            return mCameraBack;
        } else {
            Log.i(TAG, "GetCameraObj type error, type=" + type);
            return null;
        }
    }

    void SetCameraObj(int type, Camera c) {
        if (FRONT == type) {
            mCameraFront = c;
        } else if (BACK == type) {
            mCameraBack = c;
        } else {
            Log.i(TAG, "SetCameraObj type error, type=" + type);
        }
    }

    class NT_SP_SurfaceHolderCallback implements Callback {
        private int type_ = 0;

        public NT_SP_SurfaceHolderCallback(int type) {
            type_ = type;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {

            Log.i(TAG, "surfaceCreated..type_=" + type_);

            if (type_ != FRONT && type_ != BACK) {
                Log.e(TAG, "surfaceCreated type error, type=" + type_);
                return;
            }

            try {

                if (type_ == FRONT) {
                    int cammeraIndex = findFrontCamera();
                    if (cammeraIndex == -1) {
                        Log.e(TAG, "surfaceCreated, There is no front camera!!");
                        return;
                    }
                } else if (type_ == BACK) {
                    int cammeraIndex = findBackCamera();
                    if (-1 == cammeraIndex) {
                        Log.e(TAG, "surfaceCreated, there is no back camera");

                        return;
                    }
                }

                if (GetCameraObj(type_) == null) {
                    Camera c = openCamera(type_);
                    SetCameraObj(type_, c);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.e(TAG, "surfaceChanged..");

            if (type_ != FRONT && type_ != BACK)
                return;

            //initCamera(type_, holder);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // TODO Auto-generated method stub
            Log.i(TAG, "Surface Destroyed");
        }
    }

    class NT_SP_CameraPreviewCallback implements PreviewCallback {
        private int type_ = 0;

        private int frameCount_ = 0;

        public NT_SP_CameraPreviewCallback(int type) {
            type_ = type;
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {

            frameCount_++;
            if (frameCount_ % 5000 == 0) {
                //Log.i("OnPre", "gc+");
                System.gc();
                //Log.i("OnPre", "gc-");
            }

            if (type_ == FRONT) {
                isFrontCameraPreviewOK = true;
                //Log.e(TAG,"前前前前前前前前摄像头onPreviewFrame");
            } else if (type_ == BACK) {
                isBackCameraPreviewOK = true;
                //Log.e(TAG,"后后后后后后后后后后后后摄像头onPreviewFrame");
            }

            if (data == null) {
                Parameters params = camera.getParameters();
                Size size = params.getPreviewSize();
                int bufferSize = (((size.width | 0x1f) + 1) * size.height * ImageFormat.getBitsPerPixel(params.getPreviewFormat())) / 8;
                camera.addCallbackBuffer(new byte[bufferSize]);

                if (type_ == FRONT) {
                    //	Log.e(TAG,"前前前前前前前前摄像头data= null");
                } else if (type_ == BACK) {
                    ///		Log.e(TAG,"后后后后后后后后后后后后摄像头data= null");
                }

            } else {

                if( VideoConfig.instance.swtichToOne == false)
                {
                    //if(  isPushing || isRecording )// isPushing || isRecording//todo 其实应该单独判断每路的推流状态。
                    {
                        if (FRONT == type_ && publisherHandleFront != 0) {
                            if (libPublisher != null)
                                libPublisher.SmartPublisherOnCaptureVideoData(publisherHandleFront, data, data.length, BACK, VideoConfig.instance.currentOrigentation);
                        }

                        if (BACK == type_ && publisherHandleBack != 0) {
                            if (libPublisher != null)
                                libPublisher.SmartPublisherOnCaptureVideoData(publisherHandleBack, data, data.length, BACK, VideoConfig.instance.currentOrigentation);
                        }
                    }
                }
                else
                    {
                        if( VideoConfig.instance.curPushWay ==1 )
                        {
                            if (FRONT == type_ && publisherHandleCurrent != 0) {
                                if (libPublisher != null)
                                    libPublisher.SmartPublisherOnCaptureVideoData(publisherHandleCurrent, data, data.length, BACK, VideoConfig.instance.currentOrigentation);
                            }
                        }
                        else {
                            if (BACK == type_ && publisherHandleCurrent != 0) {
                                if (libPublisher != null)
                                    libPublisher.SmartPublisherOnCaptureVideoData(publisherHandleCurrent, data, data.length, BACK, VideoConfig.instance.currentOrigentation);
                            }
                        }
                    }


                camera.addCallbackBuffer(data);
            }
        }
    }

    @SuppressLint("NewApi")
    private Camera openCamera(int type) {

        int frontIndex = -1;
        int backIndex = -1;
        int cameraCount = Camera.getNumberOfCameras();
        Log.i(TAG, "cameraCount: " + cameraCount);

        CameraInfo info = new CameraInfo();
        for (int cameraIndex = 0; cameraIndex < cameraCount; cameraIndex++) {
            Camera.getCameraInfo(cameraIndex, info);

            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                frontIndex = cameraIndex;
            } else if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
                backIndex = cameraIndex;
            }
        }

        if (type == FRONT && frontIndex != -1) {
            curFrontCameraIndex = frontIndex;
            return Camera.open(frontIndex);
        } else if (type == BACK && backIndex != -1) {
            curBackCameraIndex = backIndex;
            return Camera.open(backIndex);
        }

        return null;
    }


    //Check if it has front camera
    private int findFrontCamera() {
        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();

        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                return camIdx;
            }
        }
        return -1;
    }

    //Check if it has back camera
    private int findBackCamera() {
        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();

        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return camIdx;
            }
        }
        return -1;
    }

    private int setHardwareEncoderKbps(int width, int height) {
        int hwEncoderKpbs = 560;

        switch (width) {
            case 176:
                hwEncoderKpbs = 220;
                break;
            case 320:
                hwEncoderKpbs = 380;
                break;
            case 640:
                hwEncoderKpbs = 560;
                break;
            case 1280:
                hwEncoderKpbs = 1200;
                break;
            default:
                hwEncoderKpbs = 1000;
        }

        return hwEncoderKpbs;
    }

    /**
     * 根据目录创建文件夹
     *
     * @param context
     * @param cacheDir
     * @return
     */
    public static File getOwnCacheDirectory(Context context, String cacheDir) {
        File appCacheDir = null;
        //判断sd卡正常挂载并且拥有权限的时候创建文件
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) && hasExternalStoragePermission(context)) {
            appCacheDir = new File(Environment.getExternalStorageDirectory(), cacheDir);
            Log.i(TAG, "appCacheDir: " + appCacheDir);
        }
        if (appCacheDir == null || !appCacheDir.exists() && !appCacheDir.mkdirs()) {
            appCacheDir = context.getCacheDir();
        }
        return appCacheDir;
    }

    /**
     * 检查是否有权限
     *
     * @param context
     * @return
     */
    private static boolean hasExternalStoragePermission(Context context) {
        int perm = context.checkCallingOrSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE");
        return perm == 0;
    }


}