# Nachos Project 3: Multiprogramming & Virtual Memory Implementation Guide

## Overview

This project focuses on implementing multiprogramming support for Nachos, allowing multiple user processes to run concurrently. You will implement system calls, memory management, and process control mechanisms.

The main tasks include:
1. Implementing file system calls
2. Adding support for multiprogramming
3. Building process management system calls

## Environment Setup

- Run the project in the `proj2` directory
- Compile test programs with `make test`
- Run the basic Nachos system with `nachos -d ma`
- Test specific programs with `nachos -x PROGNAME.coff`
- Use Docker container `papama/cs3053:p3` for autograding

## Key Implementation Requirements

### 1. Memory Management

- **Total Memory**: 64 physical pages of 1024 bytes each (64KB total)
- **Implementation in `UserKernel.java`**:
  - Define and maintain a list of free physical pages (LinkedList<Integer>)
  - Use synchronization when accessing shared resources
  - Valid physical page numbers (ppn) range from 0-63
  - Allocate pages efficiently (use gaps in memory, not just contiguous blocks)

### 2. File System Calls

- **Implementation in `UserProcess.java`**:
  - Create a per-process open file table (array of OpenFile, maximum 16 entries)
  - Reserve first two entries for standard input/output:
    - Entry 0: `UserKernel.console.openForReading()` (stdin)
    - Entry 1: `UserKernel.console.openForWriting()` (stdout)
  - Implement system calls by completing each handler method:
    - `handleCreate(int namePtr)` - Create a file
    - `handleOpen(int namePtr)` - Open a file
    - `handleRead(int fileDescriptor, int bufferPtr, int count)` - Read from a file
    - `handleWrite(int fileDescriptor, int bufferPtr, int count)` - Write to a file
    - `handleClose(int fileDescriptor)` - Close a file
    - `handleUnlink(int namePtr)` - Remove a file
  - The file descriptor returned by open/create is an index into the process file table
  - Handle all error conditions gracefully, returning -1 on error

### 3. Virtual Memory Management

- **Modify `readVirtualMemory()` and `writeVirtualMemory()`**:
  - Update to translate virtual addresses to physical addresses
  - Use the process page table for address translation
  - Handle transfers that cross page boundaries
  
- **Update `loadSections()`**:
  - Acquire free physical pages from the global free page list
  - Load program sections into appropriate physical pages
  - Set up the process page table with correct mappings
  - Mark read-only pages appropriately using `CoffSection.isReadOnly()`
  - Allocate 8 pages for stack plus 1 for arguments (9 pages total)
  - Return error if not enough physical memory is available

- **Implement `unloadSections()`**:
  - Free all physical pages used by the process
  - Return pages to the free page list

### 4. Implement Process Control System Calls

- **Process Management**:
  - Assign a unique process ID to each process (static counter)
  - Track parent-child relationships between processes
  
- **Implement Remaining System Calls**:
  - `handleExec(int fileNamePtr, int argc, int argvPtr)` - Start a new process
  - `handleJoin(int processID, int statusPtr)` - Wait for a child process
  - `handleExit(int status)` - Terminate the current process
  - `handleHalt()` - Shut down the system (only allowed for root process)

- **Process Exit Behavior**:
  - Close all open files
  - Free all memory resources
  - Transfer exit status to parent process (for join)
  - If this is the last process, call `Machine.halt()` directly

### 5. Modifications to SynchConsole.java

- Remove `writelock.acquire()` and `writelock.release()` from the `writeByte()` method
- Add them to protect the for-loop in the `write()` method to prevent fragmented console output

## Implementation Sequence

For a logical development approach, implement the system in the following stages:

1. **Stage 1**: Implement file system calls
   - Create, Open, Read, Write handlers
   - Set up the process file table

2. **Stage 2**: Implement virtual memory management
   - Update memory translation functions
   - Modify loadSections() for page allocation

3. **Stage 3**: Implement Close and Unlink system calls

4. **Stage 4**: Implement process management system calls
   - Exec, Join, Exit, Halt handlers
   - Process tracking and cleanup

## Testing Your Implementation

The autograder will test your implementation in stages:

1. **File system calls**: Create, Open, Read, Write graders
2. **Paging system**: Read Grader 3, Write Grader 3
3. **Additional file system**: Read Grader 2, Write Grader 2, Close Grader 1/2, Unlink 1
4. **Process management**: Halt, Exec, Join, Exit graders

Run final tests with both `gradep3` and `haltgrader`.

## Debugging Tips

- Print temporary information using `System.out.println()`
- Add debug statements in `handleSyscall()` to track system call numbers and arguments
- Build skeleton code for all handlers first, returning -1 for unimplemented features
- For memory debugging, test with Read Grader 3 and Write Grader 3

## Requirements Checklist

- [ ] `UserKernel.java`: Global free page list and management
- [ ] `SynchConsole.java`: Modified synchronization in write methods
- [ ] `UserProcess.java`: Open file table (16 entries)
- [ ] `UserProcess.java`: File system calls (6 handlers)
- [ ] `UserProcess.java`: Modified virtual memory access methods
- [ ] `UserProcess.java`: Updated section loading with page allocation
- [ ] `UserProcess.java`: Process management system calls (3 handlers)
- [ ] `UserProcess.java`: Process cleanup on exit

## Common Pitfalls to Avoid

- Not protecting shared resources with synchronization
- Failing to handle cross-page memory transfers
- Not properly initializing file descriptors 0 and 1
- Improper handling of errors in system calls
- Memory leaks when processes exit
- Not updating page tables correctly during address translation
- Failing to bullet-proof against invalid user inputs

Remember that proper error handling is crucial - user programs should never be able to crash the kernel.
