# Coroutines vs Threads Demo

This Android demo compares how coroutines and threads behave when asked to perform the same logical task at very large scale.

## What each task does

Each task performs two steps:

1. Computes the sum of squares from 0 to 999 (a small, CPU-bound calculation)
2. Waits for 3 seconds

Both implementations perform **identical logical work**.

---

## Key idea

The logical work is the same: compute the sum of squares from 0 to 999, then wait 3 seconds, but the runtime cost of being in that waiting state is fundamentally different.

A suspended coroutine only needs a small amount of state.  
A blocked thread needs an entire OS thread, which is much heavier.

---

## What this demo shows

- Coroutines can scale to very large numbers of concurrent tasks
- Threads are significantly more expensive because each waiting task occupies a real OS thread
- "Same logical task" does not mean "same runtime representation"

---

## Demo behavior

### Coroutine path

Each coroutine:
- computes the sum of squares from 0 to 999
- retains a small amount of state
- suspends for 3 seconds using `delay(...)`
- resumes and completes

This allows **100,000 coroutine tasks** to complete successfully.

---

### Thread path

Each thread:
- performs the same calculation (sum of squares from 0 to 999)
- allocates additional native memory to amplify thread cost for demonstration purposes
- blocks for 3 seconds using `Thread.sleep(...)`

Because each thread is heavyweight and remains active while sleeping, the app typically crashes after only a few hundred threads, depending on device limits.

---

## Notes

- The exact crash count for threads depends on the device, emulator, OS, and memory limits.
- The thread demo allocates native memory because the runtime cost of being "waiting" is fundamentally different. A suspended coroutine only needs a small amount of state. A blocked thread needs an entire OS thread, which is much heavier. Even without additional memory pressure, the thread-based version will still fail after only a few thousand threads due to OS-level limits on thread resources.
- This is a teaching/demo app, not a production benchmark.

---

## Tech stack

- Kotlin
- Jetpack Compose
- Kotlin Coroutines
- Android Studio

---

## Running the app

1. Open the project in Android Studio
2. Run the app on an emulator or Android device
3. Tap **"Run 100,000 Coroutine Tasks"**
4. Tap **"Run Same Work with Threads"**

---

## Why this matters

In real Android apps, many operations are primarily waiting:

- network requests
- database queries
- file I/O
- retries and timers

Coroutines scale well for these cases because waiting does not require blocking a thread.

Threads, on the other hand, must remain active while waiting, which limits how many tasks can run concurrently.

---

## Takeaway

**Same task, different runtime representation.**

## Accompanying Slides

This demo is part of a presentation that walks through coroutines, threads, and structured concurrency in more detail.

You can view the slides here:

👉 [View the slide deck](https://docs.google.com/presentation/d/1M_U-poWm2T5L7opZDoRbTptg43EPakeNm7eWmb0HzYA/edit?usp=sharing)
