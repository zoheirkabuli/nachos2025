package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
        lock = new Lock();
        speakerWaiting = new Condition2(lock);
        listenerWaiting = new Condition2(lock);
        wordReady = new Condition2(lock);
        
        hasWord = false;
        speakerCount = 0;
        listenerCount = 0;
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
        lock.acquire();
        
        speakerCount++;
        
        while (hasWord) {
            speakerWaiting.sleep();
        }
        
        this.word = word;
        hasWord = true;
        
        if (listenerCount > 0) {
            listenerWaiting.wake();
        }
        
        while (hasWord) {
            wordReady.sleep();
        }
        
        speakerCount--;
        
        if (speakerCount > 0) {
            speakerWaiting.wake();
        }
        
        lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
        lock.acquire();
        
        listenerCount++;
        
        while (!hasWord) {
            
            if (speakerCount > 0) {
                speakerWaiting.wake();
            }
            
            listenerWaiting.sleep();
        }
        
        int receivedWord = this.word;
        
        hasWord = false;
        
        wordReady.wake();
        
        listenerCount--;
        
        if (listenerCount > 0) {
            listenerWaiting.wake();
        }
        
        lock.release();
        
        return receivedWord;
    }
    
    private Lock lock;
    private Condition2 speakerWaiting;
    private Condition2 listenerWaiting;
    private Condition2 wordReady;
    
    private int word;
    private boolean hasWord;
    private int speakerCount;
    private int listenerCount;
}
