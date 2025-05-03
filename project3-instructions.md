# Nachos Project 3: System Call and Virtual Memory Implementation Guide

## 1. Overview

The primary goal of this project is to extend the Nachos operating system simulator to support multiprogramming by implementing system calls and a virtual memory system within the `nachos/userprog` directory[cite: 6]. You will modify existing Java files to add this functionality[cite: 7, 8, 15].

## 2. Environment and Submission

- **Testing Environment:** Use the Docker container `papama/cs3053:p3` for testing your implementation.
- **Submission:** Upload a zipped `userprog` folder containing all modified Java source files and a two-page PDF report to Harvey[cite: 10, 13].
- **Report Format:** Times New Roman, fontsize 11, before and after spacing 0. Include descriptions of your approach for each task and feedback on the project[cite: 11, 12].

## 3. Core Files for Modification

Modify the following files within the `nachos/userprog` directory:

### 3.1. `UserKernel.java` [cite: 6, 21]

- **Purpose:** Manages simulated MIPS machine resources, including physical memory[cite: 6].
- **Task:** Implement and maintain a list of free physical memory pages.
  - The MIPS machine has 64 physical pages (ppn range 0-63), configurable via `nachos.conf`[cite: 1, 6]. Get this value using `Machine.processor().getNumPhysPages()`[cite: 6, 91].
  - A `LinkedList<Integer>` is suggested for tracking free physical page numbers (ppn)[cite: 6, 83].
  - Ensure synchronized access to this list if accessed concurrently[cite: 84].

### 3.2. `SynchConsole.java` [cite: 6, 23]

- **Purpose:** Provides synchronized access to the console for multiple threads/processes[cite: 23].
- **Task:** Modify locking for console output.
  - Remove `writelock.acquire()` and `writelock.release()` from the `writeByte()` method.
  - Add `writelock.acquire()` and `writelock.release()` to protect the `for`-loop within the `write()` method to prevent interleaved output from different processes[cite: 6].

### 3.3. `UserProcess.java` [cite: 6, 22]

