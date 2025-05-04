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
    private Lock lock;
    private Condition2 speakerCondition;
    private Condition2 listenerCondition;
    
    private int word;
    private boolean wordAvailable;
    private int waitingSpeakers;
    private int waitingListeners;

    /**
     * Allocate a new communicator.
     */
    public Communicator() {
        lock = new Lock();
        speakerCondition = new Condition2(lock);
        listenerCondition = new Condition2(lock);
        
        wordAvailable = false;
        waitingSpeakers = 0;
        waitingListeners = 0;
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
        
        waitingSpeakers++;
        
        // Wait until no word is available AND a listener is ready
        while (wordAvailable || waitingListeners == 0) {
            speakerCondition.sleep();
        }
        
        // Transfer the word
        this.word = word;
        wordAvailable = true;
        waitingSpeakers--;
        
        // Wake up the listener that we paired with
        listenerCondition.wake();
        
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
        
        waitingListeners++;
        
        // If no word is ready, wake a speaker if one is waiting
        if (!wordAvailable) {
            speakerCondition.wake();
        }
        
        // Wait until a word is available
        while (!wordAvailable) {
            listenerCondition.sleep();
        }
        
        // Receive the word
        int receivedWord = this.word;
        wordAvailable = false;
        waitingListeners--;
        
        // Wake the next speaker, if any (handles case where multiple speakers arrived before listener)
        speakerCondition.wake();
        
        lock.release();
        return receivedWord;
    }
}
