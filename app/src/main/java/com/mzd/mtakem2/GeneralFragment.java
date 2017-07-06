package com.mzd.mtakem2;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;

/**
 * Created by Administrator on 2017/5/10.
 */

public class GeneralFragment extends PreferenceFragment {
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String verString = sharedPreferences.getString("verType", "manmode");
            if (verString.equals("advanced")) {
                addPreferencesFromResource(R.xml.preference);
            } else if (verString.equals("automode")) {
                addPreferencesFromResource(R.xml.preference_automode);
            } else {
                addPreferencesFromResource(R.xml.preference_manmode);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
