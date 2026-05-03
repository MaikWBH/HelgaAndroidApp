package com.helga.android.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.helga.android",
        stableIterations = 2,
        maxIterations = 8,
        startupModes = setOf(StartupMode.COLD),
    ) {
        pressHome()
        startActivityAndWait()
        scrollRecipeList()
    }
}

private fun MacrobenchmarkScope.scrollRecipeList() {
    val list = device.findObject(By.scrollable(true)) ?: return
    repeat(3) { list.scroll(Direction.DOWN, 1f) }
    repeat(3) { list.scroll(Direction.UP, 1f) }
}
