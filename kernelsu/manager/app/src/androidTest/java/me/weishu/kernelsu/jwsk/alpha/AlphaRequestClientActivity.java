package me.weishu.kernelsu.jwsk.alpha;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Separate-package, separate-UID test client. Only asks the daemon to run id. */
public final class AlphaRequestClientActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView output = new TextView(this);
        output.setText("Waiting for a JWSK Root decision");
        output.setTextSize(20);
        setContentView(output);
        new Thread(() -> {
            String result;
            Process process = null;
            try {
                process = new ProcessBuilder("/debug_ramdisk/magisk", "su", "-c", "id -u").redirectErrorStream(true).start();
                if (!process.waitFor(75000, TimeUnit.MILLISECONDS)) throw new IllegalStateException("Root request timed out");
                result = "exit=" + process.exitValue() + "\n" + new String(process.getInputStream().readNBytes(4096), StandardCharsets.UTF_8);
            } catch (Exception failure) { result = failure.getClass().getSimpleName(); }
            finally { if (process != null && process.isAlive()) process.destroyForcibly(); }
            String text = result;
            runOnUiThread(() -> output.setText(text));
        }, "jwsk-isolated-client").start();
    }
}
