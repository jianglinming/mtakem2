package com.mzd.mtakem2;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
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
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.mzd.mtakem2.utils.ComFunc;
import com.mzd.mtakem2.utils.HttpUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
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

    private String currentActivityName = WECHAT_LUCKMONEY_GENERAL_ACTIVITY;
    private boolean bContentUpdated = false;
    private boolean bUnpackedSuccessful = false;
    private Notification currentNotification = null;
    private ArrayList<Notification> currentNotifications = new ArrayList<Notification>();
    private HbInfo lastHb = new HbInfo();


    private boolean bAutoMode = false;//后台全自动抢红包模式，抢完红包自动回桌面
    private boolean bIgnoreNotify = false;//忽略通知处理，通知的红包信息忽略，专注单窗钱红包
    private boolean bAutoReply = false; //收到红包后自动回复


    private Handler handler = new Handler();
    //定期检查本地未上传服务器的HB信息
    private Handler handler1 = new Handler();
    private SharedPreferences sharedPreferences;

    private static final int NSTATUS_CHECKNOTIFYSANDCONTENT = 1;
    private static final int NSTATUS_NOTIFYOPENCHATWINDOW = 2;
    private static final int NSTATUS_NOTIFYUNPACKHB = 3;
    private static final int NSTATUS_NOTIFYCHECKRETURN = 4;
    private static final int NSTATUS_RETURNCHECKDELAY = 5;
    private static final int NSTATUS_AUTOREPLY = 6;
    private static final int NSTATUS_AUTOREPLYDELAY = 7;
    private static final int NSTATUS_OPENNOHBDELAY = 8;


    private int nStatus = NSTATUS_CHECKNOTIFYSANDCONTENT;
    private int nStatusCounter = 0;

    private boolean bWxforeground = false;
    private boolean bChatWindow = false;

    private long detect_tm = 0;
    private long notify_detect_tm = 0;
    private long chatlist_detect_tm = 0;

    private String mac = "";
    private String app_ver = "";
    String device_model = Build.MODEL; // 设备型号 。
    String version_release = Build.VERSION.RELEASE; // 设备的系统版本 。

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

    //HB金额文本按钮
    //6.5.7:bfw , 6.5.8:bii
    private static final String HBAMOUNTTEXT_STRING_ID = "com.tencent.mm:id/bii";

    private String windowtitle = "";
    private String sender = "";
    private String hbcontent = "";
    private String hb_amount = "";


    @Override
    public void onDestroy() {
        super.onDestroy();
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
        mac = ComFunc.getMac();
        app_ver = ComFunc.getVersion(this);
        Log.i(TAG, "APP Ver:" + app_ver);
        Log.i(TAG, "Mac :" + mac);
        Log.i(TAG, "Dev:" + device_model);
        Log.i(TAG, "VERSION:" + version_release);
        //handler.postDelayed(runnable, 2000);
        //handler1.postDelayed(runnable1, 3000);//轮询本地数据库又不有保存HB信息，还有就上传

        //动态增加FLAG配置，注意这非常重要，这个将使得能获取窗体的全部完整的节点。
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags = info.flags | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY;
        setServiceInfo(info);

        //注册监听配置更新并初始化配置
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        bAutoMode = sharedPreferences.getBoolean("autoMode", false);
        bIgnoreNotify = sharedPreferences.getBoolean("check_box_ignorenotify", false);
        bAutoReply = sharedPreferences.getBoolean("check_box_autoReply", false);

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("autoMode")) {
            bAutoMode = sharedPreferences.getBoolean(key, false);
        }
        if (key.equals("check_box_ignorenotify")) {
            bIgnoreNotify = sharedPreferences.getBoolean(key, false);
        }
        if (key.equals("check_box_autoReply")) {
            bAutoReply = sharedPreferences.getBoolean(key, false);
        }
    }

    //创建后台状态机监控内容变化
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            // TODO Auto-generated method stub
            //每1ms轮询一次。
            //点击红包

            AccessibilityNodeInfo nd = getRootInActiveWindow();
            try {
                //判断微信在前台还是在后台
                if (nd != null) {
                    if (nd.getPackageName().equals("com.tencent.mm")) bWxforeground = true;
                    else bWxforeground = false;
                    //判断微信处在聊天窗口还是聊天列表窗口。/a3_这个资源列表，聊天窗那个喇叭(wx6.5.7)
                    //6.5.8变为a47
                    List<AccessibilityNodeInfo> tmpnds = nd.findAccessibilityNodeInfosByViewId(SOUNDBUTTON_STRING_ID);
                    if (tmpnds.isEmpty()) bChatWindow = false;
                    else bChatWindow = true;
                }

                switch (nStatus) {
                    //通知栏消息检查
                    case NSTATUS_CHECKNOTIFYSANDCONTENT: {
                        synchronized (this) {
                            if (!currentNotifications.isEmpty()) {
                                currentNotification = currentNotifications.get(0);
                                currentNotifications.remove(0);
                            }
                        }
                        if (currentNotification != null) {
                            PendingIntent pendingIntent = currentNotification.contentIntent;
                            try {
                                pendingIntent.send();
                                String content = currentNotification.tickerText != null ? currentNotification.tickerText.toString() : "";
                                Log.i(TAG, "通知栏消息:" + content);
                                if (content.contains("[微信红包]")) {
                                    nStatusCounter = 0;
                                    nStatus = NSTATUS_NOTIFYOPENCHATWINDOW;
                                    detect_tm = 0;
                                    notify_detect_tm = Calendar.getInstance().getTimeInMillis();
                                    chatlist_detect_tm = 0;
                                    Log.i(TAG, "查到HB消息,进入聊天窗，检查HB");
                                } else {
                                    nStatusCounter = 0;
                                    nStatus = NSTATUS_OPENNOHBDELAY;
                                    Log.i(TAG, "非HB消息，打开即可");
                                }

                            } catch (PendingIntent.CanceledException e) {
                                e.printStackTrace();
                            } finally {
                                currentNotification = null;
                            }


                        } else {
                            if (bWxforeground && bContentUpdated) {
                                bContentUpdated = false;
                                if (bChatWindow) {
                                    if (chatWindowCheck(nd)) {
                                        nStatusCounter = 0;
                                        bUnpackedSuccessful = false;
                                        nStatus = NSTATUS_NOTIFYUNPACKHB;
                                        detect_tm = Calendar.getInstance().getTimeInMillis();
                                        notify_detect_tm = 0;
                                        chatlist_detect_tm = 0;
                                        Log.i(TAG, "监控发现HB项,进入打开阶段");
                                    }
                                } else {
                                    if (chatListCheck(nd)) {
                                        nStatusCounter = 0;
                                        nStatus = NSTATUS_NOTIFYOPENCHATWINDOW;
                                        detect_tm = 0;
                                        notify_detect_tm = 0;
                                        chatlist_detect_tm = Calendar.getInstance().getTimeInMillis();
                                        Log.i(TAG, "监控聊天列表发现HB消息,进入聊天窗，检查HB");
                                    }

                                }
                            }
                        }

                        //正常1ms左右循环一次
                        //无人值守模式下，10没监控到任何消息的话，把应用放到后台去。
                        if (bAutoMode) {
                            nStatusCounter++;
                            if (nStatusCounter >= 100) {
                                nStatusCounter = 0;
                                if (bWxforeground) back2Home();
                            }
                        }
                    }
                    break;

                    case NSTATUS_NOTIFYOPENCHATWINDOW: {
                        if (chatWindowCheck(nd)) {
                            nStatusCounter = 0;
                            bUnpackedSuccessful = false;
                            nStatus = NSTATUS_NOTIFYUNPACKHB;
                            detect_tm = Calendar.getInstance().getTimeInMillis();
                            Log.i(TAG, "发现HB项,进入打开阶段");

                        } else {
                            try {
                                //如果没发现实际微信红包的话，有课能是假的红包，这是立刻忽视它，以节约时间。
                                //聊天记录的位置信息作为补充的页面信息（6.5.7为if,6.5.8变为im)
                                List<AccessibilityNodeInfo> falseHbChecks = nd.findAccessibilityNodeInfosByViewId(WINDOWCHATTEXT_STRING_ID);
                                if (falseHbChecks != null && !falseHbChecks.isEmpty()) {
                                    AccessibilityNodeInfo falseHbCheck = falseHbChecks.get(falseHbChecks.size() - 1);
                                    if (falseHbCheck != null && falseHbCheck.getText().toString().contains("[微信红包]")) {
                                        Log.i(TAG, "假HB，返回通知检查");
                                        if (bAutoMode)
                                            performGlobalAction(GLOBAL_ACTION_HOME);//打开红包后返回到聊天页面
                                        nStatusCounter = 0;
                                        nStatus = NSTATUS_CHECKNOTIFYSANDCONTENT;
                                    }
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            //20ms一次
                            nStatusCounter++;
                            if (nStatusCounter >= 100) {
                                nStatusCounter = 0;
                                nStatus = NSTATUS_CHECKNOTIFYSANDCONTENT;
                                Log.i(TAG, "聊天窗HB项,检查超时，返回通知检查");
                                if (bAutoMode) back2Home();//如果是自动模式，聊天窗自动到后台
                            }

                        }
                    }
                    break;

                    case NSTATUS_NOTIFYUNPACKHB: {
                        if (unpackHb(nd)) {
                            nStatusCounter = 0;
                            bUnpackedSuccessful = true;
                            nStatus = NSTATUS_NOTIFYCHECKRETURN;
                            Log.i(TAG, "执行打开Hb,进入打开阶段");
                            if (notify_detect_tm != 0)
                                Log.i(TAG, "从通知发现红包到打开耗时:" + String.valueOf(Calendar.getInstance().getTimeInMillis() - notify_detect_tm) + "ms");
                            if (chatlist_detect_tm != 0)
                                Log.i(TAG, "从窗口列表发现红包到打开耗时:" + String.valueOf(Calendar.getInstance().getTimeInMillis() - chatlist_detect_tm) + "ms");
                            Log.i(TAG, "从窗口发现红包到打开耗时:" + String.valueOf(Calendar.getInstance().getTimeInMillis() - detect_tm) + "ms");
                        } else {

                            if (dealOpenedHb(nd)) {
                                nStatusCounter = 0;
                                nStatus = NSTATUS_RETURNCHECKDELAY;
                                Log.i(TAG, "Hb已经打开,窗体返回后延时");
                            } else {
                                nStatusCounter++;
                                if (nStatusCounter >= 300) {
                                    nStatusCounter = 0;
                                    nStatus = NSTATUS_CHECKNOTIFYSANDCONTENT;
                                    Log.i(TAG, "执行打开Hb超时，返回通知检查");
                                }
                            }
                        }
                    }
                    break;

                    case NSTATUS_NOTIFYCHECKRETURN: {
                        if (dealOpenedHb(nd)) {
                            nStatusCounter = 0;
                            nStatus = NSTATUS_RETURNCHECKDELAY;
                            Log.i(TAG, "打开HB,窗体返回后延时");
                        } else {
                            nStatusCounter++;
                            if (nStatusCounter >= 300) {
                                nStatusCounter = 0;
                                nStatus = NSTATUS_CHECKNOTIFYSANDCONTENT;
                                Log.i(TAG, "打开HB返回处理超时，返回通知检查");
                            }
                        }
                    }
                    break;

                    case NSTATUS_RETURNCHECKDELAY: {
                        nStatusCounter++;
                        if (nStatusCounter >= 10) {

                            if (bUnpackedSuccessful) {
                                try {
                                    uploadHbInfo();
                                    Log.i(TAG, "服务端保存数据");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            //如果成功打开红包
                            if (bAutoReply && bUnpackedSuccessful) {
                                nStatusCounter = 0;
                                bUnpackedSuccessful = false;
                                nStatus = NSTATUS_AUTOREPLY;
                                Log.i(TAG, "延时完成,准备自动回复");
                            } else {
                                nStatusCounter = 0;
                                nStatus = NSTATUS_CHECKNOTIFYSANDCONTENT;
                                if (bAutoMode)
                                    back2Home();//performGlobalAction(GLOBAL_ACTION_HOME);
                                Log.i(TAG, "延时完成,回到监控状态");
                            }

                        }
                    }
                    break;

                    case NSTATUS_AUTOREPLY: {
                        if (findEditText(nd, generateCommentString())) {
                            send();
                            nStatusCounter = 0;
                            nStatus = NSTATUS_AUTOREPLYDELAY;
                            Log.i(TAG, "自动回复完成,回复后进入延时");
                        } else {
                            nStatusCounter++;
                            if (nStatusCounter >= 100) {
                                nStatusCounter = 0;
                                nStatus = NSTATUS_CHECKNOTIFYSANDCONTENT;
                                if (bAutoMode)
                                    back2Home();
                                Log.i(TAG, "自动回复超时,回到监控状态");
                            }
                        }
                    }
                    break;

                    case NSTATUS_AUTOREPLYDELAY: {
                        nStatusCounter++;
                        if (nStatusCounter >= 20) {
                            nStatusCounter = 0;
                            nStatus = NSTATUS_CHECKNOTIFYSANDCONTENT;
                            if (bAutoMode)
                                back2Home();
                            Log.i(TAG, "回复后进入延时完成,回到监控状态");
                        }
                    }
                    break;

                    case NSTATUS_OPENNOHBDELAY: {

                        if (currentActivityName.contains(WECHAT_LUCKMONEY_GENERAL_ACTIVITY)) {
                            back2Home();
                            nStatusCounter = 0;
                            nStatus = NSTATUS_CHECKNOTIFYSANDCONTENT;
                        } else {
                            nStatusCounter++;
                            if (nStatusCounter > 100) {
                                back2Home();
                                nStatusCounter = 0;
                                nStatus = NSTATUS_CHECKNOTIFYSANDCONTENT;
                            }
                        }

                    }
                    break;

                    //其他，也相当于IDLE
                    default: {

                    }
                    break;

                }
            } catch (Exception e) {
                nStatusCounter = 0;
                nStatus = NSTATUS_CHECKNOTIFYSANDCONTENT;
                e.printStackTrace();
            }
            handler.postDelayed(this, 1);
        }
    };


    /*
         检查数据库定时任务，是否有HB数据,如果有就一次性全部上传
      */
    private Runnable runnable1 = new Runnable() {
        @Override
        public void run() {
            try {
                final HbHistory hb = new HbHistory(getApplicationContext());
                final JSONObject obj = new JSONObject();
                JSONArray array = new JSONArray();
                final Vector v = new Vector();
                int total = 0;
                try {
                    Cursor c = hb.query();
                    int i = 0;
                    for (i = 0; i < 10; i++) {
                        JSONObject item = new JSONObject();
                        if (c.moveToNext()) {
                            item.put("device", device_model + "(" + version_release + ")");
                            item.put("machine_id", mac);
                            item.put("mtakem2ver", app_ver);
                            int j = 0;
                            for (j = 0; j < c.getColumnCount(); j++) {
                                item.put(c.getColumnName(j), c.getString(j));
                            }
                            v.add(c.getInt(c.getColumnIndex("id")));
                            array.put(item);
                            total++;
                        } else {
                            break;
                        }
                    }
                    obj.put("total", total);
                    obj.put("rows", array);
                    Log.i(TAG, obj.toString());
                    c.close();
                } catch (Exception e) {
                    total = 0;
                    e.printStackTrace();
                }

                //如果数据库有数据就上传
                if (total != 0) {
                    try {
                        HttpUtils.doPostAsyn("http://39.108.106.173/Mtakem2Web/httpfun.jsp?action=InsertHbInfo", "strHbInfo=" + URLEncoder.encode(obj.toString(), "gbk"), new HttpUtils.CallBack() {
                            @Override
                            public void onRequestComplete(String result) {
                                boolean bUploadSuccessful = false;
                                try {
                                    Log.i(TAG, URLDecoder.decode(result, "gbk"));
                                    JSONObject objResult = new JSONObject(URLDecoder.decode(result, "gbk"));
                                    if (objResult.getBoolean("result")) {
                                        bUploadSuccessful = true;
                                        Log.i(TAG, "上传成功:" + objResult.getString("msg"));
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                //如果上传成功就，删除对应的数据库项
                                if (bUploadSuccessful) {
                                    Log.i(TAG, "上传成功");
                                    int i = 0;
                                    for (i = 0; i < v.size(); i++) {
                                        hb.delete((int) v.get(i));
                                    }
                                } else {
                                    Log.i(TAG, "上传失败");
                                }
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                hb.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            handler1.postDelayed(this, 60000);
        }
    };


    private boolean chatListCheck(AccessibilityNodeInfo nd) {
        try {
            //6.5.7是afx,6.5.8变为agy
            List<AccessibilityNodeInfo> nodeInfos1 = nd.findAccessibilityNodeInfosByViewId(CHATLISTTEXT_STRING_ID);
            AccessibilityNodeInfo findNode = null;
            for (int i = 0; i < nodeInfos1.size(); i++) {
                if (nodeInfos1.get(i).getText().toString().contains("[微信红包]")) {
                    findNode = nodeInfos1.get(i);
                    break;
                }
            }
            if (findNode != null) {
                if (findNode != null && findNode.getParent() != null && findNode.getParent().getParent() != null && findNode.getParent().getParent().getParent() != null && findNode.getParent().getParent().getParent().getParent() != null) {
                    AccessibilityNodeInfo clickableParentNode = findNode.getParent().getParent().getParent().getParent();
                    //如果有新消息提醒的话，就点击这个可以用android studio 中的adm的"Dump View hierarchy for UI Automator"层次关系
                    if (clickableParentNode.getChild(0).getChildCount() > 1) {
                        nStatusCounter = 0;
                        clickableParentNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return true;
                    }

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    private boolean notifyCheck(AccessibilityNodeInfo nd) {
        try {
            synchronized (this) {
                if (!currentNotifications.isEmpty()) {
                    currentNotification = currentNotifications.get(0);
                    currentNotifications.remove(0);
                }
            }
            if (currentNotification != null) {
                PendingIntent pendingIntent = currentNotification.contentIntent;
                try {
                    pendingIntent.send();
                    currentNotification = null;
                    return true;
                } catch (PendingIntent.CanceledException e) {
                    e.printStackTrace();
                }
            }
            currentNotification = null;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }


    private AccessibilityNodeInfo GetHbInfo(AccessibilityNodeInfo rn, HbInfo hbInfo) {
        AccessibilityNodeInfo rdReturn = null;
        try {
            String contextString = "";
            //聊天窗口的标题(6.5.7为gh,6.5.8改为gp)
            List<AccessibilityNodeInfo> titleNodes = rn.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
            if (titleNodes != null && !titleNodes.isEmpty()) {
                hbInfo.SetChatWindowTitle(titleNodes.get(0).getText().toString());
            }

            List<AccessibilityNodeInfo> hbNodes = rn.findAccessibilityNodeInfosByText("领取红包");
            //List<AccessibilityNodeInfo> hbNodes = rn.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/a6a");
            hbInfo.bIsAHb = false;
            if (hbNodes != null && !hbNodes.isEmpty()) {
                AccessibilityNodeInfo hbNode = hbNodes.get(hbNodes.size() - 1);
                try {
                    hbInfo.SetDescription(hbNode.getParent().getChild(0).getText().toString());
                    AccessibilityNodeInfo imgNodeRoot = hbNode.getParent().getParent().getParent().getParent().getParent().getParent();
                    hbInfo.SetSender(imgNodeRoot.getChild(0).getChild(2).getContentDescription().toString().replaceAll("头像$", ""));
                    hbNode.getBoundsInScreen(hbInfo.hbRect);
                    hbInfo.bIsAHb = true;
                    rdReturn = hbNode;
                } catch (Exception e) {
                    hbInfo.bIsAHb = false;
                    rdReturn = null;
                }
            }

            if (hbInfo.bIsAHb) {
                //获取时间
                List<AccessibilityNodeInfo> tmNodes = rn.findAccessibilityNodeInfosByViewId(WINDOWCHATTTIME_STRING_ID);
                if (tmNodes != null && !tmNodes.isEmpty()) {
                    //最后的时间标识红包
                    hbInfo.SetHappendedTime(tmNodes.get(tmNodes.size() - 1).getText().toString());
                } else {
                    hbInfo.SetHappendedTime("unknowntime");
                }

                //聊天记录的位置信息作为补充的页面信息（6.5.7为if,6.5.8变为im)
                List<AccessibilityNodeInfo> ifNodes = rn.findAccessibilityNodeInfosByViewId(WINDOWCHATTEXT_STRING_ID);
                for (AccessibilityNodeInfo ifNode : ifNodes) {
                    Rect ifRect = new Rect();
                    ifNode.getBoundsInScreen(ifRect);
                    if ((ifRect.bottom - ifRect.top) > 80) {
                        contextString = contextString + ifNode.getText() + ifRect.toString();
                    }
                }
                hbInfo.SetContextString(contextString);

                //红包是否被领取
                List<AccessibilityNodeInfo> isgethbNodes = rn.findAccessibilityNodeInfosByText("你领取了" + hbInfo.GetSender() + "的红包");
                if (isgethbNodes != null && !isgethbNodes.isEmpty()) {
                    AccessibilityNodeInfo isgethbNode = isgethbNodes.get(isgethbNodes.size() - 1);
                    Rect rect = new Rect();
                    isgethbNode.getBoundsInScreen(rect);
                    //在红包下发出现，且高度小于100，实际高度是80，表明红吧被领取了。
                    if (hbInfo.hbRect.bottom < rect.top && (rect.bottom - rect.top) < 100) {
                        hbInfo.bIsGetBySelf = true;
                    } else {
                        hbInfo.bIsGetBySelf = false;
                    }
                }
            }

        } catch (Exception e) {
            rdReturn = null;
        }
        return rdReturn;
    }

    private boolean chatWindowCheck(AccessibilityNodeInfo nd) {
        //检查领取红包项
       /*
        if (nd != null) {
            List<AccessibilityNodeInfo> nodeInfos = nd.findAccessibilityNodeInfosByText("领取红包");
            if (nodeInfos != null && !nodeInfos.isEmpty()) {
                AccessibilityNodeInfo nodeInfo = nodeInfos.get(nodeInfos.size() - 1);
                if (nodeInfo != null && nodeInfo.getParent() != null && nodeInfo.getParent().getParent() != null && nodeInfo.getParent().getParent().getParent() != null && nodeInfo.getParent().getParent().getParent().getParent() != null) {
                    nodeInfo.getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return true;
                }
            }
        }
        return false;*/
        try {
            if (nd != null) {
                HbInfo hbInfo = new HbInfo();
                AccessibilityNodeInfo nodeInfo = GetHbInfo(nd, hbInfo);
                //if (hbInfo != null) Log.i(TAG, "nowHb:" + hbInfo.toString());
                //if (lastHb != null) Log.i(TAG, "lastHb:" + lastHb.toString());
                if (nodeInfo != null && !hbInfo.equals(lastHb)) {
                    lastHb = hbInfo;
                    if (!hbInfo.bIsGetBySelf) {
                        if (nodeInfo != null && nodeInfo.getParent() != null && nodeInfo.getParent().getParent() != null && nodeInfo.getParent().getParent().getParent() != null && nodeInfo.getParent().getParent().getParent().getParent() != null) {
                            nodeInfo.getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            detect_tm = Calendar.getInstance().getTimeInMillis();
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;

    }

    private boolean unpackHb(AccessibilityNodeInfo nd) {
        //属于红包窗体，后打开红包窗口就点击打开。

        /*
        if (currentActivityName.contains("luckymoney.ui.En")) {
            AccessibilityNodeInfo node2 = findOpenButton(getRootInActiveWindow());
            if (node2 != null) {
                //拆包
                node2.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
        }
        return false;*/
        try {
            if (nd != null) {
                if (currentActivityName.contains("luckymoney.ui.En")) {
                    List<AccessibilityNodeInfo> openNodes = nd.findAccessibilityNodeInfosByViewId(HBOPENBUTTON_STRING_ID);
                    if (openNodes != null && !openNodes.isEmpty()) {
                        AccessibilityNodeInfo openNode = openNodes.get(0);
                        openNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    private boolean dealOpenedHb(AccessibilityNodeInfo nd) {
        //如果直接就是打开的红包，则直接返回
        //由于抢过的红包和待抢的红包一个窗体名称，所以从有不有打开按钮区分一下红包是不是打开的。
        try {
            boolean bHbOpenedSuccessful = false;
            if (currentActivityName.contains("luckymoney.ui.En")) {
                List<AccessibilityNodeInfo> openNodes = nd.findAccessibilityNodeInfosByViewId(HBOPENBUTTON_STRING_ID);
                if (openNodes != null && !openNodes.isEmpty()) {
                    bHbOpenedSuccessful = false;
                } else {
                    bHbOpenedSuccessful = true;
                }
            }

            if (bHbOpenedSuccessful || currentActivityName.contains("luckymoney.ui.LuckyMoneyDetailUI")) {
                //如果检查到红包已经领用则返回处理
                boolean hasNodes = hasOneOfThoseNodes(
                        WECHAT_BETTER_LUCK_CH, WECHAT_BETTER_LUCK_EN, WECHAT_EXPIRES_CH);
                if (hasNodes) {
                    performGlobalAction(GLOBAL_ACTION_BACK);//打开红包后返回到聊天页面
                    currentNotification = null;
                    return true;
                } else {
                    List<AccessibilityNodeInfo> hbAmounts = nd.findAccessibilityNodeInfosByViewId(HBAMOUNTTEXT_STRING_ID);
                    if (hbAmounts != null && !hbAmounts.isEmpty()) {
                        AccessibilityNodeInfo hbAmount = hbAmounts.get(0);
                        lastHb.SetHbAmount(hbAmount.getText().toString());
                        Log.i(TAG, "红包大小：" + lastHb.GetHbAmount());
                        performGlobalAction(GLOBAL_ACTION_BACK);//打开红包后返回到聊天页面
                        currentNotification = null;
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }


    /*选择禁用服务触发事件*/
    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind");
        handler.removeCallbacksAndMessages(null);
        return super.onUnbind(intent);
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "onInterrupt");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            switch (event.getEventType()) {
                case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED: {
                    //只有在监听阶段，的内容消息才认可处理。
                    if (event.getParcelableData() != null && event.getParcelableData() instanceof Notification) {
                        Notification notification = (Notification) event.getParcelableData();
                        String content = notification.tickerText != null ? notification.tickerText.toString() : "";
                        if (content.contains("[微信红包]")) {
                            PendingIntent pendingIntent = notification.contentIntent;
                            try {
                                pendingIntent.send();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            notify_detect_tm = Calendar.getInstance().getTimeInMillis();
                        } else {
                            //back2Home();
                        }
                    }
                }
                break;

                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED: {
                    Log.i(TAG,"TYPE_WINDOW_CONTENT_CHANGED");
                    String className = event.getClassName().toString();
                    Log.i(TAG,className);
                    if (className.equals("com.tencent.mm.ui.LauncherUI") || className.equals("com.tencent.mm.ui.chatting.En_5b8fbb1e")) {
                        Log.i(TAG,"LauncherUI enter");
                        AccessibilityNodeInfo hd = getRootInActiveWindow();
                        List<AccessibilityNodeInfo> hbNodes = hd.findAccessibilityNodeInfosByViewId(HB_STRING_ID);
                        if (hbNodes != null && !hbNodes.isEmpty()) {
                            hbNodes.get(hbNodes.size() - 1).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.i(TAG,"hbClick");
                            detect_tm = Calendar.getInstance().getTimeInMillis();
                        }
                    } else if (className.equals("com.tencent.mm.plugin.luckymoney.ui.En_fba4b94f")) {
                        AccessibilityNodeInfo hd = getRootInActiveWindow();
                        List<AccessibilityNodeInfo> hbNodes = hd.findAccessibilityNodeInfosByViewId(HBOPENBUTTON_STRING_ID);
                        if (hbNodes != null && !hbNodes.isEmpty()) {
                            hbNodes.get(hbNodes.size() - 1).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.i(TAG,"Notify Time:"+String.valueOf(Calendar.getInstance().getTimeInMillis()-notify_detect_tm));
                            Log.i(TAG,"Detect Time:"+String.valueOf(Calendar.getInstance().getTimeInMillis()-detect_tm));
                        }
                    } else if (className.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI")) {
                        AccessibilityNodeInfo hd = getRootInActiveWindow();
                        recycle(hd);

                    }

                }
                break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG,e.getMessage());
        }
    }

    /*
    获得当前窗体名称
    * com.tencent.mm/.plugin.luckymoney.ui.En_fba4b94f:没打开红包窗体（可以打开）
    * com.tencent.mm/.plugin.luckymoney.ui.LuckyMoneyDetailUI：自己领取的红包窗体
    * com.tencent.mm/.plugin.luckymoney.ui.En_fba4b94f：别人领取的红包窗体
    * com.tencent.mm/.ui.LauncherUI：列表窗体和聊天窗口窗体
    * */
    private void setCurrentActivityName(AccessibilityEvent event) {
        try {
            ComponentName componentName = new ComponentName(
                    event.getPackageName().toString(),
                    event.getClassName().toString()
            );
            getPackageManager().getActivityInfo(componentName, 0);
            currentActivityName = componentName.flattenToShortString();
        } catch (PackageManager.NameNotFoundException e) {
            currentActivityName = WECHAT_LUCKMONEY_GENERAL_ACTIVITY;
        }
        Log.i(TAG, "currentActivityName:" + currentActivityName);
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

    /* 查找聊天窗口中，有“领取红包”和“查看红包”文字的节点集合，并返回最后一个节点 */
    private AccessibilityNodeInfo getTheLastNode(String... texts) {
        int bottom = 0;
        AccessibilityNodeInfo lastNode = null, tempNode;
        List<AccessibilityNodeInfo> nodes;

        for (String text : texts) {
            if (text == null) continue;

            nodes = getRootInActiveWindow().findAccessibilityNodeInfosByText(text);

            if (nodes != null && !nodes.isEmpty()) {
                tempNode = nodes.get(nodes.size() - 1);
                if (tempNode == null) return null;
                Rect bounds = new Rect();
                tempNode.getBoundsInScreen(bounds);
                if (bounds.bottom > bottom) {
                    bottom = bounds.bottom;
                    lastNode = tempNode;
                }
            }
        }
        return lastNode;
    }

    /*
        再领红包或别人领过的红包的窗口上找一个按钮。
     */
    private AccessibilityNodeInfo findOpenButton(AccessibilityNodeInfo node) {
        if (node == null) return null;
        //非layout元素
        if (node.getChildCount() == 0) {
            if ("android.widget.Button".equals(node.getClassName()))
                return node;
            else
                return null;
        }
        //layout元素，遍历找button
        AccessibilityNodeInfo button;
        for (int i = 0; i < node.getChildCount(); i++) {
            button = findOpenButton(node.getChild(i));
            if (button != null)
                return button;
        }
        return null;
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
    /*private ArrayList<AccessibilityNodeInfo> getNodesFromWindows() {
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
        String group_name = lastHb.GetChatWindowTitle();
        if (group_name.lastIndexOf("(") != -1) {
            group_name = group_name.substring(0, group_name.lastIndexOf("("));
        }
        values.put("group_name", group_name);
        values.put("sender", lastHb.GetSender());
        values.put("content", lastHb.GetDescription());
        values.put("hb_amount", lastHb.GetHbAmount());
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
                    HbHistory hb = new HbHistory(getApplicationContext());
                    hb.insert(values);
                    hb.close();
                    Log.i(TAG, "上传失败,保存到本地数据库");
                }

            }
        }).start();

        /*
        //不管服务端的设置输出是gkb，还是UTF-8，get这种方法都必须经过下面的编码转换
        try {
            byte tmp[] = obj.toString().getBytes("utf-8");
            String sendStr = new String(tmp, "gbk");
            Log.i(TAG,sendStr);
            HttpUtils.doGetAsyn("http://39.108.106.173/Mtakem2Web/httpfun.jsp?action=InsertHbInfo&strHbInfo=" + URLEncoder.encode(sendStr.toString(), "gbk"), new HttpUtils.CallBack() {
                @Override
                public void onRequestComplete(String result) {
                    try {
                        //服务端返回需要对字符进行encode处理，才不会乱码。
                        Log.i("main", URLDecoder.decode(result, "gbk"));
                    } catch (Exception e) {
                        Log.i(TAG, "上传异常1");
                    }


                }
            });
        }catch (Exception e){
            e.printStackTrace();
            Log.i(TAG, "上传异常2");
        }*/

        /*
        //post方法，就不用对字符串进行变换
        String postcontent = "";
        try {
            Log.i(TAG, obj.toString());
            postcontent = URLEncoder.encode(obj.toString(), "gbk");
        } catch (Exception e) {
            e.printStackTrace();
            postcontent = "";
        }

        if (!postcontent.equals("")) {
            try {
                Log.i(TAG,"执行通信");
                HttpUtils.doPostAsyn("http://39.108.106.173/Mtakem2Web/httpfun.jsp?action=InsertHbInfo", "strHbInfo=" + postcontent, new HttpUtils.CallBack() {
                    @Override
                    public void onRequestComplete(String result) {
                        try {
                            String resp = URLDecoder.decode(result, "gbk");
                            Log.i("main", resp);
                            JSONObject obj = new JSONObject(resp);
                            if (obj.getBoolean("result")) {

                            } else {
                                //报告插入失败，将红包存在本地
                                Log.i(TAG, "插入失败:" + obj.getString("msg"));
                                HbHistory hb = new HbHistory(getApplicationContext());
                                hb.insert(values);
                            }
                        } catch (Exception e) {
                            //回复错误信息也插入数据库
                            Log.i(TAG, "插入异常1:" + e.getMessage());
                            HbHistory hb = new HbHistory(getApplicationContext());
                            hb.insert(values);
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Log.i(TAG, "插入异常2:" + e.getMessage());
                //通信失败也存在本地
                HbHistory hb = new HbHistory(getApplicationContext());
                hb.insert(values);
            }
        }*/

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


}
