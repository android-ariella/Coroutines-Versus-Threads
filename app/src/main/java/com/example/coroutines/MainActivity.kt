package com.example.coroutines

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.coroutines.ui.theme.CoroutinesTheme
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TASK_COUNT = 100_000
private const val CALCULATION_ITERATIONS = 1_000
private const val WAIT_MS = 5_000L
private const val COROUTINE_STATE_BYTES = 128
private const val NATIVE_BYTES_PER_THREAD = 5 * 1024 * 1024
private const val COROUTINE_PROGRESS_INTERVAL = 10_000
private const val THREAD_PROGRESS_INTERVAL = 10
private const val MEMORY_PAGE_SIZE = 4096
private const val CRASH_PAUSE_MS = 3_000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoroutinesTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CoroutineComparison(
                        modifier = Modifier.padding(innerPadding),
                        runOnUiThread = { action -> runOnUiThread(action) }
                    )
                }
            }
        }
    }
}

@Composable
fun CoroutineComparison(
    modifier: Modifier = Modifier,
    runOnUiThread: (Runnable) -> Unit
) {
    var coroutineStatus by remember { mutableStateOf("Not started") }
    var threadStatus by remember { mutableStateOf("Not started") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Coroutines vs Threads Demo App",
            style = MaterialTheme.typography.titleLarge
        )

        DemoCard(
            title = "Coroutines (100,000 tasks)",
            status = coroutineStatus,
            buttonText = "Run 100,000 Coroutine Tasks",
            onClick = {
                runCoroutineDemo(
                    scope = scope,
                    updateStatus = { coroutineStatus = it }
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        DemoCard(
            title = "Threads (same work + native memory pressure)",
            status = threadStatus,
            buttonText = "Run Same Work with Threads",
            isError = true,
            onClick = {
                runThreadDemo(
                    runOnUiThread = runOnUiThread,
                    updateStatus = { threadStatus = it }
                )
            }
        )
    }
}

@Composable
private fun DemoCard(
    title: String,
    status: String,
    buttonText: String,
    onClick: () -> Unit,
    isError: Boolean = false
) {
    Card(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(0.9f),
        colors = if (isError) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text("Status: $status")
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onClick,
                colors = if (isError) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonText)
            }
        }
    }
}

private fun runCoroutineDemo(
    scope: CoroutineScope,
    updateStatus: (String) -> Unit
) {
    updateStatus("Launching...")

    scope.launch {
        val startTime = System.currentTimeMillis()

        withContext(Dispatchers.Default) {
            coroutineScope {
                repeat(TASK_COUNT) { index ->
                    launch {
                        runCoroutineTask()
                    }

                    if ((index + 1) % COROUTINE_PROGRESS_INTERVAL == 0) {
                        val launched = index + 1
                        withContext(Dispatchers.Main) {
                            updateStatus("Launched $launched / $TASK_COUNT coroutine tasks...")
                        }
                    }
                }
            }
        }

        val duration = System.currentTimeMillis() - startTime
        updateStatus("Success! 100k coroutine tasks finished in ${duration}ms")
    }
}

private fun runThreadDemo(
    runOnUiThread: (Runnable) -> Unit,
    updateStatus: (String) -> Unit
) {
    updateStatus("Launching...")

    Thread {
        val startTime = System.currentTimeMillis()
        val threads = mutableListOf<Thread>()
        var started = 0
        var peakActiveThreads = Thread.activeCount()

        try {
            repeat(TASK_COUNT) {
                val worker = Thread {
                    runThreadTask()
                }

                threads.add(worker)
                worker.start()
                started++

                val activeNow = Thread.activeCount()
                peakActiveThreads = maxOf(peakActiveThreads, activeNow)

                if (started % THREAD_PROGRESS_INTERVAL == 0) {
                    val message = buildThreadProgressStatus(
                        started = started,
                        activeNow = activeNow,
                        peakActiveThreads = peakActiveThreads
                    )
                    runOnUiThread(Runnable { updateStatus(message) })
                }
            }

            threads.forEach { it.join() }

            val duration = System.currentTimeMillis() - startTime
            val finalActive = Thread.activeCount()
            val message = buildThreadSuccessStatus(
                duration = duration,
                finalActive = finalActive,
                peakActiveThreads = peakActiveThreads
            )
            runOnUiThread(Runnable { updateStatus(message) })
        } catch (t: Throwable) {
            val duration = System.currentTimeMillis() - startTime
            val activeNow = Thread.activeCount()
            peakActiveThreads = maxOf(peakActiveThreads, activeNow)

            val message = buildThreadCrashStatus(
                started = started,
                duration = duration,
                activeNow = activeNow,
                peakActiveThreads = peakActiveThreads,
                throwable = t
            )

            runOnUiThread(Runnable { updateStatus(message) })

            try {
                Thread.sleep(CRASH_PAUSE_MS)
            } catch (_: Exception) {
            }

            throw t
        }
    }.start()
}

private suspend fun runCoroutineTask() {
    doSharedCalculation()
    val coroutineState = allocateCoroutineState()
    delay(WAIT_MS)
}

private fun runThreadTask() {
    doSharedCalculation()
    val directBuffer = allocateDirectThreadMemory()
    Thread.sleep(WAIT_MS)
}

private fun doSharedCalculation() {
    var result = 0L
    repeat(CALCULATION_ITERATIONS) {
        result += it * it
    }
}

private fun allocateCoroutineState(): ByteArray {
    return ByteArray(COROUTINE_STATE_BYTES).apply {
        this[0] = 1
        this[lastIndex] = 1
    }
}

private fun allocateDirectThreadMemory(): ByteBuffer {
    val directBuffer = ByteBuffer.allocateDirect(NATIVE_BYTES_PER_THREAD)

    var i = 0
    while (i < NATIVE_BYTES_PER_THREAD) {
        directBuffer.put(i, 1)
        i += MEMORY_PAGE_SIZE
    }
    directBuffer.put(NATIVE_BYTES_PER_THREAD - 1, 1)

    return directBuffer
}

private fun buildThreadProgressStatus(
    started: Int,
    activeNow: Int,
    peakActiveThreads: Int
): String {
    return "Started $started threads | Active now: $activeNow | Peak observed: $peakActiveThreads"
}

private fun buildThreadSuccessStatus(
    duration: Long,
    finalActive: Int,
    peakActiveThreads: Int
): String {
    return "Success! 100k thread tasks finished in ${duration}ms | Active now: $finalActive | Peak observed: $peakActiveThreads"
}

private fun buildThreadCrashStatus(
    started: Int,
    duration: Long,
    activeNow: Int,
    peakActiveThreads: Int,
    throwable: Throwable
): String {
    return "About to crash... Started: $started threads | Active now: $activeNow | Peak observed: $peakActiveThreads | ${throwable::class.java.simpleName}: ${throwable.message ?: "no message"}"
}