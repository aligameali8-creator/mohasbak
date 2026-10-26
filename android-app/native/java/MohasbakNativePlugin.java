package com.mohasbak.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.concurrent.Executor;

@CapacitorPlugin(name = "MohasbakNative")
public class MohasbakNativePlugin extends Plugin {
    private static final int AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    /** يحفظ نصوص الودجت ويحدّث كل الودجتات على الشاشة */
    @PluginMethod
    public void updateWidget(PluginCall call) {
        Context ctx = getContext();
        ctx.getSharedPreferences(MohasbakWidget.PREFS, Context.MODE_PRIVATE).edit()
            .putString("title", call.getString("title", "مسموح تصرف اليوم"))
            .putString("big", call.getString("big", ""))
            .putString("line1", call.getString("line1", ""))
            .putString("line2", call.getString("line2", ""))
            .apply();
        MohasbakWidget.refreshAll(ctx);
        call.resolve();
    }

    @PluginMethod
    public void consumeAction(PluginCall call) {
        SharedPreferences p = getContext().getSharedPreferences(MohasbakWidget.PREFS, Context.MODE_PRIVATE);
        String a = p.getString("pending_action", null);
        p.edit().remove("pending_action").apply();
        JSObject r = new JSObject();
        r.put("action", a);
        call.resolve(r);
    }

    @PluginMethod
    public void canBiometric(PluginCall call) {
        int s = BiometricManager.from(getContext()).canAuthenticate(AUTH);
        JSObject r = new JSObject();
        r.put("available", s == BiometricManager.BIOMETRIC_SUCCESS);
        r.put("status", s);
        call.resolve(r);
    }

    @PluginMethod
    public void authenticate(PluginCall call) {
        final FragmentActivity act = getActivity();
        final String title = call.getString("title", "محاسبك المالي");
        final String subtitle = call.getString("subtitle", "");
        act.runOnUiThread(() -> {
            Executor ex = ContextCompat.getMainExecutor(act);
            BiometricPrompt prompt = new BiometricPrompt(act, ex, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) { call.resolve(); }
                @Override
                public void onAuthenticationError(int code, CharSequence msg) { call.reject(String.valueOf(msg), String.valueOf(code)); }
            });
            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title).setSubtitle(subtitle).setAllowedAuthenticators(AUTH).build();
            prompt.authenticate(info);
        });
    }
}
