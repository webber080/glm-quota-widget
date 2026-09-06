package cn.guan.glmquota;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 共享刷新逻辑；三个尺寸子类只提供布局 id。
 *  v1.6: 一次请求三组件共享 + 失败重试 + 失败时回退上次成功数据（标「缓存」） */
public abstract class BaseQuotaProvider extends AppWidgetProvider {

    public static final String PREFS = "glm_quota";
    public static final String KEY_API = "api_key";
    private static final String KEY_LAST_GOOD = "last_good";   // JSON
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static final int GREEN = 0xFF34C759;
    static final int AMBER = 0xFFFFB020;
    static final int RED   = 0xFFFF453A;
    static final int GRAY  = 0xFF8A8F98;

    /** 共享请求：60s 内的刷新复用同一次结果；失败重试 2 次 */
    private static final Object FETCH_LOCK = new Object();
    private static QuotaApi.Result sCache;
    private static long sCacheAt;
    private static final long CACHE_MS = 60_000;

    private static QuotaApi.Result fetchShared(String key) throws Exception {
        synchronized (FETCH_LOCK) {
            if (sCache != null && System.currentTimeMillis() - sCacheAt < CACHE_MS) {
                return sCache;
            }
            Exception last = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    QuotaApi.Result r = QuotaApi.query(key);
                    sCache = r;
                    sCacheAt = System.currentTimeMillis();
                    return r;
                } catch (Exception e) {
                    last = e;
                    if (attempt < 2) {
                        try { Thread.sleep(1500L * (attempt + 1)); }
                        catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new Exception("中断");
                        }
                    }
                }
            }
            throw last != null ? last : new Exception("查询失败");
        }
    }

    /** 子类返回自己的布局 */
    protected abstract int layoutId();

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) refreshOne(ctx, mgr, id, layoutId());
    }

    /** 刷新全部三种小组件（设置页手动刷新调用） */
    public static void refreshAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        refreshKind(ctx, mgr, QuotaWidgetRing.class, R.layout.widget_1x1);
        refreshKind(ctx, mgr, QuotaWidgetBar.class, R.layout.widget_2x1);
        refreshKind(ctx, mgr, QuotaWidgetCard.class, R.layout.widget_2x2);
    }

    private static void refreshKind(Context ctx, AppWidgetManager mgr,
                                    Class<? extends BaseQuotaProvider> cls, int layout) {
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, cls));
        for (int id : ids) refreshOne(ctx, mgr, id, layout);
    }

    private static void refreshOne(Context ctx, AppWidgetManager mgr, int id, int layout) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = sp.getString(KEY_API, "");
        RemoteViews v = new RemoteViews(ctx.getPackageName(), layout);
        v.setOnClickPendingIntent(R.id.widget_root, SettingsActivity.pendingIntent(ctx));

        if (key.isEmpty()) {
            fill(v, layout, "设置", "点击填入 Key", 0, GRAY, "");
            mgr.updateAppWidget(id, v);
            return;
        }

        // 先用上次成功数据立即渲染（避免刷新期间闪 ERR/空白）
        Render cached = loadLastGood(sp);
        if (cached != null) render(mgr, id, v, layout, cached, true);

        final String fKey = key;
        EXEC.execute(() -> {
            Render r;
            try {
                QuotaApi.Result res = fetchShared(fKey);
                QuotaApi.Window w = res.fiveHour();
                QuotaApi.Window wk = null;
                for (QuotaApi.Window x : res.windows) if (x.label.contains("周")) wk = x;
                r = new Render();
                r.pct = w.usedPct + "%";
                r.progress = w.usedPct;
                r.color = w.usedPct >= 90 ? RED : w.usedPct >= 70 ? AMBER : GREEN;
                r.sub = fmtReset(w);
                r.subShort = fmtResetShort(w);
                r.week = wk == null ? res.level : "周 " + wk.usedPct + "% · " + res.level;
                saveLastGood(sp, r);
            } catch (Exception e) {
                Render last = loadLastGood(sp);
                if (last != null) {
                    r = last;           // 网络失败 → 显示上次成功数据，标缓存
                } else {
                    r = new Render();
                    r.pct = "ERR";
                    r.progress = 0;
                    r.color = GRAY;
                    r.sub = e.getMessage() != null ? e.getMessage() : "查询失败";
                    r.subShort = "ERR";
                    r.week = "";
                }
            }
            final Render fr = r;
            MAIN.post(() -> {
                RemoteViews fv = new RemoteViews(ctx.getPackageName(), layout);
                fv.setOnClickPendingIntent(R.id.widget_root, SettingsActivity.pendingIntent(ctx));
                boolean stale = fr.cached;
                render(mgr, id, fv, layout, fr, stale);
            });
        });
    }

    private static void render(AppWidgetManager mgr, int id, RemoteViews v, int layout,
                               Render r, boolean stale) {
        String pct = stale ? "≈" + r.pct : r.pct;
        String sub = stale ? r.sub + " · 缓存" : r.sub;
        fill(v, layout, r.pct.equals("ERR") ? r.pct : pct, sub, r.progress, r.color, r.subShort);
        if (layout == R.layout.widget_2x2) v.setTextViewText(R.id.w3_week, r.week);
        mgr.updateAppWidget(id, v);
    }

    /** 渲染所需的一组值 */
    private static class Render {
        String pct, sub, subShort, week;
        int progress, color;
        boolean cached;
    }

    private static void saveLastGood(SharedPreferences sp, Render r) {
        try {
            JSONObject o = new JSONObject();
            o.put("pct", r.pct);
            o.put("progress", r.progress);
            o.put("color", r.color);
            o.put("sub", r.sub);
            o.put("subShort", r.subShort);
            o.put("week", r.week == null ? "" : r.week);
            sp.edit().putString(KEY_LAST_GOOD, o.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static Render loadLastGood(SharedPreferences sp) {
        String s = sp.getString(KEY_LAST_GOOD, null);
        if (s == null) return null;
        try {
            JSONObject o = new JSONObject(s);
            Render r = new Render();
            r.pct = o.getString("pct");
            r.progress = o.optInt("progress", 0);
            r.color = o.optInt("color", GRAY);
            r.sub = o.optString("sub", "");
            r.subShort = o.optString("subShort", "5h");
            r.week = o.optString("week", "");
            r.cached = true;
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private static String fmtReset(QuotaApi.Window w) {
        double h = w.resetInMs / 3_600_000.0;
        if (h < 1) return String.format("%d分钟后重置", (int) (h * 60));
        if (h < 48) return String.format("%.1fh 后重置", h);
        return String.format("%.1f天后重置", h / 24);
    }

    /** 1×1 用的紧凑倒计时 */
    private static String fmtResetShort(QuotaApi.Window w) {
        if (w == null) return "5h";
        double h = w.resetInMs / 3_600_000.0;
        if (h < 1) return String.format("%d分", (int) (h * 60));
        if (h < 48) return String.format("%.1fh", h);
        return String.format("%.1f天", h / 24);
    }

    private static void fill(RemoteViews v, int layout, String pct, String reset,
                             int progress, int color, String sub1x1) {
        if (layout == R.layout.widget_1x1) {
            v.setTextViewText(R.id.w1_pct, pct);
            v.setTextColor(R.id.w1_pct, color);
            v.setTextViewText(R.id.w1_sub, sub1x1);
            v.setProgressBar(R.id.w1_bar, 100, progress, false);
        } else if (layout == R.layout.widget_2x1) {
            v.setTextViewText(R.id.w2_pct, pct);
            v.setTextColor(R.id.w2_pct, color);
            v.setTextViewText(R.id.w2_reset, reset);
            v.setProgressBar(R.id.w2_bar, 100, progress, false);
        } else {
            v.setTextViewText(R.id.w3_pct, pct);
            v.setTextColor(R.id.w3_pct, color);
            v.setTextViewText(R.id.w3_reset, reset);
            v.setProgressBar(R.id.w3_bar, 100, progress, false);
        }
    }
}
