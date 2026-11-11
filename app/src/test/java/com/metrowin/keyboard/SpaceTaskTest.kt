package com.metrowin.keyboard

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.metrowin.keyboard.layout.Key
import com.metrowin.keyboard.layout.KeyType
import com.metrowin.keyboard.layout.KeyboardBuilder
import com.metrowin.keyboard.ui.Icon
import com.metrowin.keyboard.ui.KeyboardListener
import com.metrowin.keyboard.ui.KeyboardView
import com.metrowin.keyboard.ui.TaskItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpaceTaskTest {
    private class Spy : KeyboardListener {
        val functions = ArrayList<KeyType>(); val tasks = ArrayList<String>(); val texts = ArrayList<String>()
        override fun onPress(key: Key) {}
        override fun onText(key: Key, text: String) { texts += text }
        override fun onFunction(key: Key, repeat: Boolean) { functions += key.type }
        override fun onPopupText(text: String) {}
        override fun onSwipe(path: List<com.metrowin.keyboard.engine.SwipePoint>) {}
        override fun onCursorMove(steps: Int) {}
        override fun onShiftLongPress() {}
        override fun onSpaceTask(id: String) { tasks += id }
    }

    private val items = listOf("emoji", "gif", "translate", "clipboard", "mic", "help", "settings").map { TaskItem(it, Icon.EMOJI, it) }

    private fun setup(withTask: Boolean = true): Triple<KeyboardView, Spy, com.metrowin.keyboard.layout.KeyboardPage> {
        val app = ApplicationProvider.getApplicationContext<KeyboardApp>()
        // Attached to a window, as it is on the phone: a view that is not attached keeps what it posts until it is.
        val activity = org.robolectric.Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity::class.java).setup().get()
        val view = KeyboardView(activity)
        activity.setContentView(view, android.view.ViewGroup.LayoutParams(1080, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        val spy = Spy(); view.listener = spy
        val page = KeyboardBuilder.letters(app.layouts.get("en")!!, false, true, false)
        view.setPage(page)
        if (withTask) view.setSpaceTask(items)
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, 1080, view.measuredHeight)
        view.setPage(page)
        return Triple(view, spy, page)
    }

    private fun touch(v: KeyboardView, action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, action, x, y, 0))
    }

    private fun pause(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)

    @Test fun holdingTheSpaceBarAndSlidingToAnItemChoosesItWithoutLiftingTheFinger() {
        val (v, spy, page) = setup()
        val space = page.keys.first { it.type == KeyType.SPACE }
        touch(v, MotionEvent.ACTION_DOWN, space.centerX, space.centerY)
        pause(600)
        // The menu is open: slide up into the third column of seven.
        val cell = 1080f / items.size
        touch(v, MotionEvent.ACTION_MOVE, cell * 2.5f, space.top - 80f)
        touch(v, MotionEvent.ACTION_UP, cell * 2.5f, space.top - 80f)
        assertEquals(listOf("translate"), spy.tasks)
        assertTrue("no space is typed", spy.functions.none { it == KeyType.SPACE })
    }

    @Test fun lettingGoOnTheSpaceBarClosesTheMenuAndChoosesNothing() {
        val (v, spy, page) = setup()
        val space = page.keys.first { it.type == KeyType.SPACE }
        touch(v, MotionEvent.ACTION_DOWN, space.centerX, space.centerY)
        pause(600)
        touch(v, MotionEvent.ACTION_UP, space.centerX, space.centerY)
        assertTrue(spy.tasks.isEmpty()); assertTrue(spy.functions.none { it == KeyType.SPACE })
    }

    @Test fun aQuickTapOnTheSpaceBarStillTypesASpace() {
        val (v, spy, page) = setup()
        val space = page.keys.first { it.type == KeyType.SPACE }
        touch(v, MotionEvent.ACTION_DOWN, space.centerX, space.centerY)
        pause(80)
        touch(v, MotionEvent.ACTION_UP, space.centerX, space.centerY)
        assertEquals(listOf(KeyType.SPACE), spy.functions); assertTrue(spy.tasks.isEmpty())
    }

    @Test fun withoutSpaceTaskHoldingTheSpaceBarDoesNothingSpecial() {
        val (v, spy, page) = setup(withTask = false)
        val space = page.keys.first { it.type == KeyType.SPACE }
        touch(v, MotionEvent.ACTION_DOWN, space.centerX, space.centerY)
        pause(600)
        touch(v, MotionEvent.ACTION_UP, space.centerX, space.centerY)
        assertTrue(spy.tasks.isEmpty())
    }

    @Test fun holdingTheSymbolsKeyOpensTheEmoji() {
        val (v, spy, page) = setup()
        v.setHoldSymbolsEmoji(true)
        val sym = page.keys.first { it.type == KeyType.SYMBOLS }
        touch(v, MotionEvent.ACTION_DOWN, sym.centerX, sym.centerY)
        pause(600)
        touch(v, MotionEvent.ACTION_UP, sym.centerX, sym.centerY)
        assertEquals(listOf(KeyType.EMOJI), spy.functions)
    }
}
