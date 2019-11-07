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
import com.mzd.mtakem2.utils.ObtainParamThread;

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
import java.io.UnsupportedEncodingException;
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
import java.util.concurrent.ExecutionException;

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
    private static final int MSG_TYPE_GROUP = 0;
    private static final int MSG_TYPE_PERSON = 1;
    private static final int MSG_TYPE_OTHER = 2;
    //聊天窗中的讲话按钮，用来区分当前页面是聊天窗口，还是聊天列表窗口
    // 6.5.7：a3_  ,6.5.8:a47,6.5.10:a5c,6.5.13:a6e,6.6.1:aad,6.7.2:aen,7.0:alk;7.0.7:aqa
    private static final String SOUNDBUTTON_STRING_ID = "com.tencent.mm:id/aqa";
    //聊天窗口的标题信息，标识了所在的群或者聊天对象
    //6.5.7:gh , 6.5.8:gp,6.5.10:gs,6.5.13:gz,6.6.1:ha,6.7.2:j1,7.0:jw;7.0.7:ls
    private static final String WINDOWTITLETEXT_STRING_ID = "com.tencent.mm:id/ls";
    //android 7.0取消了领取红包四个字,这里准备使用,红包标志这个特征
    //7.0,apc;//7.0.7:at_
    private static final String HB_LOGO_STRING_ID = "com.tencent.mm:id/at_";
    //显示红包领取情况的（比如：已领取，已被领完）
    private static final String HB_LQSTATUS_STRING_ID = "com.tencent.mm:id/ape";
    //聊天的文本控件ID
    // 6.5.7:if  , 6.5.8:im,6.5.10:ij,6.5.13:iq(NAF=TRUE,not accessibility friendly)
    private static final String WINDOWCHATTEXT_STRING_ID = "com.tencent.mm:id/iq";
    //聊天信息中的时间标签ID
    //6.5.7:t , 6.5.8:t (没变化）,6.5.10:u,6.5.13:y
    private static final String WINDOWCHATTTIME_STRING_ID = "com.tencent.mm:id/u";
    //聊天列表中的最后文本信息
    //6.5.7:afx , 6.5.8:agy,6.5.10:aii,6.5.13:aje,6.6.1:apv,6.7.2:aun,7.0:b4q,7.0.7:bai
    private static final String CHATLISTTEXT_STRING_ID = "com.tencent.mm:id/bai";
    //com.tencent.mm:id/ie
    //6.5.13:聊天列表中的未读红点文本控件ID,6.6.1:com.tencent.mm:id/iu,6.7.2:lg,7.0:mm,7.0.7:com.tencent.mm:id/oo
    private static final String CHATLISTUNREADMSG_STRING_ID = "com.tencent.mm:id/oo";
    //聊天列表中的群标题信息
    //6.5.10 aig,6.5.13:ajc,6.6.1:apt,6.7.2:aul,7.0:b4o,7.0.7:bag
    private static final String CHATLISTTITLE_STRING_ID = "com.tencent.mm:id/bag";
    //聊天窗中的HB信息
    //6.5.7:   , 6.5.8:a6_ ,6.5.10:a8h,6.5.13:a8q
    private static final String HB_STRING_ID = "com.tencent.mm:id/a8q";
    //HB打开按钮
    //6.5.7:bjj , 6.5.8:bm4,6.5.10:bnr,6.5.13:bp6,6.6.1:c2i,6.7.2:cb1,7.0:cv0,7.0.7:da7
    private static final String HBOPENBUTTON_STRING_ID = "com.tencent.mm:id/da7";
    //HB派完了文本
    //6.5.7: , 6.5.8:bm3,6.5.10:bnq,6.5.13:bp5,6.6.1:c2h,6.7.2:cb0,7.0:cuz,7.0.7:da6
    private static final String HBNONETEXT_STRING_ID = "com.tencent.mm:id/da6";
    //HB金额文本按钮
    //6.5.7:bfw , 6.5.8:bii,6.5.10:bk6,6.5.13:bli,6.6.1:byw,6.7.2:c8g,7.0:cqv,7.0.7:d5l
    private static final String HBAMOUNTTEXT_STRING_ID = "com.tencent.mm:id/d5l";
    //HB发送人文本
    //6.5.7: , 6.5.8:bie,6.5.10:bk2,6.5.13:ble,6.6.1:bys,6.7.2:c8c,7.0:cqr,7.0.7:com.tencent.mm:id/d5h
    private static final String HBSENDER_STRING_ID = "com.tencent.mm:id/d5h";
    //HB内容文本
    //6.5.7: , 6.5.8:big,6.5.10:bk4,6.5.13:blg,6.6.1:byu,6.7.2:c8e,7.0:cqt,7.0.7:d5j
    private static final String HBCONTENT_STRING_ID = "com.tencent.mm:id/d5j";
    //聊天窗返回列表窗返回箭头
    //6.5.8:gn,6.5.10:gq,6.5.13:gx,6.6.1:h9,6.7.2:iz,7.0:ju,7.0.7:lq
    private static final String HBRETURN_STRING_ID = "com.tencent.mm:id/lq";
    //wx聊天列表下面的按钮ID
    //6.5.8:buh,6.5.10:bw6,6.5.13:bwm,6.6.1:c8t,6.7.2:chp,7.0:com.tencent.mm:id/d3t,7.0.7:djv
    private static final String HBBOTTOMBTN_STRING_ID = "com.tencent.mm:id/djv";
    //wx聊天窗口中的“更多未读消息按钮”，聊天窗口向上拉，直到未读内容全部读取就会自动消失
    //6.5.13：a5t,6.6.1:a_r,6.7.2:ae1,7.0:aky,7.0.7:apm
    private static final String HBMOREMSG_STRING_ID = "com.tencent.mm:id/apm";
    //wx聊天窗口中的listview控件ID
    //6.5.13:a5j,6.6.1:a_h,7.0.7:af
    private static final String CHATCONTENTWINDOWLISTVIEW_STRING_ID = "com.tencent.mm:id/af";
    //wx聊天列表中的listview控件ID
    //6.5.13:bqc,6.6.1:c3p,6.7.2:ccf,7.0.7:dbz
    private static final String CHATLISTWINDOWLISTVIEW_STRING_ID = "com.tencent.mm:id/dbz";
    //wx名称的textid
    //6.5.8:by2,6.5.10:bzr,6.5.13:c07,6.6.1:cba,6.7.2:a0b,7.0:a5b,7.0.7:a9z
    private static final String HBWXUSER_STRING_ID = "com.tencent.mm:id/a9z";
    //wx的号码
    //6.6.1:cbb,6.7.2:cl8,7.0:d7y,7.0.7:dq5
    private static final String HBWXUSERID_STRING_ID = "com.tencent.mm:id/dq5";
    //6.5.8:g5,6.5.10:g8,6.5.13:ge,6.6.1:h6,6.7.2:iw(这个按钮直接可以点击，区别以前靠相对位置来点击),7.0:jr
    //7.0.7:com.tencent.mm:id/ln
    private static final String HBOPENGROUPDETAIL_STRING_ID = "com.tencent.mm:id/ln";
    //群信息的列表ID(listview,scrollable:true)
    //6.5.8:list,6.5.10:list,6.5.13:list,6.6.1:list,6.7.2:list,7.0.7:android:id/list
    private static final String HBGROUPLIST_STRING_ID = "android:id/list";
    //删除并退出按钮
    //6.5.8:title,6.5.10:title,6.5.13:title,6.6.1:title,6.7.2:android:id/title,7.0.7:d8
    private static final String HBDELANDQUIT_STRING_ID = "com.tencent.mm:id/d8";
    //删除并退出确认按钮
    //6.5.8:ad8,6.5.10:aes,6.5.13:afm,6.6.1:alo,6.7.2:apj,7.0:ayb,7.0.7:b47
    private static final String HBDELANDQUITCONFIRM_STRING_ID = "com.tencent.mm:id/b47";
    //接受加好接受加好友验证相关
    //邀请加入群聊的标题
    //6.5.8:text1,6.5.10:text1,6.5.13:text1,6.6.1:text1,6.7.2:text1,7.0.7:text1
    private static final String HBYQTITLE_STRING_ID = "android:id/text1";
    //接受
    //6.5.8:axg,6.5.10:aym,6.5.13:azm,6.6.1:b7w,6.7.2:beo,7.0.7:c1t
    private static final String HBACCEPTBIGBTN_STRING_ID = "com.tencent.mm:id/c1t";
    //通过验证按钮
    //6.5.8:aeb,6.5.10:afx,6.5.13:agt,6.6.1:an4,6.7.2:ard,7.0.7:dkm
    private static final String HBPASSCHECK_STRING_ID = "com.tencent.mm:id/dkm";
    //好友用户信息
    //6.5.8:summary,6.5.10:summary.6.5.13:summary,6.6.1:summary,6.7.2:summary
    private static final String HBFRIENDINFO_STRING_ID = "android:id/summary";
    //验证完成按钮
    //6.5.8:gl,6.5.10:go,6.5.13:gv,6.6.1:h5,6.7.2:iv,7.0.7:lm
    private static final String HBFINISHCHECK_STRING_ID = "com.tencent.mm:id/lm";
    //验证完后返回按钮
    //6.5.8:h3,6.5.10:h6,6.5.13:hc,6.6.1:ho,6.7.2:j7,7.0.7:lz
    private static final String HBRETURNAFTERCHECK_STRING_ID = "com.tencent.mm:id/lz";
    //自动邀请好友加群相关
    //点击邀请好友的加号
    //6.5.10:cgr,6.5.13:ch8,6.6.1:cx4,6.7.2:d92,7.0.7:eia
    private static final String HBYQFRIENDSINGROUPBTN = "com.tencent.mm:id/eia";
    //群信息的listview
    //6.5.10:f3,6.5.13:f_
    private static final String HBYQFRIENDSCROLLVIEWID = "com.tencent.mm:id/f_";
    //好友清单中的好友名称
    //6.5.10:ja,6.5.13:jr,6.6.1:kh,6.7.2:nr,7.0.7:s6
    private static final String HBYQFRIENDNAMEID = "com.tencent.mm:id/s6";
    //好友清单顺序字母
    //6.5.10:abw,6.5.13:aco
    private static final String HBYQFRIENDLISTWORD = "com.tencent.mm:id/aco";
    //好友清单顺序后的复选框ID
    //6.5.10:oo,6.5.13:pc,6.6.1:s1,6.7.2:v8
    private static final String HBYQFRIENDCHECKBOXID = "com.tencent.mm:id/v8";
    //确认邀请名单
    //6.5.10:go,6.5.13:gv,6.6.1:h5,6.7.2:iv,7.0.7:lm
    private static final String HBYQFRIENDCONFIRMNAMESID = "com.tencent.mm:id/lm";
    //确认邀请
    //6.5.10:aes,6.5.13:afm,6.6.1:alo,6.7.2:apj
    private static final String HBYQFRIENDCONFIRMYQID = "com.tencent.mm:id/apj";
    //聊天窗口中的图片资源ID
    //6.5.10:a8a,6.5.13:a9j,6.6.1:aee,6.7.2:ai8,7.0.7:av2
    private static final String HBQRCODEYJPICID = "com.tencent.mm:id/av2";
    //点击打开后的图片大图的资源ID
    //6.5.10:yf,6.5.13:aq,6.6.1:ax,6.7.2:dwt,7.0.7:com.tencent.mm:id/ah3
    private static final String HBQRCODEYJBIGPICID = "com.tencent.mm:id/ah3";
    //二维码识别后，加入页面的返回按钮
    //6.5.10:h7,6.5.13:hd,6.6.1:hp,6.7.2:j8,7.0.7:m0
    private static final String HBQRCODERETURN = "com.tencent.mm:id/m0";
    //com.tencent.mm:id/akc，邀请好友的搜索输入框
    //6.5.13 akc，6.6.1:arp
    private static final String HBYQFRIENDSEARCHEDITBOX = "com.tencent.mm:id/arp";
    //清空消息相关
    //清空消息是发现历史消息中还有没有领取的红包的话，弹出的窗口，内容是："过去24小时内，你的2个聊天中有红包、转账以及群收款未处理，是否清空聊天记录？"（com.tencent.mm:id/c8f）
    //一个按钮是："清空聊天记录"(com.tencent.mm:id/aln)，一个按是："查看"（com.tencent.mm:id/alo）
    //6.6.1：alo,6.7.2:apj
    private static final String HBLOOKUPHISTORYHB = "com.tencent.mm:id/apj";
    //6.6.1:apr,6.7.2:auj
    private static final String HBHISTORYLISTITEMS = "com.tencent.mm:id/auj";

    //发消息搜索相关
    //聊天列表上的搜索按钮
    //7.0.7:c7
    private static final String SENDMSGSEARCHBTN = "com.tencent.mm:id/c7";

    //搜索编辑框
    //7.0.7:m6
    private static final String SENDMSGSEARCHEDITBOX = "com.tencent.mm:id/m6";
    private static final String SENDMSGSEARCHRETURNBTN = "com.tencent.mm:id/m3";

    //搜索处理的列表
    //7.0.7:s6
    private static final String SENDMSGSEARCHLISTTITLE = "com.tencent.mm:id/s6";

    //搜索的分类项，如果有相关项的按钮
    //7.0.7:b0_
    private static final String SENDMSGSEARCHCLASSIFYTITLE = "com.tencent.mm:id/b0_";

    //输入关键字，搜不出任何关联项，微信会提示搜索微信号按钮ID
    //7.0.7:c6q
    private static final String SENDMSGSEARCHWXBTN= "com.tencent.mm:id/c6q";

    //输入关键字，搜不出任何关联项，微信会提示搜索微信号按钮ID
    //7.0.7:c70
    private static final String SENDMSGSEARCHPYQDBTN= "com.tencent.mm:id/c70";

    //搜索页面的返回按钮
    //7.0.7:m4
    private static final String SENDMSGSEARCHKEYRETURNBTN= "com.tencent.mm:id/m4";

    boolean bInviteAgain = false; //二次根据服务器判断的群清单，再次执行要求指令
    Handler mHander = new Handler();
    Handler haltCheckHandler = new Handler();
    //看门狗处理
    Handler dogHandler = new Handler();
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

    //退群操作相关
    private int nClearMsgNum = 0;
    private int nNowMsgCounter = 0;
    //宿主指令
    private String hostCmd = ""; //宿主指令
    private RemoteParam remoteParam;

    //加群相关
    private String blackGroup = "";
    private java.util.Date blackGroupStTime;
    private Object blackSyncKey;
    private String inviteAgainGroup = ""; //再次要求的群清单
    private java.util.Date inviteAgainStTime;
    private Object inviteAgainSyncKey;
    private long autoReplyDelay = 1000;
    private boolean bCanUse = true;
    private boolean bAutoClickChatList = false;
    private boolean bAutoClickHbItem = false;
    private boolean bAutoClickOpenDetail = false;//这个标志为用于实现收到打开HB详情是，详情不自动返回的功能
    private boolean bAutoClickOpenButton = false;
    private SharedPreferences sharedPreferences;
    //抢到红包后自动回复
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

    private static final int NOTIFYDOGWATCHERTIME = 60*1000*3;//看门狗周期(ms)
    boolean reset_dog_flag = false;
    Runnable dogFunc = new Runnable() {
        @Override
        public void run() {
            if(!reset_dog_flag && bAutoMode){
                Log.i(TAG,"执行看门狗处理程序");
                try {
                    final Intent intent = getApplicationContext().getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    getApplicationContext().startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                //打开应用后检查一下应用
                int i = 0;
                int nStatus = 0;
                for (i = 0; i < 100; i++) {
                    AccessibilityNodeInfo hd = getRootInActiveWindow();
                    if(hd!=null){
                        try{
                            switch (nStatus){
                                case 0:{
                                    List<AccessibilityNodeInfo> confirmBtns = hd.findAccessibilityNodeInfosByText("确认");
                                    List<AccessibilityNodeInfo> waitBtns = hd.findAccessibilityNodeInfosByText("等待");
                                    if(confirmBtns!=null && !confirmBtns.isEmpty()){
                                        if(waitBtns!=null&&!waitBtns.isEmpty()){
                                            for (AccessibilityNodeInfo confirmBtn:confirmBtns){
                                                if(confirmBtn.isClickable()){
                                                    confirmBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                    Log.i(TAG,"不等待,确认关闭");
                                                }
                                            }
                                        }
                                    }
                                    nStatus = 1;
                                }
                                break;

                                case 1:{
                                    if("com.tencent.mm".equals(hd.getPackageName())){
                                        performGlobalAction(GLOBAL_ACTION_BACK);
                                        performGlobalAction(GLOBAL_ACTION_BACK);
                                        back2Home();
                                        i = 200;
                                        nStatus = 100;
                                    }
                                }
                                break;
                            }
                        }
                        catch (Exception e){
                            e.printStackTrace();
                        }
                    }

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            reset_dog_flag = false;
            dogHandler.postDelayed(dogFunc,NOTIFYDOGWATCHERTIME);
        }
    };

    private void ResetNotifyDogWatcher(){
        reset_dog_flag = true;
        dogHandler.removeCallbacks(dogFunc);
        dogHandler.postDelayed(dogFunc,NOTIFYDOGWATCHERTIME);
    }

    private long detect_tm = 0;
    private long notify_detect_tm = 0;
    private long chatlist_detect_tm = 0;
    private String mac = "";
    private String app_ver = "";
    private String device_model = Build.MODEL; // 设备型号 。
    private String version_release = Build.VERSION.RELEASE; // 设备的系统版本 。
    private String windowtitle = "";
    private String sender = "";
    private String hbcontent = "";
    private String hb_amount = "";
    private String wx_user = "";
    private String wx_userId = "";
    private String wx_group_name = "";
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            try {
                AccessibilityNodeInfo nd = getRootInActiveWindow();
                if (nd != null && nd.getPackageName().equals("com.tencent.mm")) {
                    wx_group_name = "";
                    back2Home();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
    private String last_context_string = "";
    private HbDataCheckThread hbDataCheckThread;
    private Object lockkey;
    private ObtainParamThread obtainParamThread;

    @Override
    public void onDestroy() {
        super.onDestroy();
        hbDataCheckThread.stopThread();
        obtainParamThread.stopThread();
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

        remoteParam = new RemoteParam();
        obtainParamThread = new ObtainParamThread(getApplicationContext(), mac, remoteParam);
        obtainParamThread.start();
        //后台参数（黑名单、再次邀请的群清单、邀请的对象）


        //启动微信无响应检查函数
        //haltCheckHandler.postDelayed(haltCheckFun, 1000);

        //启动通知消息看门狗
        reset_dog_flag = false;
        dogHandler.postDelayed(dogFunc,5000);

        //启动通知消息看门狗处理，在指定的时间内如果没收到通知，则重新打开微信。

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
        wx_userId = sharedPreferences.getString("wxUserId", "");
        bAutoReceptGroup = sharedPreferences.getBoolean("autoRecept", false);
        autoReceptParam = sharedPreferences.getString("autoParam", "240 350");
        bAutoQuitGroup = sharedPreferences.getBoolean("autoQuitGroup", false);
        bCloudChatRec = sharedPreferences.getBoolean("cloudChatRec", false);
        bAutoInvite = sharedPreferences.getBoolean("autoInvite", false);
        autoInviteParam = sharedPreferences.getString("autoInviteParam", "");
        bAutoHostCmd = sharedPreferences.getBoolean("autoHostCmd", false);
        remoteHostName = sharedPreferences.getString("remoteHostName", "");
        bInviteAgain = sharedPreferences.getBoolean("autoInviteAgain", false);

        try {
            nClearMsgNum = Integer.parseInt(sharedPreferences.getString("nClearMsgNum", "0"));
        } catch (Exception e) {
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
        if (key.equals("wxUserId")) {
            wx_userId = sharedPreferences.getString("wxUserId", "");
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
        if (key.equals("autoInviteAgain")) {
            bInviteAgain = sharedPreferences.getBoolean("autoInviteAgain", false);
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
        if (info != null) {
            if (bEnable) {
                info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            } else {
                info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
            }
            setServiceInfo(info);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Log.i(TAG, "event get.");
        return super.onKeyEvent(event);
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
        int itemIndex = 0;
        int nStatusCounter = 0;
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
                            } else {
                                List<AccessibilityNodeInfo> listTitles = hd.findAccessibilityNodeInfosByViewId(CHATLISTTITLE_STRING_ID);
                                if (listTitles != null && !listTitles.isEmpty()) {
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
                                    btn.getParent().getParent().getParent().getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 3;
                                }
                            }
                        }
                        break;
                        case 3: {
                            List<AccessibilityNodeInfo> btns = hd.findAccessibilityNodeInfosByText("聊天");
                            if (btns != null && !btns.isEmpty()) {
                                for (AccessibilityNodeInfo btn : btns) {
                                    btn.getParent().getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 4;
                                }
                            }
                        }
                        break;
                        case 4: {
                            List<AccessibilityNodeInfo> btns = hd.findAccessibilityNodeInfosByText("清空聊天记录");
                            if (btns != null && !btns.isEmpty()) {
                                for (AccessibilityNodeInfo btn : btns) {
                                    btn.getParent().getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
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
                        case 6: {
                            List<AccessibilityNodeInfo> chbBtns = hd.findAccessibilityNodeInfosByViewId(HBLOOKUPHISTORYHB);
                            if (chbBtns != null && !chbBtns.isEmpty()) {
                                for (AccessibilityNodeInfo chbBtn : chbBtns) {
                                    try {
                                        if (chbBtn.getText().toString().contains("查看")) {
                                            chbBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            i = 0; //为抢红包，把时间置零，这样抢红包的时间能长一些
                                            nStatus = 7;
                                            nStatusCounter = 0;
                                            itemIndex = 0;
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                        break;

                        case 7: {
                            List<AccessibilityNodeInfo> hbItems = hd.findAccessibilityNodeInfosByViewId(HBHISTORYLISTITEMS);
                            if (hbItems != null && !hbItems.isEmpty()) {
                                try {
                                    if (itemIndex > hbItems.size() - 1) {
                                        nStatus = 20; //退出不执行
                                        Log.i(TAG, "退出不执行1");
                                        i = 200;
                                    } else {
                                        AccessibilityNodeInfo hbItem = hbItems.get(itemIndex);
                                        hbItem.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        Log.i(TAG, "历史红包点击:" + String.valueOf(itemIndex));
                                        itemIndex++;
                                        nStatus = 9;
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                //如果在聊天列表下，则直接退出去
                                List<AccessibilityNodeInfo> bottomBtns = hd.findAccessibilityNodeInfosByViewId(HBBOTTOMBTN_STRING_ID);
                                if (bottomBtns != null && !bottomBtns.isEmpty()) {
                                    nStatus = 20; //退出不执行
                                    Log.i(TAG, "退出不执行2");
                                    i = 200;
                                }


                            } else {
                                //如果查看只有一个红包信息，则直接进入聊天界面
                                List<AccessibilityNodeInfo> hbNodes = hd.findAccessibilityNodeInfosByText("领取红包");
                                if (hbNodes != null && !hbNodes.isEmpty()) {
                                    Log.i(TAG, "只有一个红包，已经发现红包，去到9处理.");
                                    nStatus = 9;
                                } else {
                                    List<AccessibilityNodeInfo> moBtns = hd.findAccessibilityNodeInfosByViewId(HBMOREMSG_STRING_ID);
                                    if (moBtns != null && !moBtns.isEmpty()) {
                                        Log.i(TAG, "有更多消息，去到9处理.");
                                        nStatus = 9;
                                    }
                                }
                                nStatusCounter++;
                                if (nStatusCounter > 50) {
                                    Log.i(TAG, "7超时，去到9处理.");
                                    nStatusCounter = 0;
                                    nStatus = 9;
                                }
                            }
                        }
                        break;

                        case 9: {
                            List<AccessibilityNodeInfo> nodeSnds = hd.findAccessibilityNodeInfosByViewId(SOUNDBUTTON_STRING_ID);
                            if (nodeSnds != null && !nodeSnds.isEmpty()) {
                                List<AccessibilityNodeInfo> titleNodes = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                                if (titleNodes != null && !titleNodes.isEmpty()) {
                                    windowtitle = titleNodes.get(0).getText().toString();
                                    List<AccessibilityNodeInfo> hbNodes = hd.findAccessibilityNodeInfosByText("领取红包");
                                    if (hbNodes != null && !hbNodes.isEmpty()) {
                                        for (int j = hbNodes.size() - 1; j >= 0; j--) {
                                            AccessibilityNodeInfo nodeInfo = hbNodes.get(j);
                                            try {
                                                AccessibilityNodeInfo pNode = nodeInfo.getParent().getParent().getParent().getParent();
                                                if (pNode.getClassName().toString().contains("LinearLayout")) {
                                                    pNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                    chatlist_detect_tm = Calendar.getInstance().getTimeInMillis();
                                                    notify_detect_tm = 0;
                                                    detect_tm = 0;
                                                    Log.i(TAG, "发现点击红包");
                                                    nStatus = 10;
                                                    break;
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }

                                    if (nStatus != 10) { //没有找到红包
                                        List<AccessibilityNodeInfo> moBtns = hd.findAccessibilityNodeInfosByViewId(HBMOREMSG_STRING_ID);
                                        if (moBtns != null && !moBtns.isEmpty()) {
                                            //com.tencent.mm:id/a5j
                                            List<AccessibilityNodeInfo> listvs = hd.findAccessibilityNodeInfosByViewId(CHATCONTENTWINDOWLISTVIEW_STRING_ID);
                                            if (listvs != null && !listvs.isEmpty()) {
                                                for (AccessibilityNodeInfo listv : listvs) {
                                                    try {
                                                        listv.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                                    //Log.i(TAG, "向上退");
                                                }
                                            }

                                        } else {
                                            //没找到红包信息同时也没有more信息，则返回
                                            performGlobalAction(GLOBAL_ACTION_BACK);
                                            nStatus = 7;
                                        }
                                    }
                                }
                            }
                        }
                        break;

                        case 10: {
                            List<AccessibilityNodeInfo> hbNodes = hd.findAccessibilityNodeInfosByViewId(HBOPENBUTTON_STRING_ID);
                            if (hbNodes != null && !hbNodes.isEmpty()) {
                                hbNodes.get(hbNodes.size() - 1).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                Log.i(TAG, "拆开红包");
                                Log.i(TAG, "Notify Time:" + String.valueOf(Calendar.getInstance().getTimeInMillis() - notify_detect_tm));
                                Log.i(TAG, "Detect Time:" + String.valueOf(Calendar.getInstance().getTimeInMillis() - detect_tm));
                                nStatus = 11;
                                nStatusCounter = 0;
                            } else {
                                List<AccessibilityNodeInfo> hbNodes1 = hd.findAccessibilityNodeInfosByViewId(HBNONETEXT_STRING_ID);
                                if (hbNodes1 != null && !hbNodes1.isEmpty()) {
                                    Log.i(TAG, "红包派完了");
                                    performGlobalAction(GLOBAL_ACTION_BACK);
                                    nStatus = 9;
                                }
                                nStatusCounter++;
                                if (nStatusCounter > 100) {
                                    nStatusCounter = 0;
                                    performGlobalAction(GLOBAL_ACTION_BACK);
                                    nStatus = 9;
                                }
                            }
                        }
                        break;

                        case 11: {
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
                                wx_group_name = "";
                                uploadHbInfo();
                                performGlobalAction(GLOBAL_ACTION_BACK);
                                nStatus = 9;
                            } else {
                                nStatusCounter++;
                                if (nStatusCounter > 100) {
                                    nStatusCounter = 0;
                                    performGlobalAction(GLOBAL_ACTION_BACK);
                                    nStatus = 9;
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
                                        for (AccessibilityNodeInfo lCheck : lChecks) {
                                            lCheck.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            nStatus = 1;
                                        }
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
                                            titleHd.getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
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
                                        //execShellCmd("input tap " + autoReceptParam);
                                        ClickJoinBtn();
                                        bClickJoinBtn = true;
                                        //自动生成邀请设定好友名单
                                        if (bAutoHostCmd) {
                                            hostCmd = "admin[sp]邀请加入[sp]" + ComFunc.MD5(gName.trim()) + "[sp]" + autoInviteParam;
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
        if (bAllSucYq && bAutoInvite) {
            //yqfriends(autoInviteParam);
            hostCmd = "";
        }
        back2Home();
        Log.i(TAG, "邀请检查完毕");
    }

    /*
            自动邀请好友加群
     */
    private String yqfriends(String groupName, String friendStr) throws InterruptedException {
        int i = 0;
        int nStatus = 0;
        String result = "超时或者错误";
        String group = "";//最终邀请的全名
        String members = "";//最终邀请人人员
        String members_joined = "";//已经加群的好友
        String members_unjoined = "";//还没加这个群的好友
        String members_all = "";//全部工作群好友

        String friends[] = friendStr.split("\\|");
        for (i = 0; i < 200; i++) {
            AccessibilityNodeInfo hd = getRootInActiveWindow();
            if (hd != null) {
                try {
                    switch (nStatus) {
                        case 0: { //查找点击进入群信息按钮
                            List<AccessibilityNodeInfo> titleNodes = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                            if (titleNodes != null && !titleNodes.isEmpty()) {
                                String gName = titleNodes.get(0).getText().toString();
                                if (gName.lastIndexOf("(") != -1) {
                                    gName = gName.substring(0, gName.lastIndexOf("("));
                                }
                               // Log.i(TAG,"发现的群:"+gName);
                                if (cmpGroup(gName, groupName)) {
                                    Log.i(TAG,"当前窗口为目标群,目标("+gName+"),实际("+groupName+")");
                                    List<AccessibilityNodeInfo> lChecks = hd.findAccessibilityNodeInfosByViewId(HBOPENGROUPDETAIL_STRING_ID);
                                    if (lChecks != null && !lChecks.isEmpty()) {
                                        try {
                                            for (AccessibilityNodeInfo lCheck : lChecks) {
                                                lCheck.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                nStatus = 4;
                                            }
                                        } catch (Exception e) {
                                            nStatus = 0;
                                            e.printStackTrace();
                                        }
                                    }
                                } else {
                                    Log.i(TAG,"当前窗口非目标群,目标("+gName+"),实际("+groupName+")");
                                    performGlobalAction(GLOBAL_ACTION_BACK);
                                    nStatus = 1;
                                }
                            }
                        }
                        break;
                        case 1:{
                            List<AccessibilityNodeInfo> searchBtns = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHBTN);
                            if(searchBtns!=null&&!searchBtns.isEmpty()){
                                for(AccessibilityNodeInfo searchBtn:searchBtns){
                                    if(searchBtn.isClickable()){
                                        searchBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        Log.i(TAG,"点击搜索按钮");
                                        nStatus = 2;
                                    }
                                }
                            }
                            else{
                                //CHATLISTWINDOWLISTVIEW_STRING_ID
                                List<AccessibilityNodeInfo> lviews = hd.findAccessibilityNodeInfosByViewId(CHATLISTWINDOWLISTVIEW_STRING_ID);
                                if(lviews!=null&&!lviews.isEmpty()){
                                    for(AccessibilityNodeInfo lview:lviews){
                                        lview.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                                    }
                                }
                            }
                        }
                        break;
                        case 2:{
                            List<AccessibilityNodeInfo> searchEditBoxs = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHEDITBOX);
                            if(searchEditBoxs!=null&&!searchEditBoxs.isEmpty()){
                                if(findEditText(hd,groupName)){
                                    Log.i(TAG,"输入搜索关键词");
                                    nStatus = 3;
                                    Thread.sleep(500);
                                }
                            }
                        }
                        break;

                        case 3:{
                            List<AccessibilityNodeInfo> listTitles = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHLISTTITLE);
                            if(listTitles!=null&&!listTitles.isEmpty()){
                                boolean bGetSearchList = false;
                                for(AccessibilityNodeInfo listTitle:listTitles){
                                    if(cmpGroup(listTitle.getText().toString(),groupName)){
                                        if(listTitle.getParent().getParent().getParent().getParent().isClickable()){
                                            listTitle.getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            group = listTitle.getText().toString();
                                            Log.i(TAG,"点击搜索到的群组");
                                            bGetSearchList = true;
                                        }
                                    }
                                }
                                if(bGetSearchList){
                                    nStatus = 0;
                                }
                                else{
                                    Log.i(TAG,"没找到发送的对象1");
                                    result = "没找到要邀请的群1";
                                    nStatus = 200;
                                    i = 200;
                                }
                            }else{
                                //如果能找到这些项，说明确实搜索完了，找不到相关信息
                                List<AccessibilityNodeInfo> classifies = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHCLASSIFYTITLE);
                                List<AccessibilityNodeInfo> swxs = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHWXBTN);
                                List<AccessibilityNodeInfo> pyqs = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHPYQDBTN);
                                List<AccessibilityNodeInfo> rtbtns = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHKEYRETURNBTN);
                                if((classifies!=null&&!classifies.isEmpty())||(swxs!=null&&!swxs.isEmpty())||(pyqs!=null&&!pyqs.isEmpty())){
                                    for(AccessibilityNodeInfo rtbtn:rtbtns){
                                        rtbtn.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    }
                                    Log.i(TAG,"没找到发送的对象2");
                                    result = "没找到要邀请的群2";
                                    nStatus = 200;
                                    i = 200;
                                }
                            }
                        }
                        break;

                        case 4: {
                            List<AccessibilityNodeInfo> yqNodes = hd.findAccessibilityNodeInfosByViewId(HBYQFRIENDSINGROUPBTN);
                            if (yqNodes != null && !yqNodes.isEmpty()) {
                                for (AccessibilityNodeInfo yqNode : yqNodes) {
                                    CharSequence charSequence = yqNode.getContentDescription();
                                    if (charSequence != null) {
                                        if (charSequence.toString().contains("添加成员")) {
                                            yqNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            nStatus = 5;
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
                        case 5: {
                            if (findEditText(hd, "")) {
                                pressBackButton();
                                nStatus = 6;
                            }
                        }
                        break;
                        //点击分类标签
                        case 6: {
                            List<AccessibilityNodeInfo> nds = hd.findAccessibilityNodeInfosByText("我的工作群");
                            if (nds != null && !nds.isEmpty()) {
                                for (AccessibilityNodeInfo nd : nds) {
                                    nd.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    Thread.sleep(500);
                                    nStatus = 8;
                                }
                            }                            ;
                        }
                        break;
                        //根据邀请名单进行选择
                        case 8:{
                            List<AccessibilityNodeInfo> nds = hd.findAccessibilityNodeInfosByViewId(HBYQFRIENDNAMEID);
                            if(nds!=null&&!nds.isEmpty()){
                                boolean bSel = false;
                                for(AccessibilityNodeInfo nd:nds){
                                    try{
                                        AccessibilityNodeInfo clickNode = nd.getParent().getParent().getParent().getParent().getParent();
                                        AccessibilityNodeInfo checkNode = nd.getParent().getParent().getParent().getChild(1);
                                        members_all = members_all + nd.getText().toString()+"、";
                                        if(!checkNode.isChecked()){
                                            for(String friend:friends){
                                                if (nd.getText().toString().contains(friend)) {
                                                    clickNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                    bSel = true;
                                                    Log.i(TAG,"click:"+nd.getText().toString());
                                                    members = members + friend+"、";
                                                    break;
                                                }
                                            }
                                            members_unjoined = members_unjoined + nd.getText().toString()+"|";
                                            Log.i(TAG,"check:"+nd.getText().toString());
                                        }
                                        else{
                                            members_joined = members_joined + nd.getText().toString()+"、";
                                        }
                                    }
                                    catch (Exception e){
                                    }
                                }

                                //如果有选择则进行下一步，如果一个都没有，则表明邀请的对象没有，或已经在群当中。
                                if(bSel){
                                    nStatus = 9;
                                }
                                else{
                                    nStatus = 200;
                                    i = 200;
                                    result = "邀请的对象不在工作群中或已经入群";
                                }
                            }
                        }
                        break;
                        //确认邀请选择
                        case 9:{
                            List<AccessibilityNodeInfo> nds = hd.findAccessibilityNodeInfosByViewId(HBYQFRIENDCONFIRMNAMESID);
                            if(nds!=null&&!nds.isEmpty()){
                                for(AccessibilityNodeInfo nd:nds){
                                    try{
                                        if(nd.isEnabled()){
                                            Log.i(TAG,"确认选择");
                                            nd.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            nStatus = 10;
                                        }
                                    }
                                    catch(Exception e){
                                    }
                                }
                            }
                        }
                        break;
                        case 10:{
                            List<AccessibilityNodeInfo> nds = hd.findAccessibilityNodeInfosByViewId(HBYQFRIENDCONFIRMNAMESID);
                            if(nds!=null&&!nds.isEmpty()){
                                for(AccessibilityNodeInfo nd:nds){
                                    Log.i(TAG,"确认邀请");
                                    nd.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 11;
                                }
                            }
                        }
                        //最后执行邀请
                        case 11:{
                            List<AccessibilityNodeInfo> nds = hd.findAccessibilityNodeInfosByText("邀请");
                            if(nds!=null&&!nds.isEmpty()){
                                for(AccessibilityNodeInfo nd:nds){
                                    Log.i(TAG,"执行邀请");
                                    nd.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 200;
                                    i = 300;
                                    result = "成功发送邀请";
                                }
                            }
                            List<AccessibilityNodeInfo> nds1 = hd.findAccessibilityNodeInfosByText("发送");
                            if(nds1!=null&&!nds1.isEmpty()){
                                for(AccessibilityNodeInfo nd1:nds1){
                                    Log.i(TAG,"提交邀请申请");
                                    nd1.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 200;
                                    i = 300;
                                    result = "成功发送邀请申请（需群主确认）";
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
        back2Home();
        Log.i(TAG, "邀请完毕");
        result = "*"+result+"*" + "\r\n【邀请群】:"+group+"\r\n【执行邀请好友】:"+members+"\r\n【全部工作群好友】:"+members_all+"\r\n【已经在群的好友】:"+members_joined+"\r\n【参考指令】：[sp]邀请加群[sp]"+groupName+"[sp]"+members_unjoined;
        return result;
    }
    /*
        ROOT点击加入群按钮
     */
    private void ClickJoinBtn(){
        String[] arps = autoReceptParam.split(" ");
        if(arps.length==2){
            try{
                int x = Integer.parseInt(arps[0].trim());
                int y = Integer.parseInt(arps[1].trim());
                execShellCmd("input tap "+String.valueOf(x)+" "+String.valueOf(y-10));
                execShellCmd("input tap "+String.valueOf(x)+" "+String.valueOf(y));
                execShellCmd("input tap "+String.valueOf(x)+" "+String.valueOf(y+10));
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        else{
            try{
                execShellCmd("input tap " + autoReceptParam);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /*
        二维码识别加入群
        groupName:默认反馈消息的群，默认在哪发送，在哪反馈。
        bakGroupName:如果默认的反馈消息的地方失败，发这个群。
     */
    private String qrcodeJoinGroup(String groupName, String bakGroupName) throws InterruptedException {
        int i = 0;
        int nStatus = 0;
        int nStatusCounter = 0;
        String resultStr = "";
        for (i = 0; i < 200; i++) {
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
                                execShellCmd("input swipe 10 200 11 200 2000"); //采用划线的方式模拟屏幕长按
                                nStatus = 2;
                            }else{
                                List<AccessibilityNodeInfo> imgNodes = hd.findAccessibilityNodeInfosByViewId(HBQRCODEYJPICID);
                                if (imgNodes != null && !imgNodes.isEmpty()) {
                                    imgNodes.get(imgNodes.size() - 1).getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                }
                            }
                        }
                        break;
                        case 2: {
                            List<AccessibilityNodeInfo> qrcodeBtns = hd.findAccessibilityNodeInfosByText("识别图中的二维码");
                            if (qrcodeBtns != null && !qrcodeBtns.isEmpty()) {
                                qrcodeBtns.get(0).getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                Thread.sleep(1000);
                                nStatus = 3;
                            }
                        }
                        break;
                        case 3: {
                            List<AccessibilityNodeInfo> nodeInfos = hd.findAccessibilityNodeInfosByViewId(HBQRCODERETURN);
                            if (nodeInfos != null && !nodeInfos.isEmpty()) {
                                //execShellCmd("input tap " + autoReceptParam);
                                ClickJoinBtn();
                                nStatusCounter = 0;
                                nStatus = 4;
                            } else {
                                List<AccessibilityNodeInfo> windowTexts = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                                if (windowTexts != null && !windowTexts.isEmpty()) {
                                    if (!"".equals(windowTexts.get(0).getText())) {
                                        nStatus = 5;
                                        resultStr = "该群已经加入";
                                    }
                                }
                            }
                        }
                        break;
                        case 4: {
                            List<AccessibilityNodeInfo> windowTexts = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                            if (windowTexts != null && !windowTexts.isEmpty()) {
                                if (!"".equals(windowTexts.get(0).getText())) {
                                    nStatus = 5;
                                    resultStr = "加群成功";
                                }
                            } else {
                                nStatusCounter++;
                                if (nStatusCounter >= 40) { //2000毫秒点击一次
                                   // execShellCmd("input tap " + autoReceptParam);
                                    ClickJoinBtn();
                                    nStatusCounter = 0;
                                }
                            }
                        }
                        break;

                        case 5: {
                            //在调用sendMsg时，打开窗口首先判断的是窗口标题，所以前面的处理函数，在调用sendMsg前应该确保不在聊天窗口。
                            List<AccessibilityNodeInfo> windowTexts = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                            if (windowTexts != null && !windowTexts.isEmpty()) {
                                performGlobalAction(GLOBAL_ACTION_BACK);
                            }else{
                                i = 200;
                                nStatus = 6;
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
        if(nStatus!=6){
            resultStr = "加群超时或错误";
        }
        Log.i(TAG,"加群处理完成");
        return resultStr;

    }

    /*
        补抢
     */
    private String checkGroupHb(String groupName){
        String result = "超时或错误";
        int i = 0;
        int nStatus = 0;
        int  nStatusCounter = 0;
        int hbNum = 0;
        int hbNumPacked = 0;
        int hbNumUnpacked = 0;
        for (i = 0; i < 200; i++) {
            AccessibilityNodeInfo hd = getRootInActiveWindow();
            if (hd != null) {
                try{
                    switch (nStatus) {
                        case 0: { //查找点击进入群信息按钮
                            List<AccessibilityNodeInfo> titleNodes = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                            if (titleNodes != null && !titleNodes.isEmpty()) {
                                String gName = titleNodes.get(0).getText().toString();
                                if (gName.lastIndexOf("(") != -1) {
                                    gName = gName.substring(0, gName.lastIndexOf("("));
                                }
                                if (cmpGroup(gName, groupName)) {
                                    Log.i(TAG,"当前窗口为目标群,目标("+gName+"),实际("+groupName+")");
                                    nStatus = 4;
                                } else {
                                    Log.i(TAG,"当前窗口非目标群,目标("+gName+"),实际("+groupName+")");
                                    performGlobalAction(GLOBAL_ACTION_BACK);
                                    nStatus = 1;
                                }
                            }
                        }
                        break;
                        case 1:{
                            List<AccessibilityNodeInfo> searchBtns = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHBTN);
                            if(searchBtns!=null&&!searchBtns.isEmpty()){
                                for(AccessibilityNodeInfo searchBtn:searchBtns){
                                    if(searchBtn.isClickable()){
                                        searchBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        Log.i(TAG,"点击搜索按钮");
                                        nStatus = 2;
                                    }
                                }
                            }
                            else{
                                //CHATLISTWINDOWLISTVIEW_STRING_ID
                                List<AccessibilityNodeInfo> lviews = hd.findAccessibilityNodeInfosByViewId(CHATLISTWINDOWLISTVIEW_STRING_ID);
                                if(lviews!=null&&!lviews.isEmpty()){
                                    for(AccessibilityNodeInfo lview:lviews){
                                        lview.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                                    }
                                }
                            }
                        }
                        break;
                        case 2:{
                            List<AccessibilityNodeInfo> searchEditBoxs = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHEDITBOX);
                            if(searchEditBoxs!=null&&!searchEditBoxs.isEmpty()){
                                if(findEditText(hd,groupName)){
                                    Log.i(TAG,"输入搜索关键词");
                                    nStatus = 3;
                                    Thread.sleep(500);
                                }
                            }
                        }
                        break;
                        case 3:{
                            List<AccessibilityNodeInfo> listTitles = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHLISTTITLE);
                            if(listTitles!=null&&!listTitles.isEmpty()){
                                boolean bGetSearchList = false;
                                for(AccessibilityNodeInfo listTitle:listTitles){
                                    if(cmpGroup(listTitle.getText().toString(),groupName)){
                                        if(listTitle.getParent().getParent().getParent().getParent().isClickable()){
                                            listTitle.getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            Log.i(TAG,"点击搜索到的群组");
                                            bGetSearchList = true;
                                        }
                                    }
                                }
                                if(bGetSearchList){
                                    nStatus = 0;
                                }
                                else{
                                    Log.i(TAG,"没找到发送的对象1");
                                    result = "没找到要邀请的群1";
                                    nStatus = 200;
                                    i = 200;
                                }
                            }else{
                                //如果能找到这些项，说明确实搜索完了，找不到相关信息
                                List<AccessibilityNodeInfo> classifies = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHCLASSIFYTITLE);
                                List<AccessibilityNodeInfo> swxs = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHWXBTN);
                                List<AccessibilityNodeInfo> pyqs = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHPYQDBTN);
                                List<AccessibilityNodeInfo> rtbtns = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHKEYRETURNBTN);
                                if((classifies!=null&&!classifies.isEmpty())||(swxs!=null&&!swxs.isEmpty())||(pyqs!=null&&!pyqs.isEmpty())){
                                    for(AccessibilityNodeInfo rtbtn:rtbtns){
                                        rtbtn.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    }
                                    Log.i(TAG,"没找到发送的对象2");
                                    result = "没找到要邀请的群2";
                                    nStatus = 200;
                                    i = 200;
                                }
                            }
                        }
                        break;
                        //搜索是否有红包
                        case 4:{
                            List<AccessibilityNodeInfo> titleNodes = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                            if (titleNodes != null && !titleNodes.isEmpty()) {
                                windowtitle = titleNodes.get(0).getText().toString();
                                List<AccessibilityNodeInfo> hbNodes = hd.findAccessibilityNodeInfosByViewId(HB_LOGO_STRING_ID);
                                if (hbNodes != null && !hbNodes.isEmpty()) {
                                    for (int j = hbNodes.size() - 1; j >= 0; j--) {
                                        AccessibilityNodeInfo nodeInfo = hbNodes.get(j);
                                        try {
                                            if ("微信红包".equals(nodeInfo.getChild(1).getChild(0).getText().toString())) {
                                                hbNum++;
                                                //Log.i(TAG,"数量"+String.valueOf(nodeInfo.getChild(0).getChildCount()));//企业群3，个人群1
                                                boolean bylq = false;//是否已经领取判断
                                                //大于1说明的企业号发的红包，否则是个人发的
                                                if(nodeInfo.getChild(0).getChildCount()>1){
                                                    //企业发的红包
                                                    Log.i(TAG,"企业红包");
                                                    if(nodeInfo.getChild(0).getChild(1).getChild(1).getChildCount() > 1){
                                                        bylq = true;
                                                    }
                                                }
                                                else{
                                                    Log.i(TAG,"个人红包");
                                                    //个人发的红包
                                                    if(nodeInfo.getChild(0).getChild(0).getChild(1).getChildCount() > 1){
                                                        bylq = true;
                                                    }
                                                }
                                                //这个有两个，说明已经领过了
                                                if (bylq) {
                                                    Log.i(TAG, "该红包已经领取或被领完");
                                                    hbNumPacked++;
                                                } else {
                                                    nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                    chatlist_detect_tm = Calendar.getInstance().getTimeInMillis();
                                                    notify_detect_tm = 0;
                                                    detect_tm = 0;
                                                    Log.i(TAG, "发现点击红包("+String.valueOf(j)+")");
                                                    nStatus = 5;
                                                    nStatusCounter = 0;
                                                    break;
                                                }
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }
                            if (nStatus != 5) {
                                List<AccessibilityNodeInfo> moBtns = hd.findAccessibilityNodeInfosByViewId(HBMOREMSG_STRING_ID);
                                if (moBtns != null && !moBtns.isEmpty()) {
                                    //com.tencent.mm:id/a5j
                                    List<AccessibilityNodeInfo> listvs = hd.findAccessibilityNodeInfosByViewId(CHATCONTENTWINDOWLISTVIEW_STRING_ID);
                                    if (listvs != null && !listvs.isEmpty()) {
                                        for (AccessibilityNodeInfo listv : listvs) {
                                            try {
                                                listv.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                            //Log.i(TAG, "向上退");
                                        }
                                    }

                                } else {
                                    back2Home();
                                    nStatus = 200;
                                    i = 200;
                                    result = "执行完毕:发现总红包("+String.valueOf(hbNum)+")发现领完红包("+String.valueOf( hbNumPacked )+"),打开红包("+String.valueOf(hbNumUnpacked)+")";
                                    Log.i(TAG,result);
                                }
                            }
                        }
                        break;

                        case 5: {
                            List<AccessibilityNodeInfo> hbNodes = hd.findAccessibilityNodeInfosByViewId(HBOPENBUTTON_STRING_ID);
                            if (hbNodes != null && !hbNodes.isEmpty()) {
                                hbNodes.get(hbNodes.size() - 1).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                Log.i(TAG, "拆开红包");
                                Log.i(TAG, "Notify Time:" + String.valueOf(Calendar.getInstance().getTimeInMillis() - notify_detect_tm));
                                Log.i(TAG, "Detect Time:" + String.valueOf(Calendar.getInstance().getTimeInMillis() - detect_tm));
                                nStatus = 6;
                                nStatusCounter = 0;
                                hbNumUnpacked++;
                            } else {
                                List<AccessibilityNodeInfo> hbNodes1 = hd.findAccessibilityNodeInfosByViewId(HBNONETEXT_STRING_ID);
                                if (hbNodes1 != null && !hbNodes1.isEmpty()) {
                                    Log.i(TAG, "红包派完了");
                                    //performGlobalAction(GLOBAL_ACTION_BACK);
                                    //nStatus = 4;
                                    result = "红包派完了";
                                    nStatus = 10;
                                }else{
                                    List<AccessibilityNodeInfo> hbNodes2 = hd.findAccessibilityNodeInfosByViewId(HBSENDER_STRING_ID);
                                    if(hbNodes2!=null&&!hbNodes2.isEmpty()){
                                        Thread.sleep(2000);
                                        Log.i(TAG, "红包领取了");
                                        //performGlobalAction(GLOBAL_ACTION_BACK);
                                        //nStatus = 4;
                                        result = "红包已经领取了";
                                        nStatus = 10;
                                    }
                                }
                                nStatusCounter++;
                                if (nStatusCounter > 50) {
                                    nStatusCounter = 0;
                                    performGlobalAction(GLOBAL_ACTION_BACK);
                                    nStatus = 4;
                                }
                            }
                        }
                        break;

                        case 6: {
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
                                wx_group_name = "";
                                Log.i(TAG, "windowtitle=" + windowtitle);
                                uploadHbInfo();
                                //performGlobalAction(GLOBAL_ACTION_BACK);
                                result = "成功领取红包:"+hb_amount+"元";
                                nStatus = 10;
                            } else {
                                nStatusCounter++;
                                if (nStatusCounter > 50) {
                                    nStatusCounter = 0;
                                    performGlobalAction(GLOBAL_ACTION_BACK);
                                    nStatus = 4;
                                }
                            }
                        }
                        break;

                        //结束状态处理
                        case 10:{
                            nStatus = 200;
                            i = 200;
                            nStatus = 200;
                            back2Home();
                        }
                        break;
                    }

                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return result;
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

    /*
        群名称比较，考虑到不同的群有很多特殊字符很难输入，所以采用多关键字比较法进行比较
     */
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
        if(ComFunc.MD5(targetGroup).equals(inputGroup)){
            bIsTheGroup = true;
        }
        return bIsTheGroup;
    }

    /*
        通用同步群消息发送
     */
    private boolean sendMsg(String groupName, String msg) throws InterruptedException {
        boolean bResult = false;
        boolean bSearchSend = false;//区分搜索群点击发送还是直接匹配就发送
        int i = 0;
        int nStatus = 0;

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
                                        Log.i(TAG, "发消息");
                                        if (send()) {
                                            bResult = true;
                                            Log.i(TAG, "执行发消息尝试成功");
                                            if(bSearchSend){
                                                Log.i(TAG, "搜索群发消息后续处理，以便能连续接收指令");
                                                nStatus = 4;
                                            }else{
                                                Log.i(TAG, "直接窗口发送不用后续处理，也可以继续工作");
                                                nStatus = 200;
                                                i = 200;
                                            }

                                        } else {
                                            Log.i(TAG, "执行发消息尝试失败");
                                        }
                                    }
                                    else{
                                        Log.i(TAG,"没找到编辑框");
                                    }
                                } else {
                                    performGlobalAction(GLOBAL_ACTION_BACK);
                                    bSearchSend = true;
                                    nStatus = 1;
                                }
                            }else{
                                //万一点击失败，使得还可以尝试点击。
                                List<AccessibilityNodeInfo> listTitles = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHLISTTITLE);
                                if(listTitles!=null&&!listTitles.isEmpty()){
                                    boolean bGetSearchList = false;
                                    for(AccessibilityNodeInfo listTitle:listTitles){
                                        if(cmpGroup(listTitle.getText().toString(),groupName)){
                                            if(listTitle.getParent().getParent().getParent().getParent().isClickable()){
                                                listTitle.getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                Log.i(TAG,"点击搜索到的群组2");
                                            }
                                        }
                                    }
                                }
                            }

                        }
                        break;

                        case 1:{
                            List<AccessibilityNodeInfo> searchBtns = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHBTN);
                            if(searchBtns!=null&&!searchBtns.isEmpty()){
                                for(AccessibilityNodeInfo searchBtn:searchBtns){
                                    if(searchBtn.isClickable()){
                                        searchBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        Log.i(TAG,"点击搜索按钮");
                                        nStatus = 2;
                                    }
                                }
                            }
                            else{
                                //CHATLISTWINDOWLISTVIEW_STRING_ID
                                List<AccessibilityNodeInfo> lviews = hd.findAccessibilityNodeInfosByViewId(CHATLISTWINDOWLISTVIEW_STRING_ID);
                                if(lviews!=null&&!lviews.isEmpty()){
                                    for(AccessibilityNodeInfo lview:lviews){
                                        lview.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                                    }
                                }
                            }
                        }
                        break;

                        case 2:{
                            List<AccessibilityNodeInfo> searchEditBoxs = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHEDITBOX);
                            if(searchEditBoxs!=null&&!searchEditBoxs.isEmpty()){
                                if(findEditText(hd,groupName)){
                                    Log.i(TAG,"输入搜索关键词");
                                    nStatus = 3;
                                }
                            }
                        }
                        break;

                        case 3:{
                            List<AccessibilityNodeInfo> listTitles = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHLISTTITLE);
                            if(listTitles!=null&&!listTitles.isEmpty()){
                                boolean bGetSearchList = false;
                                for(AccessibilityNodeInfo listTitle:listTitles){
                                    if(cmpGroup(listTitle.getText().toString(),groupName)){
                                        if(listTitle.getParent().getParent().getParent().getParent().isClickable()){
                                            listTitle.getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            Log.i(TAG,"点击搜索到的群组");
                                            bGetSearchList = true;
                                        }
                                    }
                                }
                                if(bGetSearchList){
                                    nStatus = 0;
                                }
                            }else{
                                //如果能找到这些项，说明确实搜索完了，找不到相关信息
                                List<AccessibilityNodeInfo> classifies = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHCLASSIFYTITLE);
                                List<AccessibilityNodeInfo> swxs = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHWXBTN);
                                List<AccessibilityNodeInfo> pyqs = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHPYQDBTN);
                                List<AccessibilityNodeInfo> rtbtns = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHKEYRETURNBTN);
                                if((classifies!=null&&!classifies.isEmpty())||(swxs!=null&&!swxs.isEmpty())||(pyqs!=null&&!pyqs.isEmpty())){
                                    for(AccessibilityNodeInfo rtbtn:rtbtns){
                                        rtbtn.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    }
                                    Log.i(TAG,"没找到发送的对象");
                                    nStatus = 4;
                                }
                            }
                        }
                        break;

                        case 4:{
                            List<AccessibilityNodeInfo> goes = hd.findAccessibilityNodeInfosByViewId(HBRETURN_STRING_ID);
                            if (goes != null && !goes.isEmpty()) {
                                for (AccessibilityNodeInfo go : goes) {
                                    go.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    nStatus = 5;
                                }
                            } else {
                                List<AccessibilityNodeInfo> bottomBtns = hd.findAccessibilityNodeInfosByViewId(HBBOTTOMBTN_STRING_ID);
                                for (AccessibilityNodeInfo bottomBtn : bottomBtns) {
                                    if (bottomBtn.getText() != null && bottomBtn.getText().toString().contains("我")) {
                                        if(bottomBtn.getParent().getParent().isClickable()){
                                            bottomBtn.getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            nStatus = 6;
                                        }
                                    }
                                }
                            }
                        }
                        break;
                        case 5: { //页面后退，防止连续发消息时回到主页后，就无法再发了。（4,5,6状态都是这个目的)
                            List<AccessibilityNodeInfo> searchRtns = hd.findAccessibilityNodeInfosByViewId(SENDMSGSEARCHRETURNBTN);
                            if(searchRtns!=null&&!searchRtns.isEmpty()){
                                for(AccessibilityNodeInfo searchRtn:searchRtns){
                                    searchRtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                }
                                nStatus = 6;
                            }
                        }
                        break;
                        case 6: {
                            List<AccessibilityNodeInfo> bottomBtns = hd.findAccessibilityNodeInfosByViewId(HBBOTTOMBTN_STRING_ID);
                            for (AccessibilityNodeInfo bottomBtn : bottomBtns) {
                                if (bottomBtn.getText() != null && bottomBtn.getText().toString().contains("我")) {
                                    if(bottomBtn.getParent().getParent().isClickable()){
                                        bottomBtn.getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        nStatus = 7;
                                    }
                                }
                            }
                        }
                        break;

                        case 7: {
                            List<AccessibilityNodeInfo> btns = hd.findAccessibilityNodeInfosByText("设置");
                            if (btns != null && !btns.isEmpty()) {
                                for (AccessibilityNodeInfo btn : btns) {
                                    btn.getParent().getParent().getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    i = 200; //退出循环的意思
                                    nStatus = 200;
                                    bResult = true;

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
                                acceptBtn.getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                friendName = acceptBtn.getParent().getParent().getChild(0).getChild(0).getText().toString();
                                reqContent = acceptBtn.getParent().getParent().getChild(0).getChild(1).getText().toString();
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

                                int k = 0;
                                for (k = 0; k < acceptBtns.size(); k++) {
                                    try {
                                        AccessibilityNodeInfo acceptBtn = acceptBtns.get(k);
                                        if ("通过验证".equals(acceptBtn.getChild(0).getChild(0).getText())) {
                                            Log.i(TAG, "点击通过验证按钮");
                                            acceptBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            nStatus = 2;
                                        }
                                    } catch (Exception e) {
                                    }
                                }
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
                            List<AccessibilityNodeInfo> acceptBtns = hd.findAccessibilityNodeInfosByText("发消息");
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
                    ResetNotifyDogWatcher();//重置通知消息看门狗
                    nNowMsgCounter++; //收到一个通知，计数器加一
                    //get send person
                    int msgType = MSG_TYPE_GROUP;//0,群消息
                    int endIndex = content.indexOf(":");
                    String send_person = "";
                    if (endIndex != -1) {
                        send_person = content.substring(0, endIndex);
                        if (send_person.equals(group_name)) {
                            msgType = MSG_TYPE_PERSON;
                            //Log.i(TAG,"PERSON MSG");
                        } else {
                            msgType = MSG_TYPE_GROUP;
                            //Log.i(TAG,"GROUP MSG");
                        }
                    } else {
                        msgType = MSG_TYPE_OTHER;
                    }
                    //Log.i(TAG,send_person);
                    //Log.i(TAG,group_name);
                    if (endIndex != -1) {

                        //upload msg，个人发送消息和其他加入朋友的消息不记录（个人消息发送人和消息标题相同，而新创建群聊，未命名，首次消息为标题为群成员用顿号分开）
                        if (bCloudChatRec) {
                            try {
                                rdnonhbInfo(group_name, content, send_person, wx_user, content.length());
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
                            wx_group_name = group_name;
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
                            getWxUserNameAndId();
                            setEventTypeContentAndStatus(true);
                        } else {

                            //自动加群处理,个人发送的邀请才有效
                            if (bAutoReceptGroup && msgType == MSG_TYPE_PERSON && content.contains("[链接] 邀请你加入群聊")) {
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
                                return;
                            }

                            //退群指令检查
                            String group_name_md5 = ComFunc.MD5(group_name);
                            if (bAutoQuitGroup && remoteParam.blackGroups.contains("{={" + group_name_md5 + "}=}")) {
                                PendingIntent pendingIntent = notification.contentIntent;
                                setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                try {
                                    pendingIntent.send();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                syncQuitFromGroup(group_name_md5);
                                setEventTypeContentAndStatus(true);
                                return;
                            }

                            //二次邀请加群检查
                            if (bInviteAgain && remoteParam.inviteAgainGroup.contains("{={" + group_name_md5 + "}=}")) {
                                PendingIntent pendingIntent = notification.contentIntent;
                                setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                try {
                                    pendingIntent.send();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                //yqfriends(remoteParam.autoInviteParam);
                                setEventTypeContentAndStatus(true);
                                return;
                            }

                            //自动二次邀请加群检查
                            //自动清空群消息处理
                            // Log.i(TAG, "nNowMsgCounter:" + String.valueOf(nNowMsgCounter) + ",nClearMsgNum:" + String.valueOf(nClearMsgNum));
                            if (nClearMsgNum > 100 && nNowMsgCounter > nClearMsgNum) {
                                nNowMsgCounter = 0;
                                PendingIntent pendingIntent = notification.contentIntent;
                                setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                try {
                                    pendingIntent.send();
                                    clearChatContent();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                hostCmd = "";
                                setEventTypeContentAndStatus(true);
                                return;
                            }


                            //自动指令处理
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
                                    } else if (cmds[1].equals("执行命令")) {//申请ROOT
                                        try {
                                            execShellCmd(cmds[2]);
                                            Log.i(TAG, "执行命令：" + cmds[2]);
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
                                    } else if (cmds[1].equals("后台参数设置")) {//清空消息
                                        PendingIntent pendingIntent = notification.contentIntent;
                                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        String dealResult = "";
                                        try {
                                            if (sharedPreferences.contains(cmds[2])) {
                                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                                editor.putString(cmds[2], cmds[3]);
                                                editor.commit();
                                                dealResult = "成功";
                                            } else {
                                                dealResult = "失败(没这个参数项)";
                                            }
                                        } catch (Exception e) {
                                            dealResult = "失败(设置异常)";
                                            e.printStackTrace();
                                        }
                                        sendMsg(group_name, cmds[2] + "设置为" + cmds[3] + dealResult);
                                        back2Home();
                                        hostCmd = "";
                                        setEventTypeContentAndStatus(true);
                                    } else if (cmds[1].equals("帮助")) {//清空消息
                                        PendingIntent pendingIntent = notification.contentIntent;
                                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        String helpMsg = "mtakem2版本:" + app_ver + "\n" + "[sp]发消息[sp]要发的群[sp]要发的内容\n" +
                                                "[sp]后台参数设置[sp]nClearMsgNum[sp]101\n[sp]后台参数设置[sp]autoInviteParam[sp]jzgz01|jzgz02|jzgz03\n" +
                                                "[sp]后台参数设置[sp]remoteHostName[sp]你的微信名 微信群名\n" +
                                                "[sp]清空群消息[sp]随意填[sp]随意\n[sp]申请ROOT[sp]随意填[sp]随意\n" +
                                                "[sp]邀请加入[sp]要邀请的群[sp]邀请谁加入？\n" +
                                                "[sp]二维码加群[sp]反馈消息的备用群[sp]随意\n" +
                                                "[sp]获取余额[sp]要反馈的群名称[sp]随意\n" +
                                                "[sp]搜索历史消息[sp]作用时间（必须是数字）[sp]随意\n" +
                                                "[sp]执行指令[sp]指令内容[sp]随意";
                                        sendMsg(group_name, helpMsg);
                                        back2Home();
                                        hostCmd = "";
                                        setEventTypeContentAndStatus(true);

                                    } else if (cmds[1].equals("获取余额")) {//清空消息
                                        PendingIntent pendingIntent = notification.contentIntent;
                                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                        //penging可以重复使用，可用于定位消息框
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        String amt = getAndSendWxAmount();

                                        ////penging可以重复使用，可用于定位消息框
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        if (!sendMsg(group_name, wx_user + ":" + amt)) {
                                            sendMsg(cmds[2], wx_user + ":" + amt);
                                        }
                                        back2Home();
                                        hostCmd = "";
                                        setEventTypeContentAndStatus(true);
                                    } else if (cmds[1].equals("搜索历史消息")) {
                                        PendingIntent pendingIntent = notification.contentIntent;
                                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                        try {
                                            pendingIntent.send();

                                            int nLong = 100;//搜索时间
                                            try {
                                                nLong = Integer.parseInt(cmds[2]);
                                            } catch (Exception e) {
                                                nLong = 100;
                                            }
                                            hbJlCheck(nLong);
                                            back2Home();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
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
                                        String rt = qrcodeJoinGroup(group_name, cmds[2]);
                                        ////penging可以重复使用，可用于定位消息框
                                        back2Home();
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                        if (!sendMsg(group_name, rt)) {
                                            sendMsg(cmds[2],rt);
                                        }
                                        back2Home();
                                        hostCmd = "";
                                        setEventTypeContentAndStatus(true);
                                    } else if (cmds[1].equals("邀请加群")) {
                                        PendingIntent pendingIntent = notification.contentIntent;
                                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        //处理后返回消息
                                        Log.i(TAG, "邀请加群:" + group_name);
                                        String rt = yqfriends(cmds[2], cmds[3]);
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        sendMsg(group_name,rt);
                                        back2Home();
                                        setEventTypeContentAndStatus(true);
                                    }  else if (cmds[1].equals("查群红包")) {
                                        PendingIntent pendingIntent = notification.contentIntent;
                                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        //处理后返回消息
                                        String rt = checkGroupHb(cmds[2]);
                                        Thread.sleep(500);
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        sendMsg(group_name,rt);
                                        back2Home();
                                        setEventTypeContentAndStatus(true);
                                    } else {
                                        PendingIntent pendingIntent = notification.contentIntent;
                                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        sendMsg(group_name, "已收到指令:" + hostCmd);
                                        back2Home();
                                        setEventTypeContentAndStatus(true);
                                    }
                                }
                            }
                            //检查宿主指令
                            //宿主指令格式:指令名称 群名称 相关参数
                            if (!"".equals(hostCmd)) {
                                String[] cmds = hostCmd.split("\\[sp\\]");
                                boolean bIsTheGroup = true;
                                //第二个参数定义为群名称，所有指令都针对群而设立
                                /*
                                String groupkeys[] = cmds[2].split(" ");
                                for (String groupKey : groupkeys) {
                                    if (!group_name.contains(groupKey.trim())) {
                                        bIsTheGroup = false;
                                        break;
                                    }
                                }*/
                                bIsTheGroup = ComFunc.MD5(group_name).equals(cmds[2]);
                                if (cmds.length == 4 && bIsTheGroup) {
                                    if (cmds[1].equals("邀请加入")) {
                                        PendingIntent pendingIntent = notification.contentIntent;
                                        setEventTypeContentAndStatus(false); //暂时屏蔽content和statu消息监控
                                        try {
                                            pendingIntent.send();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        // yqfriends(cmds[3]);
                                        hostCmd = "";
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
                            List<AccessibilityNodeInfo> hbNodes = hd.findAccessibilityNodeInfosByViewId(HB_LOGO_STRING_ID);
                            if (hbNodes != null && !hbNodes.isEmpty()) {
                                for (int i = hbNodes.size() - 1; i >= 0; i--) {
                                    AccessibilityNodeInfo nodeInfo = hbNodes.get(i);
                                    try {
                                        //有些小程序也用HB_LOGO_STRING_ID这个标识，所以要找出差异区分
                                        if ("微信红包".equals(nodeInfo.getChild(1).getChild(0).getText().toString())) {
                                            //Log.i(TAG,"数量"+String.valueOf(nodeInfo.getChild(0).getChildCount()));//企业群3，个人群1
                                            boolean bylq = false;//是否已经领取判断
                                            //大于1说明的企业号发的红包，否则是个人发的
                                            if(nodeInfo.getChild(0).getChildCount()>1){
                                                //企业微信发的红包
                                                if(nodeInfo.getChild(0).getChild(1).getChild(1).getChildCount() > 1){
                                                    bylq = true;
                                                }
                                            }
                                            else{
                                                //个人发的红包
                                                if(nodeInfo.getChild(0).getChild(0).getChild(1).getChildCount() > 1){
                                                    bylq = true;
                                                }
                                            }
                                            //这个有两个，说明已经领过了
                                            if (bylq) {
                                                Log.i(TAG, "该红包已经领取或被领完");
                                            } else {
                                                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                Log.i(TAG, "发现点击红包");
                                                detect_tm = Calendar.getInstance().getTimeInMillis();
                                                bAutoClickOpenDetail = true;
                                                bAutoClickHbItem = true;
                                                break;
                                            }
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                if (!bAutoClickHbItem) {
                                    Log.i(TAG, "假消息，假HB，退出");
                                    wx_group_name = "";
                                    back2Home();
                                    mHander.removeCallbacks(runnable);
                                }
                            } else {
                                Log.i(TAG, "假消息，没有红包");
                                wx_group_name = "";
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
                                wx_group_name = "";
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
                        } else {
                            List<AccessibilityNodeInfo> hbNodes1 = hd.findAccessibilityNodeInfosByViewId(HBNONETEXT_STRING_ID);
                            if (hbNodes1 != null && !hbNodes1.isEmpty()) {
                                try {
                                    if (hbNodes.get(0).getText().toString().contains("红包派完了")) {
                                        Log.i(TAG, "最终红包派完了");
                                        back2Home();
                                        mHander.removeCallbacks(runnable);
                                        bUnpackedSuccessful = false;
                                        bAutoClickOpenDetail = false;
                                        bAutoClickHbItem = false;
                                        bAutoClickOpenButton = false;
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
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
                // Log.i(TAG, className);
                if (className.contains("LauncherUI")) {
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
                                List<AccessibilityNodeInfo> hbNodes = hd.findAccessibilityNodeInfosByViewId(HB_LOGO_STRING_ID);
                                if (hbNodes != null && !hbNodes.isEmpty()) {
                                    for (int i = hbNodes.size() - 1; i >= 0; i--) {
                                        AccessibilityNodeInfo nodeInfo = hbNodes.get(i);
                                        try {
                                            if ("微信红包".equals(nodeInfo.getChild(1).getChild(0).getText().toString())) {
                                                //Log.i(TAG,"数量"+String.valueOf(nodeInfo.getChild(0).getChildCount()));//企业群3，个人群1
                                                boolean bylq = false;//是否已经领取判断
                                                //大于1说明的企业号发的红包，否则是个人发的
                                                if(nodeInfo.getChild(0).getChildCount()>1){
                                                    //个人发的红包
                                                    if(nodeInfo.getChild(0).getChild(1).getChild(1).getChildCount() > 1){
                                                        bylq = true;
                                                    }
                                                }
                                                else{
                                                    //个人发的红包
                                                    if(nodeInfo.getChild(0).getChild(0).getChild(1).getChildCount() > 1){
                                                        bylq = true;
                                                    }
                                                }
                                                //这个有两个，说明已经领过了
                                                if (bylq) {
                                                    Log.i(TAG, "该红包已经领取或被领完");
                                                } else {
                                                    nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                    Log.i(TAG, "发现点击红包");
                                                    detect_tm = Calendar.getInstance().getTimeInMillis();
                                                    bAutoClickOpenDetail = true;
                                                    bAutoClickHbItem = true;
                                                    break;
                                                }
                                            }
                                        } catch (Exception e) {
                                            //e.printStackTrace();
                                        }
                                    }
                                }

                            }
                        }
                    }

                } else if (className.contains("luckymoney.ui.LuckyMoneyNotHookReceiveUI")) {

                    for (int i = 0; i < 30; i++) {
                        try {
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
                                    i = 100;//退出循环
                                } else {
                                    boolean hasNodes = hasOneOfThoseNodes(
                                            WECHAT_BETTER_LUCK_CH, WECHAT_BETTER_LUCK_EN, WECHAT_EXPIRES_CH, WECHAT_WHOGIVEYOUAHB);
                                    if (hasNodes) {
                                        if (bAutoClickHbItem) {
                                            performGlobalAction(GLOBAL_ACTION_BACK);//打开红包后返回到聊天页面
                                        } else bAutoClickOpenDetail = false;
                                        i = 100;//退出循环
                                    }
                                }
                            }
                            Thread.sleep(100);
                        } catch (Exception e) {
                            e.printStackTrace();
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
        回头检查全部消息看有不有红包
     */

    private int hbJlCheck(int nLong) throws InterruptedException {
        int i = 0;
        int nStatus = 0;
        int nStatusCounter = 0;
        boolean backmode = false;
        String currentTitle = "";

        ArrayList<String> arrayLists = new ArrayList();
        for (i = 0; i < nLong; i++) {
            AccessibilityNodeInfo hd = getRootInActiveWindow();
            if (hd != null) {
                try {
                    switch (nStatus) {
                        case 0: {//点击到聊天列表页面
                            List<AccessibilityNodeInfo> goes = hd.findAccessibilityNodeInfosByViewId(HBRETURN_STRING_ID);
                            if (goes != null && !goes.isEmpty()) {
                                for (AccessibilityNodeInfo go : goes) {
                                    try {
                                        go.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        if (backmode) {
                                            nStatus = 3;
                                            nStatusCounter = 0;
                                        } else {
                                            nStatus = 1;
                                            nStatusCounter = 0;
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                }
                            } else {
                                List<AccessibilityNodeInfo> listTitles = hd.findAccessibilityNodeInfosByViewId(CHATLISTTITLE_STRING_ID);
                                if (listTitles != null && !listTitles.isEmpty()) {
                                    try {
                                        for (AccessibilityNodeInfo listTitle : listTitles) {
                                            listTitle.getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        }
                                        Log.i(TAG, "纠正点击1");
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                        break;
                        case 1: {//检查列表消息
                            backmode = false;
                            List<AccessibilityNodeInfo> nodeSnds = hd.findAccessibilityNodeInfosByViewId(SOUNDBUTTON_STRING_ID);//在列表窗口
                            if (nodeSnds != null && nodeSnds.isEmpty()) {
                                List<AccessibilityNodeInfo> listTitles = hd.findAccessibilityNodeInfosByViewId(CHATLISTUNREADMSG_STRING_ID);
                                if (listTitles != null && !listTitles.isEmpty()) {
                                    for (AccessibilityNodeInfo listTitle : listTitles) {
                                        try {
                                            AccessibilityNodeInfo ttNode = listTitle.getParent().getParent().getChild(1).getChild(0).getChild(0).getChild(0);
                                            if (!arrayLists.contains(ttNode.getText().toString())) {
                                                arrayLists.add(ttNode.getText().toString());
                                                Log.i(TAG, "点击红点列表1:" + ttNode.getText().toString());
                                                nNowMsgCounter++;
                                                currentTitle = ttNode.getText().toString() + "(10)";
                                                listTitle.getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                nStatusCounter = 0;
                                                nStatus = 2;
                                                break;
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                    }
                                    if (nStatus != 2) {//都访问了的话
                                        nStatusCounter++;
                                        if (nStatusCounter > 5) {
                                            //连续向下拉5次，都没有最新的群，说明到底了
                                            nStatusCounter = 0;
                                            Log.i(TAG, "进入向前检查模式");
                                            nStatus = 3;
                                        }
                                        /*
                                        try {
                                            listTitles.get(0).getParent().getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                                            Log.i(TAG, "向上退1");
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }*/

                                        List<AccessibilityNodeInfo> listviews = hd.findAccessibilityNodeInfosByViewId(CHATLISTWINDOWLISTVIEW_STRING_ID);
                                        if (listviews != null && !listviews.isEmpty()) {
                                            for (AccessibilityNodeInfo listview : listviews) {
                                                try {
                                                    listview.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                                                    Log.i(TAG, "向上退1");
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    nStatusCounter++;
                                    if (nStatusCounter > 5) {
                                        //连续向下拉5次，都没有最新的群，说明到底了
                                        nStatusCounter = 0;
                                        Log.i(TAG, "进入向前检查模式");
                                        nStatus = 3;
                                    }
                                    List<AccessibilityNodeInfo> listviews = hd.findAccessibilityNodeInfosByViewId(CHATLISTWINDOWLISTVIEW_STRING_ID);
                                    if (listviews != null && !listviews.isEmpty()) {
                                        for (AccessibilityNodeInfo listview : listviews) {
                                            try {
                                                listview.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                                                Log.i(TAG, "向上退2");
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                }
                            }

                        }
                        break;

                        case 2: {//检查窗口消息中的红包信息

                            List<AccessibilityNodeInfo> nodeSnds = hd.findAccessibilityNodeInfosByViewId(SOUNDBUTTON_STRING_ID);
                            if (nodeSnds != null && !nodeSnds.isEmpty()) {
                                List<AccessibilityNodeInfo> titleNodes = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                                if (titleNodes != null && !titleNodes.isEmpty()) {
                                    windowtitle = titleNodes.get(0).getText().toString();
                                    List<AccessibilityNodeInfo> hbNodes = hd.findAccessibilityNodeInfosByViewId(HB_LOGO_STRING_ID);
                                    if (hbNodes != null && !hbNodes.isEmpty()) {
                                        for (int j = hbNodes.size() - 1; j >= 0; j--) {
                                            AccessibilityNodeInfo nodeInfo = hbNodes.get(j);
                                            try {
                                                if ("微信红包".equals(nodeInfo.getChild(1).getChild(0).getText().toString())) {
                                                    //Log.i(TAG,"数量"+String.valueOf(nodeInfo.getChild(0).getChildCount()));//企业群3，个人群1
                                                    boolean bylq = false;//是否已经领取判断
                                                    //大于1说明的企业号发的红包，否则是个人发的
                                                    if(nodeInfo.getChild(0).getChildCount()>1){
                                                        //个人发的红包
                                                        if(nodeInfo.getChild(0).getChild(1).getChild(1).getChildCount() > 1){
                                                            bylq = true;
                                                        }
                                                    }
                                                    else{
                                                        //个人发的红包
                                                        if(nodeInfo.getChild(0).getChild(0).getChild(1).getChildCount() > 1){
                                                            bylq = true;
                                                        }
                                                    }
                                                    //这个有两个，说明已经领过了
                                                    if (bylq) {
                                                        Log.i(TAG, "该红包已经领取或被领完");
                                                    } else {
                                                        nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                                        chatlist_detect_tm = Calendar.getInstance().getTimeInMillis();
                                                        notify_detect_tm = 0;
                                                        detect_tm = 0;
                                                        Log.i(TAG, "发现点击红包");
                                                        nStatus = 4;
                                                        nStatusCounter = 0;
                                                        break;
                                                    }
                                                }
                                            } catch (Exception e) {
                                                //e.printStackTrace();
                                            }
                                        }
                                    }
                                }

                                if (nStatus != 4) {
                                    List<AccessibilityNodeInfo> moBtns = hd.findAccessibilityNodeInfosByViewId(HBMOREMSG_STRING_ID);
                                    if (moBtns != null && !moBtns.isEmpty()) {
                                        //com.tencent.mm:id/a5j
                                        List<AccessibilityNodeInfo> listvs = hd.findAccessibilityNodeInfosByViewId(CHATCONTENTWINDOWLISTVIEW_STRING_ID);
                                        if (listvs != null && !listvs.isEmpty()) {
                                            for (AccessibilityNodeInfo listv : listvs) {
                                                try {
                                                    listv.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                                //Log.i(TAG, "向上退");
                                            }
                                        }

                                    } else {
                                        nStatus = 0;
                                    }
                                }

                            } else {
                                List<AccessibilityNodeInfo> listTitles = hd.findAccessibilityNodeInfosByViewId(CHATLISTTITLE_STRING_ID);
                                if (listTitles != null && !listTitles.isEmpty()) {
                                    try {
                                        for (AccessibilityNodeInfo listTitle : listTitles) {
                                            listTitle.getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        }
                                        Log.i(TAG, "纠正点击2");
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                            }

                        }
                        break;

                        case 3: {
                            backmode = true;
                            List<AccessibilityNodeInfo> nodeSnds = hd.findAccessibilityNodeInfosByViewId(SOUNDBUTTON_STRING_ID);//在列表窗口
                            if (nodeSnds != null && nodeSnds.isEmpty()) {
                                List<AccessibilityNodeInfo> nodeInfos1 = hd.findAccessibilityNodeInfosByViewId(CHATLISTUNREADMSG_STRING_ID);
                                if (nodeInfos1 != null && !nodeInfos1.isEmpty()) {
                                    for (AccessibilityNodeInfo nodeInfo : nodeInfos1) {
                                        try {
                                            AccessibilityNodeInfo clickableParentNode = nodeInfo.getParent().getParent();
                                            AccessibilityNodeInfo ttNode = nodeInfo.getParent().getParent().getChild(1).getChild(0).getChild(0).getChild(0);
                                            Log.i(TAG, "点击有红点的列表2:" + ttNode.getText().toString());
                                            nNowMsgCounter++;
                                            currentTitle = ttNode.getText().toString() + "(10)";
                                            clickableParentNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                            nStatus = 2;
                                            break;
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }

                                if (nStatus != 2) {
                                    List<AccessibilityNodeInfo> listTitles = hd.findAccessibilityNodeInfosByViewId(CHATLISTTITLE_STRING_ID);
                                    if (listTitles != null && !listTitles.isEmpty()) {
                                        try {
                                            listTitles.get(0).getParent().getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                                            // Log.i(TAG, "向下拉");
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }

                        }
                        break;

                        case 4: {
                            List<AccessibilityNodeInfo> hbNodes = hd.findAccessibilityNodeInfosByViewId(HBOPENBUTTON_STRING_ID);
                            if (hbNodes != null && !hbNodes.isEmpty()) {
                                hbNodes.get(hbNodes.size() - 1).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                Log.i(TAG, "拆开红包");
                                Log.i(TAG, "Notify Time:" + String.valueOf(Calendar.getInstance().getTimeInMillis() - notify_detect_tm));
                                Log.i(TAG, "Detect Time:" + String.valueOf(Calendar.getInstance().getTimeInMillis() - detect_tm));
                                nStatus = 5;
                                nStatusCounter = 0;
                            } else {
                                List<AccessibilityNodeInfo> hbNodes1 = hd.findAccessibilityNodeInfosByViewId(HBNONETEXT_STRING_ID);
                                if (hbNodes1 != null && !hbNodes1.isEmpty()) {
                                    Log.i(TAG, "红包派完了");
                                    performGlobalAction(GLOBAL_ACTION_BACK);
                                    nStatus = 2;
                                }
                                nStatusCounter++;
                                if (nStatusCounter > 50) {
                                    nStatusCounter = 0;
                                    performGlobalAction(GLOBAL_ACTION_BACK);
                                    nStatus = 2;
                                }
                            }
                        }
                        break;

                        case 5: {
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
                                wx_group_name = "";
                                Log.i(TAG, "windowtitle=" + windowtitle);
                                Log.i(TAG, "currentTitle=" + currentTitle);
                                uploadHbInfo();
                                performGlobalAction(GLOBAL_ACTION_BACK);
                                nStatus = 2;
                            } else {
                                nStatusCounter++;
                                if (nStatusCounter > 50) {
                                    nStatusCounter = 0;
                                    performGlobalAction(GLOBAL_ACTION_BACK);
                                    nStatus = 2;
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
        // back2Home();
        Log.i(TAG, "全部消息再检查完毕");
        return nStatus;
    }


    /*
        获得微信钱包金额
     */

    private String getAndSendWxAmount() throws InterruptedException {

        String result = "未获取到";
        int i = 0;
        int nStatus = 0;
        for (i = 0; i < 150; i++) {
            AccessibilityNodeInfo hd = getRootInActiveWindow();
            if (hd != null) {
                try {
                    switch (nStatus) {
                        case 0: {//点击到聊天列表页面
                            List<AccessibilityNodeInfo> goes = hd.findAccessibilityNodeInfosByViewId(HBRETURN_STRING_ID);
                            if (goes != null && !goes.isEmpty()) {
                                for (AccessibilityNodeInfo go : goes) {
                                    go.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    Log.i(TAG,"获取余额点击返回键");
                                    nStatus = 1;
                                }
                            }
                        }
                        break;
                        case 1: {//点击“我”
                            List<AccessibilityNodeInfo> bottomBtns = hd.findAccessibilityNodeInfosByViewId(HBBOTTOMBTN_STRING_ID);
                            for (AccessibilityNodeInfo bottomBtn : bottomBtns) {
                                if (bottomBtn.getText() != null && bottomBtn.getText().toString().contains("我")) {
                                    bottomBtn.getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    Log.i(TAG,"获取余额点击我按钮");
                                    nStatus = 2;
                                }
                            }
                        }
                        break;
                        case 2: {
                            List<AccessibilityNodeInfo> btns = hd.findAccessibilityNodeInfosByText("支付");
                            if (btns != null && !btns.isEmpty()) {
                                for (AccessibilityNodeInfo btn : btns) {
                                    btn.getParent().getParent().getParent().getParent().getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    Log.i(TAG,"获取余额点击支付按钮");
                                    nStatus = 3;
                                }
                            }else{
                                List<AccessibilityNodeInfo> bottomBtns = hd.findAccessibilityNodeInfosByViewId(HBBOTTOMBTN_STRING_ID);
                                for (AccessibilityNodeInfo bottomBtn : bottomBtns) {
                                    if (bottomBtn.getText() != null && bottomBtn.getText().toString().contains("我")) {
                                        bottomBtn.getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        Log.i(TAG,"再次余额点击我按钮");
                                    }
                                }
                            }
                        }
                        break;

                        case 3: {
                            List<AccessibilityNodeInfo> btns = hd.findAccessibilityNodeInfosByText("钱包");
                            if (btns != null && !btns.isEmpty()) {
                                for (AccessibilityNodeInfo btn : btns) {
                                    try {
                                        result = btn.getParent().getChild(2).getText().toString();
                                    } catch (Exception e) {
                                        Log.i(TAG, e.getMessage());
                                        result = "获取异常";
                                    }
                                    Log.i(TAG, wx_user + ":" + result);
                                    performGlobalAction(GLOBAL_ACTION_BACK);
                                    Log.i(TAG,"查看钱包后，全局后退");
                                    nStatus = 4;
                                    break;
                                }
                            }
                        }
                        break;
                        case 4: {
                            List<AccessibilityNodeInfo> bottomBtns = hd.findAccessibilityNodeInfosByViewId(HBBOTTOMBTN_STRING_ID);
                            for (AccessibilityNodeInfo bottomBtn : bottomBtns) {
                                if (bottomBtn.getText() != null && bottomBtn.getText().toString().contains("微信")) {
                                    bottomBtn.getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    Log.i(TAG,"获取余额点击底部微信按钮");
                                    nStatus = 5;
                                }
                            }
                        }
                        break;
                        case 5: {
                            List<AccessibilityNodeInfo> nodeInfos1 = hd.findAccessibilityNodeInfosByViewId(CHATLISTTEXT_STRING_ID);
                            //找到了有消息条目，说明就进入了窗口了
                            if (nodeInfos1 != null && !nodeInfos1.isEmpty()) {
                                for (AccessibilityNodeInfo nodeInfo : nodeInfos1) {
                                    try {
                                        AccessibilityNodeInfo clickableParentNode = nodeInfo.getParent().getParent().getParent().getParent();
                                        clickableParentNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        Log.i(TAG,"随便点击一个群列表，进入聊天窗口");
                                        nStatus = 6;
                                        break;
                                    } catch (Exception e) {
                                        //e.printStackTrace();
                                    }
                                }
                            }else{
                                List<AccessibilityNodeInfo> bottomBtns = hd.findAccessibilityNodeInfosByViewId(HBBOTTOMBTN_STRING_ID);
                                for (AccessibilityNodeInfo bottomBtn : bottomBtns) {
                                    if (bottomBtn.getText() != null && bottomBtn.getText().toString().contains("微信")) {
                                        bottomBtn.getParent().getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        Log.i(TAG,"再次点击获取余额点击底部微信按钮");
                                    }
                                }
                            }

                        }
                        break;
                        case 6: {
                            List<AccessibilityNodeInfo> titleNodes = hd.findAccessibilityNodeInfosByViewId(WINDOWTITLETEXT_STRING_ID);
                            if (titleNodes != null && !titleNodes.isEmpty()) {
                                Log.i(TAG,"再次进入聊天窗口后退出");
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
        return result;
    }

    /*
        获得微信名称
     */
    private void getWxUserNameAndId() throws InterruptedException {

        int i = 0;
        try {
            boolean bClickReturn1 = false;
            boolean bClickReturn2 = false;
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
                    if (bClickReturn1 && !bClickReturn2) {
                        List<AccessibilityNodeInfo> bottomBtns = hd.findAccessibilityNodeInfosByViewId(HBBOTTOMBTN_STRING_ID);
                        for (AccessibilityNodeInfo bottomBtn : bottomBtns) {
                            if (bottomBtn.getText() != null && bottomBtn.getText().toString().contains("我")) {
                                bottomBtn.getParent().getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                bClickReturn2 = true;
                            }
                        }
                    }

                    if (bClickReturn1 && bClickReturn2) {
                        List<AccessibilityNodeInfo> wxnames = hd.findAccessibilityNodeInfosByViewId(HBWXUSER_STRING_ID);
                        List<AccessibilityNodeInfo> wxuserids = hd.findAccessibilityNodeInfosByViewId(HBWXUSERID_STRING_ID);
                        if (!wxnames.isEmpty() && !wxuserids.isEmpty()) {
                            wx_user = wxnames.get(0).getText().toString();
                            wx_userId = wxuserids.get(0).getText().toString();
                            wx_userId = wx_userId.substring("微信号：".length());
                            Log.i(TAG, "wxUser:" + wx_user);
                            Log.i(TAG, "wxUserId:" + wx_userId);
                            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("wxUser", wx_user);
                            editor.putString("wxUserId", wx_userId);
                            editor.commit();
                            back2Home();
                            break;
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
    }

    /*根据系统的配置，随机获得回复的词语*/
    private String generateCommentString() {
        String[] wordsArray = sharedPreferences.getString("edit_text_autoReplyText", "").split("\\|");
        if (wordsArray.length == 0) return "~^o^~";
        int rdmindex = (int) (Math.random() * wordsArray.length);
        Log.i(TAG,"回复词种类数："+String.valueOf(wordsArray.length)+"---随机选择序号:"+String.valueOf(rdmindex));
        return wordsArray[rdmindex];
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
    private Boolean send() {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        Boolean result = false;
        android.util.Log.i("maptrix", "nodeInfo is" + (nodeInfo != null ? nodeInfo.getClassName().toString() : "null"));
        if (nodeInfo != null) {
            List<AccessibilityNodeInfo> list = nodeInfo
                    .findAccessibilityNodeInfosByText("发送");
            if (list != null && list.size() > 0) {
                for (AccessibilityNodeInfo n : list) {
                    n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    result = true;
                }

            } else {
                List<AccessibilityNodeInfo> liste = nodeInfo
                        .findAccessibilityNodeInfosByText("Send");
                if (liste != null && liste.size() > 0) {
                    for (AccessibilityNodeInfo n : liste) {
                        n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        result = true;
                    }
                }
            }
        }
        return result;
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

    /**
     * 模拟输入字符串
     *
     * @param
     */
    private void inputString(String content) {
        Runtime runtime = Runtime.getRuntime();
        try {
            runtime.exec("input keyevent " + KeyEvent.KEYCODE_BUTTON_2);
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
        if (!wx_group_name.equals("")) {
            group_name = wx_group_name;
            wx_group_name = "";
        }
        Log.i(TAG, "group_name=" + group_name);
        values.put("group_name", group_name);
        values.put("group_name_md5", ComFunc.MD5(group_name));
        values.put("sender", sender);
        values.put("content", hbcontent);
        values.put("hb_amount", hb_amount.equals("") ? "0" : hb_amount);
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
        item.put("wxUserId", wx_userId);
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
    private void rdnonhbInfo(String group_name, String content, String last_send_person, String wxUser, int len) throws JSONException {
        final JSONObject obj = new JSONObject();
        JSONArray array = new JSONArray();
        JSONObject item = new JSONObject();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
        String strGroupNameMd5 = "";
        strGroupNameMd5 = ComFunc.MD5(group_name);
        // Log.i(TAG, group_name);
        // Log.i(TAG, strGroupNameMd5);
        item.put("group_name", group_name);
        item.put("content", content);
        item.put("group_name_md5", strGroupNameMd5);
        item.put("last_send_person", last_send_person);
        item.put("wxUser", wxUser);
        item.put("wxUserId", wx_userId);
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
                            //Log.i(TAG,"blackGroups:"+remoteParam.blackGroups);
                            //Log.i(TAG,"inviteAgainGroup:"+remoteParam.inviteAgainGroup);
                            //Log.i(TAG,"autoInviteParam:"+remoteParam.autoInviteParam);
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
