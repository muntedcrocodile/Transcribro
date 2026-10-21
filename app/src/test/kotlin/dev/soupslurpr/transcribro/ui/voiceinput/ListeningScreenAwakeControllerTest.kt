package dev.soupslurpr.transcribro.ui.voiceinput

import android.app.Dialog
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36])
class ListeningScreenAwakeControllerTest {
    private lateinit var dialog: Dialog
    private lateinit var controller: ListeningScreenAwakeController
    private lateinit var listener: RecognitionListener
    private val events = mutableListOf<String>()

    @Before
    fun setUp() {
        dialog = Dialog(RuntimeEnvironment.getApplication())
        controller = ListeningScreenAwakeController { dialog.window }
        listener = controller.track(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { events.add("ready") }
            override fun onBeginningOfSpeech() { events.add("beginning") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { events.add("end") }
            override fun onError(error: Int) { events.add("error:$error") }
            override fun onResults(results: Bundle?) { events.add("results") }
            override fun onPartialResults(partialResults: Bundle?) { events.add("partial") }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        controller.onStartInputView()
        controller.onWindowShown()
    }

    private fun assertAwake(expected: Boolean) {
        assertEquals(expected, dialog.window!!.attributes.flags and FLAG_KEEP_SCREEN_ON != 0)
    }

    @Test
    fun defaultOffDoesNotKeepScreenAwake() {
        controller.startListening(false) {}
        listener.onReadyForSpeech(Bundle())
        assertAwake(false)
    }

    @Test
    fun enabledOnlyHoldsFlagDuringListeningAndPreservesOtherFlags() {
        dialog.window!!.addFlags(FLAG_SECURE)
        controller.setEnabled(true)
        assertAwake(false)
        controller.startListening(false) { assertAwake(true) }
        listener.onReadyForSpeech(Bundle())
        listener.onPartialResults(Bundle())
        assertAwake(true)
        listener.onResults(Bundle())
        assertAwake(false)
        assertTrue(dialog.window!!.attributes.flags and FLAG_SECURE != 0)
        assertEquals(listOf("ready", "partial", "results"), events)
    }

    @Test
    fun continuousListeningStaysAwakeBetweenPhrases() {
        controller.setEnabled(true)
        controller.startListening(false) {}
        listener.onBeginningOfSpeech()
        listener.onEndOfSpeech()
        listener.onPartialResults(Bundle())
        assertAwake(true)
        listener.onBeginningOfSpeech()
        assertAwake(true)
        controller.onListeningStopped()
        assertAwake(false)
    }

    @Test
    fun autoStopReleasesBeforeFinalTranscription() {
        controller.setEnabled(true)
        controller.startListening(true) {}
        assertAwake(true)
        listener.onEndOfSpeech()
        assertAwake(false)
        listener.onResults(Bundle())
        assertAwake(false)
        assertEquals(listOf("end", "results"), events)
    }

    @Test
    fun stopOrCancelReleasesImmediatelyAndAllowsNextSession() {
        controller.setEnabled(true)
        repeat(2) {
            controller.startListening(false) {}
            assertAwake(true)
            controller.onListeningStopped()
            assertAwake(false)
        }
    }

    @Test
    fun everyRecognitionErrorReleasesTheFlagAndIsForwarded() {
        controller.setEnabled(true)
        for (error in 1..15) {
            controller.startListening(false) {}
            assertAwake(true)
            listener.onError(error)
            assertAwake(false)
            assertEquals("error:$error", events.last())
        }
    }

    @Test
    fun failedStartReleasesTheFlagAndRethrows() {
        controller.setEnabled(true)
        assertThrows(SecurityException::class.java) {
            controller.startListening(false) { throw SecurityException("Microphone denied") }
        }
        assertAwake(false)
    }

    @Test
    fun changingPreferenceTakesEffectDuringListening() {
        controller.startListening(false) {}
        controller.setEnabled(true)
        assertAwake(true)
        controller.setEnabled(false)
        assertAwake(false)
        controller.setEnabled(true)
        assertAwake(true)
        listener.onError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        controller.setEnabled(false)
        controller.setEnabled(true)
        assertAwake(false)
    }

    @Test
    fun hidingAndReshowingCannotReviveOldListeningSession() {
        controller.setEnabled(true)
        controller.startListening(false) {}
        controller.onWindowHidden()
        assertAwake(false)
        controller.startListening(false) { fail("Started after keyboard dismissal") }
        listener.onReadyForSpeech(Bundle())
        listener.onBeginningOfSpeech()
        controller.onWindowShown()
        assertAwake(false)
        controller.onStartInputView()
        controller.startListening(false) {}
        assertAwake(true)
    }

    @Test
    fun finishingInputViewRejectsQueuedStartsAndLateCallbacks() {
        controller.setEnabled(true)
        controller.startListening(false) {}
        controller.onFinishInputView()
        assertAwake(false)
        controller.startListening(false) { fail("Started after keyboard dismissal") }
        listener.onReadyForSpeech(Bundle())
        listener.onBeginningOfSpeech()
        controller.onStartInputView()
        controller.onWindowShown()
        assertAwake(false)
    }

    @Test
    fun destructionReleasesAndRejectsFurtherStarts() {
        controller.setEnabled(true)
        controller.startListening(false) {}
        controller.onDestroy()
        assertAwake(false)
        controller.onStartInputView()
        controller.onWindowShown()
        controller.startListening(false) { fail("Started after destruction") }
        listener.onReadyForSpeech(Bundle())
        assertAwake(false)
    }

    @Test
    fun startBeforeWindowShownWaitsForVisibility() {
        controller.onWindowHidden()
        controller.setEnabled(true)
        controller.onStartInputView()
        controller.startListening(false) {}
        assertAwake(false)
        controller.onWindowShown()
        assertAwake(true)
    }

    @Test
    fun missingWindowIsSafeAndReappliesWhenAvailable() {
        var available = false
        val lateWindow = ListeningScreenAwakeController { if (available) dialog.window else null }
        lateWindow.setEnabled(true)
        lateWindow.onStartInputView()
        lateWindow.startListening(false) {}
        assertAwake(false)
        available = true
        lateWindow.onWindowShown()
        assertAwake(true)
        lateWindow.onDestroy()
        assertAwake(false)
    }
}
