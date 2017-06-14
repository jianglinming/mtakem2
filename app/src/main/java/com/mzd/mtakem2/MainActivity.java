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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
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

public class MainActivity extends AppCompatActivity implements AccessibilityManager.AccessibilityStateChangeListener,SharedPreferences.OnSharedPreferenceChangeListener  {

    private static final String TAG = "MainActivity";
    //开关切换按钮
    private TextView pluginStatusText;
    private ImageView pluginStatusIcon;
    //AccessibilityService 管理
    private AccessibilityManager accessibilityManager;


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
                        try{
                            JSONObject obj = new JSONObject(data.getString("result"));
                            LinearLayout linearLayout = (LinearLayout)findViewById(R.id.lbtn_activate);
                            TextView textView = (TextView)findViewById(R.id.textView8);
                            if(obj.getBoolean("canuse")){
                                linearLayout.setClickable(false);
                                textView.setText(getText(R.string.activated_success));
                            }
                            else{
                                linearLayout.setClickable(true);
                                textView.setText(getText(R.string.activate));
                            }
                        }
                        catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                    break;
                    case UPDATE_AUTHORIZE_STATUS_FROMBUTTON:{
                        Bundle data = msg.getData();
                        updateAuthorizeStatus(data.getString("result"));
                        try{
                            JSONObject obj = new JSONObject(data.getString("result"));
                            LinearLayout linearLayout = (LinearLayout)findViewById(R.id.lbtn_activate);
                            TextView textView = (TextView)findViewById(R.id.textView8);
                            if(obj.getBoolean("canuse")){
                                Toast.makeText(getApplicationContext(), getText(R.string.activated_success), Toast.LENGTH_SHORT).show();
                                linearLayout.setClickable(false);
                                textView.setText(getText(R.string.activated_success));
                            }
                            else{
                                Toast.makeText(getApplicationContext(), getText(R.string.soft_expired), Toast.LENGTH_SHORT).show();
                                linearLayout.setClickable(true);
                                textView.setText(getText(R.string.click_activate));
                            }
                        }
                        catch (Exception e){
                            Toast.makeText(getApplicationContext(), getText(R.string.activated_fail), Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        }
                    }
                    break;

                }
            }catch (Exception e){
                e.printStackTrace();
            }
            return false;
        }
    });

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        LinearLayout linearLayout = (LinearLayout)findViewById(R.id.lbtn_activate);
        if (key.equals("canUse")) {
            Log.i(TAG,"canUsechage");
            if(sharedPreferences.getBoolean(key, true)){
                linearLayout.setClickable(false);
            }else{
                linearLayout.setClickable(true);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //获得mac地址
        TextView txtMac = (TextView) findViewById(R.id.txtMac);
        txtMac.setText(ComFunc.getMac());
        //获得版本
        TextView txtVer = (TextView) findViewById(R.id.txtVer);
        txtVer.setText(ComFunc.getVersion(this));

        pluginStatusText = (TextView) findViewById(R.id.textView3);
        pluginStatusIcon = (ImageView) findViewById(R.id.imageView);

        //监听AccessibilityService 变化
        accessibilityManager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        accessibilityManager.addAccessibilityStateChangeListener(this);
        updateServiceStatus();
        new AuthorizedCheckThread(this,mHandler).start();
        Log.i(TAG,"OnMainActivityCreate");
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

    public void openabout(View view){
        Intent intent = new Intent(this,AboutActivity.class);
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
    public void copyMactoClip(View view){
        ClipData clip = ClipData.newPlainText("label", ComFunc.getMac());
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardManager.setPrimaryClip(clip);
        Toast.makeText(this, "已将本机Mac码复制到粘贴板", Toast.LENGTH_SHORT).show();
    }

    //在线激活
    private void updateAuthorizeStatus(String result){
        TextView txtActivateMsg = (TextView) findViewById(R.id.txtActivateMsg);
        try{
            JSONObject obj = new JSONObject(result);
            boolean bCanUse = obj.getBoolean("canuse");
            String status = obj.getString("status");
            if(bCanUse){
                txtActivateMsg.setText(status + " left "+ obj.getString("leftusehours") +" h");
            }
            else{
                txtActivateMsg.setText(status + " expired " + obj.getString("msg") );
            }
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("canUse",bCanUse);
            editor.commit();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
    public void activateSoft(View view){
        HttpUtils.doGetAsyn("http://39.108.106.173/Mtakem2Web/httpfun.jsp?action=activatesoft&mac="+ComFunc.getMac()+"&rand="+String.valueOf(new java.util.Date().getTime()), new HttpUtils.CallBack() {
            @Override
            public void onRequestComplete(String result) {
                Message msg = mHandler.obtainMessage();
                msg.what = UPDATE_AUTHORIZE_STATUS_FROMBUTTON;
                Bundle data = new Bundle();
                data.putString("result",result);
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
