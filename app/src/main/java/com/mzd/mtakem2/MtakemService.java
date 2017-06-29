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
    private boolean bAutoReceptGroup = false;
    private String autoReceptParam = "240 350";

    private long autoReplyDelay = 1000;
    private boolean bCanUse = true;

    Handler mHander = new Handler();

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
    // 6.5.7：a3_  ,6.5.8:a47
    private static final String SOUNDBUTTON_STRING_ID = "com.tencent.mm:id/a47";

    //聊天窗口的标题信息，标识了所在的群或者聊天对象
    //6.5.7:gh , 6.5.8:gp
    private static final String WINDOWTITLETEXT_STRING_ID = "com.tencent.mm:id/gp";

    //聊天的文本控件ID
    // 6.5.7:if  , 6.5.8:im
    private static final String WINDOWCHATTEXT_STRING_ID = "com.tencent.mm:id/im";

    //聊天信息中的时间标签ID
    //6.5.7:t , 6.5.8:t (没变化）
    private static final String WINDOWCHATTTIME_STRING_ID = "com.tencent.mm:id/t";

    //聊天列表中的最后文本信息
    //6.5.7:afx , 6.5.8:agy
    private static final String CHATLISTTEXT_STRING_ID = "com.tencent.mm:id/agy";

    //聊天窗中的HB信息
    //6.5.7:   , 6.5.8:a6_
    private static final String HB_STRING_ID = "com.tencent.mm:id/a6_";

    //HB打开按钮
    //6.5.7:bjj , 6.5.8:bm4
    private static final String HBOPENBUTTON_STRING_ID = "com.tencent.mm:id/bm4";

    //HB打开按钮
    //6.5.7: , 6.5.8:bm3
    private static final String HBNONETEXT_STRING_ID = "com.tencent.mm:id/bm3";

    //HB金额文本按钮
    //6.5.7:bfw , 6.5.8:bii
    private static final String HBAMOUNTTEXT_STRING_ID = "com.tencent.mm:id/bii";

    //HB发送人文本
    //6.5.7: , 6.5.8:bie
    private static final String HBSENDER_STRING_ID = "com.tencent.mm:id/bie";

    //HB内容文本
    //6.5.7: , 6.5.8:big
    private static final String HBCONTENT_STRING_ID = "com.tencent.mm:id/big";

    //聊天窗返回列表窗返回箭头
    //6.5.8:gn
    private static final String HBRETURN_STRING_ID = "com.tencent.mm:id/gn";

    //wx聊天列表下面的按钮ID
    //6.5.8:buh
    private static final String HBBOTTOMBTN_STRING_ID = "com.tencent.mm:id/buh";

    //wx名称的textid
    //6.5.8:by2
    private static final String HBWXUSER_STRING_ID = "com.tencent.mm:id/by2";

    //群信息的列表ID
    //6.5.8:list
    private static final String HBGROUPLIST_STRING_ID = "android:id/list";

    //删除并退出按钮
    //6.5.8:title
    private static final String HBDELANDQUIT_STRING_ID = "android:id/title";

    //删除并退出确认按钮
    //6.5.8:ad8
    private static final String HBDELANDQUITCONFIRM_STRING_ID = "com.tencent.mm:id/ad8";


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
        bAutoReceptGroup = sharedPreferences.getBoolean("autoRecept",false);
        autoReceptParam = sharedPreferences.getString("autoParam","240 350");
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("autoMode")) {
            bAutoMode = sharedPreferences.getBoolean(key, false);
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

        if (key.equals("autoParam")) {
            autoReceptParam = sharedPreferences.getString("autoParam", "240 350");
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
        退出出群聊过程，进入群聊窗口后执行。
     */
    private void quitFromGroup(AccessibilityEvent event) throws InterruptedException {
        AccessibilityNodeInfo hd = getRootInActiveWindow();
        if (hd != null) {
            if ("com.tencent.mm.plugin.chatroom.ui.ChatroomInfoUI".equals(event.getClassName())) {
                int i = 0;
                for (i = 0; i < 10; i++) {
                    List<AccessibilityNodeInfo> listHds = hd.findAccessibilityNodeInfosByViewId(HBGROUPLIST_STRING_ID);
                    if (listHds != null && !listHds.isEmpty()) {
                        for (AccessibilityNodeInfo listHd : listHds) {
                            listHd.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                        }
                    }
                    List<AccessibilityNodeInfo> titleHds = hd.findAccessibilityNodeInfosByViewId(HBDELANDQUIT_STRING_ID);
                    if (titleHds != null && !titleHds.isEmpty()) {
                        for (AccessibilityNodeInfo titleHd : titleHds) {
                            Log.i(TAG, "i=" + String.valueOf(i));
                            if ("删除并退出".equals(titleHd.getText())) {
                                Log.i(TAG, "找到退出");
                                if (titleHd.getParent() != null) {
                                    titleHd.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                }
                                i = 10;
                                break;
                            }
                        }
                    }
                    Thread.sleep(100);
                }
            }

            if ("com.tencent.mm.ui.base.h".equals(event.getClassName())) {
                int i = 0;
                for (i = 0; i < 10; i++) {
                    List<AccessibilityNodeInfo> confirmNodes = hd.findAccessibilityNodeInfosByViewId(HBDELANDQUITCONFIRM_STRING_ID);

                    for (AccessibilityNodeInfo confirmNode : confirmNodes) {
                        confirmNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        i = 10;
                    }
                    Thread.sleep(100);
                }

            }
        }
    }

    private void joingroup() throws InterruptedException {
        int i = 0;
        boolean bClickYqBtn = false;
        boolean bClickJoinBtn = false;
        for (i = 0; i < 100; i++) {
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
                                            pNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            Log.i(TAG,pNode.toString());
                                            bClickYqBtn = true;
                                            break;
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                if (!bClickYqBtn) {
                                    i = 180;
                                    back2Home();
                                    Log.i(TAG,"假邀请，退出");
                                }
                            }else{
                                i = 180;
                                back2Home();
                                Log.i(TAG,"假邀请");
                            }
                        }
                    }

                    if(bClickYqBtn && !bClickJoinBtn){
                        List<AccessibilityNodeInfo> qltitles = hd.findAccessibilityNodeInfosByText("群聊邀请");
                        if(qltitles!=null && !qltitles.isEmpty()){
                            List<AccessibilityNodeInfo> webviews = hd.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/an6");
                            Log.i(TAG,"webviews count:"+String.valueOf(webviews.size()));
                            if(webviews!=null && !webviews.isEmpty()){
                                AccessibilityNodeInfo webview = webviews.get(0);
                                try{
                                    AccessibilityNodeInfo pNodeInfo = webview.getChild(0).getChild(0);
                                    //execShellCmd("input keyevent 3");
                                    execShellCmd("input tap "+autoReceptParam);
                                    pNodeInfo.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                                    pNodeInfo.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);

                                    bClickJoinBtn = true;
                                }
                                catch (Exception e){
                                    e.printStackTrace();
                                    Log.i(TAG,e.getMessage());
                                }
                                if(!bClickJoinBtn){
                                    i = 130;
                                    back2Home();
                                    Log.i(TAG,"假邀请1 退出");
                                }
                            }
                            else{
                                i = 130;
                                back2Home();
                                Log.i(TAG,"假邀请1");
                            }
                        }
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            Thread.sleep(100);
        }
        back2Home();
        Log.i(TAG,"邀请检查完毕");
    }

    private void autoDealHb(AccessibilityEvent event) throws JSONException, InterruptedException {
        //一旦有动静，在自动模式下，就执行窗口置后。
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED: {
                if (event.getParcelableData() != null && event.getParcelableData() instanceof Notification) {
                    Notification notification = (Notification) event.getParcelableData();
                    String content = notification.tickerText != null ? notification.tickerText.toString() : "";

                    Bundle bundle = notification.extras;
                    String group_name = bundle.getString(Notification.EXTRA_TITLE);
                    group_name = group_name != null ? group_name : "";
                    // Log.i(TAG,bundle.getString(Notification.EXTRA_TITLE));
                    // Log.i(TAG,bundle.getString(Notification.EXTRA_TEXT));
                    try {
                        rdnonhbInfo(group_name, wx_user, content.length());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Log.i(TAG, content.toString());
                    if (bAutoReceptGroup && content.contains("[链接] 邀请你加入群聊")) {
                        PendingIntent pendingIntent = notification.contentIntent;
                        try {
                            pendingIntent.send();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Log.i(TAG,"群聊自动加入");
                        joingroup();
                    }

                    if (content.contains("[微信红包]")) {
                        PendingIntent pendingIntent = notification.contentIntent;
                        try {
                            pendingIntent.send();
                            notify_detect_tm = Calendar.getInstance().getTimeInMillis();
                            chatlist_detect_tm = 0;
                            detect_tm = 0;
                            mHander.postDelayed(runnable, 6000);
                            bAutoClickOpenDetail = false;
                            bAutoClickChatList = false;
                            bAutoClickHbItem = false;
                            bAutoClickOpenButton = false;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        if ("".equals(wx_user)) {
                            PendingIntent pendingIntent = notification.contentIntent;
                            try {
                                pendingIntent.send();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            wx_user = getWxUserName();
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
                                List<AccessibilityNodeInfo> contextNodes = hd.findAccessibilityNodeInfosByViewId(WINDOWCHATTEXT_STRING_ID);
                                for (AccessibilityNodeInfo contextNode : contextNodes) {
                                    Rect rect = new Rect();
                                    contextNode.getBoundsInScreen(rect);
                                    contextString = contextString + contextNode.getText().toString() + rect.toString();
                                }
                                if (!contextString.equals(last_context_string)) {
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
                                    last_context_string = contextString;
                                }
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
                            if ("我".equals(bottomBtn.getText())) {
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
                activtyManager.moveTaskToFront(runningTaskInfo.id, ActivityManager.MOVE_TASK_WITH_HOME);
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
        values.put("sender", sender);
        values.put("content", hbcontent);
        values.put("hb_amount", hb_amount);
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
                    URL url = new URL("http://39.108.106.173/Mtakem2Web/httpfun.jsp?action=InsertHbInfo&strHbInfo=" + URLEncoder.encode(obj.toString(), "utf-8"));
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

    private void rdnonhbInfo(String group_name, String wxUser, int len) throws JSONException {
        final JSONObject obj = new JSONObject();
        JSONArray array = new JSONArray();
        JSONObject item = new JSONObject();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
        item.put("group_name", group_name);
        item.put("wxUser", wxUser);
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
                    URL url = new URL("http://39.108.106.173/Mtakem2Web/httpfun.jsp?action=inserthistory&strHistory=" + URLEncoder.encode(obj.toString(), "utf-8"));
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
                            // Log.i(TAG, "上传成功:" + objResult.getString("msg"));
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

    /**这个要ROOT才行
     * 执行shell命令
     *z
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
