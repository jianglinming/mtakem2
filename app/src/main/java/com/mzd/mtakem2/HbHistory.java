package com.mzd.mtakem2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Created by Administrator on 2017/6/1 0001.
 */

public class HbHistory extends SQLiteOpenHelper {

    private static final String TAG = "HbHistory";
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "HbHistory.db";
    private final static String TABLE_NAME = "hblist";

    public HbHistory(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        Log.i(TAG, "数据库构造器");
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        Log.i(TAG, "数据库打开");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE hblist (id INTEGER PRIMARY KEY AUTOINCREMENT,group_name TEXT,sender TEXT, content TEXT," +
                " unpacked_time TEXT, notify_consuming INT,chatlist_consuming INT,chatwindow_consuming INT,hb_amount REAL)");
        //db.execSQL("CREATE TABLE nohb (id INTEGER PRIMARY KEY AUTOINCREMENT,group_name TEXT,wxUser TEXT,receive_time TEXT,len INT)");//预留离线保存历史记录
        Log.i(TAG, "创建数据表");
    }
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "数据库更新");
    }

    //插入方法
    public void insert(ContentValues values){
        //获取SQLiteDatabase实例
        SQLiteDatabase db = getWritableDatabase();
        //插入数据库中
        db.insert(TABLE_NAME, null, values);
        db.close();
    }

    //查询方法
    public Cursor query(){
        SQLiteDatabase db = getReadableDatabase();
        //获取Cursor
        Cursor c = db.query(TABLE_NAME, null, null, null, null, null, null, null);
        return c;

    }

    //根据唯一标识_id  来删除数据
    public void delete(int id){
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, "id=?", new String[]{String.valueOf(id)});
        db.close();
    }

    //更新数据库的内容
    public void update(ContentValues values, String whereClause, String[]whereArgs){
        SQLiteDatabase db = getWritableDatabase();
        db.update(TABLE_NAME, values, whereClause, whereArgs);
        db.close();
    }

    /**
     * 删除数据库
     *
     * @param context
     * @return
     */
    public boolean deleteDatabase(Context context) {
        return context.deleteDatabase(DATABASE_NAME);
    }

}
