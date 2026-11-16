package com.savoj.volumpdf.ui.reader

import android.content.pm.ActivityInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigRecreateLogicTest {
    @Test
    fun fontScaleUiModeLocaleAndDirectionNeedRecreate() {
        assertTrue(ConfigRecreateLogic.needsRecreate(ActivityInfo.CONFIG_FONT_SCALE))
        assertTrue(ConfigRecreateLogic.needsRecreate(ActivityInfo.CONFIG_UI_MODE))
        assertTrue(ConfigRecreateLogic.needsRecreate(ActivityInfo.CONFIG_LOCALE or ActivityInfo.CONFIG_ORIENTATION))
        assertTrue(ConfigRecreateLogic.needsRecreate(ActivityInfo.CONFIG_LAYOUT_DIRECTION))
        assertTrue(ConfigRecreateLogic.needsRecreate(ActivityInfo.CONFIG_FONT_WEIGHT_ADJUSTMENT))
    }

    @Test
    fun orientationAndSizeAreHandledInPlace() {
        assertFalse(ConfigRecreateLogic.needsRecreate(0))
        assertFalse(ConfigRecreateLogic.needsRecreate(
            ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_SIZE or
                ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or ActivityInfo.CONFIG_SCREEN_LAYOUT
        ))
    }

    @Test
    fun recreateWaitsForOpenDialogOrCard() {
        assertFalse(ConfigRecreateLogic.shouldRecreateNow(pending = true, busy = true))
        assertTrue(ConfigRecreateLogic.shouldRecreateNow(pending = true, busy = false))
        assertFalse(ConfigRecreateLogic.shouldRecreateNow(pending = false, busy = false))
    }
}
