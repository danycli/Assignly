package com.danycli.assignmentchecker.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "com.danycli.assignmentchecker"

@LargeTest
@RunWith(AndroidJUnit4::class)
class ScrollingBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollAssignmentList() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            // Start the app and wait for the assignment list to appear
            // This assumes the user is already logged in (using our offline-first cache)
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.res("assignment_list")), 10000)
        }
    ) {
        val list = device.findObject(By.res("assignment_list"))
        // Perform a fling gesture to measure frame timing
        list.fling(androidx.test.uiautomator.Direction.DOWN)
    }
}
