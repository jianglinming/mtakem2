package com.mzd.mtakem2.utils;


import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mzd.mtakem2.HbHistory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Objects;
import java.util.Vector;

/**
 * Created by Administrator on 2017/6/7 0007.
 */

public class HbDataCheckThread extends Thread {
    private static final String TAG = "HbDataCheckThread";
    private Handler mHandler;
    private Context mContext;
    private Object lockkey;

    public HbDataCheckThread(Context context, Object obj){
        mContext = context;
        lockkey = obj;
    }

    @Override
    public void run() {
        super.run();
        Looper.prepare();
        mHandler = new Handler();
        mHandler.postDelayed(runnable, 1000);
        Log.i(TAG,"HbDataCheckThread Start");
        Log.i(TAG, "ThreadId:" + String.valueOf(Thread.currentThread().getId()));
        Looper.loop();
    }


    public void stopThread(){
        mHandler.removeCallbacks(runnable);
        Log.i(TAG,"HbDataCheckThread Stop");
    }

    /*
           检查数据库定时任务，是否有HB数据,如果有就一次性全部上传
        */
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {

            try {
                final HbHistory hb = new HbHistory(mContext);
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
                            item.put("device", Build.MODEL + "(" + Build.VERSION.RELEASE + ")");
                            item.put("machine_id", ComFunc.getMac());
                            item.put("mtakem2ver", ComFunc.getVersion(mContext));
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
                                    synchronized (lockkey) {
                                        Log.i(TAG, "上传成功");
                                        int i = 0;
                                        for (i = 0; i < v.size(); i++) {
                                            hb.delete((int) v.get(i));
                                        }
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
            mHandler.postDelayed(this,1000*60*5);
        }
    };


}
