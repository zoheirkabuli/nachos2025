# Nachos Operating System Project 3: File System Interface, Multiprogramming Support, and Memory Management

## Overview

The goal of this project is to provide Nachos with support for multiprogramming at the user level. You will implement system calls, create a virtual memory system, and enable multiple processes to run concurrently. This project builds on previous work and allows user-level programs to access OS kernel routines via system calls.

You will test your finished code on Docker container "papama/cs3053:p3". For final submission, upload a zipped userprog folder and a two-page PDF report to Harvey. The report must be formatted as "Times New Roman, font size 11, before and after spacing 0."

## Project Components

### 1. Files to Modify

The following files in the `userprog` folder should be modified or completed:

#### (1) UserKernel.java (for all testing stages)

This class defines the Nachos kernel that manages the resources of the simulated MIPS machine. You need to:

- Maintain a list of free physical memory pages
- The simulated MIPS has 64 free pages (preset by "nachos.conf")
- You can obtain this value from `Machine.processor().getNumPhysPages()`
- Implement a list of free pages (a LinkedList of Integer type is recommended)
- Each element in the list represents a physical page number (ppn), valid range: 0-63

#### (2) SynchConsole.java (for all testing stages)

- Remove `writelock.acquire()` and `writelock.release()` from the `writeByte()` method
- Add them to protect the for-loop in the `write()` method
- This change ensures that messages sent by different processes to the console will not be broken

#### (3) UserProcess.java

More than 95% of the programming should be done in this file. Modify or complete the following methods:

##### A. UserProcess fields and constructor

- Define an open-file table as a private 1D array of the type OpenFile
- Create an associated status array indicating if each spot in the openfile table is being used
- Support at most 16 open files per process
- Reserve the first two spots for system input (keyboard) and system output (console/screen):
  - For system input: `UserKernel.console.openForReading()` (first spot)
  - For system output: `UserKernel.console.openForWriting()` (second spot)
- The openfile table has 14 remaining spots for ordinary files
- File descriptors returned by `handleCreate()` and `handleOpen()` are indices to this table (range: 0-15)

##### B. handleSyscall() method (for all testing stages)

Implement the following syscall handlers (in recommended order):

1. `handleCreate()` (testing stage 1)
2. `handleOpen()` (testing stage 1)
3. `handleRead()` (testing stages 1, 3, 4)
4. `handleWrite()` (testing stages 1, 3, 4)
5. `handleClose()` (testing stage 3)
6. `handleUnlink()` (testing stage 3)
7. `handleExec()` (testing stage 4)
8. `handleJoin()` (testing stage 4)
9. `handleExit()` (testing stage 4)
10. `handleHalt()` (provided but incomplete, for testing stage 4)

For each handle method, refer to syscall.h in the test folder for arguments, functionality, and return definitions.

##### C. Method execute() (for testing stage 4)

May need slight changes when implementing `handleExec()` and `handleExit()` (tested by execgraders and exitgraders).

##### D. readVirtualMemory() and writeVirtualMemory() (for testing stages 2, 3, 4)

- Modify the second version of each method to support an actual paging system
- The starter code assumes Nachos only supports a single process using all 64 physical pages
- The current implementation doesn't distinguish between physical and virtual addresses
- `readVirtualMemory()` allows the kernel to read bytes from user's virtual address space into a kernel buffer
- Use the process pageTable to translate virtual addresses to physical addresses
- Update the `System.arraycopy()` calls in both methods
- Ensure these methods handle data transfers across multiple virtual pages
- Virtual addresses appear contiguous to users, but may map to different physical pages

##### E. loadSections() (for testing stages 2, 4)

- Modify to allocate free physical pages using the list defined in UserKernel.java
- The loading process involves:
  1. Checking if enough free pages are available for the requested numPages
  2. Loading program sections into free pages
  3. Updating the process pageTable entries for each loaded page
  4. Assigning 9 more pages (8 for stack, 1 for arguments)
  5. Setting read-only flags for appropriate pages
- Remove or comment out the for-loop in the constructor that assigns all 64 pages to a single process

##### F. unloadSections() (for testing stage 4)

- Called by `handleExit()`
- Free all physical pages assigned to the process
- Add the freed pages back to the free page list

## Testing Strategy

Follow these stages to test your implementation:

### Stage 1: File System Calls

Implement file system calls first. You don't need paging for these tests:

