package com.mohasbak.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import androidx.core.app.NotificationCompat;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * يستقبل الرسائل الجديدة (نسخة الاختبار فقط — الصلاحية معلنة في manifest نسخة debug).
 * يحفظ رسائل البنوك فقط على الجهاز في قائمة انتظار، ويعرض إشعاراً لتأكيدها داخل التطبيق.
 * لا يرسل أي رسالة لأي جهة.
 */
public class SmsReceiver extends BroadcastReceiver {
    private static final Pattern MONEY = Pattern.compile("(SAR|SR|ر\\.?\\s?س|ريال|مبلغ|Amount)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OTP = Pattern.compile("(رمز\\s*(التحقق|التفعيل|الدخول)|OTP|verification\\s*code|لا\\s*تشارك|do\\s*not\\s*share)", Pattern.CASE_INSENSITIVE);
    private static final String CH = "mohasbak_sms";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SharedPreferences p = ctx.getSharedPreferences(MohasbakWidget.PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("sms_auto", false)) return;
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) return;
        StringBuilder b = new StringBuilder();
        String from = parts[0].getOriginatingAddress();
        long at = parts[0].getTimestampMillis();
        for (SmsMessage m : parts) if (m != null && m.getMessageBody() != null) b.append(m.getMessageBody());
        String body = b.toString();
        if (!MONEY.matcher(body).find() || OTP.matcher(body).find()) return;
        try {
            JSONArray q = new JSONArray(p.getString("sms_queue", "[]"));
            JSONObject o = new JSONObject();
            o.put("body", body.length() > 1500 ? body.substring(0, 1500) : body);
            o.put("date", at);
            o.put("from", from == null ? "" : from);
            q.put(o);
            while (q.length() > 60) q.remove(0);
            p.edit().putString("sms_queue", q.toString()).apply();
            notifyUser(ctx, q.length());
        } catch (Exception e) { /* */ }
    }

    private void notifyUser(Context ctx, int n) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CH, "رسائل البنك", NotificationManager.IMPORTANCE_DEFAULT));
            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            open.putExtra("mohasbak_action", "sms");
            PendingIntent pi = PendingIntent.getActivity(ctx, 77, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            int icon = ctx.getResources().getIdentifier("ic_stat_mohasbak", "drawable", ctx.getPackageName());
            NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, CH)
                .setSmallIcon(icon != 0 ? icon : ctx.getApplicationInfo().icon)
                .setContentTitle(n > 1 ? "عندك " + n + " عمليات بنكية بانتظار التسجيل" : "وصلت عملية من البنك")
                .setContentText("اضغط لمراجعتها وتسجيلها بلمسة")
                .setColor(0xFF3B43A6)
                .setAutoCancel(true)
                .setContentIntent(pi);
            nm.notify(7701, nb.build());
        } catch (Exception e) { /* إذن الإشعارات غير ممنوح */ }
    }
}
