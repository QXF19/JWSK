package moe.shizuku.manager.root

internal object RootProbeParser {
    private val kernelVersion = Regex("^(?:Kernel Version:\\s*)?([0-9]+)$")

    fun parse(lines: List<String>): RootEnvironment {
        fun value(key: String) = lines.firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')?.trim()
        fun positiveVersion(text: String?): String? = text?.let {
            kernelVersion.matchEntire(it)?.groupValues?.get(1)
                ?.takeIf { version -> (version.toLongOrNull() ?: 0L) > 0 }
        }
        val root = value("JWSK_UID") == "0"
        val magisk = value("JWSK_MAGISK")?.takeIf { it.isNotBlank() }
        // debug version succeeds and prints zero even without a KernelSU kernel.
        // Leftover /data/adb/ksu files are also not evidence of an active kernel.
        val ksu = positiveVersion(value("JWSK_KSU"))
            ?: positiveVersion(value("JWSK_KSU_KERNEL"))
        val backend = when {
            !root -> RootBackend.NONE
            magisk != null && ksu != null -> RootBackend.HYBRID
            ksu != null -> RootBackend.KERNEL_SU
            magisk != null -> RootBackend.MAGISK
            else -> RootBackend.NONE
        }
        return RootEnvironment(
            backend = backend,
            rootGranted = root,
            magiskVersion = magisk,
            kernelSuVersion = ksu,
            kernelMode = if (ksu != null) value("JWSK_KSU_MODE") else null,
            statusDetail = when {
                backend == RootBackend.HYBRID -> "已获得 Root 权限，同时检测到 Magisk 和 KernelSU"
                backend == RootBackend.MAGISK -> "Magisk 已就绪，JWSK 已获得 Root 权限"
                backend == RootBackend.KERNEL_SU -> "KernelSU 已就绪，JWSK 已获得 Root 权限"
                root -> "已获得 Root 权限，但无法识别框架；Comput 可用"
                else -> "请在 Magisk 或 KernelSU 中允许 JWSK 的 Root 请求，再重新检测"
            },
            warning = when (backend) {
                RootBackend.HYBRID -> "两套框架同时活动，请选择保留其中一套后管理模块和授权。"
                RootBackend.KERNEL_SU -> "应用授权请使用当前内核认可的 KernelSU 管理器。"
                else -> null
            }
        )
    }
}
