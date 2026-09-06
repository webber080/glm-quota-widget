package cn.guan.glmquota;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 智谱 GLM Coding Plan 用量查询（已用真实接口 open.bigmodel.cn/api/monitor/usage/quota/limit 验证） */
public final class QuotaApi {

    public static class Window {
        public final String label;      // "5小时" / "每周"
        public final int usedPct;       // 已用百分比
        public final long resetInMs;    // 距重置毫秒
        Window(String label, int usedPct, long resetInMs) {
            this.label = label; this.usedPct = usedPct; this.resetInMs = resetInMs;
        }
    }

    public static class Result {
        public final String level;      // lite / pro / max
        public final Window[] windows;
        Result(String level, Window[] windows) { this.level = level; this.windows = windows; }
        /** 5 小时窗口（找不到就返回第一个） */
        public Window fiveHour() {
            for (Window w : windows) if (w.label.contains("小时")) return w;
            return windows.length > 0 ? windows[0] : null;
        }
    }

    private static final String API =
            "https://open.bigmodel.cn/api/monitor/usage/quota/limit";

    public static Result query(String apiKey) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        BufferedReader r = new BufferedReader(new InputStreamReader(
                code >= 400 ? c.getErrorStream() : c.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        if (code != 200) throw new Exception("HTTP " + code);

        JSONObject root = new JSONObject(sb.toString());
        if (!root.optBoolean("success")) throw new Exception(root.optString("msg", "查询失败"));
        JSONObject data = root.getJSONObject("data");

        JSONArray limits = data.optJSONArray("limits");
        if (limits == null || limits.length() == 0) throw new Exception("无额度数据");
        Window[] out = new Window[limits.length()];
        long now = System.currentTimeMillis();
        for (int i = 0; i < limits.length(); i++) {
            JSONObject lim = limits.getJSONObject(i);
            int unit = lim.optInt("unit", -1);
            int number = lim.optInt("number", 1);
            String label;
            if (unit == 3) label = number + "小时";
            else if (unit == 6) label = "每周";
            else label = "unit" + unit;
            long resetIn = lim.optLong("nextResetTime", 0) - now;
            out[i] = new Window(label, lim.optInt("percentage", 0), resetIn);
        }
        return new Result(data.optString("level", "unknown"), out);
    }

    private QuotaApi() {}
}
