# Nachos Operating Systems Project Implementation Guide

## Project Overview

This guide provides instructions for implementing Project 1 for the CS3053 Operating Systems course using the Nachos educational operating system framework. Nachos is a teaching operating system that helps students understand OS concepts by implementing key components.

## Development Environment Setup

1. Install Java Development Kit (JDK)
2. Set up Visual Studio Code or your preferred IDE
3. Download and unzip the Nachos Java version
4. Configure your project structure by placing the "nachos" folder into your src directory
5. Modify Machine.java line 55 to avoid deprecated class warnings (comment it out)
6. Set up the launch configuration with proper working directory:
   - For Windows: `"cwd": "${workspaceFolder}\\src\\nachos\\proj1"`
   - For MacOS: `"cwd": "${workspaceFolder}/src/nachos/proj1"`

## Important Project Rules

1. Do NOT use the synchronized keyword anywhere in your code
2. Do NOT use Java threads directly (java.lang.Thread)
3. Do not modify any classes in nachos.machine, nachos.ag, or nachos.security
4. Only modify files in the nachos.threads package
5. Properly synchronize your code to work with any scheduler order
6. No busy waiting in any solution
7. The autograder will not call ThreadedKernel.selfTest() or ThreadedKernel.run()

## Critical Nachos Methods

### Thread Management

```java
// Get current thread reference
KThread currentThread = KThread.currentThread();

// Put a thread to sleep
boolean intStatus = Machine.interrupt().disable();
KThread.sleep();
Machine.interrupt().restore(intStatus);

// Wake up a thread
boolean intStatus = Machine.interrupt().disable();
myThread.ready();
Machine.interrupt().restore(intStatus);
```

## Project 1 Tasks

### Task 1: Implement KThread.join()

This method allows one thread to wait for another thread to complete execution.

**Implementation Requirements:**
- A KThread X calling join() on KThread Y must wait for Y to finish before continuing
- Join does not have to be called, but if called, must be called only once
- The result of calling join() a second time is undefined
- A thread must finish executing normally whether or not it is joined

**Implementation Steps:**

1. Check if the current thread is trying to join itself (prevent self-join)
2. Inside the join() method, save a reference to the current thread (the joiner)
3. Put the current thread to sleep if the target thread hasn't finished yet
4. In the finish() method, check if any thread has called join() on this thread
5. If yes, wake up the waiting thread(s)

**Sample Approach:**

```java
public void join() {
    // Don't join on yourself
    if (this == currentThread()) {
        return;
    }
    
    // If thread is already finished, nothing to do
    if (status == statusFinished) {
        return;
    }
    
    // Disable interrupts for atomicity
    boolean intStatus = Machine.interrupt().disable();
    
    // Save reference to current thread in joinQueue
    // (Using ThreadQueue is recommended for priority scheduling later)
    joinQueue.waitForAccess(currentThread());
    
    // Put current thread to sleep
    KThread.sleep();
    
    // Restore interrupts
    Machine.interrupt().restore(intStatus);
}

// In finish() method:
if (joinQueue != null) {
    // Wake up any threads waiting on this one
    KThread thread = joinQueue.nextThread();
    if (thread != null) {
        thread.ready();
    }
}
```

### Task 2: Implement Alarm.waitUntil(int x)

This method suspends a thread's execution until a specified time has elapsed.

**Implementation Requirements:**
- A thread calls waitUntil to suspend execution until time has advanced to at least now + x
- No requirement that threads start running immediately after waking up
- Do not fork additional threads
- Multiple threads may call waitUntil and be suspended at any time

**Implementation Steps:**

1. Create a data structure to store:
   - A reference to the thread
   - Its wake-up time (current time + x)
2. In waitUntil():
   - Calculate wake-up time
   - Store thread and wake-up time
   - Put the thread to sleep
3. In timerInterrupt() method:
   - Check current time against wake-up times
   - Wake up any threads whose wake-up time has passed

**Sample Approach:**

```java
// Add to Alarm class
private List<KThread> waitingThreads = new LinkedList<>();
private List<Long> wakeTimes = new LinkedList<>();

public void waitUntil(long x) {
    // Don't wait for negative time
    if (x <= 0) {
        return;
    }
    
    long wakeTime = Machine.timer().getTime() + x;
    boolean intStatus = Machine.interrupt().disable();
    
    // Save thread and wake time
    waitingThreads.add(KThread.currentThread());
    wakeTimes.add(wakeTime);
    
    // Sleep thread
    KThread.sleep();
    
    Machine.interrupt().restore(intStatus);
}

// Modify timerInterrupt() to check and wake threads
public void timerInterrupt() {
    long currentTime = Machine.timer().getTime();
    boolean intStatus = Machine.interrupt().disable();
    
    // Check all waiting threads
    for (int i = 0; i < waitingThreads.size(); i++) {
        if (wakeTimes.get(i) <= currentTime) {
            // Wake up this thread
            KThread thread = waitingThreads.get(i);
            waitingThreads.remove(i);
            wakeTimes.remove(i);
            thread.ready();
            i--; // Adjust index after removal
        }
    }
    
    Machine.interrupt().restore(intStatus);
}
```

