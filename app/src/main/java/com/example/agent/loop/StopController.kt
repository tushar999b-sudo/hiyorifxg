package com.example.agent.loop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

class StopController {

    private val _isStopped = MutableStateFlow(false)
    val isStopped: StateFlow<Boolean> = _isStopped.asStateFlow()

    @Volatile
    private var activeLoopJob: Job? = null

    private val queuedActions = ConcurrentLinkedQueue<String>()
    private val stopListeners = ConcurrentLinkedQueue<() -> Unit>()

    fun attachJob(job: Job) {
        activeLoopJob = job
    }

    fun enqueueAction(actionLabel: String) {
        queuedActions.add(actionLabel)
    }

    fun dequeueAction(actionLabel: String) {
        queuedActions.remove(actionLabel)
    }

    fun addStopListener(listener: () -> Unit) {
        stopListeners.add(listener)
    }

    fun resetForNewRun() {
        _isStopped.value = false
        queuedActions.clear()
    }

    fun requestStop() {
        _isStopped.value = true
        queuedActions.clear()
        activeLoopJob?.cancel(CancellationException("Agent stopped immediately by Master"))
        activeLoopJob = null
        stopListeners.forEach { runCatching { it.invoke() } }
    }

    fun throwIfStopped() {
        if (_isStopped.value) {
            throw CancellationException("Execution halted by StopController")
        }
    }
}
