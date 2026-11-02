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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.android.billingclient.api.*;
import com.getcapacitor.JSArray;
import com.getcapacitor.PermissionState;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import android.Manifest;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import org.json.JSONArray;

@CapacitorPlugin(name = "MohasbakNative", permissions = {
    @Permission(alias = "sms", strings = { Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS })
})
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
        String sh = p.getString("pending_share", null);
        p.edit().remove("pending_action").remove("pending_share").apply();
        JSObject r = new JSObject();
        r.put("action", a);
        if (sh != null) r.put("share", sh);
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

    /* ───────── Google Play Billing ───────── */
    private BillingClient billing;
    private PluginCall buyCall;
    private final Map<String, ProductDetails> details = new HashMap<>();
    private static final String[] SUBS = { "pro_monthly", "pro_yearly" };
    private static final String[] INAPP = { "pro_lifetime" };

    private void withBilling(PluginCall call, Runnable ok) {
        if (billing == null) {
            billing = BillingClient.newBuilder(getContext())
                .setListener(this::onPurchasesUpdated)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
        }
        if (billing.isReady()) { ok.run(); return; }
        billing.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) ok.run();
                else call.reject("Google Play غير متاح: " + r.getDebugMessage(), String.valueOf(r.getResponseCode()));
            }
            @Override public void onBillingServiceDisconnected() { /* نعيد الاتصال عند الطلب التالي */ }
        });
    }

    private static List<QueryProductDetailsParams.Product> products(String[] ids, String type) {
        List<QueryProductDetailsParams.Product> l = new ArrayList<>();
        for (String id : ids) l.add(QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build());
        return l;
    }

    private static ProductDetails.SubscriptionOfferDetails baseOffer(ProductDetails pd) {
        List<ProductDetails.SubscriptionOfferDetails> offers = pd.getSubscriptionOfferDetails();
        if (offers == null || offers.isEmpty()) return null;
        for (ProductDetails.SubscriptionOfferDetails o : offers) if (o.getOfferId() == null) return o;
        return offers.get(0);
    }

    private static String priceOf(ProductDetails pd) {
        if (BillingClient.ProductType.INAPP.equals(pd.getProductType())) {
            ProductDetails.OneTimePurchaseOfferDetails o = pd.getOneTimePurchaseOfferDetails();
            return o == null ? null : o.getFormattedPrice();
        }
        ProductDetails.SubscriptionOfferDetails o = baseOffer(pd);
        if (o == null) return null;
        List<ProductDetails.PricingPhase> ph = o.getPricingPhases().getPricingPhaseList();
        return ph.isEmpty() ? null : ph.get(ph.size() - 1).getFormattedPrice();
    }

    /** أسعار المنتجات من Google Play (بعملة المستخدم) */
    @PluginMethod
    public void billingProducts(PluginCall call) {
        withBilling(call, () -> {
            final JSArray out = new JSArray();
            final AtomicInteger left = new AtomicInteger(2);
            ProductDetailsResponseListener l = (r, list) -> {
                if (list != null) for (ProductDetails pd : list) {
                    details.put(pd.getProductId(), pd);
                    JSObject o = new JSObject();
                    o.put("id", pd.getProductId()); o.put("type", pd.getProductType()); o.put("price", priceOf(pd)); o.put("title", pd.getName());
                    synchronized (out) { out.put(o); }
                }
                if (left.decrementAndGet() == 0) { JSObject res = new JSObject(); res.put("products", out); call.resolve(res); }
            };
            billing.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products(SUBS, BillingClient.ProductType.SUBS)).build(), l);
            billing.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products(INAPP, BillingClient.ProductType.INAPP)).build(), l);
        });
    }

    private void launch(PluginCall call, ProductDetails pd) {
        BillingFlowParams.ProductDetailsParams.Builder b = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd);
        if (BillingClient.ProductType.SUBS.equals(pd.getProductType())) {
            ProductDetails.SubscriptionOfferDetails o = baseOffer(pd);
            if (o == null) { call.reject("لا يوجد عرض لهذا الاشتراك"); return; }
            b.setOfferToken(o.getOfferToken());
        }
        List<BillingFlowParams.ProductDetailsParams> l = new ArrayList<>(); l.add(b.build());
        final BillingFlowParams params = BillingFlowParams.newBuilder().setProductDetailsParamsList(l).build();
        buyCall = call;
        getActivity().runOnUiThread(() -> {
            BillingResult r = billing.launchBillingFlow(getActivity(), params);
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK && buyCall != null) {
                buyCall.reject(r.getDebugMessage(), String.valueOf(r.getResponseCode())); buyCall = null;
            }
        });
    }

    /** شراء اشتراك أو مدى الحياة */
    @PluginMethod
    public void billingBuy(PluginCall call) {
        final String id = call.getString("id");
        final String type = "inapp".equals(call.getString("type")) ? BillingClient.ProductType.INAPP : BillingClient.ProductType.SUBS;
        if (id == null) { call.reject("id مطلوب"); return; }
        withBilling(call, () -> {
            ProductDetails pd = details.get(id);
            if (pd != null) { launch(call, pd); return; }
            List<QueryProductDetailsParams.Product> one = new ArrayList<>();
            one.add(QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build());
            billing.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(one).build(), (r, list) -> {
                if (list == null || list.isEmpty()) { call.reject("المنتج غير متاح في Google Play بعد", String.valueOf(r.getResponseCode())); return; }
                details.put(id, list.get(0)); launch(call, list.get(0));
            });
        });
    }

    private JSArray handle(List<Purchase> purchases, boolean[] pending) {
        JSArray owned = new JSArray();
        if (purchases == null) return owned;
        for (Purchase p : purchases) {
            if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) { pending[0] = true; continue; }
            if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
            if (!p.isAcknowledged()) billing.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(), r -> { });
            for (String id : p.getProducts()) { JSObject o = new JSObject(); o.put("id", id); o.put("token", p.getPurchaseToken()); o.put("time", p.getPurchaseTime()); owned.put(o); }
        }
        return owned;
    }

    private void onPurchasesUpdated(BillingResult r, List<Purchase> purchases) {
        PluginCall c = buyCall; buyCall = null;
        boolean[] pending = { false };
        JSArray owned = handle(purchases, pending);
        if (c == null) return;
        if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            JSObject res = new JSObject(); res.put("owned", owned); res.put("state", owned.length() > 0 ? "purchased" : pending[0] ? "pending" : "none"); c.resolve(res);
        } else c.reject(r.getDebugMessage(), String.valueOf(r.getResponseCode()));
    }

    /** استعادة المشتريات الحالية (الاشتراكات الفعّالة + مدى الحياة) */
    @PluginMethod
    public void billingRestore(PluginCall call) {
        withBilling(call, () -> {
            final JSArray all = new JSArray();
            final AtomicInteger left = new AtomicInteger(2);
            final boolean[] failed = { false };
            PurchasesResponseListener l = (r, list) -> {
                if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) failed[0] = true;
                JSArray got = handle(list, new boolean[] { false });
                synchronized (all) { for (int i = 0; i < got.length(); i++) { try { all.put(got.get(i)); } catch (Exception e) { /* */ } } }
                if (left.decrementAndGet() == 0) {
                    if (failed[0] && all.length() == 0) { call.reject("تعذر الاستعلام من Google Play"); return; }
                    JSObject res = new JSObject(); res.put("owned", all); call.resolve(res);
                }
            };
            billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(), l);
            billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(), l);
        });
    }

    /* ───── رسائل البنك تلقائياً: متاحة فقط إذا كانت الصلاحية معلنة (نسخة الاختبار حتى موافقة Google) ───── */
    private boolean smsDeclared() {
        try {
            PackageInfo pi = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), PackageManager.GET_PERMISSIONS);
            if (pi.requestedPermissions == null) return false;
            for (String s : pi.requestedPermissions) if (Manifest.permission.READ_SMS.equals(s)) return true;
        } catch (Exception e) { /* */ }
        return false;
    }

    @PluginMethod
    public void smsStatus(PluginCall call) {
        JSObject r = new JSObject();
        boolean d = smsDeclared();
        r.put("available", d);
        r.put("granted", d && getPermissionState("sms") == PermissionState.GRANTED);
        call.resolve(r);
    }

    @PluginMethod
    public void requestSms(PluginCall call) {
        if (!smsDeclared()) { call.reject("unavailable"); return; }
        if (getPermissionState("sms") == PermissionState.GRANTED) { smsPermCb(call); return; }
        requestPermissionForAlias("sms", call, "smsPermCb");
    }

    @PermissionCallback
    private void smsPermCb(PluginCall call) {
        JSObject r = new JSObject();
        r.put("granted", getPermissionState("sms") == PermissionState.GRANTED);
        call.resolve(r);
    }

    /** رسائل الوارد منذ تاريخ معيّن (للمراجعة على الجهاز فقط — لا تُرسل لأي مكان) */
    @PluginMethod
    public void smsInbox(PluginCall call) {
        if (!smsDeclared() || getPermissionState("sms") != PermissionState.GRANTED) { call.reject("no-permission"); return; }
        long since = call.getLong("since", System.currentTimeMillis() - 31L * 86400000L);
        int limit = call.getInt("limit", 400);
        JSArray out = new JSArray();
        Cursor c = null;
        try {
            c = getContext().getContentResolver().query(Uri.parse("content://sms/inbox"), new String[] { "body", "date", "address" }, "date > ?", new String[] { String.valueOf(since) }, "date DESC");
            while (c != null && c.moveToNext() && out.length() < limit) {
                JSObject m = new JSObject();
                m.put("body", c.getString(0));
                m.put("date", c.getLong(1));
                m.put("from", c.getString(2));
                out.put(m);
            }
        } catch (Exception e) { call.reject("read-failed"); return; }
        finally { if (c != null) c.close(); }
        JSObject r = new JSObject();
        r.put("messages", out);
        call.resolve(r);
    }

    /** الرسائل التي وصلت والتطبيق مغلق (يحفظها SmsReceiver) — تُقرأ مرة وتُمسح */
    @PluginMethod
    public void smsQueue(PluginCall call) {
        SharedPreferences p = getContext().getSharedPreferences(MohasbakWidget.PREFS, Context.MODE_PRIVATE);
        String q = p.getString("sms_queue", "[]");
        p.edit().remove("sms_queue").apply();
        JSObject r = new JSObject();
        try { r.put("messages", new JSArray(q)); } catch (Exception e) { r.put("messages", new JSArray()); }
        call.resolve(r);
    }

    @PluginMethod
    public void smsAuto(PluginCall call) {
        getContext().getSharedPreferences(MohasbakWidget.PREFS, Context.MODE_PRIVATE).edit().putBoolean("sms_auto", call.getBoolean("on", false)).apply();
        call.resolve();
    }
}
