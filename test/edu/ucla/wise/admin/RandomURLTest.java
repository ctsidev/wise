package edu.ucla.wise.admin;

public class RandomURLTest {

    public static void main(String[] args) {

	int i = 0;
	while (i++ < 20) {
	    System.out.println(org.apache.commons.lang3.RandomStringUtils
		    .randomAlphanumeric(22));
	}
    }
}
