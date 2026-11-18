package com.example.agent.skill

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.service.HiyoriForegroundService
import java.util.concurrent.TimeUnit

class AgentWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val goal = inputData.getString(KEY_DEFERRED_GOAL) ?: return Result.success()
        HiyoriForegroundService.start(applicationContext, "Scheduled task: $goal")
        return Result.success()
    }

    companion object {
        const val KEY_DEFERRED_GOAL = "deferred_goal"

        fun scheduleDeferredTask(context: Context, goal: String, delayMinutes: Long) {
            val request = OneTimeWorkRequestBuilder<AgentWatchdogWorker>()
                .setInitialDelay(delayMinutes.coerceAtLeast(1L), TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_DEFERRED_GOAL to goal))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
