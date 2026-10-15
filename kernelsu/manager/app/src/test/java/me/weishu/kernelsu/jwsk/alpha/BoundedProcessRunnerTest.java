package me.weishu.kernelsu.jwsk.alpha;

import org.junit.Test;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class BoundedProcessRunnerTest {
    @Test public void exitCodeAndOrderedOutputAreReturned() throws Exception {
        var result = new BoundedProcessRunner().run(command("exit"), null, Map.of(), 10000);
        assertEquals(7, result.exitCode()); assertTrue(result.output().contains("stdout"));
        assertTrue(result.output().contains("stderr")); assertFalse(result.timedOut());
    }
    @Test public void largeOutputIsDrainedButBounded() throws Exception {
        var result = new BoundedProcessRunner().run(command("output"), null, Map.of(), 10000);
        assertEquals(0, result.exitCode()); assertEquals(65536, result.output().length()); assertTrue(result.truncated());
    }
    @Test public void timeoutTerminatesTheProcess() throws Exception {
        var result = new BoundedProcessRunner().run(command("wait"), null, Map.of(), 250);
        assertEquals(124, result.exitCode()); assertTrue(result.timedOut());
    }
    @Test public void interruptionDoesNotKeepTheCallerWaiting() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try { new BoundedProcessRunner().run(command("wait"), null, Map.of(), 10000); }
            catch (Throwable caught) { error.set(caught); }
        });
        caller.start(); Thread.sleep(200); caller.interrupt(); caller.join(2000);
        assertFalse(caller.isAlive()); assertTrue(error.get() instanceof InterruptedException);
    }
    @Test public void invalidTimeBudgetFailsBeforeStarting() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedProcessRunner().run(command("exit"), null, Map.of(), 180001));
    }
    private static List<String> command(String mode) throws Exception {
        String suffix = System.getProperty("os.name").startsWith("Windows") ? ".exe" : "";
        String java = new File(System.getProperty("java.home"), "bin/java" + suffix).getAbsolutePath();
        String classes = new File(BoundedProcessRunnerTest.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        return List.of(java, "-cp", classes, Fixture.class.getName(), mode);
    }
    public static final class Fixture {
        public static void main(String[] args) throws Exception {
            switch (args[0]) {
                case "exit" -> { System.out.println("stdout"); System.err.println("stderr"); System.exit(7); }
                case "output" -> System.out.print("x".repeat(200000));
                case "wait" -> Thread.sleep(30000);
                default -> throw new IllegalArgumentException();
            }
        }
    }
}
