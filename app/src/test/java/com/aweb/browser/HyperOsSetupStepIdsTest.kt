package com.aweb.browser

import com.aweb.browser.ui.setup.HyperOsSetupStepIds
import org.junit.Assert.*
import org.junit.Test

class HyperOsSetupStepIdsTest {

    @Test fun `required setup step ids are stable and unique`() {
        val required = HyperOsSetupStepIds.REQUIRED
        assertEquals(5, required.size)
        assertTrue(HyperOsSetupStepIds.AUTOSTART in required)
        assertTrue(HyperOsSetupStepIds.BATTERY_OPTIMIZATION in required)
        assertTrue(HyperOsSetupStepIds.HYPEROS_BATTERY_SAVER in required)
        assertTrue(HyperOsSetupStepIds.LOCK_RECENTS in required)
        assertTrue(HyperOsSetupStepIds.NOTIFICATIONS in required)
    }

    @Test fun `optional keep screen awake is not required`() {
        assertFalse(HyperOsSetupStepIds.KEEP_SCREEN_AWAKE in HyperOsSetupStepIds.REQUIRED)
    }
}
