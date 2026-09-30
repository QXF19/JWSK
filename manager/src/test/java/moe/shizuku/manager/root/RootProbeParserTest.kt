package moe.shizuku.manager.root

import org.junit.Assert.*
import org.junit.Test

class RootProbeParserTest {
    @Test fun magiskWithBundledKsudZeroIsNotHybrid() {
        val result = RootProbeParser.parse(listOf("JWSK_UID=0", "JWSK_MAGISK=30.7", "JWSK_KSU=Kernel Version: 0", "JWSK_KSU_PRESENT=1"))
        assertEquals(RootBackend.MAGISK, result.backend)
        assertTrue(result.canManageRootModules)
    }

    @Test fun staleKsuDirectoryDoesNotEnableKernelSu() {
        val result = RootProbeParser.parse(listOf("JWSK_UID=0", "JWSK_KSU=Kernel Version: 0", "JWSK_KSU_PRESENT=1"))
        assertEquals(RootBackend.NONE, result.backend)
        assertNull(result.kernelSuVersion)
    }

    @Test fun activeKernelSuHasPositiveKernelVersion() {
        val result = RootProbeParser.parse(listOf("JWSK_UID=0", "JWSK_KSU=Kernel Version: 30205", "JWSK_KSU_MODE=LKM"))
        assertEquals(RootBackend.KERNEL_SU, result.backend)
        assertEquals("30205", result.kernelSuVersion)
        assertTrue(result.canManageRootModules)
    }

    @Test fun deniedRootCannotEnablePrivilegedActions() {
        val result = RootProbeParser.parse(listOf("JWSK_UID=2000", "JWSK_MAGISK=30.7", "JWSK_KSU=Kernel Version: 30205"))
        assertEquals(RootBackend.NONE, result.backend)
        assertFalse(result.rootGranted)
        assertFalse(result.canManageRootModules)
    }

    @Test fun twoActiveFrameworksLockModuleChanges() {
        val result = RootProbeParser.parse(listOf("JWSK_UID=0", "JWSK_MAGISK=30.7", "JWSK_KSU=Kernel Version: 30205"))
        assertEquals(RootBackend.HYBRID, result.backend)
        assertFalse(result.canManageRootModules)
    }

    @Test fun failedProbeIsNotAnActiveKernel() {
        listOf("Kernel Version: -1", "permission denied", "", "0").forEach { output ->
            assertEquals(RootBackend.NONE, RootProbeParser.parse(listOf("JWSK_UID=0", "JWSK_KSU=$output")).backend)
        }
    }
}
