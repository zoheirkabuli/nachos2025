# Nachos Operating System Project - Implementation Guide

## Overview

This guide provides detailed instructions for implementing core components of the Nachos operating system, focusing on system calls, memory management, and multiprogramming support. The project will expand Nachos' capabilities to handle multiple user processes and implement standard system calls for file operations and process management.

## Project Structure

You will be implementing functionality in the `nachos.userprog` package, particularly focusing on:

1. `UserKernel.java` - Manages system resources including physical memory
2. `SynchConsole.java` - Provides synchronized console access
3. `UserProcess.java` - Manages user processes, virtual memory, and system calls

## System Requirements

1. Support at least 16 concurrently open files per process
2. Support multiple user processes with proper memory management
3. Implement all required system calls with appropriate error handling
4. Ensure system stability against malicious or buggy user programs

## Implementation Tasks

### 1. Memory Management

#### Physical Memory Management in UserKernel.java

- Create a list of free physical pages (LinkedList of Integer)
- Total pages: 64 (available through `Machine.processor().getNumPhysPages()`)
- Page size: 1024 bytes (set by `Processor.pageSize = 0x400`)
- Total memory: 64KB
- Implement thread-safe allocation and deallocation of physical pages
- Allow efficient use of "gaps" in the memory pool

```java
// Example structure in UserKernel
private static LinkedList<Integer> freePages;

// Initialize in initialize()
freePages = new LinkedList<Integer>();
for (int i = 0; i < Machine.processor().getNumPhysPages(); i++)
    freePages.add(i);
```

### 2. File System Calls in UserProcess.java

Implement the following system calls:

#### Common Requirements for All System Calls
- Validate all user-provided memory addresses and strings
- Return -1 for errors, not exceptions
- Maximum string length: 256 bytes
- Support file descriptors 0-15 (0=stdin, 1=stdout)
- Reuse file descriptors when possible

#### a. Create (int filenamePtr) → int
- Read filename from user memory at `filenamePtr`
- Create file with `ThreadedKernel.fileSystem.open(filename, true)`
- Return file descriptor or -1 on error

#### b. Open (int filenamePtr) → int
- Read filename from user memory
- Open file with `ThreadedKernel.fileSystem.open(filename, false)`
- Return file descriptor or -1 on error

#### c. Read (int fd, int buffer, int count) → int
- Check fd validity (0-15 and in use)
- Check buffer memory validity
- Read up to `count` bytes from file to kernel buffer
- Transfer data to user process memory using `writeVirtualMemory`
- Return number of bytes read or -1 on error

#### d. Write (int fd, int buffer, int count) → int
- Check fd validity and buffer validity
- Read user process memory into kernel buffer using `readVirtualMemory`
- Write buffer to file
- Return number of bytes written or -1 on error

#### e. Close (int fd) → int
- Check fd validity
- Close the file
- Mark file descriptor as available for reuse
- Return 0 on success, -1 on failure

#### f. Unlink (int filenamePtr) → int
- Read filename from user memory
- Remove file with `ThreadedKernel.fileSystem.remove(filename)`
- Return 0 on success, -1 on failure

### 3. Memory Translation and Process Management

#### a. Virtual Memory Management
- Modify `readVirtualMemory` and `writeVirtualMemory` to support proper address translation
- Each page is represented by a `TranslationEntry` containing:
  - vpn (virtual page number)
  - ppn (physical page number)
  - valid, readOnly, used, dirty flags
- Translate addresses using `pageTable[vpn].ppn`
- Handle transfers that cross page boundaries

```java
// Process for translation (simplified example)
int vpn = vaddr / pageSize;
int offset = vaddr % pageSize;
int ppn = pageTable[vpn].ppn;
int paddr = ppn * pageSize + offset;
// Use paddr with System.arraycopy()
```

#### b. Process Loading (loadSections)
- Determine number of pages needed for program
- Allocate physical pages from the free list
- Update the process page table to map virtual to physical pages
- Set readOnly flag for code sections (check with `coffSection.isReadOnly()`)
- Allocate 8 pages for the stack plus 1 page for arguments
- Return false if not enough memory available

#### c. Process Cleanup (unloadSections)
- Free all physical memory pages used by the process
- Return pages to the UserKernel free page list

### 4. Process System Calls

#### a. Exec (int filePtr, int argc, int argvPtr) → int
- Read filename and arguments from user memory
- Create a new UserProcess
- Load the program
- Return the new process ID or -1 on failure

#### b. Join (int processID, int statusPtr) → int
- Check if process is a child of the current process
- Wait for child to exit
- Write exit status to user memory at statusPtr
- Return 1 on success, 0 if process already exited, -1 on error

#### c. Exit (int status)
- Clean up process resources (memory, files)
- Transfer exit status to parent if parent is waiting
- If last process, call `Machine.halt()`
- Only the root process may call system call halt()
- The last exiting process should call Machine.halt() directly

### 5. SynchConsole Modification

- Remove `writelock.acquire()` and `writelock.release()` from `writeByte()`
- Add them around the for-loop in the `write()` method
- This prevents messages from different processes being interleaved

## Debugging Tips

1. Add print statements to `handleSyscall()` to trace system call execution
2. Implement system calls incrementally, testing each one
3. Start with file system calls, then memory management, then process calls
4. Use the included test programs to verify your implementation
5. Return -1 for unimplemented system calls as a starting point

## Testing Sequence

1. File system calls (CreateGrader1, OpenGrader1, ReadGrader1, WriteGrader1)
2. Paging system (ReadGrader3, WriteGrader3)
3. Additional file operations (ReadGrader2, WriteGrader2, CloseGrader1, CloseGrader2, Unlink1)
4. Process operations (HaltGrader1, ExecGrader1, ExecGrader2, JoinGrader1, JoinGrader2, JoinGrader3, ExitGrader1, ExitGrader2)

## Submission Requirements

Submit:
1. A zip file containing all the `.java` files in your `nachos/userprog` directory
2. A report describing your approach and implementation for each task

Good luck with your implementation!