package edu.ucla.wise.client;

public class UserHitCounter {

    private static UserHitCounter userHitCounter;

    private long numberOfUserAccesses;

    public UserHitCounter() {
	numberOfUserAccesses = 0;
    }

    public static UserHitCounter getInstance() {
	if (userHitCounter == null) {
	    initialize();
	}
	return userHitCounter;
    }

    public static void initialize() {
	userHitCounter = new UserHitCounter();
    }

    /**
     * @return the numberOfUserAccesses
     */
    public long getNumberOfUserAccesses() {
	return numberOfUserAccesses;
    }

    public void incrementNumberOfUserAccesses() {
	numberOfUserAccesses++;
    }

}
