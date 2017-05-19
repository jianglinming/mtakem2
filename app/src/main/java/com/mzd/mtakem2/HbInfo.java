package com.mzd.mtakem2;

import android.graphics.Rect;

/**
 * Created by Administrator on 2017/5/16.
 */

public class HbInfo {

    private String chattingWindowTitle = "";
    private String hbSender = "";
    private String hbDescription = "";
    private String happendedTime = "";
    private String contextString = "";
    public Rect hbRect = new Rect();
    public boolean bIsGetBySelf = false;
    public boolean bIsAHb = false;

    public void SetChatWindowTitle(String title) {
        chattingWindowTitle = title;
    }

    public String GetChatWindowTitle() {
        return chattingWindowTitle;
    }

    public void SetSender(String sender) {
        hbSender = sender;
    }

    public String GetSender() {
        return hbSender;
    }

    public void SetDescription(String des) {
        hbDescription = des;
    }

    public String GetDescription() {
        return hbDescription;
    }

    public void SetHappendedTime(String hptime) {
        happendedTime = hptime;
    }

    public String GetHappendedTime() {
        return happendedTime;
    }

    public void SetContextString(String context) {
        contextString = context;
    }
    public String GetContextString() {
        return contextString;
    }

    @Override
    public String toString() {
        return chattingWindowTitle + "|" + hbSender + "|" + hbDescription + "|" + String.format("%b", bIsGetBySelf) + "|" + happendedTime + "|" + hbRect.toString() + "|" +contextString;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof HbInfo) {
            if (obj != null) {
                return ((HbInfo) obj).toString().equals(this.toString());
            } else return false;
        } else return false;

    }
}