- **Create Grader 1**: Creating and truncating files, handling invalid arguments
- **Open Grader 1**: Opening existing files without truncating
- **Read Grader 1**: Reading from a file and printing to screen
- **Write Grader 1**: Writing messages to files

### Stage 2: Paging System

Test your paging implementation with:

- **Read Grader 3**: Reads binary contents of an array (where a[i]=i) and prints to screen
  - Tests `writeVirtualMemory()` with data larger than a page
- **Write Grader 3**: Writes a large array to a file
  - Tests `readVirtualMemory()` with data larger than a page

### Stage 3: Advanced File System Tests

Once file system calls and paging are working, test:

- **Read Grader 2**: Tests response to invalid arguments
- **Write Grader 2**: Tests response to invalid arguments
- **Close Grader 1**: Tests basic file closing
- **Close Grader 2**: Tests edge cases
- **Unlink 1**: Tests file deletion

These tests assume your file table supports exactly 16 files (2 for stdin/stdout, 14 for regular files).

### Stage 4: Process-Related System Calls

The final tests check process-related system calls:

- **Halt Grader 1**: Tests halt functionality
- **Exec Grader 1 & 2**: Tests process execution
- **Join Grader 1, 2 & 3**: Tests parent-child process joining
- **Exit Grader 1 & 2**: Tests process termination

For final testing, run both `gradep3` (combined grader) and `haltgrader`.

## Implementation Details

### File System Calls

- **Create and Open**: Return file descriptors (indices to the open file table)
- **Read and Write**: Transfer data between user and kernel space
- **Close**: Release file descriptors for reuse
- **Unlink**: Delete files

Remember:
- All memory addresses passed as arguments are virtual addresses
- Use `readVirtualMemory()` and `writeVirtualMemory()` for memory transfers
- String arguments are null-terminated with maximum length of 256 bytes
- Return -1 for errors, not exceptions
- File descriptors 0 and 1 must refer to standard input and output
- Support at least 16 concurrently open files
- Each process should have its own file descriptor table

### Multiprogramming Support

- Allocate physical memory efficiently so processes don't overlap
- User programs don't use dynamic memory (malloc/free), so memory needs are known at creation
- Allocate 8 pages for the process stack
- Maintain a global linked list of free physical pages in UserKernel
- Use synchronization when accessing this list
- Make efficient use of memory by allocating non-contiguous pages
- Free all process memory on exit (normal or abnormal)

### Memory Management

- Implement virtual-to-physical address translation using the pageTable
- Handle cross-page data transfers
- Set read-only flags for appropriate pages
- Free pages when processes terminate

### Process Management

- Implement exec, join, and exit system calls
- Use a globally unique positive integer process ID
- Only allow a parent process to join its child
- Terminate threads immediately on exit
- Transfer exit status to parent
- Only the root process can invoke halt()
- The last exiting process should call Machine.halt() directly

## Debugging Tips

- Print temporary information to the screen
- Build skeleton code for all handle methods (returning -1 for unimplemented ones)
- Determine which system calls are made by each autograder by adding debug output in `handleSyscall()`

## Key Requirements

1. Bullet-proof the kernel from user program errors
2. Ensure user programs cannot corrupt the kernel or other processes
3. Kill processes cleanly if they do anything illegal
4. Only allow the root process to invoke halt()
5. Handle memory address translation correctly
6. Support at least 16 open files per process
7. Implement efficient physical memory allocation
8. Clean up all resources on process exit

## Programming Hints

### handleCreate()

For the create system call, here's how to implement it:
1. Read the filename (string) from user memory using readVirtualMemoryString()
2. Create or truncate the file using ThreadedKernel.fileSystem.open() with true for the second parameter
3. If the file cannot be created, return -1
4. Find a free slot in the open file table
5. If no slots are available, return -1
6. Store the OpenFile object in the open file table
7. Mark the slot as in-use
8. Return the file descriptor (index in the open file table)

### handleRead() and handleWrite()

These methods need to:
1. Validate the file descriptor
2. Validate the buffer address and size
3. For read: Transfer data from file to user memory
4. For write: Transfer data from user memory to file
5. Handle cases where data spans multiple pages
6. Return the number of bytes transferred or -1 on error

### Memory Management

When implementing the paging system:
1. Each process has its own pageTable
2. Virtual address translation: vpn = vaddr/pageSize, offset = vaddr % pageSize
3. Physical address = pageTable[vpn].ppn * pageSize + offset
4. Check for valid bit and permissions
5. For reads/writes that cross page boundaries, break into multiple operations

Good luck with your implementation!