package dev.soupslurpr.transcribro.ui.voiceinput

import android.os.Bundle
import android.speech.RecognitionListener
import android.view.Window
import android.view.WindowManager

/** Main-thread state for the IME window. No power or screen-timeout settings are changed. */
internal class ListeningScreenAwakeController(private val window: () -> Window?) {
    private var enabled = false
    private var inputViewStarted = false
    private var windowVisible = false
    private var listening = false
    private var autoStopRecognition = false
    private var destroyed = false

    fun setEnabled(value: Boolean) {
        enabled = value
        updateWindow()
    }

    fun onStartInputView() {
        inputViewStarted = true
        updateWindow()
    }

    fun onWindowShown() {
        windowVisible = true
        updateWindow()
    }

    /** Start requests can precede onWindowShown, but must belong to an active input view. */
    fun startListening(autoStop: Boolean, start: () -> Unit) {
        if (!inputViewStarted || destroyed) return
        autoStopRecognition = autoStop
        listening = true
        updateWindow()
        try {
            start()
        } catch (e: RuntimeException) {
            onListeningStopped()
            throw e
        }
    }

    fun track(listener: RecognitionListener): RecognitionListener =
        object : RecognitionListener by listener {
            override fun onEndOfSpeech() {
                // The service also emits this between phrases in continuous mode.
                if (autoStopRecognition) onListeningStopped()
                listener.onEndOfSpeech()
            }

            override fun onError(error: Int) {
                onListeningStopped()
                listener.onError(error)
            }

            override fun onResults(results: Bundle?) {
                onListeningStopped()
                listener.onResults(results)
            }
        }

    /** Stop, cancel, final results and every error release the window immediately. */
    fun onListeningStopped() {
        listening = false
        updateWindow()
    }

    fun onFinishInputView() {
        inputViewStarted = false
        onListeningStopped()
    }

    fun onWindowHidden() {
        windowVisible = false
        inputViewStarted = false
        onListeningStopped()
    }

    fun onDestroy() {
        destroyed = true
        inputViewStarted = false
        windowVisible = false
        onListeningStopped()
    }

    private fun updateWindow() {
        val imeWindow = window() ?: return
        if (enabled && inputViewStarted && windowVisible && listening && !destroyed) {
            imeWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            imeWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
