package com.mohasbak.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

/** ودجت الشاشة الرئيسية: مسموح اليوم + المتبقي من الراتب + زر تسجيل مصروف */
public class MohasbakWidget extends AppWidgetProvider {
    public static final String PREFS = "mohasbak_widget";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(ctx, mgr, id);
    }

    static void update(Context ctx, AppWidgetManager mgr, int id) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.mohasbak_widget);
        v.setTextViewText(R.id.w_title, p.getString("title", "محاسبك المالي"));
        v.setTextViewText(R.id.w_big, p.getString("big", "افتح التطبيق"));
        v.setTextViewText(R.id.w_line1, p.getString("line1", "ليظهر مسموح اليوم هنا"));
        v.setTextViewText(R.id.w_line2, p.getString("line2", ""));

        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent add = new Intent(ctx, MainActivity.class);
        add.putExtra("mohasbak_action", "quick");
        add.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.w_add, PendingIntent.getActivity(ctx, 1, add, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        mgr.updateAppWidget(id, v);
    }

    static void refreshAll(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        int[] ids = m.getAppWidgetIds(new ComponentName(ctx, MohasbakWidget.class));
        for (int id : ids) update(ctx, m, id);
    }
}
