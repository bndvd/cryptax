package bdn.cryptax;

import bdn.cryptax.controller.Controller;
import bdn.cryptax.controller.ControllerException;

public class Main {

	public static void main(String[] args) {
		System.out.println("INFO: Cryptax STARTED");

		if (args.length == 1) {
			String inFileName = args[0];
			
			try {
				Controller.process(inFileName);
			}
			catch (ControllerException exc) {
				System.err.println("ERROR: " + exc.getMessage());
			}
		}
		else {
			System.err.println("ERROR: Usage: {java-main} src-file");
		}
		
		System.out.println("INFO: Cryptax EXITED");
	}

}
