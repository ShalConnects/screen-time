package com.example.screentimeoverlay

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Schedules a one-time WorkManager task to run at the next local midnight. */
object MidnightResetScheduler {

    private const val UNIQUE_WORK_NAME = "midnight_reset_work"

    fun scheduleNext(context: Context) {
        val delayMs = millisUntilNextMidnight()
        val request = OneTimeWorkRequestBuilder<MidnightResetWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun millisUntilNextMidnight(): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val tomorrow = LocalDate.now(zone).plusDays(1)
        // Use 00:01 AM to align with overlay day start
        val nextMidnight = ZonedDateTime.of(tomorrow, LocalTime.of(0, 1), zone)
        return Duration.between(now, nextMidnight).toMillis().coerceAtLeast(0L)
    }
}


