package dev.soupslurpr.transcribro.ui.voiceinput

import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36])
class VoiceInputScreenAwakeTest {
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
