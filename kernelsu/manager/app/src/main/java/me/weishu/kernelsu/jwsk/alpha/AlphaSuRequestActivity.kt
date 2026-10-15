package me.weishu.kernelsu.jwsk.alpha

import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.system.Os
import android.system.OsConstants
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.theme.KernelSUTheme
import top.yukonga.miuix.kmp.basic.*
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/** Accepts only a live daemon-owned FIFO; arbitrary VIEW intents cannot grant Root. */
class AlphaSuRequestActivity : ComponentActivity() {
    private var descriptor: FileDescriptor? = null
    private var requestUid = -1
    private val answered = AtomicBoolean(false)
    private var ready by mutableStateOf(false)
    private var allowEnabled by mutableStateOf(false)
    private var appLabel by mutableStateOf("")
    private var appPackages by mutableStateOf("")
    private var rememberDecision by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.setHideOverlayWindows(true)
        onBackPressedDispatcher.addCallback(this) { respond(1) }
        if (intent.action != Intent.ACTION_VIEW || intent.getStringExtra("action") != "request") {
            finish(); return
        }
        lifecycleScope.launch {
            var incoming: VerifiedRequest? = null
            try {
                val request = withContext(Dispatchers.IO) { validateRequest().also { incoming = it } }
                descriptor = request.fd
                requestUid = request.uid
                appLabel = request.label
                appPackages = request.packages
                incoming = null
                ready = true
                launch { delay(1000); allowEnabled = true }
                launch { delay(30000); respond(1) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { finish() }
            finally { withContext(NonCancellable + Dispatchers.IO) { incoming?.let { runCatching { Os.close(it.fd) } } } }
        }
        setContent {
            KernelSUTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(stringResource(R.string.jwsk_alpha_request), style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title2)
                        if (ready) {
                            Text(appLabel, maxLines = 3)
                            Text(appPackages, maxLines = Int.MAX_VALUE)
                            Text("UID $requestUid")
                            Text(stringResource(R.string.jwsk_alpha_request_description), maxLines = Int.MAX_VALUE)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Checkbox(state = if (rememberDecision) ToggleableState.On else ToggleableState.Off,
                                    onClick = { rememberDecision = !rememberDecision })
                                Text(stringResource(R.string.jwsk_alpha_remember))
                            }
                            TextButton(text = stringResource(R.string.jwsk_alpha_deny), onClick = { respond(1) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                            TextButton(text = stringResource(R.string.jwsk_alpha_allow), enabled = allowEnabled,
                                onClick = { respond(2) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                        } else Text(stringResource(R.string.jwsk_loading))
                    }
                }
            }
        }
    }

    private data class VerifiedRequest(val fd: FileDescriptor, val uid: Int, val label: String, val packages: String)

    private suspend fun validateRequest(): VerifiedRequest {
        check(AlphaClient(this).probe().trustedManager)
        val uid = intent.getIntExtra("uid", -1)
        val pid = intent.getIntExtra("pid", -1)
        val path = intent.getStringExtra("fifo") ?: error("Missing FIFO")
        check(uid > 0 && uid != Process.myUid() && uid / 100000 == Process.myUid() / 100000 && pid > 1)
        check(path == "/debug_ramdisk/.magisk/su_request_$pid" || path == "/sbin/.magisk/su_request_$pid")
        // FIFO ownership authenticates the daemon, not the Intent extras. Bind the
        // requested identity to the kernel's live client UID to prevent relabeling.
        val requester = AlphaClient(this).root("stat -c %u /proc/$pid", 5000)
        check(requester.exitCode() == 0)
        val actualUid = requester.output().trim().toInt()
        if (actualUid != uid) {
            val mode = AlphaPolicyStore(this).sql("SELECT value FROM settings WHERE key='multiuser_mode'")
                .firstOrNull()?.get("value")?.toIntOrNull() ?: 0
            check(mode == 1 && actualUid % 100000 == uid % 100000 && Process.myUid() / 100000 == 0)
        }
        val before = Os.lstat(path)
        check(OsConstants.S_ISFIFO(before.st_mode) && before.st_uid == Process.myUid() && before.st_gid == Process.myUid())
        check(before.st_mode and 511 == 384) // daemon creates exactly 0600
        val label = Os.getxattr(path, "security.selinux").toString(Charsets.US_ASCII).trimEnd('\u0000')
        check(label == "u:object_r:magisk_file:s0")
        val fd = Os.open(path, OsConstants.O_WRONLY or OsConstants.O_NONBLOCK or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC, 0)
        try {
            val after = Os.fstat(fd)
            check(OsConstants.S_ISFIFO(after.st_mode) && after.st_ino == before.st_ino && after.st_dev == before.st_dev)
            check(after.st_uid == Process.myUid() && after.st_mode and 511 == 384)
            val packages = packageManager.getPackagesForUid(uid).orEmpty()
            check(packages.isNotEmpty())
            val labels = packages.map { packageManager.getApplicationInfo(it, 0).loadLabel(packageManager).toString() }
            return VerifiedRequest(fd, uid, labels.joinToString(", ").take(256), packages.joinToString(", ").take(512))
        } catch (failure: Throwable) { Os.close(fd); throw failure }
    }

    private fun respond(policy: Int) {
        if (!ready || !answered.compareAndSet(false, true)) return
        allowEnabled = false
        val fd = descriptor ?: return finish()
        descriptor = null
        val persist = rememberDecision
        val uid = requestUid
        lifecycleScope.launch {
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    val bytes = ByteBuffer.allocate(4).putInt(policy).array()
                    check(Os.write(fd, bytes, 0, bytes.size) == bytes.size)
                    if (persist) AlphaPolicyStore(this@AlphaSuRequestActivity).save(AlphaPolicy(uid, policy, 0, true, true))
                } catch (_: Exception) { /* FIFO disappears when the daemon times out. */ }
                finally { runCatching { Os.close(fd) } }
            }
            finish()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.flags and (MotionEvent.FLAG_WINDOW_IS_OBSCURED or MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0) return false
        return super.dispatchTouchEvent(event)
    }

    override fun onDestroy() {
        descriptor?.let { fd ->
            // Nonblocking FIFO write: closing an unanswered prompt always denies.
            if (answered.compareAndSet(false, true)) runCatching { Os.write(fd, byteArrayOf(0, 0, 0, 1), 0, 4) }
            runCatching { Os.close(fd) }
        }
        descriptor = null
        super.onDestroy()
    }
}
