package cn.guan.glmquota;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SettingsActivity extends Activity {

    public static PendingIntent pendingIntent(Context ctx) {
        Intent i = new Intent(ctx, SettingsActivity.class);
        return PendingIntent.getActivity(ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences sp = getSharedPreferences(BaseQuotaProvider.PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        String ver;
        try {
            ver = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) { ver = "?"; }

        TextView title = new TextView(this);
        title.setText("GLM 额度小组件  v" + ver);
        title.setTextSize(18);

        EditText keyBox = new EditText(this);
        keyBox.setHint(R.string.api_key_hint);
        keyBox.setInputType(InputType.TYPE_CLASS_TEXT);
        keyBox.setText(sp.getString(BaseQuotaProvider.KEY_API, ""));

        TextView status = new TextView(this);
        status.setText("接口: open.bigmodel.cn/api/monitor/usage/quota/limit");

        Button paste = new Button(this);
        paste.setText("粘贴");
        paste.setOnClickListener((View v) -> {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) {
                status.setText("剪贴板是空的，先复制一下 Key");
                return;
            }
            CharSequence clip = cm.getPrimaryClip().getItemAt(0).getText();
            if (clip == null || clip.toString().trim().isEmpty()) {
                status.setText("剪贴板内容为空");
                return;
            }
            keyBox.setText(clip.toString().trim());
            status.setText("已从剪贴板填入，点「保存并刷新」");
        });

        Button save = new Button(this);
        save.setText(R.string.save_key);
        save.setOnClickListener((View v) -> {
            sp.edit().putString(BaseQuotaProvider.KEY_API,
                    keyBox.getText().toString().trim()).apply();
            BaseQuotaProvider.refreshAll(this);
            status.setText("已保存，正在刷新小组件…");
        });

        Button refresh = new Button(this);
        refresh.setText(R.string.refresh_now);
        refresh.setOnClickListener((View v) -> {
            BaseQuotaProvider.refreshAll(this);
            status.setText("已触发刷新");
        });

        root.addView(title);
        root.addView(keyBox);
        root.addView(paste);
        root.addView(save);
        root.addView(refresh);
        root.addView(status);
        setContentView(root);
    }
}
