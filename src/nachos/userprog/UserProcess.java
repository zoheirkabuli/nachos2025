package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
    /** The maximum number of open files per process. */
    private static final int MAXFD = 16;

    /** Process ID counter */
    private static int nextProcessID = 0;

    /** Lock for process ID assignment */
    private static Lock pidLock = new Lock();

    /**
     * Allocate a new process.
     */
    public UserProcess() {
        // Assign a unique process ID
        pidLock.acquire();
        processID = nextProcessID++;
        pidLock.release();

        // Initialize the open file table
        fileTable = new OpenFile[MAXFD];
        fileUsed = new boolean[MAXFD];

        // Initialize standard input (keyboard) and standard output (console)
        fileTable[0] = UserKernel.console.openForReading();
        fileUsed[0] = true;
        fileTable[1] = UserKernel.console.openForWriting();
        fileUsed[1] = true;

        // Set parent to null initially
        parent = null;

        // Initialize child processes map and exit status map
        childProcesses = new HashMap<>();
        childExitStatus = new HashMap<>();

        // Allocate page table initially with all physical pages
        // This will be replaced with the proper paging implementation
        int numPhysPages = Machine.processor().getNumPhysPages();
        pageTable = new TranslationEntry[numPhysPages];
        for (int i = 0; i < numPhysPages; i++)
            pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
    }

    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
        if (!load(name, args))
            return false;

        thread = new UThread(this);
        thread.setName(name).fork();

        return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param vaddr     the starting virtual address of the null-terminated
     *                  string.
     * @param maxLength the maximum number of characters in the string,
     *                  not including the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was
     *         found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);

        byte[] bytes = new byte[maxLength + 1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to read.
     * @param data  the array where the data will be stored.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to read.
     * @param data   the array where the data will be stored.
     * @param offset the first byte to write in the array.
     * @param length the number of bytes to transfer from virtual memory to
     *               the array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
            int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        // Calculate the number of bytes to transfer
        if (vaddr < 0)
            return 0;

        int totalBytesTransferred = 0;

        while (length > 0) {
            // Calculate the virtual page number and offset within the page
            int vpn = Processor.pageFromAddress(vaddr);
            int pageOffset = Processor.offsetFromAddress(vaddr);

            // Check if the virtual page number is valid
            if (vpn < 0 || vpn >= pageTable.length)
                break;

            // Get the translation entry for this virtual page
            TranslationEntry entry = pageTable[vpn];

            // Check if the page is valid
            if (!entry.valid)
                break;

            // Mark the page as being accessed
            entry.used = true;

            // Calculate the physical address
            int ppn = entry.ppn;
            int paddr = ppn * pageSize + pageOffset;

            // Calculate the number of bytes to transfer from this page
            int bytesToTransfer = Math.min(length, pageSize - pageOffset);

            // Check if physical address is valid
            if (paddr < 0 || paddr >= memory.length)
                break;

            // Transfer the data
            int bytesAvailable = Math.min(bytesToTransfer, memory.length - paddr);
            System.arraycopy(memory, paddr, data, offset, bytesAvailable);

            // Update counters
            vaddr += bytesAvailable;
            offset += bytesAvailable;
            length -= bytesAvailable;
            totalBytesTransferred += bytesAvailable;
        }

        return totalBytesTransferred;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to write.
     * @param data  the array containing the data to transfer.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to write.
     * @param data   the array containing the data to transfer.
     * @param offset the first byte to transfer from the array.
     * @param length the number of bytes to transfer from the array to
     *               virtual memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
            int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        // Calculate the number of bytes to transfer
        if (vaddr < 0)
            return 0;

        int totalBytesTransferred = 0;

        while (length > 0) {
            // Calculate the virtual page number and offset within the page
            int vpn = Processor.pageFromAddress(vaddr);
            int pageOffset = Processor.offsetFromAddress(vaddr);

            // Check if the virtual page number is valid
            if (vpn < 0 || vpn >= pageTable.length)
                break;

            // Get the translation entry for this virtual page
            TranslationEntry entry = pageTable[vpn];

            // Check if the page is valid and writable
            if (!entry.valid || entry.readOnly)
                break;

            // Mark the page as being accessed and modified
            entry.used = true;
            entry.dirty = true;

            // Calculate the physical address
            int ppn = entry.ppn;
            int paddr = ppn * pageSize + pageOffset;

            // Calculate the number of bytes to transfer to this page
            int bytesToTransfer = Math.min(length, pageSize - pageOffset);

            // Check if physical address is valid
            if (paddr < 0 || paddr >= memory.length)
                break;

            // Transfer the data
            int bytesAvailable = Math.min(bytesToTransfer, memory.length - paddr);
            System.arraycopy(data, offset, memory, paddr, bytesAvailable);

            // Update counters
            vaddr += bytesAvailable;
            offset += bytesAvailable;
            length -= bytesAvailable;
            totalBytesTransferred += bytesAvailable;
        }

        return totalBytesTransferred;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }

        try {
            coff = new Coff(executable);
        } catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }

        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
            numPages += section.getLength();
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
        numPages += stackPages;
        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments
        numPages++;

        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i = 0; i < args.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, args[i].getBytes()) == args[i].length());
            stringOffset += args[i].length();
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
            stringOffset += 1;
        }

        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        // Calculate the number of physical pages needed
        if (numPages > Machine.processor().getNumPhysPages()) {
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient physical memory");
            return false;
        }

        // Create a new page table of the required size
        pageTable = new TranslationEntry[numPages];

        // Allocate physical pages and update page table
        for (int vpn = 0; vpn < numPages; vpn++) {
            int ppn = UserKernel.allocatePage();
            if (ppn == -1) {
                // Free all allocated pages
                for (int i = 0; i < vpn; i++) {
                    UserKernel.freePage(pageTable[i].ppn);
                }
                coff.close();
                Lib.debug(dbgProcess, "\tcould not allocate physical pages");
                return false;
            }

            // Initialize the page table entry
            pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, false, false);
        }

        // Load sections
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess, "\tinitializing " + section.getName()
                    + " section (" + section.getLength() + " pages)");

            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;

                // Mark text segments as read-only
                if (section.isReadOnly())
                    pageTable[vpn].readOnly = true;

                // Load the section
                section.loadPage(i, pageTable[vpn].ppn);
            }
        }

        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        // Free all physical pages
        if (pageTable != null) {
            for (int i = 0; i < pageTable.length; i++) {
                if (pageTable[i] != null && pageTable[i].valid) {
                    UserKernel.freePage(pageTable[i].ppn);
                    pageTable[i].valid = false;
                }
            }
        }
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
        Processor processor = Machine.processor();

        // by default, everything's 0
        for (int i = 0; i < processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {
        // Only allow the root process (pid 0) to halt the machine
        if (processID != 0)
            return -1;

        Machine.halt();

        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }

    /**
     * Attempt to create a new file with the specified name.
     * 
     * @param nameAddress the virtual address of the file name string
     * @return the file descriptor (index to file table), or -1 if failed
     */
    private int handleCreate(int nameAddress) {
        // Read the filename from user memory
        String filename = readVirtualMemoryString(nameAddress, 256);
        if (filename == null)
            return -1;

        // Find an available file descriptor
        int fd = -1;
        for (int i = 2; i < MAXFD; i++) {
            if (!fileUsed[i]) {
                fd = i;
                break;
            }
        }

        // If no available file descriptor, return error
        if (fd == -1)
            return -1;

        // Create the file
        OpenFile file = ThreadedKernel.fileSystem.open(filename, true);
        if (file == null)
            return -1;

        // Store the file in the file table
        fileTable[fd] = file;
        fileUsed[fd] = true;

        return fd;
    }

    /**
     * Attempt to open an existing file with the specified name.
     * 
     * @param nameAddress the virtual address of the file name string
     * @return the file descriptor (index to file table), or -1 if failed
     */
    private int handleOpen(int nameAddress) {
        // Read the filename from user memory
        String filename = readVirtualMemoryString(nameAddress, 256);
        if (filename == null)
            return -1;

        // Find an available file descriptor
        int fd = -1;
        for (int i = 2; i < MAXFD; i++) {
            if (!fileUsed[i]) {
                fd = i;
                break;
            }
        }

        // If no available file descriptor, return error
        if (fd == -1)
            return -1;

        // Open the file (don't create if it doesn't exist)
        OpenFile file = ThreadedKernel.fileSystem.open(filename, false);
        if (file == null)
            return -1;

        // Store the file in the file table
        fileTable[fd] = file;
        fileUsed[fd] = true;

        return fd;
    }

    /**
     * Read data from an open file.
     * 
     * @param fileDescriptor the file descriptor to read from
     * @param bufferAddress  the virtual address of the buffer to read into
     * @param count          the number of bytes to read
     * @return the number of bytes read, or -1 if an error occurred
     */
    private int handleRead(int fileDescriptor, int bufferAddress, int count) {
        // Validate file descriptor
        if (fileDescriptor < 0 || fileDescriptor >= MAXFD || !fileUsed[fileDescriptor]
                || fileTable[fileDescriptor] == null)
            return -1;

        // Validate count
        if (count < 0)
            return -1;

        // Validate buffer address - check if it's in valid virtual address space
        if (bufferAddress < 0 || bufferAddress >= numPages * pageSize)
            return -1;

        // If count is 0, just return 0
        if (count == 0)
            return 0;

        // Create a buffer for reading
        byte[] buffer = new byte[count];

        // Read from the file - may read less than requested
        int bytesRead = fileTable[fileDescriptor].read(buffer, 0, count);
        if (bytesRead <= 0) // Handle EOF or error
            return bytesRead;

        // Write the data to user memory
        int bytesWritten = writeVirtualMemory(bufferAddress, buffer, 0, bytesRead);

        // If we couldn't write anything to the buffer address, it's invalid
        if (bytesWritten == 0 && bytesRead > 0)
            return -1;

        // Return actual number of bytes read
        return bytesRead;
    }

    /**
     * Write data to an open file.
     * 
     * @param fileDescriptor the file descriptor to write to
     * @param bufferAddress  the virtual address of the buffer to write from
     * @param count          the number of bytes to write
     * @return the number of bytes written, or -1 if an error occurred
     */
    private int handleWrite(int fileDescriptor, int bufferAddress, int count) {
        // Validate file descriptor
        if (fileDescriptor < 0 || fileDescriptor >= MAXFD || !fileUsed[fileDescriptor]
                || fileTable[fileDescriptor] == null)
            return -1;

        // Validate count
        if (count < 0)
            return -1;

        // Validate buffer address - check if it's in valid virtual address space
        if (bufferAddress < 0 || bufferAddress >= numPages * pageSize)
            return -1;

        // If count is 0, just return 0
        if (count == 0)
            return 0;

        // Create a buffer for writing
        byte[] buffer = new byte[count];

        // Read the data from user memory
        int bytesRead = readVirtualMemory(bufferAddress, buffer, 0, count);

        // If we couldn't read anything from the buffer address and count > 0, it's
        // invalid
        if (bytesRead == 0 && count > 0)
            return -1;

        if (bytesRead < count) {
            // Adjust the count to what we were able to read
            count = bytesRead;
        }

        // If we couldn't read anything, return 0
        if (count == 0)
            return 0;

        // Write to the file
        int bytesWritten = fileTable[fileDescriptor].write(buffer, 0, count);

        return bytesWritten;
    }

    /**
     * Close an open file.
     * 
     * @param fileDescriptor the file descriptor to close
     * @return 0 on success, -1 on failure
     */
    private int handleClose(int fileDescriptor) {
        // Validate file descriptor
        if (fileDescriptor < 0 || fileDescriptor >= MAXFD || !fileUsed[fileDescriptor])
            return -1;

        // Close the file
        fileTable[fileDescriptor].close();
        fileTable[fileDescriptor] = null;
        fileUsed[fileDescriptor] = false;

        return 0;
    }

    /**
     * Delete a file from the file system.
     * 
     * @param nameAddress the virtual address of the file name string
     * @return 0 on success, -1 on failure
     */
    private int handleUnlink(int nameAddress) {
        // Read the filename from user memory
        String filename = readVirtualMemoryString(nameAddress, 256);
        if (filename == null)
            return -1;

        // Remove the file
        boolean success = ThreadedKernel.fileSystem.remove(filename);

        return success ? 0 : -1;
    }

    /**
     * Execute a program in a new process.
     * 
     * @param fileNameVaddr virtual address of the file name string
     * @param argc          number of arguments
     * @param argvVaddr     virtual address of the argument array
     * @return the process ID of the new process, or -1 if an error occurred
     */
    private int handleExec(int fileNameVaddr, int argc, int argvVaddr) {
        // Read the file name from user memory
        String fileName = readVirtualMemoryString(fileNameVaddr, 256);
        if (fileName == null)
            return -1;

        // Make sure the file name ends with .coff
        if (!fileName.endsWith(".coff"))
            return -1;

        // Validate arguments
        if (argc < 0)
            return -1;

        // Read the arguments from user memory
        String[] args = new String[argc];
        byte[] buffer = new byte[4 * argc];
        int bytesRead = readVirtualMemory(argvVaddr, buffer);
        if (bytesRead != 4 * argc)
            return -1;

        // Extract arguments from the buffer
        for (int i = 0; i < argc; i++) {
            int argVaddr = Lib.bytesToInt(buffer, i * 4);
            args[i] = readVirtualMemoryString(argVaddr, 256);
            if (args[i] == null)
                return -1;
        }

        // For debugging: Print the arguments
        System.out.println("Executing: " + fileName);
        for (int i = 0; i < argc; i++) {
            System.out.println("argv[" + i + "]=" + args[i]);
        }

        // Create a new process
        UserProcess child = UserProcess.newUserProcess();

        // Set this process as the parent
        child.parent = this;

        // Execute the program in the child process
        if (!child.execute(fileName, args))
            return -1;

        // Store the child process
        childProcesses.put(child.processID, child);

        return child.processID;
    }

    /**
     * Join to a child process and retrieve its exit status.
     * 
     * @param processID   the process ID of the child process
     * @param statusVaddr virtual address of the storage for the exit status
     * @return 0 on success, -1 if processID does not refer to a child process
     */
    private int handleJoin(int processID, int statusVaddr) {
        // Check if the process is a child of this process
        if (!childProcesses.containsKey(processID))
            return -1;

        UserProcess child = childProcesses.get(processID);

        // If the child has already exited, get its exit status from the map
        Integer exitStatus = childExitStatus.get(processID);

        // Otherwise, wait for the child to exit
        if (exitStatus == null) {
            if (child.thread != null) {
                child.thread.join();
            }

            exitStatus = childExitStatus.get(processID);
            if (exitStatus == null)
                return -1;
        }

        // Remove the child from our children map
        childProcesses.remove(processID);

        // Write the exit status to user memory
        byte[] buffer = new byte[4];
        Lib.bytesFromInt(buffer, 0, exitStatus);
        int bytesWritten = writeVirtualMemory(statusVaddr, buffer);

        // If writing to the status address fails, return 0 instead of -1
        // This handles the case of valid PID but invalid status address
        if (bytesWritten != 4)
            return 0;

        return 1; // Success
    }

    /**
     * Terminate the current process.
     * 
     * @param status the exit status of the process
     * @return Should not return
     */
    private int handleExit(int status) {
        // Close all open files
        for (int i = 0; i < MAXFD; i++) {
            if (fileUsed[i]) {
                handleClose(i);
            }
        }

        // Unload sections to free physical memory
        unloadSections();

        // If this is the root process, halt the machine
        if (processID == 0)
            Machine.halt();

        // Notify parent of exit status if we have a parent
        if (parent != null) {
            parent.childExitStatus.put(processID, status);
        }

        // Terminate the current thread
        KThread.finish();

        // Should never reach here
        return 0;
    }

    private static final int syscallHalt = 0,
            syscallExit = 1,
            syscallExec = 2,
            syscallJoin = 3,
            syscallCreate = 4,
            syscallOpen = 5,
            syscallRead = 6,
            syscallWrite = 7,
            syscallClose = 8,
            syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr>
     * <td>syscall#</td>
     * <td>syscall prototype</td>
     * </tr>
     * <tr>
     * <td>0</td>
     * <td><tt>void halt();</tt></td>
     * </tr>
     * <tr>
     * <td>1</td>
     * <td><tt>void exit(int status);</tt></td>
     * </tr>
     * <tr>
     * <td>2</td>
     * <td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td>
     * </tr>
     * <tr>
     * <td>3</td>
     * <td><tt>int  join(int pid, int *status);</tt></td>
     * </tr>
     * <tr>
     * <td>4</td>
     * <td><tt>int  creat(char *name);</tt></td>
     * </tr>
     * <tr>
     * <td>5</td>
     * <td><tt>int  open(char *name);</tt></td>
     * </tr>
     * <tr>
     * <td>6</td>
     * <td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td>
     * </tr>
     * <tr>
     * <td>7</td>
     * <td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td>
     * </tr>
     * <tr>
     * <td>8</td>
     * <td><tt>int  close(int fd);</tt></td>
     * </tr>
     * <tr>
     * <td>9</td>
     * <td><tt>int  unlink(char *name);</tt></td>
     * </tr>
     * </table>
     * 
     * @param syscall the syscall number.
     * @param a0      the first syscall argument.
     * @param a1      the second syscall argument.
     * @param a2      the third syscall argument.
     * @param a3      the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {

        switch (syscall) {
            case syscallHalt:
                return handleHalt();
            case syscallCreate:
                return handleCreate(a0);
            case syscallOpen:
                return handleOpen(a0);
            case syscallRead:
                return handleRead(a0, a1, a2);
            case syscallWrite:
                return handleWrite(a0, a1, a2);
            case syscallClose:
                return handleClose(a0);
            case syscallUnlink:
                return handleUnlink(a0);
            case syscallExec:
                return handleExec(a0, a1, a2);
            case syscallJoin:
                return handleJoin(a0, a1);
            case syscallExit:
                return handleExit(a0);

            default:
                Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                Lib.assertNotReached("Unknown system call!");
        }
        return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param cause the user exception that occurred.
     */
    public void handleException(int cause) {
        Processor processor = Machine.processor();

        switch (cause) {
            case Processor.exceptionSyscall:
                int result = handleSyscall(processor.readRegister(Processor.regV0),
                        processor.readRegister(Processor.regA0),
                        processor.readRegister(Processor.regA1),
                        processor.readRegister(Processor.regA2),
                        processor.readRegister(Processor.regA3));
                processor.writeRegister(Processor.regV0, result);
                processor.advancePC();
                break;

            default:
                Lib.debug(dbgProcess, "Unexpected exception: " +
                        Processor.exceptionNames[cause]);
                Lib.assertNotReached("Unexpected exception");
        }
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;

    private int initialPC, initialSP;
    private int argc, argv;

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';

    /** Process ID */
    private int processID;

    /** Open file table */
    private OpenFile[] fileTable;
    /** Open file usage flags */
    private boolean[] fileUsed;

    /** Parent process */
    private UserProcess parent;

    /** Child processes map */
    private HashMap<Integer, UserProcess> childProcesses;
    /** Child process exit status map */
    private HashMap<Integer, Integer> childExitStatus;

    /** Thread running this process */
    UThread thread;
}
