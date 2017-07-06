package com.mzd.mtakem2;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.mzd.mtakem2.utils.ComFunc;

import org.w3c.dom.Text;

/**
 * Created by Administrator on 2017/6/14 0014.
 */

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        TextView textView = (TextView)findViewById(R.id.textView11);
        TextView txtVerType = (TextView)findViewById(R.id.textView13);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String verType = sharedPreferences.getString("verType","manmode");
        if(verType.equals("advanced")){
            txtVerType.setText(getString(R.string.advanced));
        }
        else if(verType.equals("automode")){
            txtVerType.setText(getString(R.string.automode));
        }
        else{
            txtVerType.setText(getString(R.string.manmode));
        }
        textView.setText(ComFunc.getVersion(this));
    }
}
