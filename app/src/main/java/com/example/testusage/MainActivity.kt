package com.example.testusage

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Spinner
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.testusage.ui.theme.TestUsageTheme
import java.util.*
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat

//import java.text.SimpleDateFormat

class MainActivity : ComponentActivity() {
    private val sdfOutput = SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TestUsageTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    UsageStatsUI(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun UsageStatsUI(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val intervals = listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")
    var selectedInterval by remember { mutableStateOf("DAILY") }
    var startDate by remember { mutableStateOf(Calendar.getInstance()) }
    var endDate by remember { mutableStateOf(Calendar.getInstance()) }
    var output by remember { mutableStateOf("") }

    Column(modifier.padding(16.dp)) {
        Text("Select Interval Type")
        DropdownMenuBox(
            items = intervals,
            selected = selectedInterval,
            onItemSelected = { selectedInterval = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            showDateTimePicker(context, startDate) { startDate = it }
        }) { Text("Start: ${formatDateTime(startDate)}") }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            showDateTimePicker(context, endDate) { endDate = it }
        }) { Text("End: ${formatDateTime(endDate)}") }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            val intervalValue = when (selectedInterval) {
                "DAILY" -> UsageStatsManager.INTERVAL_DAILY
                "WEEKLY" -> UsageStatsManager.INTERVAL_WEEKLY
                "MONTHLY" -> UsageStatsManager.INTERVAL_MONTHLY
                "YEARLY" -> UsageStatsManager.INTERVAL_YEARLY
                else -> UsageStatsManager.INTERVAL_DAILY
            }
            output = collectAndSaveUsageStats(context, startDate.timeInMillis, endDate.timeInMillis, intervalValue)
        }) {
            Text("Load Usage Stats")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = output)
    }
}

@Composable
fun DropdownMenuBox(items: List<String>, selected: String, onItemSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }) {
            Text(selected)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item) }, onClick = {
                    onItemSelected(item)
                    expanded = false
                })
            }
        }
    }
}

fun showDateTimePicker(context: Context, initial: Calendar, onDateTimeSet: (Calendar) -> Unit) {
    // Get current date for DatePickerDialog default
    val currentCalendar = Calendar.getInstance()

    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth -> // year, month(0-11), dayOfMonth
            // Date is selected, now show TimePickerDialog
            // Use current time for TimePickerDialog default
            val currentTimeCalendar = Calendar.getInstance()
            TimePickerDialog(
                context,
                { _, hourOfDay, minute -> // hourOfDay(0-23), minute(0-59)
                    // Time is selected, create a NEW Calendar instance
                    val newCal = Calendar.getInstance().apply {
                        // Set the date selected from DatePickerDialog
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month) // Month is 0-based
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)

                        // Set the time selected from TimePickerDialog
                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)        // Reset seconds
                        set(Calendar.MILLISECOND, 0) // Reset milliseconds
                    }
                    // Pass the NEW Calendar instance back via the callback
                    onDateTimeSet(newCal)
                },
                currentTimeCalendar.get(Calendar.HOUR_OF_DAY),
                currentTimeCalendar.get(Calendar.MINUTE),
                true // Use 24-hour format
            ).show()
        },
        // Initial date shown in DatePickerDialog (use the initial value passed in or current)
        initial.get(Calendar.YEAR),
        initial.get(Calendar.MONTH),
        initial.get(Calendar.DAY_OF_MONTH)
    ).show()
}

fun formatDateTime(cal: Calendar): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(cal.time)
}

fun collectAndSaveUsageStats(context: Context, startMillis: Long, endMillis: Long, interval: Int): String {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val statsList = usm.queryUsageStats(interval, startMillis, endMillis)
    Log.d("UsageStats", "Received ${statsList.size} entries")

    val sdf = SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault())
    val fileName = "usage_stats_${System.currentTimeMillis()}.csv"
    val csvFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

    val header = listOf(
        "packageName",
        "firstTimeStamp",
        "lastTimeStamp",
        "lastTimeUsed",
        "lastTimeVisible",
        "lastTimeForegroundServiceUsed",
        "totalTimeVisible",
        "totalTimeForegroundServiceUsed",
        "totalTimeInForeground"
    )

    csvFile.bufferedWriter().use { writer ->
        writer.write(header.joinToString(","))
        writer.newLine()

        for (usageStats in statsList) {
            val totalTime = usageStats.totalTimeInForeground / 1000
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && totalTime > 0) {
                val row = listOf(
                    usageStats.packageName,
                    sdf.format(Date(usageStats.firstTimeStamp)),
                    sdf.format(Date(usageStats.lastTimeStamp)),
                    sdf.format(Date(usageStats.lastTimeUsed)),
                    sdf.format(Date(usageStats.lastTimeVisible)),
                    sdf.format(Date(usageStats.lastTimeForegroundServiceUsed)),
                    (usageStats.totalTimeVisible / 1000).toString(),
                    (usageStats.totalTimeForegroundServiceUsed / 1000).toString(),
                    (usageStats.totalTimeInForeground / 1000).toString()
                )
                writer.write(row.joinToString(","))
                writer.newLine()
            }
        }
    }

    Log.d("UsageStats", "Saved to ${csvFile.absolutePath}")
    return "Saved to ${csvFile.absolutePath}"
}
