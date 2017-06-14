package com.mzd.mtakem2.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

import com.mzd.mtakem2.MainActivity;

import org.json.JSONObject;

/**
 * Created by Administrator on 2017/6/13 0013.
 */

public class AuthorizedCheckThread extends Thread {
    private static final String TAG = "AuthorizedCheckThread";
    private Handler mHandler;
    private Handler mMainHandler;
    private Context mContext;

    public AuthorizedCheckThread(Context context,Handler mainhandler){
        mContext = context;
        mMainHandler = mainhandler;
    }

    @Override
    public void run() {
        super.run();
        Looper.prepare();
        mHandler = new Handler();
        mHandler.postDelayed(runnable,1000);
        Log.i(TAG,"AuthorizedCheckThread Start");
        Log.i(TAG, "ThreadId:" + String.valueOf(Thread.currentThread().getId()));
        Looper.loop();
    }

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            try{

                String result = HttpUtils.doGet("http://39.108.106.173/Mtakem2Web/httpfun.jsp?action=activatesoft&mac="+ComFunc.getMac()+"&rand="+String.valueOf(new java.util.Date().getTime()));
                Message msg = mMainHandler.obtainMessage();
                msg.what = MainActivity.UPDATE_AUTHORIZE_STATUS;
                Bundle data = new Bundle();
                data.putString("result",result);
                msg.setData(data);
                mMainHandler.sendMessage(msg);
                Log.i(TAG,result);
            }catch (Exception e){
                e.printStackTrace();
            }

            mHandler.postDelayed(this,1000*3600*2);
        }
    };
}
