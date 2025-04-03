# Project 2: Thread Scheduler and Synchronization Report

## Introduction

This report details the implementation of two significant components in the Nachos operating system:

1. The Hawaiian Boat Problem (synchronization)
2. Priority Scheduling with Priority Donation (thread scheduling)

Both components required careful design to handle concurrency correctly and avoid common pitfalls like deadlocks, starvation, and priority inversion.

## Task 1: Hawaiian Boat Problem

### Implementation Approach

The Hawaiian Boat Problem presented an interesting synchronization challenge with strict rules:

- Only one boat capable of carrying either two children or one adult
- Boat requires a pilot to row it between islands
- No prior knowledge of how many adults/children are present
- Need to ensure everyone eventually gets from Oahu to Molokai

My implementation used a combination of locks and condition variables to manage synchronization:

1. **State Management**: I maintained shared state variables to track:

   - Number of children and adults on each island
   - Location of the boat
   - Completion status of the overall task

2. **Synchronization Design**: Used a central lock with two condition variables:

   - `childCV`: For coordinating child threads
   - `adultCV`: For coordinating adult threads

3. **Thread-Based Simulation**: Created a separate thread for each child and adult, with each thread making local decisions based on available information, similar to how real individuals would behave.

The core algorithm focused on these key strategies:

- Always prioritize sending two children when possible (most efficient use of boat capacity)
- Have one child return with the boat when needed (to ferry more people)
- Allow adults to cross only when strategic (when few children remain on Oahu)
- Use condition variables to avoid busy waiting when threads can't take useful action

### Debugging Challenges

During the development of this solution, I encountered several challenges:

1. **Livelock Detection**: Early versions of the solution often resulted in children endlessly shuttling back and forth between islands without making progress. I resolved this by:

   - Adding a `totalPeople` counter to accurately track completion
   - Improving termination conditions to detect when everyone had reached Molokai
   - Adding more precise wake-up conditions to ensure the right threads are awakened

2. **Edge Case Handling**: Special attention was needed for end-state scenarios:

   - When only one child remains on Oahu
   - When all adults have crossed but children remain on both islands
   - When nearly everyone is on Molokai

3. **Deadlock Prevention**: Initial implementations sometimes deadlocked when:
   - The boat was at Molokai with no children to bring it back
   - All threads were sleeping waiting for others to take action

I resolved these issues by carefully designing the wakeup logic and ensuring that whenever a thread changes state, it wakes other threads that might be able to take action as a result.

## Task 2: Priority Scheduler

### Implementation Approach

The Priority Scheduler implementation required developing a scheduling policy that:

- Supports priority levels from 0 to 7
- Always selects the highest-priority ready thread
- Implements priority donation to solve priority inversion

My approach focused on these key components:

1. **PriorityQueue Class**:

   - Implemented `nextThread()` to select and remove the highest effective priority thread
   - Designed `pickNextThread()` to find the highest priority thread without removing it

2. **ThreadState Class**:

   - Created a comprehensive `getEffectivePriority()` method that accounts for priority donations
   - Implemented caching of effective priorities for performance
   - Designed `waitForAccess()` and `acquire()` to manage resource access and donations

3. **Priority Donation Algorithm**:

   - Used a HashSet to track visited threads and prevent cycles in donation chains
   - Implemented donation through multiple levels of resource dependencies
   - Added a `getDonatedPriority()` helper method to calculate recursive donations

4. **Thread Join Mechanism**:
   - Modified the KThread.join() method to use a priority-donating queue
   - Ensured the joined thread acquires the joinQueue to establish donation relationship

### Debugging Challenges

Priority donation was particularly challenging to implement correctly:

1. **Recursion and Cycles**: Priority donation involves recursive dependencies that required careful handling:

   - Initial attempts led to infinite recursion or stack overflows
   - Solved by tracking visited nodes and avoiding reprocessing them

2. **Cache Invalidation**: Effective priorities needed to be recalculated when priorities changed:

   - Created an invalidation system that propagates through donation chains
   - Used a caching mechanism to avoid expensive recalculations

3. **Priority Donation for Joins**: The most difficult issue was with priority donation through thread joins:

   - Initially, the prioritygrader4 test failed because joining threads weren't donating priority
   - The solution involved enabling transferPriority for joinQueues
   - A critical fix was ensuring the joined thread _acquires_ the joinQueue

4. **Same-Priority Ordering**: Threads with equal priority needed to be ordered by wait time:

   - Added timestamp tracking for thread waiting times
   - Ensured FIFO behavior among threads of equal priority

5. **Resource Holder Management**: Ensuring proper resource holder tracking:
   - Fixed issues where resource holders weren't properly updated
   - Ensured proper cleanup when a thread is done with a resource

## Conclusion

Both tasks required careful design and debugging to ensure correct behavior in a concurrent environment. The Hawaiian Boat Problem solution demonstrated effective use of synchronization primitives to coordinate autonomous agents, while the Priority Scheduler implementation successfully addressed priority inversion through donation.

The PriorityScheduler implementation was particularly challenging due to the complex relationships between waiting threads and resource holders, requiring careful tracking of dependencies and priority inheritance chains. Ensuring that priority donation worked correctly through joins required detailed understanding of Nachos' threading model.

These implementations successfully solved the assigned problems while maintaining the core principles of proper synchronization and thread scheduling. The solutions avoid common pitfalls like busy waiting, deadlocks, and priority inversion, demonstrating good operating systems design principles.
