package com.example.screentimeoverlay

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Runs at local midnight to prepare daily metrics rollover.
 * This worker intentionally does not touch app logic yet; it only
 * ensures the DateChangeGuard will signal a new day on next access.
 */
class MidnightResetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // No-op placeholder: DateChangeGuard behavior is check-on-read.
        // We keep this worker for scheduling cadence and future wiring.
        return Result.success()
    }
}


