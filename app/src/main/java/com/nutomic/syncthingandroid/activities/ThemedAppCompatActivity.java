package com.nutomic.syncthingandroid.activities;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.nutomic.syncthingandroid.service.Constants;

/**
 * Provides a themed instance of AppCompatActivity.
 */
public abstract class ThemedAppCompatActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Load theme.
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        Integer prefAppTheme = Integer.parseInt(sharedPreferences.getString(Constants.PREF_APP_THEME, Constants.APP_THEME_FOLLOW_SYSTEM));
        AppCompatDelegate.setDefaultNightMode(prefAppTheme);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
    }
}
