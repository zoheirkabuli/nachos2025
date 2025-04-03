package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }
    
    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    return false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    return false;

	setPriority(thread, priority-1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
	PriorityQueue(boolean transferPriority) {
	    this.transferPriority = transferPriority;
            this.waitQueue = new LinkedList<ThreadState>();
            this.resourceHolder = null;
	}

	public void waitForAccess(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).waitForAccess(this);
	}

	public void acquire(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).acquire(this);
	}

	public KThread nextThread() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    
	    ThreadState nextState = pickNextThread();
            
            if (nextState == null)
                return null;
            
            // If there's a current resource holder, they no longer hold this resource
            if (resourceHolder != null) {
                resourceHolder.resourcesHeld.remove(this);
                resourceHolder.invalidateCache();
            }
            
            // Remove the chosen thread from wait queue
            waitQueue.remove(nextState);
            
            // Set this thread as the resource holder
            resourceHolder = nextState;
            nextState.acquire(this);
            
            return nextState.thread;
	}

	/**
	 * Return the next thread that <tt>nextThread()</tt> would return,
	 * without modifying the state of this queue.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
	protected ThreadState pickNextThread() {
	    if (waitQueue.isEmpty())
                return null;
                
            // Find thread with highest effective priority
            ThreadState highestPriorityThread = null;
            int highestPriority = priorityMinimum - 1;
            long earliestTime = Long.MAX_VALUE;
            
            for (ThreadState state : waitQueue) {
                int effectivePriority = state.getEffectivePriority();
                
                if (effectivePriority > highestPriority) {
                    highestPriorityThread = state;
                    highestPriority = effectivePriority;
                    earliestTime = state.waitTime;
                } else if (effectivePriority == highestPriority && 
                           state.waitTime < earliestTime) {
                    // If same priority, choose the one waiting longest
                    highestPriorityThread = state;
                    earliestTime = state.waitTime;
                }
            }
            
            return highestPriorityThread;
	}
	
	public void print() {
	    Lib.assertTrue(Machine.interrupt().disabled());
            System.out.println("Queue Contents:");
            for (ThreadState state : waitQueue) {
                System.out.println("  Thread: " + state.thread.getName() + 
                                   ", Priority: " + state.getPriority() +
                                   ", Effective: " + state.getEffectivePriority());
            }
	}

	/**
	 * <tt>true</tt> if this queue should transfer priority from waiting
	 * threads to the owning thread.
	 */
	public boolean transferPriority;
        
        // The list of threads waiting for this resource
        public LinkedList<ThreadState> waitQueue;
        
        // The thread that currently holds this resource
        public ThreadState resourceHolder;
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	public ThreadState(KThread thread) {
	    this.thread = thread;
	    
            this.priority = priorityDefault;
            this.cachedPriority = priorityDefault;
            this.cachedPriorityValid = true;
            
            // Resources this thread is waiting on
            this.waitForResource = null;
            
            // Resources this thread currently holds
            this.resourcesHeld = new LinkedList<PriorityQueue>();
            
            // Time when this thread started waiting (for FIFO ordering of same-priority threads)
            this.waitTime = Machine.timer().getTime();
	}

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
	    return priority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {
            if (cachedPriorityValid)
                return cachedPriority;
            
            // Start with the base priority
            cachedPriority = priority;
            
            // If we're not holding any resources that can receive donation, we're done
            if (resourcesHeld.isEmpty()) {
                cachedPriorityValid = true;
                return cachedPriority;
            }
            
            // Use a HashSet to track visited nodes and avoid cycles
            HashSet<ThreadState> visited = new HashSet<ThreadState>();
            visited.add(this);
            
            // Process all resources this thread holds
            for (PriorityQueue pq : resourcesHeld) {
                // Only consider resources that transfer priority and have waiters
                if (pq.transferPriority && !pq.waitQueue.isEmpty()) {
                    for (ThreadState waiter : pq.waitQueue) {
                        if (!visited.contains(waiter)) {
                            int donatedPriority = waiter.getDonatedPriority(visited);
                            cachedPriority = Math.max(cachedPriority, donatedPriority);
                        }
                    }
                }
            }
            
            cachedPriorityValid = true;
            return cachedPriority;
	}
        
        /**
         * Get the priority this thread would donate, considering its own priority
         * and any priority donated to it.
         */
        private int getDonatedPriority(HashSet<ThreadState> visited) {
            visited.add(this);
            
            // Start with this thread's priority
            int donated = priority;
            
            // If this thread holds resources, it might receive donations
            for (PriorityQueue pq : resourcesHeld) {
                if (pq.transferPriority && !pq.waitQueue.isEmpty()) {
                    for (ThreadState waiter : pq.waitQueue) {
                        if (!visited.contains(waiter)) {
                            donated = Math.max(donated, waiter.getDonatedPriority(visited));
                        }
                    }
                }
            }
            
            return donated;
        }

	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
	    if (this.priority == priority)
		return;
	    
	    this.priority = priority;
	    
            // Priority changed, invalidate our cached priority
            invalidateCache();
	}
        
        /**
         * Invalidate the cached effective priority for this thread and
         * any threads that this thread is donating priority to.
         */
        private void invalidateCache() {
            if (cachedPriorityValid) {
                cachedPriorityValid = false;
                
                // If we're waiting for a resource that transfers priority,
                // we need to invalidate the resource holder's cache
                if (waitForResource != null && waitForResource.transferPriority && 
                    waitForResource.resourceHolder != null) {
                    waitForResource.resourceHolder.invalidateCache();
                }
            }
        }

	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified priority queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(PriorityQueue waitQueue) {
            // If we were holding this resource, we need to release it first
            if (resourcesHeld.contains(waitQueue)) {
                resourcesHeld.remove(waitQueue);
                if (waitQueue.resourceHolder == this) {
                    waitQueue.resourceHolder = null;
                }
            }
            
            // Record that we're waiting for this resource
            this.waitForResource = waitQueue;
            
            // Update wait time for FIFO ordering of same-priority threads
            this.waitTime = Machine.timer().getTime();
            
            // Add to the wait queue
            waitQueue.waitQueue.add(this);
            
            // If there's a resource holder and this queue transfers priority,
            // invalidate the holder's cached priority for donation
            if (waitQueue.transferPriority && waitQueue.resourceHolder != null) {
                waitQueue.resourceHolder.invalidateCache();
            }
	}

	/**
	 * Called when the associated thread has acquired access to whatever is
	 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	 * <tt>thread</tt> is the associated thread), or as a result of
	 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
	 *
	 * @see	nachos.threads.ThreadQueue#acquire
	 * @see	nachos.threads.ThreadQueue#nextThread
	 */
	public void acquire(PriorityQueue waitQueue) {
            // If we were in the wait queue, we're not anymore
            if (this.waitForResource == waitQueue) {
                this.waitForResource = null;
            }
            
            // Add the resource to our held resources if not already there
            if (!resourcesHeld.contains(waitQueue)) {
                resourcesHeld.add(waitQueue);
            }
            
            // Set ourselves as the holder of this resource
            waitQueue.resourceHolder = this;
            
            // Invalidate our cached priority as we may receive donations
            invalidateCache();
	}

	/** The thread with which this object is associated. */	   
	protected KThread thread;
	/** The priority of the associated thread. */
	protected int priority;
        
        /** The effective priority (including donations) */
        protected int cachedPriority;
        
        /** Whether the cached priority is valid */
        protected boolean cachedPriorityValid;
        
        /** Time when this thread started waiting */
        protected long waitTime;
        
        /** The queue this thread is waiting on (if any) */
        protected PriorityQueue waitForResource;
        
        /** The resources this thread currently holds */
        protected LinkedList<PriorityQueue> resourcesHeld;
    }
}
