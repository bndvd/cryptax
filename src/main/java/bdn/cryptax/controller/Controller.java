package bdn.cryptax.controller;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import bdn.cryptax.model.CapitalGainEntry;
import bdn.cryptax.model.IncomeEntry;
import bdn.cryptax.model.Transaction;
import bdn.cryptax.model.Transaction.TransactionType;
import bdn.cryptax.model.TransactionComparator;
import bdn.cryptax.model.TransactionException;
import bdn.cryptax.model.TransactionException.TransactionExceptionType;
import bdn.cryptax.model.TransactionMemento;

public class Controller {
	
	public static enum CostBasisType {
		FIFO
	}
	private static final CSVFormat CSV_FORMAT = CSVFormat.EXCEL;
	private static final String CHARSET_UTF8 = "UTF-8";
	private static final int NUM_DECIMAL_PLACES_PRECISION = 24;
	private static final BigDecimal THRESHOLD_DECIMAL_EQUALING_ZERO = new BigDecimal(0.000000000000000000000001);
	
	
	public static void processCostBasis(String inputFileName, String outputFileNameCapGains, String outputFileNameIncome)
			throws ControllerException {
		
		if (inputFileName == null || outputFileNameCapGains == null || outputFileNameIncome == null) {
			throw new ControllerException("Input/output file name(s) are null");
		}
		
		File inputFile = new File(inputFileName);
		File folder = inputFile.getAbsoluteFile().getParentFile();
		if (!inputFile.exists() || !folder.exists()) {
			throw new ControllerException("Input file " + inputFileName + " or folder "+folder+" does not exist");
		}
		
		System.out.println("INFO: Initiating cost basis processing. Input file: "+inputFile.getAbsolutePath());
		
		List<Transaction> tList = readTransactions(inputFile);
		if (tList == null || tList.isEmpty()) {
			throw new ControllerException("Input file contained no transactions");
		}
		System.out.println("INFO: Read "+tList.size()+" transactions from input file");
		
		
		List<CapitalGainEntry> cgeList = computeCapitalGains(CostBasisType.FIFO, tList);
		if (cgeList == null) {
			throw new ControllerException("Capital Gain computation failed (returned null)");
		}
		System.out.println("INFO: Computed "+cgeList.size()+" capital gain entries");
		
		List<IncomeEntry> ieList = computeIncome(tList);
		if (ieList == null) {
			throw new ControllerException("Income computation failed (returned null)");
		}
		System.out.println("INFO: Computed "+ieList.size()+" income entries");
		
		File outputFileCapGains = new File(folder, outputFileNameCapGains);
		writeCapitalGainEntries(cgeList, outputFileCapGains);
		System.out.println("INFO: Wrote "+cgeList.size()+" capital gain entries to "+outputFileCapGains.getAbsolutePath());
		
		File outputFileIncome = new File(folder, outputFileNameIncome);
		writeIncomeEntries(ieList, outputFileIncome);
		System.out.println("INFO: Wrote "+ieList.size()+" income entries to "+outputFileIncome.getAbsolutePath());
		
	}
	
	
	private static List<Transaction> readTransactions(File inputFile) throws ControllerException {
		List<Transaction> result = new ArrayList<>();
		CSVParser parser = null;
		
		try {
			parser = CSVParser.parse(inputFile, Charset.forName(CHARSET_UTF8), CSV_FORMAT.withHeader().withSkipHeaderRecord(true));
		}
		catch (Exception exc) {
			throw new ControllerException(exc.getMessage());
		}
		
		List<Long> skippedInvalidRecordNums = new ArrayList<>();
		List<Long> skippedEmptyRecordNums = new ArrayList<>();
		for (CSVRecord r : parser) {
			try {
				Transaction t = new Transaction(r);
				result.add(t);
			}
			catch(TransactionException exc) {
				TransactionExceptionType excType = exc.getType();
				if (excType == TransactionExceptionType.INVALID_DATA) {
					skippedInvalidRecordNums.add(r.getRecordNumber());
					System.err.println("ERROR: Encountered CSV record with invalid data - "+exc.getMessage());
				}
				else if (excType == TransactionExceptionType.EMPTY_DATA) {
					skippedEmptyRecordNums.add(r.getRecordNumber());
				}
			}
		}
		
		if (skippedInvalidRecordNums.size() > 0) {
			String skippedInvalidStr = "";
			for (Long l : skippedInvalidRecordNums) {
				skippedInvalidStr += (" " + l);
			}
			System.err.println("ERROR: Skipped "+skippedInvalidRecordNums.size()+" CSV record with invalid data #s:"+skippedInvalidStr);
		}
		if (skippedEmptyRecordNums.size() > 0) {
			String skippedEmptyStr = "";
			for (Long l : skippedEmptyRecordNums) {
				skippedEmptyStr += (" " + l);
			}
			System.out.println("INFO: Skipped "+skippedEmptyRecordNums.size()+" CSV record with empty data #s:"+skippedEmptyStr);
		}
		
		return result;
	}
	
	
	private static List<CapitalGainEntry> computeCapitalGains(CostBasisType cbType, List<Transaction> tList) throws ControllerException {
		if (tList == null) {
			return null;
		}
		if (cbType != CostBasisType.FIFO) {
			throw new ControllerException("Could not compute capital gains due to an unsupported cost basis type. Only FIFO is supported.");
		}
		
		List<CapitalGainEntry> result = new ArrayList<>();
		
		// Sort all transactions in chronological order
		TransactionComparator tc = new TransactionComparator();
		tList.sort(tc);
		
		// Enqueue the acquisition and income amounts and their cost basis
		// For each disposal pop the oldest events and compute capital gains
		List<TransactionMemento> tmQueue = new ArrayList<>();
		
		for (Transaction t : tList) {
			Transaction.TransactionType tType = t.getTxnType();
			
			if (tType == TransactionType.ACQUIRE || tType == TransactionType.INCOME || tType == TransactionType.MNG_INCOME) {
				TransactionMemento tm = new TransactionMemento();
				tm.dttm = t.getTxnDttm();
				tm.coinAmnt = t.getTxnCoinAmnt();
				
				if (t.getTxnUsdAmnt() != null) {
					BigDecimal costBasis = t.getTxnUsdAmnt();
					if (t.getTxnBrkrFeeUsd() != null) {
						costBasis = costBasis.add(t.getTxnBrkrFeeUsd());
					}
					tm.effUsdPerUnit = costBasis.divide(tm.coinAmnt, NUM_DECIMAL_PLACES_PRECISION, RoundingMode.HALF_UP);
				}
				else if (t.getTxnUsdPerUnit() != null) {
					BigDecimal txnUsdPerUnit = t.getTxnUsdPerUnit();
					if (t.getTxnBrkrFeeUsd() != null) {
						BigDecimal costBasis = tm.coinAmnt.multiply(txnUsdPerUnit);
						costBasis = costBasis.add(t.getTxnBrkrFeeUsd());
						tm.effUsdPerUnit = costBasis.divide(tm.coinAmnt, NUM_DECIMAL_PLACES_PRECISION, RoundingMode.HALF_UP);
					}
					else {
						tm.effUsdPerUnit = txnUsdPerUnit;
					}
				}
				else {
					// this should not happen, since validation occurred at Transaction creation
					throw new ControllerException("Encountered unexpected null data in txn USD or USD/unit in calculating capital gain acq/inc at Transaction dttm "
							+ t.getTxnDttm());
				}
				
				tmQueue.add(tm);
			}
			else if (tType == TransactionType.TRANSFER || tType == TransactionType.DISPOSE || tType == TransactionType.MNG_PURCHASE) {
				TransactionMemento dispTM = new TransactionMemento();
				dispTM.dttm = t.getTxnDttm();
				dispTM.coinAmnt = BigDecimal.ZERO;
				if (t.getTxnFeeCoin() != null) {
					dispTM.coinAmnt = dispTM.coinAmnt.add(t.getTxnFeeCoin());
				}
				if (tType == TransactionType.DISPOSE) {
					dispTM.coinAmnt = dispTM.coinAmnt.add(t.getTxnCoinAmnt());
				}
				else if (tType == TransactionType.MNG_PURCHASE && t.getTxnCoinAmnt() != null) {
					dispTM.coinAmnt = dispTM.coinAmnt.add(t.getTxnCoinAmnt());
				}
				
				// if it's a non-dispose zero-fee transaction, skip it
				if (dispTM.coinAmnt.compareTo(THRESHOLD_DECIMAL_EQUALING_ZERO) <= 0) {
					System.out.println("INFO: Skipping non-taxable Transaction dttm " + t.getTxnDttm());
					continue;
				}
				
				if (t.getTxnUsdAmnt() != null) {
					dispTM.effUsdPerUnit = t.getTxnUsdAmnt().divide(t.getTxnCoinAmnt(), NUM_DECIMAL_PLACES_PRECISION, RoundingMode.HALF_UP);
				}
				else if (t.getTxnUsdPerUnit() != null) {
					dispTM.effUsdPerUnit = t.getTxnUsdPerUnit();
				}
				else {
					// this should not happen, since validation occurred at Transaction creation
					throw new ControllerException("Encountered unexpected null data in txn USD or USD/unit in calculating capital gain tran/disp"
							+ t.getTxnDttm());
				}
				
				while (dispTM.coinAmnt.compareTo(THRESHOLD_DECIMAL_EQUALING_ZERO) > 0) {
					if (tmQueue.isEmpty() || tmQueue.get(0) == null) {
						// this should not happen, since we should not dispose of more coins than we acquired
						throw new ControllerException("Encountered less acquired coins than disposed coins at Transaction dttm "
								+ t.getTxnDttm());
					}
					
					TransactionMemento acqTM = tmQueue.get(0);
					BigDecimal minCoinAmnt = dispTM.coinAmnt.min(acqTM.coinAmnt);
					BigDecimal proceedsUsd = minCoinAmnt.multiply(dispTM.effUsdPerUnit);
					BigDecimal costBasisUsd = minCoinAmnt.multiply(acqTM.effUsdPerUnit);
					BigDecimal gainUsd = proceedsUsd.subtract(costBasisUsd);
					
					CapitalGainEntry cge = new CapitalGainEntry(acqTM.dttm.toLocalDate(), dispTM.dttm.toLocalDate(), minCoinAmnt,
							proceedsUsd, costBasisUsd, gainUsd);
					result.add(cge);
					
					dispTM.coinAmnt = dispTM.coinAmnt.subtract(minCoinAmnt);
					acqTM.coinAmnt = acqTM.coinAmnt.subtract(minCoinAmnt);
					
					if (acqTM.coinAmnt.compareTo(THRESHOLD_DECIMAL_EQUALING_ZERO) <= 0) {
						tmQueue.remove(0);
					}
				}
			}
						
		}
		
		return result;
	}
	
	
	private static List<IncomeEntry> computeIncome(List<Transaction> tList) throws ControllerException {
		if (tList == null) {
			return null;
		}

		List<IncomeEntry> result = new ArrayList<>();
		
		// Sort all transactions in chronological order
		TransactionComparator tc = new TransactionComparator();
		tList.sort(tc);
		
		String year = null;
		BigDecimal ordIncomeUsdSum = BigDecimal.ZERO;
		BigDecimal mngIncomeUsdSum = BigDecimal.ZERO;
		
		for (Transaction t : tList) {
			Transaction.TransactionType tType = t.getTxnType();
			if (tType == TransactionType.INCOME || tType == TransactionType.MNG_INCOME) {
				String tYear = t.getTxnYear();
				if (year == null) {
					// first income transaction
					year = tYear;
				}
				
				BigDecimal incomeUsd = t.getTxnUsdAmnt();
				if (incomeUsd == null) {
					incomeUsd = t.getTxnCoinAmnt().multiply(t.getTxnUsdPerUnit());
				}
				
				// same year
				if (year.equals(tYear)) {
					if (tType == TransactionType.INCOME) {
						ordIncomeUsdSum = ordIncomeUsdSum.add(incomeUsd);
					}
					else if (tType == TransactionType.MNG_INCOME) {
						mngIncomeUsdSum = mngIncomeUsdSum.add(incomeUsd);
					}
				}
				// new year
				else {
					IncomeEntry ie = new IncomeEntry(year, ordIncomeUsdSum, mngIncomeUsdSum, null);
					result.add(ie);
					
					year = tYear;
					ordIncomeUsdSum = BigDecimal.ZERO;
					mngIncomeUsdSum = BigDecimal.ZERO;
					if (tType == TransactionType.INCOME) {
						ordIncomeUsdSum = incomeUsd;
					}
					else if (tType == TransactionType.MNG_INCOME) {
						mngIncomeUsdSum = incomeUsd;
					}
				}
			}
		}
		if (ordIncomeUsdSum.compareTo(THRESHOLD_DECIMAL_EQUALING_ZERO) > 0 ||
				mngIncomeUsdSum.compareTo(THRESHOLD_DECIMAL_EQUALING_ZERO) > 0) {
			IncomeEntry ie = new IncomeEntry(year, ordIncomeUsdSum, mngIncomeUsdSum, null);
			result.add(ie);
		}
		
		return result;
	}
	
