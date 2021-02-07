package bdn.cryptax;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.commons.io.FilenameUtils;

import bdn.cryptax.controller.Controller;
import bdn.cryptax.controller.ControllerException;

public class Main {

	public static void main(String[] args) {
		System.out.println("INFO: Cryptax STARTED");

		if (args.length == 1) {
			String inFileName = args[0];
			
			try {
				String fileBaseName = FilenameUtils.getBaseName(inFileName);
				DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
				String now = LocalDateTime.now().format(dtf);
				String outFileNameCapitalGains = fileBaseName + "_cg" + now + ".csv";
				String outFileNameIncome = fileBaseName + "_inc" + now + ".csv";
				
				Controller.processCostBasis(inFileName, outFileNameCapitalGains, outFileNameIncome);
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
