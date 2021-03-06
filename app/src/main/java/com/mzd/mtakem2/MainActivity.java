package com.mzd.mtakem2;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mzd.mtakem2.utils.AuthorizedCheckThread;
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
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity implements AccessibilityManager.AccessibilityStateChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "MainActivity";
    //开关切换按钮
    private TextView pluginStatusText;
    private ImageView pluginStatusIcon;
    //AccessibilityService 管理
    private AccessibilityManager accessibilityManager;

    Handler opwxHandler = new Handler();
    Runnable opwxRunable = new Runnable() {
        @Override
        public void run() {
            try {
                // 通过包名获取要跳转的app，创建intent对象
                Intent intent = getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
                // 这里如果intent为空，就说名没有安装要跳转的应用嘛
                if (intent != null) {
                    // 这里跟Activity传递参数一样的嘛，不要担心怎么传递参数，还有接收参数也是跟Activity和Activity传参数一样
                    intent.putExtra("name", "Liu xiang");
                    intent.putExtra("birthday", "1983-7-13");
                    startActivity(intent);
                } else {
                    // 没有安装要跳转的app应用，提醒一下
                    Toast.makeText(getApplicationContext(), "哟，赶紧下载安装这个APP吧", Toast.LENGTH_LONG).show();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
            opwxHandler.postDelayed(opwxRunable,1000*5);
        }
    };

    public static final int UPDATE_AUTHORIZE_STATUS = 1;
    public static final int UPDATE_AUTHORIZE_STATUS_FROMBUTTON = 2;
    //认证检查线程
    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            try {
                switch (msg.what) {
                    case UPDATE_AUTHORIZE_STATUS: {
                        Bundle data = msg.getData();
                        updateAuthorizeStatus(data.getString("result"));
                        try {
                            //Log.i(TAG, data.getString("result"));
                            JSONObject obj = new JSONObject(data.getString("result"));
                            LinearLayout linearLayout = (LinearLayout) findViewById(R.id.lbtn_activate);
                            TextView textView = (TextView) findViewById(R.id.textView8);
                            if (obj.getBoolean("canuse")) {
                                linearLayout.setClickable(false);
                                textView.setText(getText(R.string.activated_success));
                            } else {
                                linearLayout.setClickable(true);
                                textView.setText(getText(R.string.activate));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                    case UPDATE_AUTHORIZE_STATUS_FROMBUTTON: {
                        Bundle data = msg.getData();
                        updateAuthorizeStatus(data.getString("result"));
                        try {
                            JSONObject obj = new JSONObject(data.getString("result"));
                            LinearLayout linearLayout = (LinearLayout) findViewById(R.id.lbtn_activate);
                            TextView textView = (TextView) findViewById(R.id.textView8);
                            if (obj.getBoolean("canuse")) {
                                Toast.makeText(getApplicationContext(), getText(R.string.activated_success), Toast.LENGTH_SHORT).show();
                                linearLayout.setClickable(false);
                                textView.setText(getText(R.string.activated_success));
                            } else {
                                Toast.makeText(getApplicationContext(), getText(R.string.soft_expired), Toast.LENGTH_SHORT).show();
                                linearLayout.setClickable(true);
                                textView.setText(getText(R.string.click_activate));
                            }
                        } catch (Exception e) {
                            Toast.makeText(getApplicationContext(), getText(R.string.activated_fail) + data.getString("result"), Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        }
                    }
                    break;

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
    });

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case R.id.resetwxuser: {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("wxUser", "");
                editor.putString("wxUserId", "");
                editor.commit();
                TextView txtWxUser = (TextView) findViewById(R.id.txtWxUser);
                txtWxUser.setText("()");
            }
            break;
        }
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.lbtn_activate);
        if (key.equals("canUse")) {
            if (sharedPreferences.getBoolean(key, true)) {
                linearLayout.setClickable(false);
            } else {
                linearLayout.setClickable(true);
            }
        }
        if (key.equals("wxUser")) {
            TextView txtWxUser = (TextView) findViewById(R.id.txtWxUser);
            txtWxUser.setText(sharedPreferences.getString("wxUser", "") + "(" + sharedPreferences.getString("wxUserId", "") + ")");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //获得mac地址
        TextView txtMac = (TextView) findViewById(R.id.txtMac);
        txtMac.setText(ComFunc.getDeviceId(this));
        //获得版本
        TextView txtVer = (TextView) findViewById(R.id.txtVer);
        txtVer.setText(ComFunc.getVersion(this));

        pluginStatusText = (TextView) findViewById(R.id.textView3);
        pluginStatusIcon = (ImageView) findViewById(R.id.imageView);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        TextView txtWxUser = (TextView) findViewById(R.id.txtWxUser);
        txtWxUser.setText(sharedPreferences.getString("wxUser", "") + "(" + sharedPreferences.getString("wxUserId", "") + ")");


        //监听AccessibilityService 变化
        accessibilityManager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        accessibilityManager.addAccessibilityStateChangeListener(this);
        updateServiceStatus();
        new AuthorizedCheckThread(this, mHandler).start();
        Log.i(TAG, "OnMainActivityCreate");
        //opwxHandler.postDelayed(opwxRunable,5000);
    }


    //打开无障碍设置
    public void openAccessibility(View view) {
        try {
            Intent accessibleIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(accessibleIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void openabout(View view) {
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
    }

    //打开设置
    public void openSettings(View view) {
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        startActivity(settingsIntent);
    }

    //检查更新
    public void openCheckUpdate(View view) throws Exception {
        Intent updateIntent = new Intent(this, UpdateActivity.class);
        startActivity(updateIntent);
    }

    //复制mac码
    public void copyMactoClip(View view) {
        ClipData clip = ClipData.newPlainText("label", ComFunc.getDeviceId(this));
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardManager.setPrimaryClip(clip);
        Toast.makeText(this, "已将本机Mac码复制到粘贴板", Toast.LENGTH_SHORT).show();
    }

    //在线激活
    private void updateAuthorizeStatus(String result) {
        TextView txtActivateMsg = (TextView) findViewById(R.id.txtActivateMsg);
        try {
            JSONObject obj = new JSONObject(result);
            boolean bCanUse = obj.getBoolean("canuse");
            String verType = obj.getString("verType");
            String status = obj.getString("status");

            if (bCanUse) {
                txtActivateMsg.setText(status + " left " + obj.getString("leftusehours") + " h");
            } else {
                txtActivateMsg.setText(status + " expired " + obj.getString("msg"));
            }

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String nowVerType = sharedPreferences.getString("verType", "manmode");
            if (!verType.equals(nowVerType)) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("verType", verType);
                if ("automode".equals(verType)) {
                    editor.putBoolean("autoRecept", false);
                    editor.putBoolean("autoQuitGroup", false);
                    editor.putBoolean("cloudChatRec", false);
                    Log.i(TAG, "启用无人值守版");
                } else if ("advanced".equals(verType)) {
                    Log.i(TAG, "启用高级云服务版");
                } else {
                    editor.putBoolean("autoMode", false);
                    editor.putBoolean("check_box_autoReply", false);
                    editor.putBoolean("autoRecept", false);
                    editor.putBoolean("autoQuitGroup", false);
                    editor.putBoolean("cloudChatRec", false);
                    Log.i(TAG, "启用简易版");
                }
                editor.putBoolean("canUse", bCanUse);
                editor.commit();
            } else {
                //Log.i(TAG,"相同版本模式");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void activateSoft(View view) {
        String device_model = Build.MODEL; // 设备型号 。
        String version_release = Build.VERSION.RELEASE; // 设备的系统版本 。
        String phone = "unknownphone";
        try {
            phone = URLEncoder.encode(device_model + "(" + version_release + ")", "gbk");
        } catch (UnsupportedEncodingException e) {
            phone = "unknownphone";
            e.printStackTrace();
        }
        HttpUtils.doGetAsyn(getText(R.string.uribase) + "/httpfun.jsp?action=activatesoft&phone=" + phone + "&mac=" + ComFunc.getDeviceId(this) + "&rand=" + String.valueOf(new java.util.Date().getTime()), new HttpUtils.CallBack() {
            @Override
            public void onRequestComplete(String result) {
                Message msg = mHandler.obtainMessage();
                msg.what = UPDATE_AUTHORIZE_STATUS_FROMBUTTON;
                Bundle data = new Bundle();
                data.putString("result", result);
                msg.setData(data);
                mHandler.sendMessage(msg);
            }
        });
    }

    /**
     * 更新当前 HongbaoService 显示状态
     */
    private void updateServiceStatus() {
        if (isServiceEnabled()) {
            pluginStatusText.setText(R.string.service_off);
            pluginStatusIcon.setBackgroundResource(R.mipmap.ic_stop);
        } else {
            pluginStatusText.setText(R.string.service_on);
            pluginStatusIcon.setBackgroundResource(R.mipmap.ic_start);
        }
    }

    /**
     * 获取 HongbaoService 是否启用状态
     *
     * @return
     */
    private boolean isServiceEnabled() {
        List<AccessibilityServiceInfo> accessibilityServices =
                accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (AccessibilityServiceInfo info : accessibilityServices) {
            if (info.getId().equals(getPackageName() + "/.MtakemService")) {
                return true;
            }
        }
        return false;
    }


    @Override
    public void onAccessibilityStateChanged(boolean enabled) {
        updateServiceStatus();
    }


}
