# Project 2: Thread Scheduler and Synchronization

## Introduction
This project continues your work in the Nachos threads directory, building on the synchronization primitives you implemented in Project 1. You'll tackle two significant tasks:

1. Implementing a solution to the "Hawaiian Boat Problem" using Nachos synchronization tools
2. Implementing priority scheduling with priority donation in Nachos

## Prerequisites
- You should have completed Project 1, including functioning implementations of:
  - KThread.join()
  - Alarm.waitUntil()
  - Communicator class with speak() and listen()
  - Condition2 class implementation
- Your Project 2.1 solution will rely on these synchronization tools being properly implemented
- Your Project 2.2 priority scheduler will be tested using your KThread.join() implementation

## Task 1: The Hawaiian Boat Problem (boat.java)
### Problem Statement
A number of Hawaiian adults and children are trying to get from Oahu to Molokai with the following constraints:
- They have only one boat
- The boat can carry a maximum of two children OR one adult (never one child and one adult)
- The boat requires a pilot to row it back to Oahu
- There are at least two children present

### Requirements
1. Implement the `Boat.begin()` method to fork a thread for each child and adult
2. Your solution must not know how many children or adults are present beforehand
   - You cannot pass these values to threads
   - Threads may determine this information through shared variables
3. Use appropriate BoatGrader methods to track crossings:
   - When a child pilots from Oahu to Molokai: call `ChildRowToMolokai()`
   - When a child rides as passenger from Oahu to Molokai: call `ChildRideToMolokai()`
   - Similar methods exist for other crossings
4. When a boat with two people crosses, the pilot calls the `...RowTo...` method before the passenger calls the `...RideTo...` method
5. Your solution must have no busy waiting
6. Your solution must eventually end (though threads may remain blocked)

### Design Constraints
Simulate the problem as if each thread were an actual person:
- People can see how many children/adults are on their current island
- People can see if the boat is at their island
- People know which island they are on
- This information can be stored with individual threads or shared variables

### Not Allowed
- A "top-down" controller thread sending commands to people
- Information that would be impossible in the real world (e.g., a child on Molokai cannot see people on Oahu)
- The one exception: you may use the total number of people solely to determine when to terminate

### Recommended Approach
- Condition variables will be most useful for this task
- Think about the problem from the perspective of individuals making decisions
- Consider what information would be available to a person in this situation

## Task 2: Priority Scheduler (PriorityScheduler.java)
### Overview
Implement priority scheduling in Nachos with priority donation to address priority inversion.

### Requirements
1. Priorities range from 0 to 7 (integers), with 7 being the highest priority
2. Complete the following methods:
   - In inner class PriorityQueue:
     - `nextThread()`
     - `pickNextThread()`
   - In inner class ThreadState:
     - `getEffectivePriority()`
     - `setPriority()`
     - `waitForAccess()`
     - `acquire()`
3. Always choose a thread with the highest effective priority from a PriorityQueue

### Priority Donation
- When a high-priority thread waits for a resource held by a low-priority thread, it must donate its priority
- This prevents priority inversion, where a high-priority thread waits indefinitely
- Implementation detail: `getEffectivePriority()` should return the thread's priority after considering all donations

### Testing Environment
- Task 1 uses RoundRobinScheduler
- Task 2 uses PriorityScheduler
- The Docker auto-grader for Project 2 sets these schedulers for you

## Submission Requirements
1. Submit your complete Nachos threads directory
2. Include a project report (minimum one page) that:
   - Describes your approach to solving each task
   - Explains how you overcame debugging difficulties
   - Is well-written and organized

## Important Notes
- Continue using the threads folder submitted for Project 1
- When writing your own test code, remember to set the scheduler properly in nachos.conf
- For auto-grading with Docker:
  - Download the Docker image: papama/cs3053:p2
  - Map your local threads folder to /root/nachos/threads

## Evaluation Criteria
- Functional correctness
- No busy waiting
- Appropriate use of synchronization primitives
- Proper implementation of priority donation
- Quality of project report
