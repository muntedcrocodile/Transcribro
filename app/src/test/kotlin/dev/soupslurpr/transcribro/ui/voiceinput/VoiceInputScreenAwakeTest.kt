package dev.soupslurpr.transcribro.ui.voiceinput

import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36, 37])
class VoiceInputScreenAwakeTest {
    @Test
    fun androidTeardownFinishesActiveViewBeforeDestroyingLifecycle() {
        val serviceController = Robolectric.buildService(VoiceInput::class.java).create()
        val service = serviceController.get()
        val owner = ReflectionHelpers.getField<VoiceInputLifecycleOwner>(
            service, "voiceInputLifecycleOwner"
        )
        service.onStartInputView(null, false)
        service.onWindowShown()
        service.screenAwakeController.setEnabled(true)
        service.screenAwakeController.startListening(false) {}
        val window = service.window.window!!
        val teardownEvents = mutableListOf<Lifecycle.Event>()
        var frameworkViewActiveAtDestruction: Boolean? = null
        owner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            teardownEvents.add(event)
            if (event == Lifecycle.Event.ON_DESTROY) {
                frameworkViewActiveAtDestruction = ReflectionHelpers.getField(
                    service, "mInputViewStarted"
                )
            }
        })
        teardownEvents.clear()

        // Framework-owned flags are normally set before it invokes onStartInputView.
        // Without these, directly invoking the callback misses onDestroy's finishViews path.
        ReflectionHelpers.setField(service, "mInputStarted", true)
        ReflectionHelpers.setField(service, "mInputViewStarted", true)
        serviceController.destroy()

        assertEquals(
            listOf(Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY),
            teardownEvents
        )
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        assertEquals(false, frameworkViewActiveAtDestruction)
        assertEquals(0, window.attributes.flags and FLAG_KEEP_SCREEN_ON)
    }

    @Test
    fun lateViewCallbacksCannotReviveDestroyedLifecycle() {
        val owner = VoiceInputLifecycleOwner()
        owner.onCreate()
        owner.onResume()
        owner.onDestroy()
        owner.onPause()
        owner.onResume()
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }

    @Test
    fun finishingViewBeforeResumeDoesNotPromoteLifecycle() {
        val owner = VoiceInputLifecycleOwner()
        owner.onCreate()
        owner.onPause()
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)
        owner.onDestroy()
    }

    @Test
    fun serviceLifecycleClearsActualImeWindowFlag() {
        // Exercise each Android callback independently: dismissals do not always finish input.
        val dismissals: List<(VoiceInput) -> Unit> = listOf(
            { it.onFinishInputView(false) },
            { it.onFinishInputView(true) },
            { it.onWindowHidden() },
            { it.onFinishInput() }
        )
        for (dismiss in dismissals) {
            val serviceController = Robolectric.buildService(VoiceInput::class.java).create()
            val service = serviceController.get()
            try {
                service.onStartInputView(null, false)
                service.onWindowShown()
                service.screenAwakeController.setEnabled(true)
                service.screenAwakeController.startListening(false) {}
                assertTrue(service.window.window!!.attributes.flags and FLAG_KEEP_SCREEN_ON != 0)
                dismiss(service)
                assertEquals(0, service.window.window!!.attributes.flags and FLAG_KEEP_SCREEN_ON)
                service.onStartInputView(null, false)
                service.onWindowShown()
                assertEquals(0, service.window.window!!.attributes.flags and FLAG_KEEP_SCREEN_ON)
            } finally {
                serviceController.destroy()
            }
        }
    }

    @Test
    fun destroyingServiceClearsActualImeWindowFlag() {
        val serviceController = Robolectric.buildService(VoiceInput::class.java).create()
        val service = serviceController.get()
        service.onStartInputView(null, false)
        service.onWindowShown()
        service.screenAwakeController.setEnabled(true)
        service.screenAwakeController.startListening(false) {}
        val window = service.window.window!!
        assertTrue(window.attributes.flags and FLAG_KEEP_SCREEN_ON != 0)
        serviceController.destroy()
        assertEquals(0, window.attributes.flags and FLAG_KEEP_SCREEN_ON)
    }
}
