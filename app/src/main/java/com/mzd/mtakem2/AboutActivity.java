package com.mzd.mtakem2;

import android.os.Bundle;
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
        textView.setText(ComFunc.getVersion(this));
    }
}
