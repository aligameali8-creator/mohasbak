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
        // التطبيق مفتوح أصلاً: نبلغ الواجهة فوراً (مشاركة رسالة/إشعار رسالة بنك)
        try { if (getBridge() != null) getBridge().eval("window.__nativeConsume && window.__nativeConsume()", null); } catch (Exception e) { /* */ }
    }

    /** زر + في الودجت يرسل إجراءً يقرؤه التطبيق عند العودة */
    private void stashAction(Intent intent) {
        if (intent == null) return;
        String action = intent.getStringExtra("mohasbak_action");
        // مشاركة نص (رسالة البنك) من تطبيق الرسائل ← «من رسالة البنك»
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String t = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (t != null && t.length() > 0) {
                getSharedPreferences(MohasbakWidget.PREFS, MODE_PRIVATE).edit().putString("pending_share", t.length() > 5000 ? t.substring(0, 5000) : t).apply();
            }
            intent.setAction(Intent.ACTION_MAIN);
        }
        if (action != null) {
            getSharedPreferences(MohasbakWidget.PREFS, MODE_PRIVATE).edit().putString("pending_action", action).apply();
            intent.removeExtra("mohasbak_action");
        }
    }
}
