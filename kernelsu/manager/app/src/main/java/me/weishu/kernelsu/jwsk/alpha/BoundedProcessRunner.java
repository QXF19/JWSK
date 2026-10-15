package me.weishu.kernelsu.jwsk.alpha;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** App-UID process only. No su fallback, command interpolation, or connection to AI. */
public final class BoundedProcessRunner {
    public interface Lifecycle {
        void started(Process process) throws Exception;
        void terminating(Process process);
    }
    private final Lifecycle lifecycle;
    public BoundedProcessRunner() { this.lifecycle = null; }
    public BoundedProcessRunner(Lifecycle lifecycle) { this.lifecycle = lifecycle; }
    public record Result(int exitCode, String output, boolean timedOut, boolean truncated) {}
    private static final int MAX_OUTPUT = 64 * 1024;

    public Result run(List<String> arguments, File directory, Map<String, String> environment,
                      long timeoutMillis) throws Exception {
        if (arguments.isEmpty() || arguments.size() > 64 || timeoutMillis < 1 || timeoutMillis > 180000) {
            throw new IllegalArgumentException("Invalid bounded process request");
        }
        ProcessBuilder builder = new ProcessBuilder(List.copyOf(arguments)).directory(directory).redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        boolean[] truncated = {false};
        Thread reader = new Thread(() -> {
            try (var input = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                char[] buffer = new char[2048];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    synchronized (output) {
                        int remaining = MAX_OUTPUT - output.length();
                        if (count > remaining) truncated[0] = true;
                        if (remaining > 0) output.append(buffer, 0, Math.min(count, remaining));
                    }
                }
            } catch (Exception ignored) { /* Closing the process during timeout/cancellation is expected. */ }
        }, "JWSK-Alpha-output");
        reader.setDaemon(true);
        reader.start();
        try {
            if (lifecycle != null) lifecycle.started(process);
            boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!finished) {
                if (lifecycle != null) lifecycle.terminating(process);
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
            }
            reader.join(1000);
            synchronized (output) {
                return new Result(finished ? process.exitValue() : 124, output.toString(), !finished, truncated[0]);
            }
        } finally {
            if (lifecycle != null) lifecycle.terminating(process);
            if (process.isAlive()) process.destroyForcibly();
            try { process.getInputStream().close(); } catch (Exception ignored) { }
            try { process.getErrorStream().close(); } catch (Exception ignored) { }
            try { process.getOutputStream().close(); } catch (Exception ignored) { }
            reader.interrupt();
        }
    }
}
