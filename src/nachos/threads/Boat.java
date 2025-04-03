package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat
{
    static BoatGrader bg;
    
    // Shared state variables
    static Lock lock;
    static Condition adultCV;    // Adult wait condition
    static Condition childCV;    // Child wait condition
    static boolean boatAtOahu;   // Boat location
    static int childrenOnOahu;   // Count of children on Oahu
    static int adultsOnOahu;     // Count of adults on Oahu
    static int childrenOnMolokai; // Count of children on Molokai
    static int adultsOnMolokai;  // Count of adults on Molokai
    static boolean done;         // Done flag
    static int totalPeople;      // Total number of people
    
    public static void selfTest()
    {
        BoatGrader b = new BoatGrader();
        
        System.out.println("\n ***Testing Boats with only 2 children***");
        begin(0, 2, b);

        System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
        begin(1, 2, b);

        System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
        begin(3, 3, b);
    }

    public static void begin(int adults, int children, BoatGrader b)
    {
        // Store the externally generated autograder in a class
        // variable to be accessible by children.
        bg = b;

        // Initialize global variables
        lock = new Lock();
        adultCV = new Condition(lock);
        childCV = new Condition(lock);
        boatAtOahu = true;
        childrenOnOahu = children;
        adultsOnOahu = adults;
        childrenOnMolokai = 0;
        adultsOnMolokai = 0;
        done = false;
        totalPeople = adults + children;
        
        // Create a thread for each child
        for (int i = 0; i < children; i++) {
            Runnable r = new Runnable() {
                public void run() {
                    ChildItinerary();
                }
            };
            KThread t = new KThread(r);
            t.setName("Child " + i);
            t.fork();
        }
        
        // Create a thread for each adult
        for (int i = 0; i < adults; i++) {
            Runnable r = new Runnable() {
                public void run() {
                    AdultItinerary();
                }
            };
            KThread t = new KThread(r);
            t.setName("Adult " + i);
            t.fork();
        }
    }

    static void AdultItinerary()
    {
        lock.acquire();
        
        while (!done) {
            // Check if we're done (all people on Molokai)
            if (childrenOnMolokai + adultsOnMolokai == totalPeople) {
                done = true;
                childCV.wakeAll();
                adultCV.wakeAll();
                break;
            }
            
            // Adults can only row when:
            // 1. The boat is at Oahu
            // 2. There are no children at Oahu OR there's only one child left on Oahu
            // 3. There's at least one adult on Oahu
            if (boatAtOahu && childrenOnOahu <= 1 && adultsOnOahu > 0) {
                // Row to Molokai
                bg.AdultRowToMolokai();
                adultsOnOahu--;
                adultsOnMolokai++;
                boatAtOahu = false;
                
                // Wake up children to potentially bring the boat back
                childCV.wakeAll();
            } else {
                // If conditions aren't met, go to sleep
                adultCV.sleep();
            }
        }
        
        lock.release();
    }

    static void ChildItinerary()
    {
        lock.acquire();
        
        while (!done) {
            // Check if we're done (all people on Molokai)
            if (childrenOnMolokai + adultsOnMolokai == totalPeople) {
                done = true;
                childCV.wakeAll();
                adultCV.wakeAll();
                break;
            }
            
            if (boatAtOahu) {
                // Boat is at Oahu
                if (childrenOnOahu >= 2) {
                    // Two children can go to Molokai
                    childrenOnOahu -= 2;  // Remove both children from Oahu
                    childrenOnMolokai += 2;  // Add both to Molokai
                    
                    bg.ChildRowToMolokai();  // First child (pilot)
                    bg.ChildRideToMolokai(); // Second child (passenger)
                    
                    boatAtOahu = false;
                    
                    // Wake everyone up to reassess the situation
                    childCV.wakeAll();
                    adultCV.wakeAll();
                } 
                else if (childrenOnOahu == 1 && adultsOnOahu == 0) {
                    // Only one child left on Oahu and no adults, it can go alone
                    childrenOnOahu--;
                    childrenOnMolokai++;
                    bg.ChildRowToMolokai();
                    boatAtOahu = false;
                    
                    // Now check if we're done
                    if (childrenOnMolokai + adultsOnMolokai == totalPeople) {
                        done = true;
                        childCV.wakeAll();
                        adultCV.wakeAll();
                    }
                } 
                else {
                    // Not enough children or there are adults who need to go
                    // but need children to come back with the boat
                    childCV.sleep();
                }
            } 
            else if (!boatAtOahu && childrenOnMolokai > 0) {
                // Boat is at Molokai
                
                // Only send a child back to Oahu if there are still people there
                if (childrenOnOahu + adultsOnOahu > 0) {
                    childrenOnMolokai--;
                    childrenOnOahu++;
                    bg.ChildRowToOahu();
                    boatAtOahu = true;
                    
                    // Wake up everyone to reassess
                    childCV.wakeAll();
                    adultCV.wakeAll();
                } 
                else {
                    // Everyone is on Molokai, we're done!
                    done = true;
                    childCV.wakeAll();
                    adultCV.wakeAll();
                }
            } 
            else {
                // Wait for the boat to arrive or other conditions to change
                childCV.sleep();
            }
        }
        
        lock.release();
    }

    static void SampleItinerary()
    {
        // This sample itinerary is not used in the actual solution
        System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
        bg.AdultRowToMolokai();
        bg.ChildRideToMolokai();
        bg.AdultRideToMolokai();
        bg.ChildRideToMolokai();
    }
}
