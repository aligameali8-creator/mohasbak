package com.mohasbak.app;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MohasbakNativePlugin.class);
        super.onCreate(savedInstanceState);
        stashAction(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        stashAction(intent);
    }

    /** زر + في الودجت يرسل إجراءً يقرؤه التطبيق عند العودة */
    private void stashAction(Intent intent) {
        if (intent == null) return;
        String action = intent.getStringExtra("mohasbak_action");
        if (action != null) {
            getSharedPreferences(MohasbakWidget.PREFS, MODE_PRIVATE).edit().putString("pending_action", action).apply();
            intent.removeExtra("mohasbak_action");
        }
    }
}
