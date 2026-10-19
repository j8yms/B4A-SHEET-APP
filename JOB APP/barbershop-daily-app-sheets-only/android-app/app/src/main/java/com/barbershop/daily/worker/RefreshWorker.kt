package com.barbershop.daily.worker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.barbershop.daily.data.SessionStore
import com.barbershop.daily.data.SheetsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val TAG = "RefreshWorker"
        
        // Only run if user is logged in (has a session)
        val session = SessionStore.load(applicationContext) ?: return@withContext Result.success()
        
        try {
            // Refresh today's data
            val today = SheetsRepository.listToday(session.token)
            Log.d(TAG, "Scheduled refresh completed for ${today.tabName} at ${getKenyaTime()}")
            
            // Store last successful refresh time
            storeLastRefreshTime(applicationContext, System.currentTimeMillis())
            
            // Could broadcast an intent or use a shared flow to notify UI
            // For now, the polling in HomeScreen/TransactionsScreen will pick up changes
            
            Result.success()
        } catch (e: Throwable) {
            Log.w(TAG, "Scheduled refresh failed: ${e.message}")
            // Don't retry immediately - let the next scheduled run handle it
            Result.success()
        }
    }

    private fun getKenyaTime(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Africa/Nairobi"))
        return String.format("%02d:%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND))
    }

    private fun storeLastRefreshTime(context: Context, timestamp: Long) {
        val prefs = context.getSharedPreferences("refresh_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_refresh_time", timestamp).apply()
    }

    companion object {
        // Schedule times in Kenya time (hours)
        val REFRESH_HOURS = listOf(8, 12, 18)
        
        fun scheduleRefreshes(context: Context) {
            val workManager = androidx.work.WorkManager.getInstance(context)
            val TAG = "RefreshWorker"
            
            // Cancel existing refresh workers
            workManager.cancelAllWorkByTag("scheduled_refresh")
            
            val now = Calendar.getInstance(TimeZone.getTimeZone("Africa/Nairobi"))
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMinute = now.get(Calendar.MINUTE)
            
            for (hour in REFRESH_HOURS) {
                val triggerTime = Calendar.getInstance(TimeZone.getTimeZone("Africa/Nairobi"))
                triggerTime.set(Calendar.HOUR_OF_DAY, hour)
                triggerTime.set(Calendar.MINUTE, 0)
                triggerTime.set(Calendar.SECOND, 0)
                triggerTime.set(Calendar.MILLISECOND, 0)
                
                // If this time has already passed today, schedule for tomorrow
                if (hour < currentHour || (hour == currentHour && 0 <= currentMinute)) {
                    triggerTime.add(Calendar.DAY_OF_YEAR, 1)
                }
                
                val delayMillis = triggerTime.timeInMillis - System.currentTimeMillis()
                
                if (delayMillis > 0) {
                    val request = androidx.work.OneTimeWorkRequestBuilder<RefreshWorker>()
                        .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                        .addTag("scheduled_refresh")
                        .setConstraints(
                            androidx.work.Constraints.Builder()
                                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                .build()
                        )
                        .build()
                    
                    workManager.enqueue(request)
                    Log.d(TAG, "Scheduled refresh for ${String.format("%02d:00", hour)} Kenya time (in ${delayMillis / 1000 / 60} minutes)")
                }
            }
            
            // Also schedule a periodic worker to re-schedule daily
            scheduleDailyReschedule(context)
        }
        
        fun getLastRefreshTime(context: Context): Long {
            val prefs = context.getSharedPreferences("refresh_prefs", Context.MODE_PRIVATE)
            return prefs.getLong("last_refresh_time", 0)
        }

        fun storeLastRefreshTime(context: Context, timestamp: Long) {
            val prefs = context.getSharedPreferences("refresh_prefs", Context.MODE_PRIVATE)
            prefs.edit().putLong("last_refresh_time", timestamp).apply()
        }
        
        private fun scheduleDailyReschedule(context: Context) {
            val workManager = androidx.work.WorkManager.getInstance(context)
            
            // Cancel existing reschedule workers
            workManager.cancelAllWorkByTag("daily_reschedule")
            
            // Schedule a daily worker that runs at 00:05 Kenya time to re-schedule the day's refreshes
            val triggerTime = Calendar.getInstance(TimeZone.getTimeZone("Africa/Nairobi"))
            triggerTime.set(Calendar.HOUR_OF_DAY, 0)
            triggerTime.set(Calendar.MINUTE, 5)
            triggerTime.set(Calendar.SECOND, 0)
            triggerTime.set(Calendar.MILLISECOND, 0)
            
            // If already past 00:05 today, schedule for tomorrow
            val now = Calendar.getInstance(TimeZone.getTimeZone("Africa/Nairobi"))
            if (now.get(Calendar.HOUR_OF_DAY) > 0 || (now.get(Calendar.HOUR_OF_DAY) == 0 && now.get(Calendar.MINUTE) >= 5)) {
                triggerTime.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            val delayMillis = triggerTime.timeInMillis - System.currentTimeMillis()
            
            if (delayMillis > 0) {
                val request = androidx.work.OneTimeWorkRequestBuilder<RescheduleWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .addTag("daily_reschedule")
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                
                workManager.enqueue(request)
            }
        }
    }
}

class RescheduleWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        RefreshWorker.scheduleRefreshes(applicationContext)
        Result.success()
    }
}