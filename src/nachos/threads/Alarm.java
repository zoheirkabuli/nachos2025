package nachos.threads;

import nachos.machine.*;
import java.util.PriorityQueue;
import java.util.Comparator;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
	
	waitingThreads = new PriorityQueue<WaitingThread>(
	    new Comparator<WaitingThread>() {
		public int compare(WaitingThread wt1, WaitingThread wt2) {
		    if (wt1.wakeTime < wt2.wakeTime) return -1;
		    else if (wt1.wakeTime > wt2.wakeTime) return 1;
		    else return 0;
		}
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
	
	long currentTime = Machine.timer().getTime();
	
	boolean intStatus = Machine.interrupt().disable();
	
	while (!waitingThreads.isEmpty() && waitingThreads.peek().wakeTime <= currentTime) {
	    WaitingThread wt = waitingThreads.poll();
	    wt.thread.ready();
	}
	
	Machine.interrupt().restore(intStatus);
	
	KThread.currentThread().yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
	
	long wakeTime = Machine.timer().getTime() + x;
	
	if (x <= 0)
	    return;
	
	boolean intStatus = Machine.interrupt().disable();
	
	WaitingThread wt = new WaitingThread(KThread.currentThread(), wakeTime);
	
	waitingThreads.add(wt);
	
	KThread.sleep();
	
	Machine.interrupt().restore(intStatus);
    }
    
    private class WaitingThread {
	public KThread thread;
	public long wakeTime;
	
	public WaitingThread(KThread thread, long wakeTime) {
	    this.thread = thread;
	    this.wakeTime = wakeTime;
	}
    }
    
    private PriorityQueue<WaitingThread> waitingThreads;
}
