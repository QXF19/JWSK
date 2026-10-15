package me.weishu.kernelsu.jwsk.alpha

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaPolicyTest {
    @Test fun permanentGrantIsActive() {
        assertTrue(AlphaPolicy(10001, 2, 0, true, true).granted)
    }

    @Test fun expiredGrantHasNoRootBadge() {
        assertFalse(AlphaPolicy(10001, 2, 1, true, true).granted)
    }

    @Test fun futureGrantIsActive() {
        assertTrue(AlphaPolicy(10001, 2, System.currentTimeMillis() / 1000 + 3600, true, true).granted)
    }

    @Test fun deniedAndQueryAreNeverGrants() {
        assertFalse(AlphaPolicy(10001, 1, 0, true, true).granted)
        assertFalse(AlphaPolicy(10001, 0, 0, true, true).granted)
    }
}
