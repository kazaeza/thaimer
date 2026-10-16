package com.kazaeza.thaimer

import android.Manifest
import android.app.Notification
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.test.core.app.ApplicationProvider
import com.kazaeza.thaimer.service.WorkoutService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<ThaimerApp>()

    @Before
    fun grant() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `pick a workout, start it, the clock runs in a foreground service, stop it`() {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Спарринг: рваный темп"))
        compose.onNodeWithText("Спарринг: рваный темп").performClick()
        compose.onNodeWithText("СТАРТ").performClick()
        compose.waitForIdle()

        val session = app.controller.state.value
        assertNotNull(session)
        assertTrue(session!!.running)
        assertEquals("sparring", session.timeline.workout.id)
        compose.onNodeWithText("ДАЛЕЕ").assertExists()

        val started = shadowOf(app).nextStartedService
        assertEquals(WorkoutService::class.java.name, started.component?.className)

        // Prep (10 s) runs out: the warm-up begins.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        compose.waitForIdle()
        assertEquals(1, app.controller.state.value!!.segment)
        compose.onNodeWithText("Суставная разминка").assertExists()

        // The service goes foreground with a live notification and lock-screen controls.
        val controller = Robolectric.buildService(WorkoutService::class.java, started).create().startCommand(0, 1)
        val service = controller.get()
        val notification: Notification = shadowOf(service).lastForegroundNotification
        assertTrue(notification.extras.getString(Notification.EXTRA_TITLE)!!.contains("Суставная разминка"))
        assertEquals(3, notification.actions.size)

        // "Стоп" from the notification ends the workout; the app is back on the workout's page.
        controller.withIntent(Intent(app, WorkoutService::class.java).setAction(WorkoutService.ACTION_STOP)).startCommand(0, 2)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(app.controller.state.value)
        compose.waitForIdle()
        compose.onNodeWithText("СТАРТ").assertExists()
    }
}
