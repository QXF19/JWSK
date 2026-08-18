package moe.shizuku.manager.patch

import android.content.Context
import android.net.Uri
import android.system.Os
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

enum class PatchMode(val title: String, val detail: String) {
    MAGISK("Magisk 30.7", "修补 boot / init_boot ramdisk，保留加密与 dm-verity"),
    KERNELSU_LKM("KernelSU 3.2.5 LKM", "向 boot / init_boot 注入匹配 KMI 的 KernelSU LKM"),
    KERNELSU_KERNEL("KernelSU 自定义内核", "用你提供的匹配内核替换 boot 镜像中的 kernel")
}

data class SelectedImage(
    val file: File,
    val displayName: String,
    val size: Long,
    val sha256: String,
    val format: String
)

data class PatchResult(
    val output: File,
    val sha256: String,
    val log: String
)

object BootImageInspector {
    fun import(context: Context, uri: Uri, prefix: String): SelectedImage {
        val root = File(context.cacheDir, "jwsk_import/${System.currentTimeMillis()}")
        check(root.mkdirs()) { "无法创建临时目录" }
        val name = queryName(context, uri) ?: "$prefix.img"
        val output = File(root, "$prefix.img")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取所选文件" }
            output.outputStream().use { input.copyTo(it) }
        }
        val header = ByteArray(8)
        FileInputStream(output).use { it.read(header) }
        val magic = header.toString(Charsets.US_ASCII)
        val format = when {
            magic.startsWith("ANDROID!") -> "Android boot / init_boot"
            magic.startsWith("VNDRBOOT") -> "Android vendor_boot"
            else -> "未知镜像（将由所选引擎再次验证）"
        }
        return SelectedImage(output, name, output.length(), sha256(output), format)
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 128)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun queryName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        return uri.lastPathSegment
    }
}

object PatchExecutor {
    fun patch(
        context: Context,
        input: SelectedImage,
        mode: PatchMode,
        kmi: String,
        customKernel: SelectedImage?
    ): PatchResult {
        val work = File(context.cacheDir, "jwsk_patch/${System.currentTimeMillis()}")
        check(work.mkdirs()) { "无法创建修补工作目录" }
        val inputCopy = File(work, "input.img")
        input.file.copyTo(inputCopy)
        return when (mode) {
            PatchMode.MAGISK -> patchMagisk(context, work, inputCopy)
            PatchMode.KERNELSU_LKM -> patchKernelSu(context, work, inputCopy, kmi, null)
            PatchMode.KERNELSU_KERNEL -> {
                requireNotNull(customKernel) { "请先选择自定义内核文件" }
                val kernelCopy = File(work, "kernel")
                customKernel.file.copyTo(kernelCopy)
                patchKernelSu(context, work, inputCopy, kmi, kernelCopy)
            }
        }
    }

    private fun patchMagisk(context: Context, work: File, input: File): PatchResult {
        listOf("boot_patch.sh", "util_functions.sh", "stub.apk").forEach {
            copyAsset(context, "patch/magisk/$it", File(work, it))
        }
        val chrome = File(work, "chromeos")
        check(chrome.mkdirs()) { "无法创建 ChromeOS 资源目录" }
        listOf("futility", "kernel.keyblock", "kernel_data_key.vbprivk").forEach {
            copyAsset(context, "patch/magisk/chromeos/$it", File(chrome, it))
        }
        val native = File(context.applicationInfo.nativeLibraryDir)
        mapOf(
            "magiskboot" to "libmagiskboot.so",
            "magiskinit" to "libmagiskinit.so",
            "magisk" to "libmagisk.so",
            "init-ld" to "libinit-ld.so",
            "busybox" to "libbusybox.so"
        ).forEach { (link, library) ->
            val target = File(native, library)
            require(target.isFile) { "缺少 Magisk 引擎文件：$library" }
            Os.symlink(target.absolutePath, File(work, link).absolutePath)
        }
        val process = ProcessBuilder("/system/bin/sh", "boot_patch.sh", input.absolutePath)
            .directory(work)
            .redirectErrorStream(true)
        process.environment().apply {
            put("BOOTMODE", "true")
            put("KEEPVERITY", "true")
            put("KEEPFORCEENCRYPT", "true")
            put("PATCHVBMETAFLAG", "false")
            put("RECOVERYMODE", "false")
            put("JWSK_TMPDIR", work.absolutePath)
        }
        val running = process.start()
        val log = running.inputStream.bufferedReader().use { it.readText() }
        val exit = running.waitFor()
        check(exit == 0) { "Magisk 修补失败（$exit）\n$log" }
        val generated = File(work, "new-boot.img")
        require(generated.isFile) { "Magisk 未生成 new-boot.img\n$log" }
        val output = File(work, "JWSK-magisk-30.7.img")
        generated.copyTo(output)
        return PatchResult(output, BootImageInspector.sha256(output), log)
    }

    private fun patchKernelSu(
        context: Context,
        work: File,
        input: File,
        kmi: String,
        kernel: File?
    ): PatchResult {
        val binary = File(context.applicationInfo.nativeLibraryDir, "libksud.so")
        require(binary.isFile) { "缺少 KernelSU 修补引擎 libksud.so" }
        val outputName = if (kernel == null) "JWSK-kernelsu-lkm-3.2.5.img" else "JWSK-kernelsu-kernel-3.2.5.img"
        val args = mutableListOf(
            binary.absolutePath, "boot-patch",
            "--boot", input.absolutePath,
            "--out", work.absolutePath,
            "--out-name", outputName
        )
        if (kmi.isNotBlank()) args += listOf("--kmi", kmi.trim())
        if (kernel != null) args += listOf("--kernel", kernel.absolutePath)
        val process = ProcessBuilder(args).directory(work).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        check(exit == 0) { "KernelSU 修补失败（$exit）\n$log" }
        val output = File(work, outputName)
        require(output.isFile) { "KernelSU 未生成输出镜像\n$log" }
        return PatchResult(output, BootImageInspector.sha256(output), log)
    }

    private fun copyAsset(context: Context, asset: String, output: File) {
        context.assets.open(asset).use { input ->
            output.outputStream().use { input.copyTo(it) }
        }
        output.setReadable(true, true)
        output.setExecutable(true, true)
    }
}
