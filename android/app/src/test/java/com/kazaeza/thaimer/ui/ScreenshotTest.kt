package com.kazaeza.thaimer.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.kazaeza.thaimer.R
import com.kazaeza.thaimer.audio.VoiceStatus
import com.kazaeza.thaimer.data.Settings
import com.kazaeza.thaimer.data.Stats
import com.kazaeza.thaimer.data.Workouts
import com.kazaeza.thaimer.engine.Session
import com.kazaeza.thaimer.model.Kind
import com.kazaeza.thaimer.model.Timeline
import com.kazaeza.thaimer.model.TimelineBuilder
import com.kazaeza.thaimer.ui.detail.DetailScreen
import com.kazaeza.thaimer.ui.finish.FinishScreen
import com.kazaeza.thaimer.ui.home.HomeScreen
import com.kazaeza.thaimer.ui.intro.IntroScreen
import com.kazaeza.thaimer.ui.session.PlanSheet
import com.kazaeza.thaimer.ui.session.SessionActions
import com.kazaeza.thaimer.ui.session.SessionContent
import com.kazaeza.thaimer.ui.settings.SettingsSheet
import com.kazaeza.thaimer.ui.theme.ThaimerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/** Renders every screen to build/screenshots so the UI can be eyeballed without a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val noop = SessionActions({}, {}, {}, {}, {}, {}, {})
    private val dayMs = 24 * 60 * 60 * 1000L
    private val now = 1_790_000_000_000L

    private fun save(name: String, bitmap: Bitmap) {
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun shoot(name: String, dark: Boolean, rootIndex: Int = 0, content: @Composable () -> Unit) {
        compose.setContent { ThaimerTheme(dark = dark) { content() } }
        compose.waitForIdle()
        save(name, captureWindows())
    }

    /**
     * Draws every window (activity, then sheets/dialogs on top) with a software canvas.
     * Compose's own captureToImage() waits for a hardware frame that Robolectric never commits.
     */
    private fun captureWindows(): Bitmap {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val views = (global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<View>)
            .filter { it.isShown && it.width > 0 && it.height > 0 }
        val base = views.first()
        val bitmap = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        views.forEach { v ->
            val at = IntArray(2).also(v::getLocationOnScreen)
            canvas.save()
            canvas.translate(at[0].toFloat(), at[1].toFloat())
            v.draw(canvas)
            canvas.restore()
        }
        return bitmap
    }

    private fun timeline(id: String, seed: Long = 3) = TimelineBuilder.build(Workouts.byId(id)!!, 10, Random(seed))

    private fun session(t: Timeline, elapsed: Long, running: Boolean = true, finished: Boolean = false) =
        Session(t, elapsed, 0, running, finished, t.indexAt(elapsed), workedMs = elapsed)

    private val stats = Stats(
        workouts = 12, seconds = 6 * 3600 + 20 * 60,
        lastDone = mapOf("sparring" to now - dayMs, "classic" to now - 3 * dayMs),
        timesDone = mapOf("sparring" to 4, "classic" to 7),
    )

    @Test fun home_light() = shoot("01_home_light", false) {
        HomeScreen(Workouts.groups, stats, {}, {}, now = now)
    }

    @Test fun home_dark() = shoot("02_home_dark", true) {
        HomeScreen(Workouts.groups, stats, {}, {}, now = now)
    }

    @Test fun detail_sparring_light() = shoot("03_detail_sparring_light", false) {
        DetailScreen(Workouts.byId("sparring")!!, Settings(), {}, {})
    }

    @Test fun detail_tabata_dark() = shoot("04_detail_tabata_dark", true) {
        DetailScreen(Workouts.byId("tabata")!!, Settings(), {}, {})
    }

    @Test fun session_classic_light() {
        val t = timeline("classic")
        val seg = t.segments.first { it.step.name == "Двойка + хук" }
        shoot("05_session_classic_light", false) {
            SessionContent(t, seg.startMs + 41_400, running = true, soundOn = true, onClose = {}, onPlan = {}, actions = noop)
        }
    }

    @Test fun session_sparring_burst_dark() {
        val t = timeline("sparring")
        val burst = t.segments.filter { it.step.surprise }[3]
        shoot("06_session_burst_dark", true) {
            SessionContent(t, burst.startMs + 3_200, running = true, soundOn = true, onClose = {}, onPlan = {}, actions = noop)
        }
    }

    @Test fun session_sparring_masked_light() {
        val t = timeline("sparring")
        val pace = t.segments.filter { t.isMasked(it) }[4]
        shoot("07_session_masked_light", false) {
            SessionContent(t, pace.startMs + 5_000, running = true, soundOn = false, onClose = {}, onPlan = {}, actions = noop)
        }
    }

    @Test fun session_rest_paused_dark() {
        val t = timeline("championship")
        val rest = t.segments.first { it.step.kind == Kind.REST }
        shoot("08_session_rest_paused_dark", true) {
            SessionContent(t, rest.startMs + 52_000, running = false, soundOn = true, onClose = {}, onPlan = {}, actions = noop)
        }
    }

    @Test fun finish_light() {
        val t = timeline("sparring")
        shoot("09_finish_light", false) { FinishScreen(session(t, t.totalMs, running = false, finished = true)) {} }
    }

    @Test fun plan_sheet_dark() {
        val t = timeline("sparring")
        val current = t.segments.filter { it.step.surprise }[1].index
        shoot("10_plan_sheet_dark", true, rootIndex = 1) {
            PlanSheet(t, current, {}, {})
        }
    }

    @Test fun settings_sheet_light() = shoot("11_settings_light", false, rootIndex = 1) {
        SettingsSheet(Settings(), VoiceStatus.MISSING_RUSSIAN, {}, {}, {})
    }

    @Test fun intro() = shoot("00_intro", true) { IntroScreen {} }

    @Test fun detail_third_minute_dark() = shoot("13_detail_third_minute_dark", true) {
        DetailScreen(Workouts.byId("third-minute")!!, Settings(), {}, {})
    }

    @Test fun session_head_start_light() {
        val t = timeline("third-minute")
        val round = t.blocks.first { it.type == com.kazaeza.thaimer.model.BlockType.ROUND }
        shoot("14_session_head_start_light", false) {
            SessionContent(t, round.startMs + 30_000, running = true, soundOn = true, onClose = {}, onPlan = {}, actions = noop, startsInMs = 1_400)
        }
    }

    @Test fun launcher_icon() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)!!
        val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 432, 432)
        icon.draw(Canvas(bitmap))
        save("12_launcher_icon", bitmap)
    }
}