	private static void writeCapitalGainEntries(List<CapitalGainEntry> cgeList, File outputFile) throws ControllerException {
		if (cgeList == null || outputFile == null) {
			throw new ControllerException("Capital Gains entries or output file is null");
		}
		if (outputFile.exists()) {
			throw new ControllerException("Could not write to output file "+outputFile.getAbsolutePath()+" as it already exists");
		}
		
		try {
			CSVPrinter printer = new CSVPrinter(new FileWriter(outputFile), CSV_FORMAT);
			
			printer.printRecord(CapitalGainEntry.COL_TAX_YEAR, CapitalGainEntry.COL_TERM, CapitalGainEntry.COL_DATE_ACQ,
					CapitalGainEntry.COL_DATE_DISP, CapitalGainEntry.COL_ASSET_AMNT, CapitalGainEntry.COL_PROCEEDS,
					CapitalGainEntry.COL_COST_BASIS, CapitalGainEntry.COL_GAIN);
			
			for (CapitalGainEntry cge : cgeList) {
				printer.printRecord(cge.getTaxYearStr(), cge.getTermStr(), cge.getDateAcquiredStr(), cge.getDateDisposedStr(),
						cge.getAssetAmntStr(), cge.getProceedsStr(), cge.getCostBasisStr(), cge.getGainStr());
			}
			
			printer.close(true);
		}
		catch (IOException ioExc) {
			throw new ControllerException(ioExc.getMessage());
		}
	}
	
	
	private static void writeIncomeEntries(List<IncomeEntry> ieList, File outputFile) throws ControllerException {
		if (ieList == null || outputFile == null) {
			throw new ControllerException("Income entries or output file is null");
		}
		if (outputFile.exists()) {
			throw new ControllerException("Could not write to output file "+outputFile.getAbsolutePath()+" as it already exists");
		}
		
		try {
			CSVPrinter printer = new CSVPrinter(new FileWriter(outputFile), CSV_FORMAT);
			
			printer.printRecord(IncomeEntry.COL_TAX_YEAR, IncomeEntry.COL_ORD_INC_USD, IncomeEntry.COL_MNG_INC_USD,
					IncomeEntry.COL_MNG_EXP_USD);
			
			for (IncomeEntry ie : ieList) {
				printer.printRecord(ie.getTaxYear(), ie.getOrdIncomeStr(), ie.getMngIncomeStr(), ie.getMngExpenseStr());
			}
			
			printer.close(true);
		}
		catch (IOException ioExc) {
			throw new ControllerException(ioExc.getMessage());
		}
	}
	
	
}
