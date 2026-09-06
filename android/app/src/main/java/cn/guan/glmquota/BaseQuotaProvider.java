package cn.guan.glmquota;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 共享刷新逻辑；三个尺寸子类只提供布局 id */
public abstract class BaseQuotaProvider extends AppWidgetProvider {

    public static final String PREFS = "glm_quota";
    public static final String KEY_API = "api_key";
    static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    static final int GREEN = 0xFF34C759;
    static final int AMBER = 0xFFFFB020;
    static final int RED   = 0xFFFF453A;
    static final int GRAY  = 0xFF8A8F98;

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

        fillSub(v, layout, "查询中…");
        mgr.updateAppWidget(id, v);

        final String fKey = key;
        EXEC.execute(() -> {
            String pct, sub, week;
            int progress, color;
            try {
                QuotaApi.Result res = QuotaApi.query(fKey);
                QuotaApi.Window w = res.fiveHour();
                pct = w.usedPct + "%";
                progress = w.usedPct;
                color = w.usedPct >= 90 ? RED : w.usedPct >= 70 ? AMBER : GREEN;
                sub = fmtReset(w);
                QuotaApi.Window wk = null;
                for (QuotaApi.Window x : res.windows) if (x.label.contains("周")) wk = x;
                week = wk == null ? res.level : "周 " + wk.usedPct + "% · " + res.level;
            } catch (Exception e) {
                pct = "ERR";
                progress = 0;
                color = GRAY;
                sub = e.getMessage() != null ? e.getMessage() : "查询失败";
                week = "";
            }
            final RemoteViews fv = v;
            final String f1 = pct, f2 = sub, f3 = week;
            // 1×1 小字放不下重置文案，只放窗口标签
            final String sub1x1 = f2.contains("后") || f2.contains("重置")
                    || f2.equals("查询中…") ? "5h" : f2;
            final int fp = progress, fc = color;
            MAIN.post(() -> {
                fill(fv, layout, f1, f2, fp, fc, sub1x1);
                if (layout == R.layout.widget_2x2) fv.setTextViewText(R.id.w3_week, f3);
                mgr.updateAppWidget(id, fv);
            });
        });
    }

    private static String fmtReset(QuotaApi.Window w) {
        double h = w.resetInMs / 3_600_000.0;
        if (h < 1) return String.format("%d分钟后重置", (int) (h * 60));
        if (h < 48) return String.format("%.1fh 后重置", h);
        return String.format("%.1f天后重置", h / 24);
    }

    private static void fill(RemoteViews v, int layout, String pct, String reset,
                             int progress, int color, String sub1x1) {
        if (layout == R.layout.widget_1x1) {
            v.setTextViewText(R.id.w1_pct, pct);
            v.setTextColor(R.id.w1_pct, color);
            v.setTextViewText(R.id.w1_sub, sub1x1);
            v.setProgressBar(R.id.w1_ring, 100, progress, false);
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

    private static void fillSub(RemoteViews v, int layout, String text) {
        if (layout == R.layout.widget_1x1) v.setTextViewText(R.id.w1_sub, text);
        else if (layout == R.layout.widget_2x1) v.setTextViewText(R.id.w2_reset, text);
        else v.setTextViewText(R.id.w3_reset, text);
    }
}