- **Purpose:** Manages a user process's address space, loads programs, and handles system calls[cite: 22]. This file requires the most modifications[cite: 6].
- **Tasks:**

  - **A. Fields and Constructor:**

    - Implement a **Process File Table**:
      - Define a private 1D array (e.g., `OpenFile[] fileTable`) of size 16 to store open files for the process[cite: 2, 6, 73].
      - Maintain a corresponding status array (e.g., `boolean[] fileStatus`) to track used slots.
      - The first two slots (index 0 and 1) are reserved for standard input and standard output[cite: 2, 6, 64].
        - `fileTable[0]`: Standard Input (Keyboard). Initialize using `UserKernel.console.openForReading()`[cite: 2, 6, 65].
        - `fileTable[1]`: Standard Output (Console). Initialize using `UserKernel.console.openForWriting()`[cite: 2, 6, 65].
      - File descriptors returned by `creat` and `open` will be indices into this table (0-15)[cite: 6, 75].

  - **B. `handleSyscall()` Method:**

    - Implement cases for the 10 required system calls. Branch each case to a specific `handle<SyscallName>()` method[cite: 6].
    - Refer to `test/syscall.h` for argument lists, functionality, and return value definitions for each system call[cite: 4, 6, 49, 63].
    - **Recommended Implementation Order:**

      1.  `handleCreate(int namePtr)`[cite: 4, 6]: Create/truncate a file. Return file descriptor or -1 on error[cite: 62]. Use `ThreadedKernel.fileSystem.open(filename, true)`[cite: 3, 67]. Handle potential errors like invalid pointers or filenames[cite: 53, 54, 55].
      2.  `handleOpen(int namePtr)`[cite: 4, 6]: Open an existing file. Return file descriptor or -1 on error[cite: 62]. Use `ThreadedKernel.fileSystem.open(filename, false)`[cite: 3, 67].
      3.  `handleRead(int fd, int bufferPtr, int count)`[cite: 4, 6]: Read from file/console into user buffer. Return bytes read or -1 on error. Use `OpenFile.read()`[cite: 3]. Requires virtual memory translation (`readVirtualMemory` / `writeVirtualMemory`)[cite: 59].
      4.  `handleWrite(int fd, int bufferPtr, int count)`[cite: 4, 6]: Write from user buffer to file/console. Return bytes written or -1 on error. Use `OpenFile.write()`[cite: 3]. Requires virtual memory translation (`readVirtualMemory` / `writeVirtualMemory`)[cite: 59].
      5.  `handleClose(int fd)`[cite: 4, 6]: Close an open file descriptor. Release the slot in the file table. Use `OpenFile.close()`.
      6.  `handleUnlink(int namePtr)`[cite: 4, 6]: Delete a file. Use `ThreadedKernel.fileSystem.remove(filename)`[cite: 3, 67].
      7.  `handleExec(int filePtr, int argc, int argvPtr)`[cite: 6, 99]: Load and execute a new program. Allocate memory, load sections, create a new thread (`UThread`), and run it. Return the new process ID (PID) or -1 on error[cite: 109].
      8.  `handleJoin(int processID, int statusPtr)`[cite: 6, 99]: Wait for a child process to exit. Return child's exit status. Only the parent can join a child[cite: 107]. Write status to `statusPtr` using `writeVirtualMemory`[cite: 101].
      9.  `handleExit(int status)`[cite: 6, 99]: Terminate the current process. Free resources (memory, files) using `unloadSections()`, notify the parent if joined, store exit status[cite: 114, 115]. If this is the last process, halt the machine (`Machine.halt()`)[cite: 117, 118].
      10. `handleHalt()`[cite: 6]: Halt the Nachos machine (`Machine.halt()`). Should only be invokable by the initial "root" process[cite: 57]. Ignore calls from other processes[cite: 58].

    - **Argument Handling:** System call arguments are passed via registers (`a0` to `a3`). Virtual addresses (pointers) passed as arguments need translation using `readVirtualMemory` or `readVirtualMemoryString`[cite: 6, 59, 101]. String arguments are null-terminated and have a max length of 256 bytes[cite: 60, 61].
    - **Error Handling:** Return -1 for errors[cite: 62]. Ensure user errors don't crash the kernel[cite: 53, 54, 55, 56].

  - **C. `execute()` Method:**

    - May require slight modifications when implementing `handleExec` and `handleExit`[cite: 6].

  - **D. `readVirtualMemory()` and `writeVirtualMemory()` Methods:**

    - Modify the second version of each method to support paging[cite: 6].
    - **Goal:** Translate virtual addresses (vaddr) provided by the user process to physical addresses (paddr) using the process's `pageTable` before accessing physical memory (`Machine.processor().getMemory()`)[cite: 6, 89, 90].
    - The `pageTable` is an array of `TranslationEntry` objects[cite: 3, 92]. Use `vpn` (virtual page number) to index the table and get the `ppn` (physical page number)[cite: 5]. Check the `valid` bit.
    - Handle data transfers that **cross page boundaries**. A contiguous virtual memory range might map to non-contiguous physical pages[cite: 6]. You'll likely need to break the transfer into page-sized (or smaller) chunks.
    - Use `System.arraycopy()` correctly with translated physical addresses.
    - Set the `readOnly` flag in `TranslationEntry` based on the COFF section's properties (`CoffSection.isReadOnly()`) during loading[cite: 92, 93].
    - These methods should return the number of bytes successfully transferred, not throw exceptions on failure[cite: 94].

  - **E. `loadSections()` Method:**

    - **Purpose:** Allocate physical memory pages for a new process and load the program's code and data sections into them[cite: 6, 95].
    - **Steps:**
      1.  Determine `numPages` required (program sections + stack (8 pages) + arguments (1 page))[cite: 6, 82].
      2.  Check if enough free physical pages are available using the free list maintained in `UserKernel`. If not, report an error (e.g., return `false`)[cite: 6, 97].
      3.  Allocate `numPages` from the free list[cite: 6, 85]. Your allocation should be able to use non-contiguous free pages ("gaps")[cite: 86, 87].
      4.  Load program sections: Iterate through the COFF sections. For each page in a section:
          - Get a free physical page number (ppn) from your allocated list.
          - Call `section.loadPage(vpn, ppn)` to load the virtual page `vpn` into the physical page `ppn`[cite: 6].
          - Update the process's `pageTable` entry for `vpn` with the corresponding `ppn`, set `valid` to true, and set `readOnly` if necessary[cite: 6, 92, 93].
      5.  Allocate pages for the stack and arguments: Get 9 more free ppns. Map the corresponding virtual pages (typically at the end of the virtual address space) to these ppns in the `pageTable`, marking them as valid and writable[cite: 6].
    - **Remove Old Code:** Delete or comment out the loop in the `UserProcess` constructor that initially assigned all physical pages to the single process. `loadSections` is now responsible for page allocation[cite: 6].

  - **F. `unloadSections()` Method:**
    - **Purpose:** Free all resources held by a process when it exits (`handleExit` should call this)[cite: 6, 114].
    - **Task:** Iterate through the process's `pageTable`. For every valid entry, add the corresponding physical page number (`ppn`) back to the global free page list in `UserKernel`[cite: 6, 88].

## 4. Testing Strategy

Follow the suggested testing stages:

- **Stage 1 (File System Calls - No Paging Needed):** [cite: 6]
  - `Create Grader 1`: Test `handleCreate`. Check file creation, truncation, and invalid argument handling.
  - `Open Grader 1`: Test `handleOpen` for existing files.
  - `Read Grader 1`: Test `handleRead` from a file to the console.
  - `Write Grader 1`: Test `handleWrite` from the console to a file.
- **Stage 2 (Paging):** [cite: 6]
  - `Read Grader 3`: Tests `writeVirtualMemory` by having the kernel write a large array (spanning >1 page) to user memory, which is then printed. Verifies `a[i] == i`.
  - `Write Grader 3`: Tests `readVirtualMemory` by having the kernel read a large array from user memory and write it to a file.
- **Stage 3 (File System Robustness - Paging Needed):** [cite: 6]
  - `Read Grader 2`, `Write Grader 2`: Test invalid arguments for read/write.
  - `Close Grader 1`, `Close Grader 2`: Test `handleClose` and file descriptor reuse.
  - `Unlink 1`: Test `handleUnlink`.
  - _(Assumes max 16 open files: 2 stdio + 14 regular)_
- **Stage 4 (Process Management - Paging Needed):** [cite: 6]

  - `Halt Grader 1`: Test `handleHalt`.
  - `Exec Grader 1`, `Exec Grader 2`: Test `handleExec`.
  - `Join Grader 1`, `Join Grader 2`, `Join Grader 3`: Test `handleJoin`.
  - `Exit Grader 1`, `Exit Grader 2`: Test `handleExit`.

- **Final Testing:** Run `gradep3` (combines most graders) and `haltgrader` separately.

## 5. Debugging Hints

- Use `System.out.println()` extensively, especially within `handleSyscall` to see which syscall number and arguments (`a0`-`a3`) are being passed by the graders.
- Initially, stub out all `handle<SyscallName>()` methods to return -1 until you implement them fully. This helps isolate issues during testing.
