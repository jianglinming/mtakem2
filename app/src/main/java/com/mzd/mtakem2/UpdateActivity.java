package com.mzd.mtakem2;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import com.mzd.mtakem2.utils.UpdateTask;

public class UpdateActivity extends AppCompatActivity {
    private static final String TAG = "UpdateActivity";

    private static final int MSG_GETLATESTVERSION = 1;
    private String apk_download_url;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final TextView txtMsg = (TextView)findViewById(R.id.textView7);
        final Button updateBtn = (Button)findViewById(R.id.btn_update);
        updateBtn.setVisibility(Button.INVISIBLE);
        txtMsg.setText("");
        updateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                executeUpdate();
            }
        });

        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what){
                    case MSG_GETLATESTVERSION:{
                        Bundle data = msg.getData();
                        String val = data.getString("value");
                        try {
                            JSONObject jsonObject = new JSONObject(val);
                            String remoteLatestVer = jsonObject.getString("tag_name");
                            String nowVer = getVersion();
                            Log.i(TAG,remoteLatestVer+"=="+nowVer);
                            if(remoteLatestVer.equals(nowVer)){
                                txtMsg.setText(getString(R.string.neednot_update));
                                updateBtn.setVisibility(Button.INVISIBLE);
                            }else {
                                txtMsg.setText(getString(R.string.find_latest_version));
                                updateBtn.setVisibility(Button.VISIBLE);
                                JSONArray jsonArray = jsonObject.getJSONArray("assets");
                                JSONObject asset = jsonArray.getJSONObject(0);
                                if(asset!=null){
                                    apk_download_url = asset.getString("browser_download_url");
                                }
                            }
                        } catch (Exception e) {
                            txtMsg.setText(getString(R.string.remote_check_error));
                            updateBtn.setVisibility(Button.INVISIBLE);
                            e.printStackTrace();
                        }
                    }
                    break;
                    default:{
                    }
                    break;
                }
                super.handleMessage(msg);

            }

        };

        //handler.post(new Runnable(){});这种做法依然被任务在主线程中运行。
        //创建后台线程，获取远程版本
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(getString(R.string.url_update_app));
                    conn = (HttpURLConnection) url
                            .openConnection();
                    //使用GET方法获取
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        Log.i(TAG, "response 200");
                        InputStream is = conn.getInputStream();
                        String result = readMyInputStream(is);
                        Message msg = new Message();
                        msg.what = MSG_GETLATESTVERSION;
                        Bundle data = new Bundle();
                        data.putString("value", result);
                        msg.setData(data);
                        handler.sendMessage(msg);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (conn != null) conn.disconnect();
                }

            }
        }).start();

    }


    private void checkVersion() {

    }

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
            String errorStr = "获取数据失败。";
            return errorStr;
        }
        return new String(result);
    }

    /**
     * 获取指定URL的响应字符串
     *
     * @param urlString
     * @return
     */
    private String getURLResponse(String urlString) {
        HttpURLConnection conn = null; //连接对象
        InputStream is = null;
        String resultData = "";
        try {
            URL url = new URL(urlString); //URL对象
            conn = (HttpURLConnection) url.openConnection(); //使用URL打开一个链接
            conn.setDoInput(true); //允许输入流，即允许下载
            conn.setDoOutput(true); //允许输出流，即允许上传
            conn.setUseCaches(false); //不使用缓冲
            conn.setRequestMethod("GET"); //使用get请求
            is = conn.getInputStream();   //获取输入流，此时才真正建立链接
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader bufferReader = new BufferedReader(isr);
            String inputLine = "";
            while ((inputLine = bufferReader.readLine()) != null) {
                resultData += inputLine + "\n";
            }

        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }

        return resultData;
    }

    /**
     * 2  * 获取版本号
     * 3  * @return 当前应用的版本号
     * 4
     */
    public String getVersion() {
        try {
            PackageManager manager = this.getPackageManager();
            PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);
            String version = info.versionName;
            return version;
        } catch (Exception e) {
            e.printStackTrace();
            return this.getString(R.string.can_not_find_version_name);
        }
    }

    //检查更新
    private void executeUpdate() {

        if(apk_download_url==null) return;

        // declare the dialog as a member field of your activity
        ProgressDialog mProgressDialog;
        // instantiate it within the onCreate method
        mProgressDialog = new ProgressDialog(UpdateActivity.this);
        mProgressDialog.setMessage(getString(R.string.download_processing));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(true);
        // execute this when the downloader must be fired
        final UpdateTask downloadTask = new UpdateTask(getApplicationContext(), mProgressDialog);
        downloadTask.execute(this.apk_download_url);
        mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                downloadTask.cancel(true);
            }
        });


    }

}
