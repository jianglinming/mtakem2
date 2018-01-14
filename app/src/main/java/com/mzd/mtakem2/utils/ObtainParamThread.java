package com.mzd.mtakem2.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import com.mzd.mtakem2.R;
import com.mzd.mtakem2.RemoteParam;

import org.json.JSONObject;

import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * Created by Administrator on 2018/1/14.
 */

public class ObtainParamThread extends Thread {
    private static final String TAG = "ObtainParamThread";
    private Handler mHandler;
    private Context mContext;
    private String deviceId;
    private RemoteParam remoteParam;

    public ObtainParamThread(Context context,String deviceId,RemoteParam remoteParam){
        mContext = context;
        this.deviceId = deviceId;
        this.remoteParam = remoteParam;
    }

    @Override
    public void run() {
        super.run();
        Looper.prepare();
        mHandler = new Handler();
        mHandler.postDelayed(runnable, 1000);
        Log.i(TAG,"ObtainParamThread Start");
        Log.i(TAG, "ThreadId:" + String.valueOf(Thread.currentThread().getId()));
        Looper.loop();
    }

    private Runnable runnable = new Runnable(){
        @Override
        public void run() {
            try{

                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
                String wxUser = sharedPreferences.getString("wxUser","");

                String result = HttpUtils.doGet(mContext.getText(R.string.uribase)+"/httpfun.jsp?action=GetParam&wxUser="+URLEncoder.encode(wxUser,"utf-8")+"&deviceId="+deviceId);
                if(result!=null){
                    Log.i(TAG,"result:"+ URLDecoder.decode(result,"utf-8"));
                    JSONObject objResult = new JSONObject(URLDecoder.decode(result, "utf-8"));
                    if(objResult.getBoolean("result")){
                        remoteParam.blackGroups = objResult.getString("blackGroups");
                        remoteParam.inviteAgainGroup = objResult.getString("inviteAgainGroup");
                        remoteParam.autoInviteParam = objResult.getString("autoInviteParam");
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("autoInviteParam", remoteParam.autoInviteParam);
                        editor.commit();
                    }
                }
                else{
                    Log.i(TAG,"null");
                }
            }
            catch (Exception e){
                e.printStackTrace();
            }
            mHandler.postDelayed(this,1000*120);
        }
    };

    public void stopThread(){
        mHandler.removeCallbacks(runnable);
        Log.i(TAG,"ObtainParamThread Stop");
    }
}
