package com.mzd.mtakem2;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.webkit.WebView;
import android.widget.RemoteViews;

import com.mzd.mtakem2.utils.ComFunc;
import com.mzd.mtakem2.utils.HbDataCheckThread;
import com.mzd.mtakem2.utils.HttpUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.Exchanger;

/**
 * Created by Administrator on 2017/5/8.
 */

public class MtakemService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "MtakemService";
    private static final String WECHAT_LUCKMONEY_GENERAL_ACTIVITY = "LauncherUI";
    private static final String WECHAT_DETAILS_EN = "Details";
    private static final String WECHAT_DETAILS_CH = "红包详情";
    private static final String WECHAT_BETTER_LUCK_EN = "Better luck next time!";
    private static final String WECHAT_BETTER_LUCK_CH = "手慢了";
    private static final String WECHAT_EXPIRES_CH = "已超过24小时";
    private static final String WECHAT_WHOGIVEYOUAHB = "给你发了一个";

    private boolean bUnpackedSuccessful = false;//成功打开HB
    private boolean bAutoMode = false;//后台全自动抢HB模式，抢完红包自动回桌面
    private boolean bEnableNotifyWatch = false;//忽略通知处理，通知的红包信息忽略，专注单窗钱红包
    private boolean bEnableChatListWatch = false;//忽略列表消息，提高单床抢HB
    private boolean bAutoReply = false; //收到红包后自动回复
    private boolean bCloudChatRec = false;
    private boolean bAutoReceptGroup = false;
    private String autoReceptParam = "240 350";
    private boolean bAutoQuitGroup = false;
    private boolean bAutoInvite = false; //自动邀请好友加群
    private String autoInviteParam = "";  //自动邀请的好友名单，名单用"|"隔开
    private boolean bAutoHostCmd = false;
    private String remoteHostName = "";

    private int nClearMsgNum = 0;
    private int nNowMsgCounter = 0;

    //宿主指令
    private String hostCmd = ""; //宿主指令

    private String blackGroup = "";
    private java.util.Date blackGroupStTime;
    private Object blackSyncKey;

    private long autoReplyDelay = 1000;
    private boolean bCanUse = true;

    Handler mHander = new Handler();
    Handler haltCheckHandler = new Handler();

    private boolean bAutoClickChatList = false;
    private boolean bAutoClickHbItem = false;
    private boolean bAutoClickOpenDetail = false;//这个标志为用于实现收到打开HB详情是，详情不自动返回的功能
    private boolean bAutoClickOpenButton = false;


    private SharedPreferences sharedPreferences;

    private long detect_tm = 0;
    private long notify_detect_tm = 0;
    private long chatlist_detect_tm = 0;

    private String mac = "";
    private String app_ver = "";
    private String device_model = Build.MODEL; // 设备型号 。
    private String version_release = Build.VERSION.RELEASE; // 设备的系统版本 。


    //聊天窗中的讲话按钮，用来区分当前页面是聊天窗口，还是聊天列表窗口
    // 6.5.7：a3_  ,6.5.8:a47,6.5.10:a5c,6.5.13:a6e
    private static final String SOUNDBUTTON_STRING_ID = "com.tencent.mm:id/a6e";

    //聊天窗口的标题信息，标识了所在的群或者聊天对象
    //6.5.7:gh , 6.5.8:gp,6.5.10:gs,6.5.13:gz
    private static final String WINDOWTITLETEXT_STRING_ID = "com.tencent.mm:id/gz";

    //聊天的文本控件ID
    // 6.5.7:if  , 6.5.8:im,6.5.10:ij,6.5.13:iq(NAF=TRUE,not accessibility friendly)
    private static final String WINDOWCHATTEXT_STRING_ID = "com.tencent.mm:id/iq";

    //聊天信息中的时间标签ID
    //6.5.7:t , 6.5.8:t (没变化）,6.5.10:u,6.5.13:y
    private static final String WINDOWCHATTTIME_STRING_ID = "com.tencent.mm:id/u";

    //聊天列表中的最后文本信息
    //6.5.7:afx , 6.5.8:agy,6.5.10:aii,6.5.13:aje
    private static final String CHATLISTTEXT_STRING_ID = "com.tencent.mm:id/aje";

    //聊天列表中的群标题信息
    //6.5.10 aig,6.5.13:ajc
    private static final String CHATLISTTITLE_STRING_ID = "com.tencent.mm:id/ajc";

    //聊天窗中的HB信息
    //6.5.7:   , 6.5.8:a6_ ,6.5.10:a8h,6.5.13:a8q
    private static final String HB_STRING_ID = "com.tencent.mm:id/a8q";

    //HB打开按钮
    //6.5.7:bjj , 6.5.8:bm4,6.5.10:bnr,6.5.13:bp6
    private static final String HBOPENBUTTON_STRING_ID = "com.tencent.mm:id/bp6";

    //HB派完了文本
    //6.5.7: , 6.5.8:bm3,6.5.10:bnq
    private static final String HBNONETEXT_STRING_ID = "com.tencent.mm:id/bnq";

    //HB金额文本按钮
    //6.5.7:bfw , 6.5.8:bii,6.5.10:bk6,6.5.13:bli
    private static final String HBAMOUNTTEXT_STRING_ID = "com.tencent.mm:id/bli";

    //HB发送人文本
    //6.5.7: , 6.5.8:bie,6.5.10:bk2,6.5.13:ble
    private static final String HBSENDER_STRING_ID = "com.tencent.mm:id/ble";

    //HB内容文本
    //6.5.7: , 6.5.8:big,6.5.10:bk4,6.5.13:blg
    private static final String HBCONTENT_STRING_ID = "com.tencent.mm:id/blg";

    //聊天窗返回列表窗返回箭头
    //6.5.8:gn,6.5.10:gq,6.5.13:gx
    private static final String HBRETURN_STRING_ID = "com.tencent.mm:id/gx";

    //wx聊天列表下面的按钮ID
    //6.5.8:buh,6.5.10:bw6,6.5.13:bwm
    private static final String HBBOTTOMBTN_STRING_ID = "com.tencent.mm:id/bwm";

    //wx名称的textid
    //6.5.8:by2,6.5.10:bzr,6.5.13:c07
    private static final String HBWXUSER_STRING_ID = "com.tencent.mm:id/c07";

    //退群操作死相关

    //6.5.8:g5,6.5.10:g8,6.5.13:ge
    private static final String HBOPENGROUPDETAIL_STRING_ID = "com.tencent.mm:id/ge";

    //群信息的列表ID(listview,scrollable:true)
    //6.5.8:list,6.5.10:list,6.5.13:list
    private static final String HBGROUPLIST_STRING_ID = "android:id/list";

    //删除并退出按钮
    //6.5.8:title,6.5.10:title,6.5.13:title
    private static final String HBDELANDQUIT_STRING_ID = "android:id/title";

    //删除并退出确认按钮
    //6.5.8:ad8,6.5.10:aes,6.5.13:afm
    private static final String HBDELANDQUITCONFIRM_STRING_ID = "com.tencent.mm:id/afm";

    //加群相关

    //接受加好接受加好友验证相关
    //邀请加入群聊的标题
    //6.5.8:text1,6.5.10:text1,6.5.13:text1
    private static final String HBYQTITLE_STRING_ID = "android:id/text1";

    //接受
    //6.5.8:axg,6.5.10:aym,6.5.13:azm
    private static final String HBACCEPTBIGBTN_STRING_ID = "com.tencent.mm:id/azm";

    //通过验证按钮
    //6.5.8:aeb,6.5.10:afx,6.5.13:agt
    private static final String HBPASSCHECK_STRING_ID = "com.tencent.mm:id/agt";

    //好友用户信息
    //6.5.8:summary,6.5.10:summary.6.5.13:summary
    private static final String HBFRIENDINFO_STRING_ID = "android:id/summary";

    //验证完成按钮
    //6.5.8:gl,6.5.10:go,6.5.13:gv
    private static final String HBFINISHCHECK_STRING_ID = "com.tencent.mm:id/gv";

    //验证完后返回按钮
    //6.5.8:h3,6.5.10:h6,6.5.13:hc
    private static final String HBRETURNAFTERCHECK_STRING_ID = "com.tencent.mm:id/hc";

    //自动邀请好友加群相关
    //点击邀请好友的加号
    //6.5.10:cgr,6.5.13:ch8
    private static final String HBYQFRIENDSINGROUPBTN = "com.tencent.mm:id/ch8";
    //群信息的listview
    //6.5.10:f3,6.5.13:f_
    private static final String HBYQFRIENDSCROLLVIEWID = "com.tencent.mm:id/f_";
    //好友清单中的好友名称
    //6.5.10:ja,6.5.13:jr
    private static final String HBYQFRIENDNAMEID = "com.tencent.mm:id/jr";
    //好友清单顺序字母
    //6.5.10:abw,6.5.13:aco
    private static final String HBYQFRIENDLISTWORD = "com.tencent.mm:id/aco";
    //好友清单顺序后的复选框ID
    //6.5.10:oo,6.5.13:pc
    private static final String HBYQFRIENDCHECKBOXID = "com.tencent.mm:id/pc";
    //确认邀请名单
    //6.5.10:go,6.5.13:gv
    private static final String HBYQFRIENDCONFIRMNAMESID = "com.tencent.mm:id/gv";
    //确认邀请
    //6.5.10:aes,6.5.13:afm
    private static final String HBYQFRIENDCONFIRMYQID = "com.tencent.mm:id/afm";

    //聊天窗口中的图片资源ID
    //6.5.10:a8a,6.5.13:a9j
    private static final String HBQRCODEYJPICID = "com.tencent.mm:id/a9j";

    //点击打开后的图片大图的资源ID
    //6.5.10:yf,6.5.13:aq
    private static final String HBQRCODEYJBIGPICID = "com.tencent.mm:id/aq";

    //二维码识别后，加入页面的返回按钮
    //6.5.10:h7,6.5.13:hd
    private static final String HBQRCODERETURN = "com.tencent.mm:id/hd";


    private String windowtitle = "";
    private String sender = "";
    private String hbcontent = "";
    private String hb_amount = "";
    private String wx_user = "";
    private String last_context_string = "";
    private HbDataCheckThread hbDataCheckThread;
    private Object lockkey;


    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            try {
                AccessibilityNodeInfo nd = getRootInActiveWindow();
                if (nd != null && nd.getPackageName().equals("com.tencent.mm")) {
                    back2Home();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    Runnable autoReplyFun = new Runnable() {
        @Override
        public void run() {
            try {
                AccessibilityNodeInfo hd = getRootInActiveWindow();
                String strReply = generateCommentString();
                if (findEditText(hd, strReply)) {
                    Log.i(TAG, "AutoReply:" + strReply);
                    send();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            back2Home();
        }
    };

    //检查微信是否无响应(10s检查一次)
    Runnable haltCheckFun = new Runnable() {
        @Override
        public void run() {
            try {
                AccessibilityNodeInfo hd = getRootInActiveWindow();
                if (hd != null) {
                    List<AccessibilityNodeInfo> haltWaitNds = hd.findAccessibilityNodeInfosByViewId("android:id/button2");
                    if (haltWaitNds != null && !haltWaitNds.isEmpty()) {
                        //Log.i(TAG, "发现无响应按钮");
                        for (AccessibilityNodeInfo haltWaitNd : haltWaitNds) {
                            Log.i(TAG, haltWaitNd.getText().toString());
                            if (haltWaitNd.getText().toString().contains("等待")) {
                                //Log.i(TAG, "发现无响应..");
                                haltWaitNd.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            }
                        }

                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            //Log.i(TAG,"微信无响应处理..");
            haltCheckHandler.postDelayed(this, 10000);
        }
    };


    @Override
    public void onDestroy() {
        super.onDestroy();
        hbDataCheckThread.stopThread();
        Log.i(TAG, "onDestroy");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
    }


    /*选择启用服务触发事件*/
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "onServiceConnected");
        mac = ComFunc.getDeviceId(getApplicationContext());
        app_ver = ComFunc.getVersion(this);
        Log.i(TAG, "APP Ver:" + app_ver);
        Log.i(TAG, "Mac :" + mac);
        Log.i(TAG, "Dev:" + device_model);
        Log.i(TAG, "VERSION:" + version_release);
        Log.i(TAG, "ThreadId:" + String.valueOf(Thread.currentThread().getId()));

        lockkey = new Object();
        hbDataCheckThread = new HbDataCheckThread(getApplicationContext(), lockkey);
        hbDataCheckThread.start();

        blackGroupStTime = new java.util.Date(new java.util.Date().getTime() + 30 * 1000);
        blackSyncKey = new Object();

        //启动微信无响应检查函数
        haltCheckHandler.postDelayed(haltCheckFun, 1000);

        //动态增加FLAG配置，注意这非常重要，这个将使得能获取窗体的全部完整的节点。
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags = info.flags | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY;
        setServiceInfo(info);

        //注册监听配置更新并初始化配置
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        bAutoMode = sharedPreferences.getBoolean("autoMode", false);
        bEnableNotifyWatch = sharedPreferences.getBoolean("check_box_ignorenotify", false);
        bEnableChatListWatch = sharedPreferences.getBoolean("check_box_ignorechatlist", false);
        bAutoReply = sharedPreferences.getBoolean("check_box_autoReply", false);
        autoReplyDelay = Integer.parseInt(sharedPreferences.getString("edit_text_autoReplyDelay", "1000"));
        bCanUse = sharedPreferences.getBoolean("canUse", true);
        wx_user = sharedPreferences.getString("wxUser", "");
        bAutoReceptGroup = sharedPreferences.getBoolean("autoRecept", false);
        autoReceptParam = sharedPreferences.getString("autoParam", "240 350");
        bAutoQuitGroup = sharedPreferences.getBoolean("autoQuitGroup", false);
        bCloudChatRec = sharedPreferences.getBoolean("cloudChatRec", false);
        bAutoInvite = sharedPreferences.getBoolean("autoInvite", false);
        autoInviteParam = sharedPreferences.getString("autoInviteParam", "");
        bAutoHostCmd = sharedPreferences.getBoolean("autoHostCmd", false);
        remoteHostName = sharedPreferences.getString("remoteHostName", "");

        try {
            nClearMsgNum = Integer.parseInt(sharedPreferences.getString("nClearMsgNum", "0"));
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("autoMode")) {
            bAutoMode = sharedPreferences.getBoolean(key, false);
            setEventTypeContentAndStatus(true);//只要修改这个参数，消息监控初始化，全部监控
        }
        if (key.equals("check_box_ignorenotify")) {
            bEnableNotifyWatch = sharedPreferences.getBoolean(key, false);
        }
        if (key.equals("check_box_ignorechatlist")) {
            bEnableChatListWatch = sharedPreferences.getBoolean(key, false);
        }
        if (key.equals("check_box_autoReply")) {
            bAutoReply = sharedPreferences.getBoolean(key, false);
        }
        if (key.equals("edit_text_autoReplyDelay")) {
            autoReplyDelay = Integer.parseInt(sharedPreferences.getString(key, "1000"));
        }
        if (key.equals("canUse")) {
            bCanUse = sharedPreferences.getBoolean(key, true);
        }
        if (key.equals("wxUser")) {
            wx_user = sharedPreferences.getString("wxUser", "");
        }
        if (key.equals("autoRecept")) {
            bAutoReceptGroup = sharedPreferences.getBoolean(key, false);
        }
        if (key.equals("autoQuitGroup")) {
            bAutoQuitGroup = sharedPreferences.getBoolean(key, false);
        }
        if (key.equals("autoParam")) {
            autoReceptParam = sharedPreferences.getString("autoParam", "240 350");
        }
        if (key.equals("cloudChatRec")) {
            bCloudChatRec = sharedPreferences.getBoolean("cloudChatRec", false);
        }

        if (key.equals("autoInvite")) {
            bAutoInvite = sharedPreferences.getBoolean("autoInvite", false);
        }
        if (key.equals("autoInviteParam")) {
            autoInviteParam = sharedPreferences.getString("autoInviteParam", "");
        }

        if (key.equals("autoHostCmd")) {
            bAutoHostCmd = sharedPreferences.getBoolean("autoHostCmd", false);
        }
        if (key.equals("remoteHostName")) {
            remoteHostName = sharedPreferences.getString("remoteHostName", "");
        }
        if (key.equals("nClearMsgNum")) {
            nClearMsgNum = Integer.parseInt(sharedPreferences.getString("nClearMsgNum", "0"));
        }

    }


    /*选择禁用服务触发事件*/
    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "onInterrupt");
    }

    private void setEventTypeContentAndStatus(boolean bEnable) {
        AccessibilityServiceInfo info = getServiceInfo();
        if (bEnable) {
            info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        } else {
            info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
        }
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            if (bCanUse) {
                if (bAutoMode) {
                    autoDealHb(event);
                } else {
                    manMode(event);
                }
            } else {
                Log.i(TAG, "辅助功能不能使用");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean hasOneOfThoseNodes(String... texts) {
        List<AccessibilityNodeInfo> nodes;
        for (String text : texts) {
            if (text == null) continue;
            try {
                nodes = getRootInActiveWindow().findAccessibilityNodeInfosByText(text);
                if (nodes != null && !nodes.isEmpty()) return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }


    /*
        清空消息
     */
    private void clearChatContent() throws InterruptedException {
        int i = 0;
        int nStatus = 0;
        for (i = 0; i < 100; i++) {
            AccessibilityNodeInfo hd = getRootInActiveWindow();
            if (hd != null) {
                try {
                    switch (nStatus) {
                        case 0: {//点击到聊天列表页面
                            List<AccessibilityNodeInfo> goes = hd.findAccessibilityNodeInfosByViewId(HBRETURN_STRING_ID);
                            if (goes != null && !goes.isEmpty()) {
                                for (AccessibilityNodeInfo go : goes) {
                                    go.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 1;
                                }
                            }
                        }
                        break;
                        case 1: {//点击“我”
                            List<AccessibilityNodeInfo> bottomBtns = hd.findAccessibilityNodeInfosByViewId(HBBOTTOMBTN_STRING_ID);
                            for (AccessibilityNodeInfo bottomBtn : bottomBtns) {
                                if (bottomBtn.getText() != null && bottomBtn.getText().toString().contains("我")) {
                                    bottomBtn.getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 2;
                                }
                            }
                        }
                        break;
                        case 2: {
                            List<AccessibilityNodeInfo> btns = hd.findAccessibilityNodeInfosByText("设置");
                            if (btns != null && !btns.isEmpty()) {
                                for (AccessibilityNodeInfo btn : btns) {
                                    btn.getParent().getParent().getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 3;
                                }
                            }
                        }
                        break;
                        case 3: {
                            List<AccessibilityNodeInfo> btns = hd.findAccessibilityNodeInfosByText("聊天");
                            if (btns != null && !btns.isEmpty()) {
                                for (AccessibilityNodeInfo btn : btns) {
                                    btn.getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 4;
                                }
                            }
                        }
                        break;
                        case 4: {
                            List<AccessibilityNodeInfo> btns = hd.findAccessibilityNodeInfosByText("清空聊天记录");
                            if (btns != null && !btns.isEmpty()) {
                                for (AccessibilityNodeInfo btn : btns) {
                                    btn.getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 5;
                                }
                            }
                        }
                        break;
                        case 5: {
                            List<AccessibilityNodeInfo> btns = hd.findAccessibilityNodeInfosByText("清空");
                            if (btns != null && !btns.isEmpty()) {
                                for (AccessibilityNodeInfo btn : btns) {
                                    btn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 6;
                                }
                            }
                        }
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Thread.sleep(100);
        }
        Log.i(TAG, "清空消息处理完毕");
        back2Home();

    }

    /*
        同步退群
     */
    private void syncQuitFromGroup(String group_name_md5) throws InterruptedException {
        int i = 0;
        int nStatus = 0;
        for (i = 0; i < 100; i++) {
            AccessibilityNodeInfo hd = getRootInActiveWindow();
            if (hd != null) {
                try {
                    switch (nStatus) {
                        case 0: { //查找点击进入群信息按钮
                            List<AccessibilityNodeInfo> titleNodes = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                            if (titleNodes != null && !titleNodes.isEmpty()) {
                                List<AccessibilityNodeInfo> lChecks = hd.findAccessibilityNodeInfosByViewId(HBOPENGROUPDETAIL_STRING_ID);
                                if (lChecks != null && !lChecks.isEmpty()) {
                                    //Log.i(TAG, "lCheckSize=" + String.valueOf(lChecks.size()));
                                    try {
                                        AccessibilityNodeInfo lCheck = lChecks.get(lChecks.size() - 1);
                                        lCheck.getChild(1).getChild(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        nStatus = 1;
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                        break;
                        case 1: {
                            List<AccessibilityNodeInfo> listHds = hd.findAccessibilityNodeInfosByViewId(HBGROUPLIST_STRING_ID);
                            if (listHds != null && !listHds.isEmpty()) {
                                for (AccessibilityNodeInfo listHd : listHds) {
                                    listHd.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                                }
                            }
                            List<AccessibilityNodeInfo> titleHds = hd.findAccessibilityNodeInfosByViewId(HBDELANDQUIT_STRING_ID);
                            if (titleHds != null && !titleHds.isEmpty()) {
                                for (AccessibilityNodeInfo titleHd : titleHds) {
                                    //Log.i(TAG, "i=" + String.valueOf(i));
                                    //if ("删除并退出".equals(titleHd.getText())) {//这个在4.4.4的android系统中不成立，但在android 6.0中成立很奇怪
                                    if (titleHd.getText() != null && titleHd.getText().toString().contains("删除并退出")) {
                                        Log.i(TAG, "找到退出");
                                        if (titleHd.getParent() != null) {
                                            titleHd.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        }
                                        nStatus = 2;
                                        break;
                                    }
                                }
                            }
                        }
                        break;
                        case 2: {
                            List<AccessibilityNodeInfo> confirmNodes = hd.findAccessibilityNodeInfosByViewId(HBDELANDQUITCONFIRM_STRING_ID);
                            for (AccessibilityNodeInfo confirmNode : confirmNodes) {
                                confirmNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                i = 130;
                                nStatus = 3;
                                back2Home();
                                Log.i(TAG, "退出成功");
                                updateQuitResult(wx_user, group_name_md5);
                            }
                        }
                        break;

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Thread.sleep(100);
        }
        Log.i(TAG, "黑名单处理完毕");
        back2Home();

    }

    /*
        自动接收邀请加群
     */
    private void joingroup() throws InterruptedException {
        int i = 0;
        boolean bClickYqBtn = false;
        boolean bClickJoinBtn = false;
        boolean bAllSucYq = false; //全部流程走完到最后，false但不代表真正邀请失败，也有可能成功，但true就代表一定成功。
        String gName = "";
        for (i = 0; i < 150; i++) {
            try {
                AccessibilityNodeInfo hd = getRootInActiveWindow();
                if (hd != null) {
                    if (!bClickYqBtn) {
                        List<AccessibilityNodeInfo> titleNodes = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                        if (titleNodes != null && !titleNodes.isEmpty()) {
                            List<AccessibilityNodeInfo> yqNodes = hd.findAccessibilityNodeInfosByText("邀请你加入群聊");
                            if (yqNodes != null && !yqNodes.isEmpty()) {
                                for (int j = yqNodes.size() - 1; j >= 0; j--) {
                                    AccessibilityNodeInfo nodeInfo = yqNodes.get(j);
                                    try {
                                        AccessibilityNodeInfo pNode = nodeInfo.getParent().getParent().getParent();
                                        if (pNode.getClassName().toString().contains("FrameLayout")) {
                                            bClickYqBtn = true;
                                            pNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            //Log.i(TAG, pNode.toString());
                                            AccessibilityNodeInfo detailNode = nodeInfo.getParent().getChild(1).getChild(0).getChild(0).getChild(0);
                                            if (detailNode.getText() != null) {
                                                gName = detailNode.getText().toString();
                                                //Log.i(TAG,gName);
                                                int stPos = gName.indexOf("邀请你加入群聊");
                                                int edPos = gName.indexOf("，进入可查看详情");
                                                //Log.i(TAG,"stPos="+String.valueOf(stPos));
                                                //Log.i(TAG,"edPos="+String.valueOf(edPos));
                                                gName = gName.substring(stPos + 7, edPos);
                                                //Log.i(TAG,"str="+gName);

                                            }
                                            break;
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                if (!bClickYqBtn) {
                                    i = 200;
                                    back2Home();
                                    Log.i(TAG, "假邀请，退出");
                                }
                            } else {
                                i = 200;
                                back2Home();
                                Log.i(TAG, "假邀请");
                            }
                        }
                    }
                    if (bClickYqBtn && !bClickJoinBtn) {
                        List<AccessibilityNodeInfo> titleNodes1 = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                        if (titleNodes1 != null && titleNodes1.isEmpty()) {
                            List<AccessibilityNodeInfo> qlTexts = hd.findAccessibilityNodeInfosByViewId(HBYQTITLE_STRING_ID);
                            if (qlTexts != null && !qlTexts.isEmpty()) {
                                AccessibilityNodeInfo qlText = qlTexts.get(0);
                                if (qlText.getText() != null && qlText.getText().toString().contains("群聊邀请")) {
                                    try {
                                        Log.i(TAG, "点击加入");
                                        Thread.sleep(500);
                                        execShellCmd("input tap " + autoReceptParam);
                                        bClickJoinBtn = true;
                                        //自动生成邀请设定好友名单
                                        if (bAutoHostCmd) {
                                            hostCmd = "admin[sp]邀请加入[sp]" + gName.trim() + "[sp]" + autoInviteParam;
                                            Log.i(TAG, "生成自动邀请指令:" + hostCmd);
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        Log.i(TAG, e.getMessage());
                                    }
                                } else {
                                    if (qlText.getText() != null) {
                                        i = 200;
                                        back2Home();
                                        Log.i(TAG, "假群聊邀请页面,退出");
                                    }
                                }
                            }
                        }
                    }

                    if (bClickJoinBtn) {
                        List<AccessibilityNodeInfo> titleNodes1 = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                        if (titleNodes1 != null && !titleNodes1.isEmpty()) {
                            Log.i(TAG, "已经成功加群");
                            i = 200;
                            bAllSucYq = true;
                        }
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            Thread.sleep(100);
        }
        //如果邀请成功,那立刻邀请好友如果不是则等待被动邀请。
        if (bAllSucYq && bAutoHostCmd) {
            yqfriends(autoInviteParam);
            hostCmd = "";
        }
        back2Home();
        Log.i(TAG, "邀请检查完毕");
    }

    /*
            自动邀请好友加群
     */
    private void yqfriends(String friendStr) throws InterruptedException {
        int i = 0;
        int nStatus = 0;
        String friends[] = friendStr.split("\\|");
        for (i = 0; i < 100; i++) {
            AccessibilityNodeInfo hd = getRootInActiveWindow();
            if (hd != null) {
                try {
                    switch (nStatus) {
                        case 0: { //查找点击进入群信息按钮
                            List<AccessibilityNodeInfo> titleNodes = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                            if (titleNodes != null && !titleNodes.isEmpty()) {
                                List<AccessibilityNodeInfo> lChecks = hd.findAccessibilityNodeInfosByViewId(HBOPENGROUPDETAIL_STRING_ID);
                                if (lChecks != null && !lChecks.isEmpty()) {
                                    try {
                                        AccessibilityNodeInfo lCheck = lChecks.get(lChecks.size() - 1);
                                        lCheck.getChild(1).getChild(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        nStatus = 1;
                                    } catch (Exception e) {
                                        nStatus = 0;
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                        break;
                        case 1: {
                            List<AccessibilityNodeInfo> yqNodes = hd.findAccessibilityNodeInfosByViewId(HBYQFRIENDSINGROUPBTN);
                            if (yqNodes != null && !yqNodes.isEmpty()) {
                                for (AccessibilityNodeInfo yqNode : yqNodes) {
                                    CharSequence charSequence = yqNode.getContentDescription();
                                    if (charSequence != null) {
                                        if (charSequence.toString().contains("添加成员")) {
                                            nStatus = 3;
                                            yqNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        }
                                    }
                                }
                            }

                            List<AccessibilityNodeInfo> listHds = hd.findAccessibilityNodeInfosByViewId(HBGROUPLIST_STRING_ID);
                            if (listHds != null && !listHds.isEmpty()) {
                                for (AccessibilityNodeInfo listHd : listHds) {
                                    listHd.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                                }
                            }
                        }
                        break;

                        case 3: {

                            int j = 0;
                            for (j = 0; j < friends.length; j++) {
                                String fr = friends[j];
                                try {
                                    if (findEditText(hd, fr)) {
                                        //重新填写搜索后，需要时间显示出列表，这里给1s的时间
                                        int ii = 0;
                                        for (ii = 0; ii < 5; ii++) {
                                            try {
                                                List<AccessibilityNodeInfo> fNames = hd.findAccessibilityNodeInfosByViewId(HBYQFRIENDNAMEID);
                                                //如果不加这一段findAccessibilityNodeInfosByViewId,引用的checkbox的checked，更新不准确？原因未知。
                                                List<AccessibilityNodeInfo> fChecks = hd.findAccessibilityNodeInfosByViewId(HBYQFRIENDCHECKBOXID);
                                                if (fNames != null && !fNames.isEmpty()) {
                                                    for (AccessibilityNodeInfo fName : fNames) {
                                                        AccessibilityNodeInfo clickNode = fName.getParent().getParent().getParent().getParent();
                                                        AccessibilityNodeInfo checkNode = fName.getParent().getParent().getParent().getChild(2);
                                                        int sz = fNames.size();
                                                        if (!checkNode.isChecked()) {
                                                            for (String friend : friends) {
                                                                if (fName.getText().toString().contains(friend)) {
                                                                    clickNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                                    break;
                                                                }
                                                            }
                                                        }
                                                    }
                                                    ii = 101; //能找到列表，即表示动态加载完毕。
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                            Thread.sleep(100);
                                        }
                                    }
                                }
                                catch (Exception e){
                                    e.printStackTrace();
                                }
                            }

                            List<AccessibilityNodeInfo> fConfirmBtns = hd.findAccessibilityNodeInfosByViewId(HBYQFRIENDCONFIRMNAMESID);
                            if (fConfirmBtns != null && !fConfirmBtns.isEmpty()) {
                                if (fConfirmBtns.get(0).isEnabled()) {
                                    fConfirmBtns.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 4;
                                } else {
                                    back2Home();
                                    nStatus = 5;
                                }
                            }

                            /*
                            List<AccessibilityNodeInfo> fFriendWordNodes = hd.findAccessibilityNodeInfosByViewId(HBYQFRIENDLISTWORD);
                            if (fFriendWordNodes != null && !fFriendWordNodes.isEmpty()) {
                                for (AccessibilityNodeInfo fFrendWordNode : fFriendWordNodes) {
                                    if (fFrendWordNode.getText().toString().contains("Z")) {
                                        List<AccessibilityNodeInfo> fConfirmBtns = hd.findAccessibilityNodeInfosByViewId(HBYQFRIENDCONFIRMNAMESID);
                                        if (fConfirmBtns != null && !fConfirmBtns.isEmpty()) {
                                            if (fConfirmBtns.get(0).isEnabled()) {
                                                fConfirmBtns.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                nStatus = 4;
                                            } else {
                                                back2Home();
                                                nStatus = 5;
                                            }
                                        }

                                    }
                                }
                            }
                            List<AccessibilityNodeInfo> listHds = hd.findAccessibilityNodeInfosByViewId(HBYQFRIENDSCROLLVIEWID);
                            if (listHds != null && !listHds.isEmpty()) {
                                for (AccessibilityNodeInfo listHd : listHds) {
                                    listHd.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                                }
                            }
                            */
                        }
                        break;

                        case 4: {
                            List<AccessibilityNodeInfo> confirmBtns = hd.findAccessibilityNodeInfosByViewId(HBYQFRIENDCONFIRMYQID);
                            List<AccessibilityNodeInfo> confirmContents = hd.findAccessibilityNodeInfosByText("群聊邀请确认");
                            if (confirmBtns != null && !confirmBtns.isEmpty()) {
                                if (confirmContents != null && confirmContents.isEmpty()) {
                                    confirmBtns.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 5;
                                    i = 200;
                                    back2Home();
                                    Log.i(TAG, "完成邀请");
                                } else {
                                    nStatus = 5;
                                    i = 200;
                                    back2Home();
                                    Log.i(TAG, "该邀请需要群主确认，建议人工处理");
                                }
                            } else {

                            }

                        }
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Thread.sleep(100);
        }
        back2Home();
        Log.i(TAG, "邀请超时完毕");
    }

    /*
        二维码识别加入群
     */
    private String qrcodeJoinGroup(String groupName) throws InterruptedException {
        int i = 0;
        int nStatus = 0;
        int nStatusCounter = 0;
        String resultStr = "";
        String msg = "";
        for (i = 0; i < 100; i++) {
            AccessibilityNodeInfo hd = getRootInActiveWindow();
            if (hd != null) {
                try {
                    switch (nStatus) {
                        case 0: {
                            List<AccessibilityNodeInfo> imgNodes = hd.findAccessibilityNodeInfosByViewId(HBQRCODEYJPICID);
                            if (imgNodes != null && !imgNodes.isEmpty()) {
                                imgNodes.get(imgNodes.size() - 1).getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                nStatus = 1;
                            }
                        }
                        break;
                        case 1: {
                            List<AccessibilityNodeInfo> mogicNodes = hd.findAccessibilityNodeInfosByViewId(HBQRCODEYJBIGPICID);
                            if (mogicNodes != null && !mogicNodes.isEmpty()) {
                                //mogicNodes.get(0).performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);//不可行
                                //execShellCmd("input keyevent --longpress 82 "); //menu按键
                                //mogicNodes.get(0).performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                                //mogicNodes.get(0).performAction(AccessibilityNodeInfo.ACTION_SELECT);
                                execShellCmd("input swipe 10 200 11 200 2000"); //采用划线的方式模拟屏幕长按
                                nStatus = 2;
                            }
                        }
                        break;
                        case 2: {
                            List<AccessibilityNodeInfo> qrcodeBtns = hd.findAccessibilityNodeInfosByText("识别图中二维码");
                            if (qrcodeBtns != null && !qrcodeBtns.isEmpty()) {
                                qrcodeBtns.get(0).getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                Thread.sleep(1000);
                                nStatus = 3;
                            }
                        }
                        break;
                        case 3: {
                            //AccessibilityNodeInfo nodeInfo = findNodeByClassName(hd, "com.tencent.smtt.webkit.WebView"); //不同的手机采用的网页引擎有可能不一样。
                            /*AccessibilityNodeInfo nodeInfo = findNodeByClassName(hd, "android.webkit.WebView"); //不同的手机采用的网页引擎有可能不一样。
                            if (nodeInfo != null) {
                                Log.i(TAG, "找到节点:" + nodeInfo.getClassName().toString());
                                if(nodeInfo.getContentDescription()!=null){
                                    Log.i(TAG,nodeInfo.getContentDescription().toString());
                                }
                                execShellCmd("input tap " + autoReceptParam);

                            }*/
                            List<AccessibilityNodeInfo> nodeInfos = hd.findAccessibilityNodeInfosByViewId(HBQRCODERETURN);
                            if (nodeInfos != null && !nodeInfos.isEmpty()) {
                                execShellCmd("input tap " + autoReceptParam);
                                nStatusCounter = 0;
                                nStatus = 4;
                            } else {
                                List<AccessibilityNodeInfo> windowTexts = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                                if (windowTexts != null && !windowTexts.isEmpty()) {
                                    if (!"".equals(windowTexts.get(0).getText())) {
                                        resultStr = "admin[sp]发消息[sp]" + groupName + "[sp]该群已经加入";
                                        nStatus = 5;
                                        i = 200;
                                        msg = "该群已经加入";
                                    }
                                }
                            }
                        }
                        break;
                        case 4: {
                            List<AccessibilityNodeInfo> windowTexts = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                            if (windowTexts != null && !windowTexts.isEmpty()) {
                                if (!"".equals(windowTexts.get(0).getText())) {
                                    resultStr = "admin[sp]发消息[sp]" + groupName + "[sp]加群成功";
                                    nStatus = 5;
                                    i = 200;
                                    msg = "加群成功";
                                }
                            } else {
                                nStatusCounter++;
                                if (nStatusCounter >= 40) { //2000毫秒点击一次
                                    execShellCmd("input tap " + autoReceptParam);
                                    nStatusCounter = 0;
                                }
                            }
                        }
                        break;

                        case 5: {

                        }
                        break;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Thread.sleep(100);
        }

        if ("".equals(resultStr)) {
            resultStr = "admin[sp]发消息[sp]" + groupName + "[sp]加群超时或群人数满100人";
            msg = "加群超时或群人数满100人";
        }

        Log.i(TAG, "识别完毕");
        performGlobalAction(GLOBAL_ACTION_BACK);
        if (sendMsg(groupName, msg)) {
            resultStr = "";//如果同步发送成功，则取消被动发送设置。
        }
        back2Home();
        setEventTypeContentAndStatus(true);
        return resultStr;

    }

    /*
        查找节点用 BY CLASS NAME
     */
    private AccessibilityNodeInfo findNodeByClassName(AccessibilityNodeInfo nd, String className) {
        AccessibilityNodeInfo nodeInfo1 = null;
        int i;
        int count = nd.getChildCount();
        for (i = 0; i < count; i++) {
            AccessibilityNodeInfo nodeInfo = nd.getChild(i);
            if (nodeInfo == null) {
                continue;
            }
            if (className.equals(nodeInfo.getClassName())) {
                return nodeInfo;
            }
            nodeInfo1 = findNodeByClassName(nodeInfo, className);
            if (nodeInfo1 != null) {
                return nodeInfo1;
            }
        }
        return null;
    }

    private boolean cmpGroup(String targetGroup, String inputGroup) {
        boolean bIsTheGroup = true;
        String groupkeys[] = inputGroup.split(" ");
        if (groupkeys.length == 1) {
            if (targetGroup.equals(inputGroup)) {
                bIsTheGroup = true;
            } else {
                bIsTheGroup = false;
            }
        } else {
            for (String groupKey : groupkeys) {
                if (!targetGroup.contains(groupKey.trim())) {
                    bIsTheGroup = false;
                    break;
                }
            }
        }
        return bIsTheGroup;
    }

    /*
        通用同步群消息发送
     */
    private boolean sendMsg(String groupName, String msg) throws InterruptedException {
        boolean bResult = false;
        int i = 0;
        int nStatus = 0;
        int nStatusCounter = 0;
        String lastGroupName = "";
        for (i = 0; i < 100; i++) {
            AccessibilityNodeInfo hd = getRootInActiveWindow();
            if (hd != null) {
                try {
                    switch (nStatus) {
                        case 0: {
                            List<AccessibilityNodeInfo> winTitles = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                            if (winTitles != null && !winTitles.isEmpty()) {
                                String gName = winTitles.get(0).getText().toString();
                                if (gName.lastIndexOf("(") != -1) {
                                    gName = gName.substring(0, gName.lastIndexOf("("));
                                }
                                if (cmpGroup(gName, groupName)) {
                                    if (findEditText(hd, msg)) {
                                        send();
                                        Log.i(TAG,"发消息1");
                                        nStatus = 4;
                                       // i = 200; //退出循环的意思
                                      //  bResult = true;
                                    }
                                } else {
                                    performGlobalAction(GLOBAL_ACTION_BACK);
                                    nStatus = 2;
                                }
                            }

                            if(nStatus!=4) { //如果最开始没有找到发送消息的对象的话，就检查列标题。
                                List<AccessibilityNodeInfo> listTitles = hd.findAccessibilityNodeInfosByViewId(CHATLISTTITLE_STRING_ID);
                                if (listTitles != null && !listTitles.isEmpty()) {
                                    for (AccessibilityNodeInfo listTitle : listTitles) {
                                        if (cmpGroup(listTitle.getText().toString(), groupName)) {
                                            listTitle.getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            nStatus = 1;
                                        }
                                    }

                                    if (nStatus == 0) { //列表中没找到的话
                                        lastGroupName = listTitles.get(0).getText().toString();
                                        nStatus = 2; //开始执行向后滚动寻找。
                                        nStatusCounter = 0;
                                    }
                                }
                            }
                        }
                        break;
                        case 1: {
                            List<AccessibilityNodeInfo> winTitles = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                            if (winTitles != null && !winTitles.isEmpty()) {
                                String gName = winTitles.get(0).getText().toString();
                                if (gName.lastIndexOf("(") != -1) {
                                    gName = gName.substring(0, gName.lastIndexOf("("));
                                }
                                if (cmpGroup(gName, groupName)) {
                                    if (findEditText(hd, msg)) {
                                        send();
                                        nStatus = 4; //发消息后，后退一下
                                        Log.i(TAG,"发消息2");
                                    }
                                }
                            }
                        }
                        break;

                        case 2: {
                            List<AccessibilityNodeInfo> listTitles = hd.findAccessibilityNodeInfosByViewId(CHATLISTTITLE_STRING_ID);
                            if (listTitles != null && !listTitles.isEmpty()) {
                                for (AccessibilityNodeInfo listTitle : listTitles) {
                                    if (cmpGroup(listTitle.getText().toString(), groupName)) {
                                        listTitle.getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        nStatus = 1;
                                    }
                                }

                                if (nStatus != 1) {//及还没找到
                                    if (listTitles.get(0).getText().toString().equals(lastGroupName)) {
                                        nStatusCounter++;
                                        if (nStatusCounter > 5) { //超时没找到的话
                                            lastGroupName = listTitles.get(listTitles.size() - 1).getText().toString();
                                            nStatus = 3; //向前面寻找
                                            nStatusCounter = 0;
                                        }
                                    }

                                    if (nStatus != 3) {//如果没转换到向前滚得模式的话：
                                        try {
                                            lastGroupName = listTitles.get(0).getText().toString();
                                            listTitles.get(0).getParent().getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }

                            }
                        }
                        break;

                        case 3: {
                            List<AccessibilityNodeInfo> listTitles = hd.findAccessibilityNodeInfosByViewId(CHATLISTTITLE_STRING_ID);
                            if (listTitles != null && !listTitles.isEmpty()) {
                                for (AccessibilityNodeInfo listTitle : listTitles) {
                                    if (cmpGroup(listTitle.getText().toString(), groupName)) {
                                        listTitle.getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        nStatus = 1;
                                    }
                                }
                                //没找到
                                if (nStatus != 1) {
                                    if (listTitles.get(listTitles.size() - 1).getText().toString().equals(lastGroupName)) {
                                        nStatusCounter++;
                                        if (nStatusCounter > 5) { //超时没找到的话
                                            i = 200;
                                            nStatus = 70; //向前面寻找
                                            nStatusCounter = 0;
                                            Log.i(TAG, "找不到要发送的群");
                                        }
                                    }

                                    try {
                                        lastGroupName = listTitles.get(listTitles.size() - 1).getText().toString();
                                        listTitles.get(0).getParent().getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                        break;

                        case 4:{ //页面后退，防止连续发消息时回到主页后，就无法再发了。（4,5,6状态都是这个目的)
                            List<AccessibilityNodeInfo> goes = hd.findAccessibilityNodeInfosByViewId(HBRETURN_STRING_ID);
                            if (goes != null && !goes.isEmpty()) {
                                for (AccessibilityNodeInfo go : goes) {
                                    go.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 5;
                                }
                            }
                        }
                        break;

                        case 5:{
                            List<AccessibilityNodeInfo> bottomBtns = hd.findAccessibilityNodeInfosByViewId(HBBOTTOMBTN_STRING_ID);
                            for (AccessibilityNodeInfo bottomBtn : bottomBtns) {
                                if (bottomBtn.getText() != null && bottomBtn.getText().toString().contains("我")) {
                                    bottomBtn.getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 6;
                                }
                            }
                        }
                        break;

                        case 6:{
                            List<AccessibilityNodeInfo> btns = hd.findAccessibilityNodeInfosByText("设置");
                            if (btns != null && !btns.isEmpty()) {
                                for (AccessibilityNodeInfo btn : btns) {
                                    btn.getParent().getParent().getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    i = 200; //退出循环的意思
                                    bResult = true;
                                    nStatus = 7;
                                }
                            }
                        }
                        break;

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Thread.sleep(100);
        }
        return bResult;
    }
    /*
        指定窗口发送消息
     */

    private void sendGroupMessage(String groupName, String msg) throws InterruptedException {
        int i = 0;
        int nStatus = 0;
        for (i = 0; i < 30; i++) {
            AccessibilityNodeInfo hd = getRootInActiveWindow();
            if (hd != null) {
                try {
                    switch (nStatus) {
                        case 0: {
                            List<AccessibilityNodeInfo> winTitles = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                            if (winTitles != null && !winTitles.isEmpty()) {
                                performGlobalAction(GLOBAL_ACTION_BACK);
                                i = 200;
                                nStatus = 5;
                            }
                        }
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Thread.sleep(100);
        }
        sendMsg(groupName, msg);
        back2Home();
    }


    /*
        自动接收加好友申请
     */
    private void acceptfriendReq() throws InterruptedException {
        int i = 0;
        int nStatus = 0;
        String friendName = "";
        String reqContent = "";
        String sig = "";
        String friendSource = "";
        boolean bMore = false;
        for (i = 0; i < 100; i++) {
            AccessibilityNodeInfo hd = getRootInActiveWindow();
            if (hd != null) {
                try {
                    switch (nStatus) {
                        case 0: {
                            List<AccessibilityNodeInfo> acceptBtns = hd.findAccessibilityNodeInfosByViewId(HBACCEPTBIGBTN_STRING_ID);//接受大按钮
                            if (acceptBtns != null && !acceptBtns.isEmpty()) {
                                AccessibilityNodeInfo acceptBtn = acceptBtns.get(0);
                                acceptBtn.getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                friendName = acceptBtn.getParent().getParent().getChild(1).getChild(0).getText().toString();
                                reqContent = acceptBtn.getParent().getParent().getChild(1).getChild(1).getText().toString();
                                if (acceptBtns.size() > 1) {
                                    bMore = true;
                                } else {
                                    bMore = false;
                                }
                                nStatus = 1;
                            }
                        }
                        break;
                        case 1: {
                            List<AccessibilityNodeInfo> acceptBtns = hd.findAccessibilityNodeInfosByViewId(HBPASSCHECK_STRING_ID);//通过验证按钮
                            if (acceptBtns != null && !acceptBtns.isEmpty()) {
                                List<AccessibilityNodeInfo> summarys = hd.findAccessibilityNodeInfosByViewId(HBFRIENDINFO_STRING_ID);
                                if (summarys != null && !summarys.isEmpty()) {
                                    int j = 0;
                                    for (j = 0; j < summarys.size(); j++) {
                                        if (summarys.size() > 1) {
                                            if (j == 0) sig = summarys.get(j).getText().toString();
                                        } else {
                                            friendSource = summarys.get(j).getText().toString();
                                        }
                                        if (j == 1)
                                            friendSource = summarys.get(j).getText().toString();
                                    }
                                } else {
                                    sig = "";
                                    friendSource = "对方没有经常联系的朋友";
                                }
                                AccessibilityNodeInfo acceptBtn = acceptBtns.get(0);
                                acceptBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                nStatus = 2;
                            }
                        }
                        break;
                        case 2: {
                            List<AccessibilityNodeInfo> acceptBtns = hd.findAccessibilityNodeInfosByViewId(HBFINISHCHECK_STRING_ID);//完成按钮
                            if (acceptBtns != null && !acceptBtns.isEmpty()) {
                                AccessibilityNodeInfo acceptBtn = acceptBtns.get(0);
                                acceptBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                nStatus = 3;
                            }
                        }
                        break;
                        case 3: {
                            List<AccessibilityNodeInfo> acceptBtns = hd.findAccessibilityNodeInfosByText("详细资料");
                            if (acceptBtns != null && !acceptBtns.isEmpty()) {
                                //Log.i(TAG, friendName + reqContent + sig + friendSource);
                                insertNewFriend(wx_user, friendName, reqContent, sig, friendSource);
                                if (bMore) {
                                    List<AccessibilityNodeInfo> returnBtns = hd.findAccessibilityNodeInfosByViewId(HBRETURNAFTERCHECK_STRING_ID);//返回按钮
                                    if (returnBtns != null && !returnBtns.isEmpty()) {
                                        for (AccessibilityNodeInfo returnBtn : returnBtns) {
                                            returnBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            nStatus = 0;
                                        }
                                    }
                                } else {
                                    i = 130;
                                    back2Home();
                                    nStatus = 5;
                                }
                            }
                        }
                        break;

                        case 5: {

                        }
                        break;

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Thread.sleep(100);
        }
        Log.i(TAG, "接收好友超时");
        back2Home();
    }

    private void autoDealHb(AccessibilityEvent event) throws JSONException, InterruptedException {
        //一旦有动静，在自动模式下，就执行窗口置后。
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED: {
                if (event.getParcelableData() != null && event.getParcelableData() instanceof Notification) {
                    Notification notification = (Notification) event.getParcelableData();
                    String content = notification.tickerText != null ? notification.tickerText.toString() : "";
                    Bundle bundle = notification.extras;
                    //Log.i(TAG, content);
                    //Log.i(TAG,bundle.toString());
                    //get group name
                    String group_name = bundle.getString(Notification.EXTRA_TITLE);
                    group_name = group_name != null ? group_name : "";
                    nNowMsgCounter ++; //收到一个通知，计数器加一
                    //get send person
                    int endIndex = content.indexOf(":");
                    String send_person = "";
                    if (endIndex != -1) {
                        send_person = content.substring(0, endIndex);
                        //upload msg，个人发送消息和其他加入朋友的消息不记录（个人消息发送人和消息标题相同，而新创建群聊，未命名，首次消息为标题为群成员用顿号分开）
                        if (bCloudChatRec) {
                            try {
                                rdnonhbInfo(group_name, send_person, wx_user, content.length());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        //接收添加好友申请,标题为申请人,内容为：请求添加你为朋友
                        if (content.contains(group_name + "请求添加你为朋友")) {
                            Log.i(TAG, "添加朋友");
                            PendingIntent pendingIntent = notification.contentIntent;
                            setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                            try {
                                pendingIntent.send();
                                acceptfriendReq();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            setEventTypeContentAndStatus(true);
                            send_person = "新朋友:" + group_name;
                        }
                    }
                    //自动加群处理
                    if (bAutoReceptGroup && content.contains("[链接] 邀请你加入群聊")) {
                        PendingIntent pendingIntent = notification.contentIntent;
                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                        try {
                            pendingIntent.send();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Log.i(TAG, "群聊自动加入");
                        joingroup();
                        setEventTypeContentAndStatus(true);
                    }

                    //退群指令检查
                    String group_name_md5 = ComFunc.MD5(group_name);
                    if (bAutoQuitGroup && blackGroup.contains("{={" + group_name_md5 + "}=}")) {
                        PendingIntent pendingIntent = notification.contentIntent;
                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                        try {
                            pendingIntent.send();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        syncQuitFromGroup(group_name_md5);
                        setEventTypeContentAndStatus(true);
                    }

                    if (bAutoQuitGroup) {
                        java.util.Date d = new java.util.Date();
                        if (d.getTime() > blackGroupStTime.getTime()) {
                            Log.i(TAG, "黑名单有效期过了,重新更新黑名单");
                            synchronized (blackSyncKey) {
                                blackGroup = "";
                            }
                        }
                    }
                    //Log.i(TAG,"content length="+String.valueOf(content.length()));
                    if (content.contains("[微信红包]") && content.length() < 100) { //<100过滤一些超长的带[微信红包]的垃圾信息
                        PendingIntent pendingIntent = notification.contentIntent;
                        setEventTypeContentAndStatus(true); //启用content和status监控
                        try {
                            pendingIntent.send();
                            notify_detect_tm = Calendar.getInstance().getTimeInMillis();
                            chatlist_detect_tm = 0;
                            detect_tm = 0;
                            mHander.postDelayed(runnable, 12000);
                            bAutoClickOpenDetail = false;
                            bAutoClickChatList = false;
                            bAutoClickHbItem = false;
                            bAutoClickOpenButton = false;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        //自动或的微信账户名称
                        if ("".equals(wx_user)) {
                            PendingIntent pendingIntent = notification.contentIntent;
                            setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                            try {
                                pendingIntent.send();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            wx_user = getWxUserName();
                            setEventTypeContentAndStatus(true);
                        } else {
                            //自动清空群消息处理
                            Log.i(TAG,"nNowMsgCounter:"+String.valueOf(nNowMsgCounter)+",nClearMsgNum:"+String.valueOf(nClearMsgNum));
                            if(nClearMsgNum>100 && nNowMsgCounter>nClearMsgNum){
                                nNowMsgCounter = 0;
                                PendingIntent pendingIntent = notification.contentIntent;
                                setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                try {
                                    pendingIntent.send();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                clearChatContent();
                                hostCmd = "";
                                setEventTypeContentAndStatus(true);
                            }

                            //自动邀请加群处理
                            if (bAutoHostCmd && remoteHostName.contains(send_person) && remoteHostName.contains(group_name)) {
                                String[] cmds = content.split("\\[sp\\]");
                                //指令为定长4
                                if (cmds.length == 4) {
                                    hostCmd = content;
                                    Log.i(TAG, "收到宿主指令:" + content);
                                    Log.i(TAG, "指令名称：" + cmds[1]);
                                    //立即执行指令
                                    if (cmds[1].equals("发消息")) {
                                        PendingIntent pendingIntent = notification.contentIntent;
                                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        sendMsg(cmds[2], cmds[3]);
                                        back2Home();
                                        hostCmd = "";
                                        setEventTypeContentAndStatus(true);
                                    } else if (cmds[1].equals("申请ROOT")) {//申请ROOT
                                        try {
                                            Runtime.getRuntime().exec("su");
                                            Log.i(TAG, "执行申请ROOT");
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        hostCmd = "";
                                    } else if (cmds[1].equals("清空群消息")) {//清空消息
                                        nNowMsgCounter = 0; //群消息计数清零
                                        PendingIntent pendingIntent = notification.contentIntent;
                                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        clearChatContent();
                                        hostCmd = "";
                                        setEventTypeContentAndStatus(true);
                                    }else if (cmds[1].equals("后台参数设置")) {//清空消息
                                        PendingIntent pendingIntent = notification.contentIntent;
                                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        String dealResult = "";
                                        try{
                                            if(sharedPreferences.contains(cmds[2]))
                                            {
                                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                                editor.putString(cmds[2],cmds[3]);
                                                editor.commit();
                                                dealResult = "成功";
                                            }
                                            else{
                                                dealResult = "失败(没这个参数项)";
                                            }
                                        }
                                        catch (Exception e){
                                            dealResult = "失败(设置异常)";
                                            e.printStackTrace();
                                        }
                                        sendMsg(group_name, cmds[2]+"设置为"+cmds[3]+dealResult);
                                        back2Home();
                                        hostCmd = "";
                                        setEventTypeContentAndStatus(true);
                                    }else if (cmds[1].equals("帮助")) {//清空消息
                                        PendingIntent pendingIntent = notification.contentIntent;
                                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        String helpMsg = "mtakem2版本:"+app_ver+"\n"+"[sp]发消息[sp]要发的群[sp]要发的内容\n" +
                                                "[sp]后台参数设置[sp]nClearMsgNum[sp]101\n[sp]后台参数设置[sp]autoInviteParam[sp]jzgz01|jzgz02|jzgz03\n[sp]后台参数设置[sp]autoInviteParam[sp]jzgz01|jzgz02|jzgz03\n" +
                                                "[sp]后台参数设置[sp]remoteHostName[sp]你的微信名 微信群名\n" +
                                                "[sp]清空群消息[sp]随意填[sp]随意\n[sp]申请ROOT[sp]随意填[sp]随意\n" +
                                         "[sp]邀请加入[sp]要邀请的群[sp]邀请谁加入？\n" +
                                                "[sp]二维码加群[sp]填一个群并给这个群发二维码[sp]随意\n" +
                                                "[sp]获取余额[sp]要反馈的群名称[sp]随意\n";
                                        sendMsg(group_name,helpMsg);
                                        back2Home();
                                        hostCmd = "";
                                        setEventTypeContentAndStatus(true);

                                    }else if (cmds[1].equals("获取余额")) {//清空消息
                                        PendingIntent pendingIntent = notification.contentIntent;
                                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        sendMsg(cmds[2],wx_user+":"+getAndSendWxAmount());
                                        back2Home();
                                        hostCmd = "";
                                        setEventTypeContentAndStatus(true);
                                    }

                                }
                            }
                            //检查宿主指令
                            //宿主指令格式:指令名称 群名称 相关参数
                            if (!"".equals(hostCmd)) {
                                String[] cmds = hostCmd.split("\\[sp\\]");
                                //第二个参数定义为群名称，所有指令都针对群而设立
                                String groupkeys[] = cmds[2].split(" ");
                                boolean bIsTheGroup = true;
                                for (String groupKey : groupkeys) {
                                    if (!group_name.contains(groupKey.trim())) {
                                        bIsTheGroup = false;
                                        break;
                                    }
                                }
                                if (cmds.length == 4 && bIsTheGroup) {
                                    if (cmds[1].equals("邀请加入")) {
                                        PendingIntent pendingIntent = notification.contentIntent;
                                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        yqfriends(cmds[3]);
                                        hostCmd = "";
                                        setEventTypeContentAndStatus(true);
                                    } else if (cmds[1].equals("二维码加群")) {
                                        PendingIntent pendingIntent = notification.contentIntent;
                                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        //处理后返回消息
                                        hostCmd = qrcodeJoinGroup(cmds[3]);
                                        setEventTypeContentAndStatus(true);
                                    }
                                }
                            }
                        }

                    }
                }
            }
            break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED: {
                AccessibilityNodeInfo hd = getRootInActiveWindow();
                if (hd != null) {
                    if (!bAutoClickHbItem) {
                        List<AccessibilityNodeInfo> titleNodes = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                        if (titleNodes != null && !titleNodes.isEmpty()) {
                            windowtitle = titleNodes.get(0).getText().toString();
                            List<AccessibilityNodeInfo> hbNodes = hd.findAccessibilityNodeInfosByText("领取红包");
                            if (hbNodes != null && !hbNodes.isEmpty()) {
                                for (int i = hbNodes.size() - 1; i >= 0; i--) {
                                    AccessibilityNodeInfo nodeInfo = hbNodes.get(i);
                                    try {
                                        AccessibilityNodeInfo pNode = nodeInfo.getParent().getParent().getParent().getParent();
                                        if (pNode.getClassName().toString().contains("LinearLayout")) {
                                            pNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            Log.i(TAG, "发现点击红包");
                                            detect_tm = Calendar.getInstance().getTimeInMillis();
                                            bAutoClickOpenDetail = true;
                                            bAutoClickHbItem = true;
                                            break;
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                if (!bAutoClickHbItem) {
                                    Log.i(TAG, "假消息，假HB，退出");
                                    back2Home();
                                    mHander.removeCallbacks(runnable);
                                }
                            } else {
                                Log.i(TAG, "假消息，没有红包");
                                back2Home();
                                mHander.removeCallbacks(runnable);
                            }
                        }
                    }
                    if (bAutoClickHbItem && !bAutoClickOpenButton) {
                        List<AccessibilityNodeInfo> hbNodes = hd.findAccessibilityNodeInfosByViewId(HBOPENBUTTON_STRING_ID);
                        if (hbNodes != null && !hbNodes.isEmpty()) {
                            hbNodes.get(hbNodes.size() - 1).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.i(TAG, "拆开红包");
                            bUnpackedSuccessful = true;
                            bAutoClickOpenButton = true;
                            Log.i(TAG, "Notify Time:" + String.valueOf(Calendar.getInstance().getTimeInMillis() - notify_detect_tm));
                            Log.i(TAG, "Detect Time:" + String.valueOf(Calendar.getInstance().getTimeInMillis() - detect_tm));
                        } else {
                            List<AccessibilityNodeInfo> hbNodes1 = hd.findAccessibilityNodeInfosByViewId(HBNONETEXT_STRING_ID);
                            if (hbNodes1 != null && !hbNodes1.isEmpty()) {
                                Log.i(TAG, "红包派完了");
                                bAutoClickHbItem = false;
                                back2Home();
                                mHander.removeCallbacks(runnable);
                            }
                        }
                    }

                    if (bAutoClickOpenButton) {
                        List<AccessibilityNodeInfo> hbNodes = null;
                        //发送人
                        hbNodes = hd.findAccessibilityNodeInfosByViewId(HBSENDER_STRING_ID);
                        if (hbNodes != null & !hbNodes.isEmpty()) {
                            sender = hbNodes.get(0).getText().toString();

                            //内容
                            hbNodes = hd.findAccessibilityNodeInfosByViewId(HBCONTENT_STRING_ID);
                            if (hbNodes != null & !hbNodes.isEmpty()) {
                                hbcontent = hbNodes.get(0).getText().toString();
                            }
                            //金额
                            hbNodes = hd.findAccessibilityNodeInfosByViewId(HBAMOUNTTEXT_STRING_ID);
                            if (hbNodes != null & !hbNodes.isEmpty()) {
                                hb_amount = hbNodes.get(0).getText().toString();
                            }

                            if (bUnpackedSuccessful) {
                                uploadHbInfo();
                                if (bAutoReply) {
                                    performGlobalAction(GLOBAL_ACTION_BACK);
                                    mHander.removeCallbacks(runnable);
                                    mHander.postDelayed(autoReplyFun, autoReplyDelay);
                                    bUnpackedSuccessful = false;
                                } else {
                                    back2Home();
                                    mHander.removeCallbacks(runnable);
                                    bUnpackedSuccessful = false;
                                    bAutoClickOpenDetail = false;
                                    bAutoClickHbItem = false;
                                    bAutoClickOpenButton = false;
                                }

                            }
                        }
                    }
                }
            }
            break;
        }
    }

    private void manMode(AccessibilityEvent event) throws JSONException {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED: {
                if ((bEnableNotifyWatch) && event.getParcelableData() != null && event.getParcelableData() instanceof Notification) {
                    Notification notification = (Notification) event.getParcelableData();
                    String content = notification.tickerText != null ? notification.tickerText.toString() : "";
                    if (content.contains("[微信红包]")) {
                        PendingIntent pendingIntent = notification.contentIntent;
                        try {
                            pendingIntent.send();
                            notify_detect_tm = Calendar.getInstance().getTimeInMillis();
                            chatlist_detect_tm = 0;
                            detect_tm = 0;
                            bAutoClickOpenDetail = false;
                            bAutoClickChatList = false;
                            bAutoClickHbItem = false;
                            bAutoClickOpenButton = false;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED: {
                //Log.i(TAG, "TYPE_WINDOW_CONTENT_CHANGED");
                String className = "";
                try {
                    ComponentName componentName = new ComponentName(
                            event.getPackageName().toString(),
                            event.getClassName().toString()
                    );
                    getPackageManager().getActivityInfo(componentName, 0);
                    className = componentName.flattenToShortString();
                } catch (PackageManager.NameNotFoundException e) {
                    className = WECHAT_LUCKMONEY_GENERAL_ACTIVITY;
                }

                //Log.i(TAG, className);
                if (className.contains("LauncherUI") || className.contains("ui.chatting")) {
                    AccessibilityNodeInfo hd = getRootInActiveWindow();
                    if (hd != null) {
                        //列表
                        if (bEnableChatListWatch) {
                            List<AccessibilityNodeInfo> nodeSnds = hd.findAccessibilityNodeInfosByViewId(SOUNDBUTTON_STRING_ID);
                            if (nodeSnds != null && nodeSnds.isEmpty() && !bAutoClickChatList) {
                                //6.5.7是afx,6.5.8变为agy
                                List<AccessibilityNodeInfo> nodeInfos1 = hd.findAccessibilityNodeInfosByViewId(CHATLISTTEXT_STRING_ID);
                                //找到了有消息条目，说明就进入了窗口了
                                if (nodeInfos1 != null && !nodeInfos1.isEmpty()) {
                                    AccessibilityNodeInfo findNode = null;
                                    for (int i = 0; i < nodeInfos1.size(); i++) {
                                        if (nodeInfos1.get(i).getText().toString().contains("[微信红包]")) {
                                            findNode = nodeInfos1.get(i);
                                            try {
                                                AccessibilityNodeInfo clickableParentNode = findNode.getParent().getParent().getParent().getParent();
                                                //如果有新消息提醒的话，就点击这个可以用android studio 中的adm的"Dump View hierarchy for UI Automator"层次关系
                                                if (clickableParentNode.getChild(0).getChildCount() > 1) {
                                                    clickableParentNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                    Log.i(TAG, "点击窗口列表");
                                                    chatlist_detect_tm = Calendar.getInstance().getTimeInMillis();
                                                    notify_detect_tm = 0;
                                                    detect_tm = 0;
                                                    bAutoClickChatList = true;
                                                    break;
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        //聊天窗口的标题(6.5.7为gh,6.5.8改为gp)
                        if (!bAutoClickHbItem) {
                            List<AccessibilityNodeInfo> titleNodes = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                            if (titleNodes != null && !titleNodes.isEmpty()) {
                                bAutoClickChatList = false;//使能点击列表
                                windowtitle = titleNodes.get(0).getText().toString();
                                String contextString = windowtitle;
                                boolean bfindHb = false;
                                //6.5.13后，红包领取后，红包条目会自动变化，故不用再检测窗口。
                                /*
                                List<AccessibilityNodeInfo> contextNodes = hd.findAccessibilityNodeInfosByViewId(WINDOWCHATTEXT_STRING_ID);
                                for (AccessibilityNodeInfo contextNode : contextNodes) {
                                    Rect rect = new Rect();
                                    contextNode.getBoundsInScreen(rect);
                                    contextString = contextString + contextNode.getText().toString() + rect.toString();
                                }*/
                                // if (!contextString.equals(last_context_string)) {
                                List<AccessibilityNodeInfo> hbNodes = hd.findAccessibilityNodeInfosByText("领取红包");
                                if (hbNodes != null && !hbNodes.isEmpty()) {
                                    for (int i = hbNodes.size() - 1; i >= 0; i--) {
                                        AccessibilityNodeInfo nodeInfo = hbNodes.get(i);
                                        try {
                                            AccessibilityNodeInfo pNode = nodeInfo.getParent().getParent().getParent().getParent();
                                            if (pNode.getClassName().toString().contains("LinearLayout")) {
                                                pNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                Log.i(TAG, "发现点击红包");
                                                detect_tm = Calendar.getInstance().getTimeInMillis();
                                                bAutoClickOpenDetail = true;
                                                bAutoClickHbItem = true;
                                                break;
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                                //last_context_string = contextString;
                                // }
                            }
                        }
                    }

                } else if (className.contains("luckymoney.ui.En_")) {
                    AccessibilityNodeInfo hd = getRootInActiveWindow();
                    if (!bAutoClickOpenButton) {
                        List<AccessibilityNodeInfo> hbNodes = hd.findAccessibilityNodeInfosByViewId(HBOPENBUTTON_STRING_ID);
                        if (hbNodes != null && !hbNodes.isEmpty()) {
                            hbNodes.get(hbNodes.size() - 1).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.i(TAG, "点击抢HB");
                            bUnpackedSuccessful = true;
                            bAutoClickOpenButton = true;
                            Log.i(TAG, "Notify Time:" + String.valueOf(Calendar.getInstance().getTimeInMillis() - notify_detect_tm));
                            Log.i(TAG, "Detect Time:" + String.valueOf(Calendar.getInstance().getTimeInMillis() - detect_tm));

                        } else {
                            boolean hasNodes = hasOneOfThoseNodes(
                                    WECHAT_BETTER_LUCK_CH, WECHAT_BETTER_LUCK_EN, WECHAT_EXPIRES_CH, WECHAT_WHOGIVEYOUAHB);
                            if (hasNodes) {
                                if (bAutoClickHbItem)
                                    performGlobalAction(GLOBAL_ACTION_BACK);//打开红包后返回到聊天页面
                                else bAutoClickOpenDetail = false;
                            }
                        }
                    }
                    bAutoClickChatList = false;
                    bAutoClickHbItem = false;

                } else if (className.contains("luckymoney.ui.LuckyMoneyDetailUI")) {
                    AccessibilityNodeInfo hd = getRootInActiveWindow();
                    bAutoClickChatList = false;
                    bAutoClickHbItem = false;
                    bAutoClickOpenButton = false;
                    if (hd != null) {
                        List<AccessibilityNodeInfo> hbNodes = null;
                        //发送人
                        hbNodes = hd.findAccessibilityNodeInfosByViewId(HBSENDER_STRING_ID);
                        if (hbNodes != null & !hbNodes.isEmpty()) {
                            sender = hbNodes.get(0).getText().toString();

                            //内容
                            hbNodes = hd.findAccessibilityNodeInfosByViewId(HBCONTENT_STRING_ID);
                            if (hbNodes != null & !hbNodes.isEmpty()) {
                                hbcontent = hbNodes.get(0).getText().toString();
                            }
                            //金额
                            hbNodes = hd.findAccessibilityNodeInfosByViewId(HBAMOUNTTEXT_STRING_ID);
                            if (hbNodes != null & !hbNodes.isEmpty()) {
                                hb_amount = hbNodes.get(0).getText().toString();
                            }

                            /*
                            Log.i(TAG, "group=" + windowtitle);
                            Log.i(TAG, "sender=" + sender);
                            Log.i(TAG, "content=" + hbcontent);
                            Log.i(TAG, "amount=" + hb_amount);*/

                            if (bUnpackedSuccessful) {
                                uploadHbInfo();
                                bUnpackedSuccessful = false;
                            }

                            if (bAutoClickOpenDetail) {
                                performGlobalAction(GLOBAL_ACTION_BACK);
                                bAutoClickOpenDetail = false;
                            }
                        }
                    }
                }
            }
            break;
        }
    }

    /*
        获得微信钱包金额
     */

    private String getAndSendWxAmount() throws InterruptedException {

        String result = "未获取到";
        int i = 0;
        int nStatus = 0;
        for (i = 0; i < 100; i++) {
            AccessibilityNodeInfo hd = getRootInActiveWindow();
            if (hd != null) {
                try {
                    switch (nStatus) {
                        case 0: {//点击到聊天列表页面
                            List<AccessibilityNodeInfo> goes = hd.findAccessibilityNodeInfosByViewId(HBRETURN_STRING_ID);
                            if (goes != null && !goes.isEmpty()) {
                                for (AccessibilityNodeInfo go : goes) {
                                    go.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 1;
                                }
                            }
                        }
                        break;
                        case 1: {//点击“我”
                            List<AccessibilityNodeInfo> bottomBtns = hd.findAccessibilityNodeInfosByViewId(HBBOTTOMBTN_STRING_ID);
                            for (AccessibilityNodeInfo bottomBtn : bottomBtns) {
                                if (bottomBtn.getText() != null && bottomBtn.getText().toString().contains("我")) {
                                    bottomBtn.getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 2;
                                }
                            }
                        }
                        break;
                        case 2: {
                            List<AccessibilityNodeInfo> btns = hd.findAccessibilityNodeInfosByText("钱包");
                            if (btns != null && !btns.isEmpty()) {
                                for (AccessibilityNodeInfo btn : btns) {
                                    btn.getParent().getParent().getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 3;
                                }
                            }
                        }
                        break;
                        case 3: {
                            List<AccessibilityNodeInfo> btns = hd.findAccessibilityNodeInfosByText("零钱");
                            if (btns != null && !btns.isEmpty()) {
                                for (AccessibilityNodeInfo btn : btns) {
                                    try {
                                        result = btn.getParent().getChild(2).getText().toString();
                                    }
                                    catch (Exception e){
                                        Log.i(TAG,e.getMessage());
                                        result = "获取异常";
                                    }
                                    Log.i(TAG,result);
                                    performGlobalAction(GLOBAL_ACTION_BACK);
                                    nStatus = 4;
                                }
                            }
                        }
                        break;
                        case 4:{
                            List<AccessibilityNodeInfo> bottomBtns = hd.findAccessibilityNodeInfosByViewId(HBBOTTOMBTN_STRING_ID);
                            for (AccessibilityNodeInfo bottomBtn : bottomBtns) {
                                if (bottomBtn.getText() != null && bottomBtn.getText().toString().contains("微信")) {
                                    bottomBtn.getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 5;
                                }
                            }
                        }
                        break;
                        case 5:{
                            List<AccessibilityNodeInfo> nodeInfos1 = hd.findAccessibilityNodeInfosByViewId(CHATLISTTEXT_STRING_ID);
                            //找到了有消息条目，说明就进入了窗口了
                            if (nodeInfos1 != null && !nodeInfos1.isEmpty()) {
                                for(AccessibilityNodeInfo nodeInfo:nodeInfos1){
                                    try {
                                        AccessibilityNodeInfo clickableParentNode = nodeInfo.getParent().getParent().getParent().getParent();
                                        clickableParentNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        nStatus = 6;
                                        break;
                                    }
                                    catch (Exception e){
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                        break;
                        case 6:{
                            List<AccessibilityNodeInfo> titleNodes = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                            if(titleNodes!=null && !titleNodes.isEmpty()){
                                nStatus = 7;
                                i = 200;
                            }
                        }
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Thread.sleep(100);
        }
        Log.i(TAG, "获取操作完成");
        return  result;
    }

    /*
        获得微信名称
     */
    private String getWxUserName() throws InterruptedException {

        int i = 0;
        try {
            boolean bClickReturn1 = false;
            for (i = 0; i < 30; i++) {
                AccessibilityNodeInfo hd = getRootInActiveWindow();
                if (hd != null) {
                    if (!bClickReturn1) {
                        List<AccessibilityNodeInfo> goes = hd.findAccessibilityNodeInfosByViewId(HBRETURN_STRING_ID);
                        if (goes != null && !goes.isEmpty()) {
                            for (AccessibilityNodeInfo go : goes) {
                                go.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            }
                            bClickReturn1 = true;
                        }
                    }
                    if (bClickReturn1) {
                        List<AccessibilityNodeInfo> bottomBtns = hd.findAccessibilityNodeInfosByViewId(HBBOTTOMBTN_STRING_ID);
                        for (AccessibilityNodeInfo bottomBtn : bottomBtns) {
                            if (bottomBtn.getText() != null && bottomBtn.getText().toString().contains("我")) {
                                bottomBtn.getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            }
                        }
                        List<AccessibilityNodeInfo> wxnames = hd.findAccessibilityNodeInfosByViewId(HBWXUSER_STRING_ID);
                        for (AccessibilityNodeInfo wxname : wxnames) {
                            back2Home();
                            Log.i(TAG, "wxUser:" + wxname.getText().toString());
                            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("wxUser", wxname.getText().toString());
                            editor.commit();
                            return wxname.getText().toString();
                        }
                    }
                }
                Thread.sleep(100);
            }
            back2Home();
        } catch (Exception e) {
            Log.i(TAG, "error");
            e.printStackTrace();
        }
        return "";
    }

    /*根据系统的配置，随机获得回复的词语*/
    private String generateCommentString() {
        String[] wordsArray = sharedPreferences.getString("edit_text_autoReplyText", "").split(" +");
        if (wordsArray.length == 0) return "~^o^~";
        return wordsArray[(int) (Math.random() * wordsArray.length)];
    }

    private boolean findEditText(AccessibilityNodeInfo rootNode, String content) {
        int count = rootNode.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo nodeInfo = rootNode.getChild(i);
            if (nodeInfo == null) {
                continue;
            }
            if ("android.widget.EditText".equals(nodeInfo.getClassName())) {
                Bundle arguments = new Bundle();
                arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                        AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD);
                arguments.putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN,
                        true);
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
                        arguments);
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                ClipData clip = ClipData.newPlainText("label", content);
                ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboardManager.setPrimaryClip(clip);
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                return true;
            }
            if (findEditText(nodeInfo, content)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 寻找窗体中的“发送”按钮，并且点击。
     */
    @SuppressLint("NewApi")
    private void send() {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        android.util.Log.i("maptrix", "nodeInfo is" + (nodeInfo != null ? nodeInfo.getClassName().toString() : "null"));
        if (nodeInfo != null) {
            List<AccessibilityNodeInfo> list = nodeInfo
                    .findAccessibilityNodeInfosByText("发送");
            if (list != null && list.size() > 0) {
                for (AccessibilityNodeInfo n : list) {
                    n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }

            } else {
                List<AccessibilityNodeInfo> liste = nodeInfo
                        .findAccessibilityNodeInfosByText("Send");
                if (liste != null && liste.size() > 0) {
                    for (AccessibilityNodeInfo n : liste) {
                        n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    }
                }
            }
            pressBackButton();
        }
    }

    /**
     * 模拟back按键
     */
    private void pressBackButton() {
        Runtime runtime = Runtime.getRuntime();
        try {
            runtime.exec("input keyevent " + KeyEvent.KEYCODE_BACK);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
            遍历当前窗体的所有节点
     */
    public void recycle(AccessibilityNodeInfo info) {
        if (info.getChildCount() == 0) {
            Log.i(TAG, "child widget----------------------------" + info.getClassName() + "(parent:" + (info.getParent() != null ? info.getParent().getClassName().toString() : "null") + ")");
            Log.i(TAG, "showDialog:" + info.canOpenPopup());
            Log.i(TAG, "Text：" + info.getText());
            Log.i(TAG, "content：" + info.getContentDescription());
            Log.i(TAG, "windowId:" + info.getWindowId());
            Log.i(TAG, "ViewIdResourceName:" + info.getViewIdResourceName());
        } else {
            for (int i = 0; i < info.getChildCount(); i++) {
                if (info.getChild(i) != null) {
                    recycle(info.getChild(i));
                }
            }
        }
    }

    /*
            得到当前窗口所有的不管活不活动，或则他的z-index在下面的窗口
     */
    /*
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private ArrayList<AccessibilityNodeInfo> getNodesFromWindows() {
        List<AccessibilityWindowInfo> windows = getWindows();
        ArrayList<AccessibilityNodeInfo> nodes =
                new ArrayList<AccessibilityNodeInfo>();
        if (windows.size() > 0) {
            for (AccessibilityWindowInfo window : windows) {
                nodes.add(window.getRoot());
            }
        }
        return nodes;
    }*/

    /**
     * 判断指定的应用是否在前台运行
     *
     * @param packageName
     * @return
     */
    private boolean isAppForeground(String packageName) {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ComponentName cn = am.getRunningTasks(1).get(0).topActivity;
        String currentPackageName = cn.getPackageName();
        if (!TextUtils.isEmpty(currentPackageName) && currentPackageName.equals(packageName)) {
            return true;
        }

        return false;
    }

    /**
     * 将当前应用运行到前台
     */
    private void bring2Front() {
        ActivityManager activtyManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> runningTaskInfos = activtyManager.getRunningTasks(3);
        for (ActivityManager.RunningTaskInfo runningTaskInfo : runningTaskInfos) {
            if (this.getPackageName().equals(runningTaskInfo.topActivity.getPackageName())) {
                //activtyManager.moveTaskToFront(runningTaskInfo.id, ActivityManager.MOVE_TASK_WITH_HOME);
                return;
            }
        }
    }

    /**
     * 回到系统桌面
     */
    private void back2Home() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        home.addCategory(Intent.CATEGORY_HOME);
        startActivity(home);
    }

    /**
     * 系统是否在锁屏状态
     *
     * @return
     */
    private boolean isScreenLocked() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return keyguardManager.inKeyguardRestrictedInputMode();
    }

    private void uploadHbInfo() throws JSONException {
        final ContentValues values = new ContentValues();
        String group_name = windowtitle;
        if (group_name.lastIndexOf("(") != -1) {
            group_name = group_name.substring(0, group_name.lastIndexOf("("));
        }
        values.put("group_name", group_name);
        values.put("group_name_md5",ComFunc.MD5(group_name));
        values.put("sender", sender);
        values.put("content", hbcontent);
        values.put("hb_amount", hb_amount.equals("")?"0":hb_amount);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
        values.put("unpacked_time", df.format(new java.util.Date()));
        values.put("notify_consuming", notify_detect_tm != 0 ? Calendar.getInstance().getTimeInMillis() - notify_detect_tm : 0);
        values.put("chatlist_consuming", chatlist_detect_tm != 0 ? Calendar.getInstance().getTimeInMillis() - chatlist_detect_tm : 0);
        values.put("chatwindow_consuming", detect_tm != 0 ? Calendar.getInstance().getTimeInMillis() - detect_tm : 0);
        final JSONObject obj = new JSONObject();
        JSONArray array = new JSONArray();
        JSONObject item = new JSONObject();

        item.put("device", device_model + "(" + version_release + ")");
        item.put("machine_id", mac);
        item.put("wxUser", wx_user);
        item.put("mtakem2ver", app_ver);
        item.put("group_name", values.getAsString("group_name"));
        item.put("group_name_md5", values.getAsString("group_name_md5"));
        item.put("sender", values.getAsString("sender"));
        item.put("content", values.getAsString("content"));
        item.put("hb_amount", values.getAsDouble("hb_amount"));
        item.put("unpacked_time", values.getAsString("unpacked_time"));
        item.put("notify_consuming", values.getAsInteger("notify_consuming"));
        item.put("chatlist_consuming", values.getAsInteger("chatlist_consuming"));
        item.put("chatwindow_consuming", values.getAsInteger("chatwindow_consuming"));
        array.put(item);
        obj.put("total", 1);
        obj.put("rows", array);

        //创建后台线程，获取远程版本
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                boolean bUploadSuccessful = false;
                try {
                    URL url = new URL(getText(R.string.uribase) + "/httpfun.jsp?action=InsertHbInfo&strHbInfo=" + URLEncoder.encode(obj.toString(), "utf-8"));
                    conn = (HttpURLConnection) url
                            .openConnection();
                    //使用GET方法获取
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        InputStream is = conn.getInputStream();
                        String result = readMyInputStream(is);
                        Log.i(TAG, URLDecoder.decode(result, "gbk"));
                        JSONObject objResult = new JSONObject(URLDecoder.decode(result, "gbk"));
                        if (objResult.getBoolean("result")) {
                            bUploadSuccessful = true;
                            Log.i(TAG, "上传成功:" + objResult.getString("msg"));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (conn != null) conn.disconnect();
                }

                //如果上传失败的话，则保存在本地数据库
                if (!bUploadSuccessful) {
                    try {
                        synchronized (lockkey) {
                            HbHistory hb = new HbHistory(getApplicationContext());
                            hb.insert(values);
                            hb.close();
                            Log.i(TAG, "上传失败,保存到本地数据库");
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }
        }).start();

    }

    /*
        上传历史情况
     */
    private void rdnonhbInfo(String group_name, String last_send_person, String wxUser, int len) throws JSONException {
        final JSONObject obj = new JSONObject();
        JSONArray array = new JSONArray();
        JSONObject item = new JSONObject();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
        String strGroupNameMd5 = "";
        strGroupNameMd5 = ComFunc.MD5(group_name);
        Log.i(TAG, group_name);
        Log.i(TAG, strGroupNameMd5);
        item.put("group_name", group_name);
        item.put("group_name_md5", strGroupNameMd5);
        item.put("last_send_person", last_send_person);
        item.put("wxUser", wxUser);
        item.put("mac", mac);
        item.put("len", len);
        item.put("receive_time", df.format(new java.util.Date()));
        array.put(item);
        obj.put("total", 1);
        obj.put("rows", array);


        //创建后台线程，获取远程版本
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                boolean bUploadSuccessful = false;
                try {
                    URL url = new URL(getText(R.string.uribase) + "/httpfun.jsp?action=inserthistory&strHistory=" + URLEncoder.encode(obj.toString(), "utf-8"));
                    conn = (HttpURLConnection) url
                            .openConnection();
                    //使用GET方法获取
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        InputStream is = conn.getInputStream();
                        String result = readMyInputStream(is);
                        //Log.i(TAG, URLDecoder.decode(result, "gbk"));
                        JSONObject objResult = new JSONObject(URLDecoder.decode(result, "gbk"));
                        if (objResult.getBoolean("result")) {
                            //Log.i(TAG, "上传成功:" + URLDecoder.decode(result, "gbk"));
                            synchronized (blackSyncKey) {
                                if (blackGroup.equals("")) {
                                    blackGroup = objResult.getString("blackGroup");
                                    blackGroupStTime = new java.util.Date(new java.util.Date().getTime() + 30 * 1000);
                                }
                            }
                        }

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    /*
        更新退出结果
     */

    private void updateQuitResult(final String wxUser, final String group_name_md5) {
//创建后台线程，获取远程版本
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                boolean bUploadSuccessful = false;
                try {
                    URL url = new URL(getText(R.string.uribase) + "/httpfun.jsp?action=updatequitstatus&txtMac=" + mac + "&wxUser=" + URLEncoder.encode(wxUser, "utf-8") + "&group_name_md5=" + URLEncoder.encode(group_name_md5, "utf-8"));
                    conn = (HttpURLConnection) url
                            .openConnection();
                    //使用GET方法获取
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        InputStream is = conn.getInputStream();
                        String result = readMyInputStream(is);
                        // Log.i(TAG, URLDecoder.decode(result, "gbk"));
                        JSONObject objResult = new JSONObject(URLDecoder.decode(result, "gbk"));
                        if (objResult.getBoolean("result")) {
                            Log.i(TAG, URLDecoder.decode(objResult.getString("msg"), "gbk"));
                        }

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    /*
        新加好友信息上传
     */
    private void insertNewFriend(final String wxUser, final String friendName, final String reqContent, final String friendSig, final String friendSource) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                boolean bUploadSuccessful = false;
                try {
                    URL url = new URL(getText(R.string.uribase) + "/httpfun.jsp?action=insertFriend&wxUser=" + URLEncoder.encode(wxUser, "utf-8") + "&friendName=" + URLEncoder.encode(friendName, "utf-8") +
                            "&reqContent=" + URLEncoder.encode(reqContent, "utf-8") + "&friendSig=" + URLEncoder.encode(friendSig, "utf-8") + "&friendSource=" + URLEncoder.encode(friendSource, "utf-8")
                    );
                    conn = (HttpURLConnection) url
                            .openConnection();
                    //使用GET方法获取
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        InputStream is = conn.getInputStream();
                        String result = readMyInputStream(is);
                        //Log.i(TAG, URLDecoder.decode(result, "gbk"));
                        JSONObject objResult = new JSONObject(URLDecoder.decode(result, "gbk"));
                        if (objResult.getBoolean("result")) {
                            Log.i(TAG, URLDecoder.decode(objResult.getString("msg"), "gbk"));
                        }

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    /*
        读取服务器反馈
     */
    private String readMyInputStream(InputStream is) {
        byte[] result;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            is.close();
            baos.close();
            result = baos.toByteArray();

        } catch (IOException e) {
            e.printStackTrace();
            String errorStr = "获取数据失败";
            return errorStr;
        }
        return new String(result);
    }

    /**
     * 这个要ROOT才行
     * 执行shell命令
     * z
     *
     * @param cmd
     */
    private void execShellCmd(String cmd) {
        try {
            // 申请获取root权限，这一步很重要，不然会没有作用
            Process process = Runtime.getRuntime().exec("su");
            // 获取输出流
            OutputStream outputStream = process.getOutputStream();
            DataOutputStream dataOutputStream = new DataOutputStream(
                    outputStream);
            dataOutputStream.writeBytes(cmd);
            dataOutputStream.flush();
            dataOutputStream.close();
            outputStream.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }


}
