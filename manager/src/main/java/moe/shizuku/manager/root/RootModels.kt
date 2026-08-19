package moe.shizuku.manager.root

enum class RootBackend {
    NONE,
    ADB,
    MAGISK,
    KERNEL_SU,
    HYBRID
}

data class RootEnvironment(
    val backend: RootBackend = RootBackend.NONE,
    val rootGranted: Boolean = false,
    val adbActive: Boolean = false,
    val magiskVersion: String? = null,
    val kernelSuVersion: String? = null,
    val kernelMode: String? = null,
    val warning: String? = null
) {
    val title: String
        get() = when (backend) {
            RootBackend.MAGISK -> "Magisk ${magiskVersion.orEmpty()}".trim()
            RootBackend.KERNEL_SU -> "KernelSU ${kernelSuVersion.orEmpty()}".trim()
            RootBackend.HYBRID -> "Magisk + KernelSU"
            RootBackend.ADB -> "ADB 激活"
            RootBackend.NONE -> "尚未激活"
        }

    val modeLabel: String
        get() = when (backend) {
            RootBackend.MAGISK -> "BOOT 修补"
            RootBackend.KERNEL_SU -> kernelMode ?: "内核修补"
            RootBackend.HYBRID -> "双框架"
            RootBackend.ADB -> "ADB"
            RootBackend.NONE -> "未激活"
        }

    val canManageRootModules: Boolean
        get() = rootGranted && backend in setOf(RootBackend.MAGISK, RootBackend.KERNEL_SU)
}

data class RootCommandResult(
    val code: Int,
    val stdout: List<String>,
    val stderr: List<String>,
    val elapsedMs: Long
) {
    val output: String
        get() = (stdout + stderr).joinToString("\n").take(64 * 1024)
}

data class RootModule(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val author: String,
    val description: String,
    val enabled: Boolean,
    val pendingRemoval: Boolean,
    val hasAction: Boolean,
    val backend: RootBackend
)

data class RootPolicy(
    val uid: Int,
    val packageName: String,
    val appName: String,
    val policy: Int,
    val logging: Boolean,
    val notification: Boolean,
    val until: Long
) {
    val allowed: Boolean get() = policy == 2
}
