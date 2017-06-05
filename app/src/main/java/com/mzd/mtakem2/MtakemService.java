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

    private int nStatusCounter = 0;

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

    //HB发送人文本
    //6.5.7: , 6.5.8:bie
    private static final String HBSENDER_STRING_ID = "com.tencent.mm:id/bie";

    //HB内容文本
    //6.5.7: , 6.5.8:big
    private static final String HBCONTENT_STRING_ID = "com.tencent.mm:id/big";


    private String windowtitle = "";
    private String sender = "";
    private String hbcontent = "";
    private String hb_amount = "";
    private String last_context_string = "";

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
        handler.postDelayed(runnable, 2000);
        handler1.postDelayed(runnable1, 3000);//轮询本地数据库又不有保存HB信息，还有就上传

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
            nStatusCounter++;
            if(nStatusCounter>150){
                nStatusCounter = 0;
                try {
                    AccessibilityNodeInfo nd = getRootInActiveWindow();
                    if (nd != null && bAutoMode && nd.getPackageName().equals("com.tencent.mm")) {
                        back2Home();
                    }
                }
                catch (Exception e){
                    e.printStackTrace();
                }

            }
            handler.postDelayed(this, 10);
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
            //一旦有动静，在自动模式下，就执行窗口置后。

            switch (event.getEventType()) {
                case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED: {
                    //只有在监听阶段，的内容消息才认可处理。
                    if (event.getParcelableData() != null && event.getParcelableData() instanceof Notification) {
                        Notification notification = (Notification) event.getParcelableData();
                        String content = notification.tickerText != null ? notification.tickerText.toString() : "";
                        if (content.contains("[微信红包]")) {
                            nStatusCounter = 0; //倒计时清0
                            PendingIntent pendingIntent = notification.contentIntent;
                            try {
                                pendingIntent.send();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            notify_detect_tm = Calendar.getInstance().getTimeInMillis();
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
                    if (className.contains("LauncherUI") || className.equals("ui.chatting")) {
                        AccessibilityNodeInfo hd = getRootInActiveWindow();
                        if (hd != null) {
                            //聊天窗口的标题(6.5.7为gh,6.5.8改为gp)
                            List<AccessibilityNodeInfo> titleNodes = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                            if (titleNodes != null && !titleNodes.isEmpty()) {
                                windowtitle = titleNodes.get(0).getText().toString();
                            }
                            String contextString = windowtitle;
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
                                        Log.i(TAG, "i=" + String.valueOf(i));
                                        AccessibilityNodeInfo nodeInfo = hbNodes.get(i);
                                        if (nodeInfo != null && nodeInfo.getParent() != null && nodeInfo.getParent().getParent() != null && nodeInfo.getParent().getParent().getParent() != null && nodeInfo.getParent().getParent().getParent().getParent() != null) {
                                            nodeInfo.getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            detect_tm = Calendar.getInstance().getTimeInMillis();
                                            last_context_string = contextString;
                                            nStatusCounter = 0; //倒计时清0
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                    } else if (className.contains("luckymoney.ui.En_")) {
                        AccessibilityNodeInfo hd = getRootInActiveWindow();
                        List<AccessibilityNodeInfo> hbNodes = hd.findAccessibilityNodeInfosByViewId(HBOPENBUTTON_STRING_ID);
                        if (hbNodes != null && !hbNodes.isEmpty()) {
                            hbNodes.get(hbNodes.size() - 1).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            bUnpackedSuccessful = true;
                            Log.i(TAG, "Notify Time:" + String.valueOf(Calendar.getInstance().getTimeInMillis() - notify_detect_tm));
                            Log.i(TAG, "Detect Time:" + String.valueOf(Calendar.getInstance().getTimeInMillis() - detect_tm));
                            nStatusCounter = 0; //倒计时清0
                        }
                        else{
                           //这个会影响抢HB，原因待查
                            /* boolean hasNodes = hasOneOfThoseNodes(
                                    WECHAT_BETTER_LUCK_CH, WECHAT_BETTER_LUCK_EN, WECHAT_EXPIRES_CH,WECHAT_WHOGIVEYOUAHB);
                            if (hasNodes) {
                                performGlobalAction(GLOBAL_ACTION_BACK);//打开红包后返回到聊天页面
                                if(bAutoMode) back2Home();
                            }*/
                        }
                    } else if (className.contains("luckymoney.ui.LuckyMoneyDetailUI")) {
                        AccessibilityNodeInfo hd = getRootInActiveWindow();
                        if (hd != null) {
                            List<AccessibilityNodeInfo> hbNodes = null;
                            //发送人
                            hbNodes = hd.findAccessibilityNodeInfosByViewId(HBSENDER_STRING_ID);
                            if (hbNodes != null & !hbNodes.isEmpty()) {
                                sender = hbNodes.get(0).getText().toString();
                            }
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
                        }
                        Log.i(TAG, "group=" + windowtitle);
                        Log.i(TAG, "sender=" + sender);
                        Log.i(TAG, "content=" + hbcontent);
                        Log.i(TAG, "amount=" + hb_amount);

                        if (bUnpackedSuccessful) {
                            uploadHbInfo();
                            bUnpackedSuccessful = false;
                        }
                        performGlobalAction(GLOBAL_ACTION_BACK);
                        if (bAutoMode) back2Home();
                    }

                }
                break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, e.getMessage());
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