### Task 3: Implement Communicator Class

Implement synchronous message passing using condition variables.

**Implementation Requirements:**
- Implement speak(int word) and listen() methods
- speak() waits until listen() is called on the same Communicator, then transfers word
- listen() waits until speak() is called, then receives and returns the word
- Must work with multiple speakers and listeners
- Use exactly one lock per Communicator
- Zero-length bounded buffer model (producer and consumer must interact directly)

**Implementation Steps:**

1. Create a Communicator class with needed fields
2. Implement synchronization using a lock and condition variables
3. Manage the speaker/listener interactions properly

**Sample Approach:**

```java
public class Communicator {
    private Lock lock;
    private Condition speakerCondition;
    private Condition listenerCondition;
    
    private int word;
    private boolean wordAvailable = false;
    private int nSpeakers = 0;
    private int nListeners = 0;
    
    public Communicator() {
        lock = new Lock();
        speakerCondition = new Condition(lock);
        listenerCondition = new Condition(lock);
    }
    
    public void speak(int word) {
        lock.acquire();
        
        // Wait for listener and no pending word
        nSpeakers++;
        while (wordAvailable || nListeners == 0) {
            speakerCondition.sleep();
        }
        
        // Transfer the word
        this.word = word;
        wordAvailable = true;
        nSpeakers--;
        
        // Wake a listener
        listenerCondition.wake();
        
        lock.release();
    }
    
    public int listen() {
        lock.acquire();
        
        // Wait for speaker with a word
        nListeners++;
        while (!wordAvailable) {
            listenerCondition.sleep();
        }
        
        // Receive word
        int receivedWord = word;
        wordAvailable = false;
        nListeners--;
        
        // Wake a speaker
        speakerCondition.wake();
        
        lock.release();
        return receivedWord;
    }
}
```

### Task 4: Implement Condition2 Class

Implement condition variables directly using interrupt enable/disable.

**Implementation Requirements:**
- Implement without using semaphores directly
- Use interrupt enable and disable for atomicity
- Provide same functionality as the existing Condition class

**Implementation Steps:**

1. Create a list to track waiting threads
2. Implement sleep(), wake(), and wakeAll() using interrupt control
3. Follow the pattern from the Lock class for safe thread management

**Sample Approach:**

```java
public class Condition2 {
    private Lock conditionLock;
    private LinkedList<KThread> waitQueue = new LinkedList<>();
    
    public Condition2(Lock conditionLock) {
        this.conditionLock = conditionLock;
    }

    public void sleep() {
        // Must hold the lock to sleep
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());

        boolean intStatus = Machine.interrupt().disable();
        
        // Release lock before sleeping
        conditionLock.release();
        
        // Add to wait queue and sleep
        waitQueue.add(KThread.currentThread());
        KThread.sleep();
        
        // Restore interrupts
        Machine.interrupt().restore(intStatus);
        
        // Reacquire lock after waking
        conditionLock.acquire();
    }

    public void wake() {
        // Must hold lock to wake threads
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        
        boolean intStatus = Machine.interrupt().disable();
        
        // Wake one thread if available
        if (!waitQueue.isEmpty()) {
            KThread thread = waitQueue.removeFirst();
            thread.ready();
        }
        
        Machine.interrupt().restore(intStatus);
    }

    public void wakeAll() {
        // Must hold lock to wake threads
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        
        boolean intStatus = Machine.interrupt().disable();
        
        // Wake all waiting threads
        while (!waitQueue.isEmpty()) {
            KThread thread = waitQueue.removeFirst();
            thread.ready();
        }
        
        Machine.interrupt().restore(intStatus);
    }
}
```

## Testing Your Implementation

1. Run Nachos from the proj1 directory for simple tests
2. Test with different interleaving by using `nachos -s <number>`
3. Ensure your code works correctly regardless of scheduling order
4. Understand thread states and context switching

## Understanding Context Switching

When tracing execution paths, pay attention to TCB.contextSwitch():
- When one thread calls TCB.contextSwitch(), that thread stops executing
- Another thread starts running and returns from TCB.contextSwitch()
- The TCB.contextSwitch() that gets called is different from the one that returns

## Moving Forward

After completing Project 1, you'll use these components in Project 2, which involves:

1. A boat synchronization problem with multiple threads
2. Priority scheduling with priority donation

Ensure your Project 1 solutions are solid as they form the foundation for future projects.
