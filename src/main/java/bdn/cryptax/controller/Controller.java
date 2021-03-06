package bdn.cryptax.controller;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import bdn.cryptax.model.CapitalGainEntry;
import bdn.cryptax.model.CapitalGainEntry.CapitalGainTerm;
import bdn.cryptax.model.IncomeEntry;
import bdn.cryptax.model.MiningContract;
import bdn.cryptax.model.MiningContract.MiningContractType;
import bdn.cryptax.model.MiningEntry;
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
	private static final BigDecimal THRESHOLD_DECIMAL_EQUALING_ZERO = new BigDecimal(0.000000000000000000000001);
	private static final MathContext PRECISION = new MathContext(34, RoundingMode.HALF_UP);
	
	
	public static void process(String inputFileName, String outputFileNameCapGains, String outputFileNameIncome,
			String outFileNameMining) throws ControllerException {
		
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
		
		List<IncomeEntry> ieList = computeIncomeAndExpenses(tList, cgeList);
		if (ieList == null) {
			throw new ControllerException("Income computation failed (returned null)");
		}
		System.out.println("INFO: Computed "+ieList.size()+" income entries");
		
		List<MiningEntry> meList = computeMining(tList);
		if (meList == null) {
			throw new ControllerException("Mining computation failed (returned null)");
		}
		System.out.println("INFO: Computed "+meList.size()+" mining entries");
		
		File outputFileCapGains = new File(folder, outputFileNameCapGains);
		writeCapitalGainEntries(cgeList, outputFileCapGains);
		System.out.println("INFO: Wrote "+cgeList.size()+" capital gain entries to "+outputFileCapGains.getAbsolutePath());
		
		File outputFileIncome = new File(folder, outputFileNameIncome);
		writeIncomeEntries(ieList, outputFileIncome);
		System.out.println("INFO: Wrote "+ieList.size()+" income entries to "+outputFileIncome.getAbsolutePath());
		
		File outputFileMining = new File(folder, outFileNameMining);
		writeMiningEntries(meList, outputFileMining);
		System.out.println("INFO: Wrote "+meList.size()+" mining entries to "+outputFileMining.getAbsolutePath());
		
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
					tm.effUsdPerUnit = costBasis.divide(tm.coinAmnt, PRECISION);
				}
				else if (t.getTxnUsdPerUnit() != null) {
					BigDecimal txnUsdPerUnit = t.getTxnUsdPerUnit();
					if (t.getTxnBrkrFeeUsd() != null) {
						BigDecimal costBasis = tm.coinAmnt.multiply(txnUsdPerUnit);
						costBasis = costBasis.add(t.getTxnBrkrFeeUsd());
						tm.effUsdPerUnit = costBasis.divide(tm.coinAmnt, PRECISION);
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
			else if (tType == TransactionType.TRANSFER || tType == TransactionType.DISPOSE || tType == TransactionType.MNG_PURCHASE ||
					tType == TransactionType.MNG_REINVEST) {
				TransactionMemento dispTM = new TransactionMemento();
				dispTM.dttm = t.getTxnDttm();
				dispTM.coinAmnt = BigDecimal.ZERO;
				if (t.getTxnFeeCoin() != null) {
					dispTM.coinAmnt = dispTM.coinAmnt.add(t.getTxnFeeCoin());
				}
				if (tType == TransactionType.DISPOSE) {
					dispTM.coinAmnt = dispTM.coinAmnt.add(t.getTxnCoinAmnt());
				}
				else if ((tType == TransactionType.MNG_PURCHASE || tType == TransactionType.MNG_REINVEST) && t.getTxnCoinAmnt() != null) {
					dispTM.coinAmnt = dispTM.coinAmnt.add(t.getTxnCoinAmnt());
				}
				
				// if it's a non-dispose zero-fee transaction, skip it
				if (dispTM.coinAmnt.compareTo(THRESHOLD_DECIMAL_EQUALING_ZERO) <= 0) {
					System.out.println("INFO: Skipping non-taxable Transaction dttm " + t.getTxnDttm());
					continue;
				}
				
				if (t.getTxnUsdAmnt() != null) {
					dispTM.effUsdPerUnit = t.getTxnUsdAmnt().divide(t.getTxnCoinAmnt(), PRECISION);
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
	
	
	private static List<IncomeEntry> computeIncomeAndExpenses(List<Transaction> tList, List<CapitalGainEntry> cgeList)
			throws ControllerException {
		
		if (tList == null || cgeList == null) {
			return null;
		}

		List<IncomeEntry> result = new ArrayList<>();
		
		
		// First take care of computing capital gain totals for each year
		HashMap<Integer, BigDecimal> yearToShortTermCapGain = new HashMap<>();
		HashMap<Integer, BigDecimal> yearToLongTermCapGain = new HashMap<>();
		int earliestCapGainYear = 0;
		for (CapitalGainEntry cge : cgeList) {
			int year = cge.getTaxYearInt();
			if (earliestCapGainYear == 0 || year < earliestCapGainYear) {
				earliestCapGainYear = year;
			}
			
			HashMap<Integer, BigDecimal> capGainMap = null;
			CapitalGainTerm term = cge.getTerm();
			if (term == CapitalGainTerm.SHORTTERM) {
				capGainMap = yearToShortTermCapGain;
			}
			else if (term == CapitalGainTerm.LONGTERM) {
				capGainMap = yearToLongTermCapGain;
			}
			if (capGainMap != null) {
				BigDecimal currCapGainInMap = capGainMap.get(year);
				if (currCapGainInMap == null) {
					currCapGainInMap = BigDecimal.ZERO;
				}
				BigDecimal gain = cge.getGain();
				if (gain != null) {
					currCapGainInMap = currCapGainInMap.add(gain);
					capGainMap.put(year, currCapGainInMap);
				}
			}
		}
		
		
		// Sort all transactions in chronological order
		TransactionComparator tc = new TransactionComparator();
		tList.sort(tc);
		
		int year = 0;
		BigDecimal ordIncomeUsdSum = BigDecimal.ZERO;
		BigDecimal mngIncomeUsdSum = BigDecimal.ZERO;
		BigDecimal mngExpenseUsdSum = BigDecimal.ZERO;
		HashMap<Integer, BigDecimal> mngAmortExpenseUsdMap = new HashMap<>();
		
		for (Transaction t : tList) {
			try {
				Transaction.TransactionType tType = t.getTxnType();
				if (tType == TransactionType.INCOME || tType == TransactionType.MNG_INCOME || tType == TransactionType.MNG_PURCHASE ||
						tType == TransactionType.MNG_REINVEST) {
					int tYear = t.getTxnYearInt();
					if (year == 0) {
						// first income transaction
						year = tYear;
						
						// if there were earlier years with capital gains, enter them first (this is not expected to happen!)
						while (earliestCapGainYear < year) {
							BigDecimal shortTermCapGain = yearToShortTermCapGain.get(earliestCapGainYear);
							BigDecimal longTermCapGain = yearToLongTermCapGain.get(earliestCapGainYear);
							IncomeEntry ie = new IncomeEntry(String.valueOf(earliestCapGainYear), null, shortTermCapGain,
									longTermCapGain, null, null, null);
							result.add(ie);
							earliestCapGainYear++;
						}
					}
					
					BigDecimal tUsdAmnt = t.getCalculatedTxnUsdAmnt();
					
					// same year
					if (year == tYear) {
						if (tType == TransactionType.INCOME) {
							ordIncomeUsdSum = ordIncomeUsdSum.add(tUsdAmnt);
						}
						else if (tType == TransactionType.MNG_INCOME) {
							mngIncomeUsdSum = mngIncomeUsdSum.add(tUsdAmnt);
						}
						else if (tType == TransactionType.MNG_PURCHASE || tType == TransactionType.MNG_REINVEST) {
							mngExpenseUsdSum = mngExpenseUsdSum.add(tUsdAmnt);
							amortizeExpenses(mngAmortExpenseUsdMap, t.getTxnDttm().toLocalDate(), t.getTermMos(), tUsdAmnt);
						}
					}
					// new year
					else {
						BigDecimal amortExpense = mngAmortExpenseUsdMap.get(year);
						if (amortExpense == null) {
							amortExpense = BigDecimal.ZERO;
						}
						BigDecimal shortTermCapGain = yearToShortTermCapGain.get(year);
						BigDecimal longTermCapGain = yearToLongTermCapGain.get(year);
						
						IncomeEntry ie = new IncomeEntry(String.valueOf(year), ordIncomeUsdSum, shortTermCapGain,
								longTermCapGain, mngIncomeUsdSum, mngExpenseUsdSum, amortExpense);
						result.add(ie);
	
						if (year > tYear) {
							// should not happen since we sorted transactions by dttm, but a defensive step to avoid an infinite loop
							throw new ControllerException("Encountered an out of order (earlier) Transaction dttm " + t.getTxnDttm());
						}
						
						year++;
						ordIncomeUsdSum = BigDecimal.ZERO;
						mngIncomeUsdSum = BigDecimal.ZERO;
						mngExpenseUsdSum = BigDecimal.ZERO;
						
						while (year < tYear) {
							amortExpense = mngAmortExpenseUsdMap.get(year);
							if (amortExpense == null) {
								amortExpense = BigDecimal.ZERO;
							}
							shortTermCapGain = yearToShortTermCapGain.get(year);
							longTermCapGain = yearToLongTermCapGain.get(year);
							
							ie = new IncomeEntry(String.valueOf(year), ordIncomeUsdSum, shortTermCapGain, longTermCapGain, mngIncomeUsdSum,
									mngExpenseUsdSum, amortExpense);
							result.add(ie);
							year++;
						}
						
						if (tType == TransactionType.INCOME) {
							ordIncomeUsdSum = tUsdAmnt;
						}
						else if (tType == TransactionType.MNG_INCOME) {
							mngIncomeUsdSum = tUsdAmnt;
						}
						else if (tType == TransactionType.MNG_PURCHASE || tType == TransactionType.MNG_REINVEST) {
							mngExpenseUsdSum = tUsdAmnt;
							amortizeExpenses(mngAmortExpenseUsdMap, t.getTxnDttm().toLocalDate(), t.getTermMos(), tUsdAmnt);
						}
					}
				}
			}
			catch (TransactionException tExc) {
				throw new ControllerException("Income and expenses compute failed due to Transaction error: " + tExc.getMessage());
			}
		}
		while (ordIncomeUsdSum.compareTo(THRESHOLD_DECIMAL_EQUALING_ZERO) > 0 ||
				mngIncomeUsdSum.compareTo(THRESHOLD_DECIMAL_EQUALING_ZERO) > 0 || mngAmortExpenseUsdMap.get(year) != null) {
			
			BigDecimal amortExpense = mngAmortExpenseUsdMap.get(year);
			if (amortExpense == null) {
				amortExpense = BigDecimal.ZERO;
			}
			BigDecimal shortTermCapGain = yearToShortTermCapGain.get(year);
			BigDecimal longTermCapGain = yearToLongTermCapGain.get(year);
			
			IncomeEntry ie = new IncomeEntry(String.valueOf(year), ordIncomeUsdSum, shortTermCapGain, longTermCapGain, mngIncomeUsdSum, 
					mngExpenseUsdSum, amortExpense);
			result.add(ie);
			
			year++;
			ordIncomeUsdSum = BigDecimal.ZERO;
			mngIncomeUsdSum = BigDecimal.ZERO;
			mngExpenseUsdSum = BigDecimal.ZERO;
		}
		
		return result;
	}
	
	
	private static void amortizeExpenses(HashMap<Integer, BigDecimal> yearToExpenseMap, LocalDate startDate, Long lengthMos,
			BigDecimal totalExpense) {
		
		if (yearToExpenseMap == null || startDate == null || lengthMos == null || totalExpense == null) {
			System.err.println("ERROR: Could not amortize expenses because passed parameter(s) were null");
			return;
		}
		
		LocalDate endDate = startDate.plusMonths(lengthMos.longValue());
		long totalAmortPeriod = ChronoUnit.DAYS.between(startDate, endDate);
		BigDecimal totalAmortPeriodBD = new BigDecimal(totalAmortPeriod);
		
		LocalDate t1 = startDate;
		long remAmortPeriod = totalAmortPeriod;
		LocalDate t2 = t1.plusDays(1).with(TemporalAdjusters.lastDayOfYear());
		if (endDate.isBefore(t2)) {
			t2 = endDate;
		}
		
		while (remAmortPeriod > 0 && t1.isBefore(t2)) {
			int year = t2.getYear();
			long amortSegment = ChronoUnit.DAYS.between(t1, t2);
			BigDecimal expenseInYear = totalExpense.multiply(new BigDecimal(amortSegment)).divide(totalAmortPeriodBD, PRECISION);
			
			BigDecimal mapValue = yearToExpenseMap.get(year);
			if (mapValue != null) {
				expenseInYear = expenseInYear.add(mapValue);
			}
			yearToExpenseMap.put(year, expenseInYear);
			
			remAmortPeriod = remAmortPeriod - amortSegment;
			t1 = t2;
			t2 = t2.plusYears(1);
			if (endDate.isBefore(t2)) {
				t2 = endDate;
			}
		}
	}
	
	
	private static List<MiningEntry> computeMining(List<Transaction> tList) throws ControllerException {
		if (tList == null) {
			return null;
		}

		List<MiningEntry> result = new ArrayList<>();
		
		// Sort all transactions in chronological order
		TransactionComparator tc = new TransactionComparator();
		tList.sort(tc);
		
		// Read in all the purchase and reinvestment contracts
		List<MiningContract> mcList = new ArrayList<>();
		LocalDate miningStartDate = null;
		LocalDate miningEndDate = null;
		HashMap<LocalDate, BigDecimal> dateToIncomeUsdMap = new HashMap<>();
		HashMap<LocalDate, Long> dateToHashrateMap = new HashMap<>();
		HashMap<LocalDate, BigDecimal> dateToIncomeCoinMap = new HashMap<>();
		
		for (Transaction t : tList) {
			try {
				TransactionType tType = t.getTxnType();
				if (tType == TransactionType.MNG_PURCHASE || tType == TransactionType.MNG_REINVEST) {
					LocalDate tDate = t.getTxnDttm().toLocalDate();
					LocalDate effectiveDate = tDate.plusDays(1);
					LocalDate endDate = tDate.plusMonths(t.getTermMos());
					
					BigDecimal totalAmountUsd = t.getCalculatedTxnUsdAmnt();
					long contractDays = ChronoUnit.DAYS.between(tDate, endDate);
					BigDecimal perDayAmountUsd = totalAmountUsd.divide(new BigDecimal(contractDays), PRECISION);
					
					MiningContractType mcType = MiningContractType.PURCHASE;
					if (tType == TransactionType.MNG_REINVEST) {
						mcType = MiningContractType.REINVESTMENT;
					}
					MiningContract mc = new MiningContract(mcType, tDate, effectiveDate, endDate, totalAmountUsd, perDayAmountUsd);
					mcList.add(mc);
					
					if (miningStartDate == null && tType == TransactionType.MNG_PURCHASE) {
						miningStartDate = tDate;
					}
					if (miningEndDate == null || miningEndDate.isBefore(endDate)) {
						miningEndDate = endDate;
					}
				}
				else if (tType == TransactionType.MNG_INCOME) {
					LocalDate tDate = t.getTxnDttm().toLocalDate();
					BigDecimal tUsdAmount = t.getCalculatedTxnUsdAmnt();
					Long tHashrate = t.getTxnHashrate();
					BigDecimal tCoinAmount = t.getTxnCoinAmnt();
					
					BigDecimal amountUsdInMap = dateToIncomeUsdMap.get(tDate);
					Long hashrateInMap = dateToHashrateMap.get(tDate);
					BigDecimal amountCoinInMap = dateToIncomeCoinMap.get(tDate);
					
					if (amountUsdInMap == null || hashrateInMap == null || amountCoinInMap == null) {
						dateToIncomeUsdMap.put(tDate, tUsdAmount);
						dateToHashrateMap.put(tDate, tHashrate);
						dateToIncomeCoinMap.put(tDate, tCoinAmount);
					}
					else {
						dateToIncomeUsdMap.put(tDate, amountUsdInMap.add(tUsdAmount));
						dateToHashrateMap.put(tDate, Long.sum(hashrateInMap, tHashrate));
						dateToIncomeCoinMap.put(tDate, amountCoinInMap.add(tCoinAmount));
					}
				}
			}
			catch (TransactionException tExc) {
				throw new ControllerException("Mining compute failed due to Transaction error: " + tExc.getMessage());
			}
		}
		
		
		// Iterate through each day and calculate the day's statistics into MiningEntries
		if (miningStartDate != null && miningEndDate != null && miningStartDate.isBefore(miningEndDate)) {
			LocalDate thisDate = miningStartDate;
			
			// cumulative values
			BigDecimal cumBasisPurchase = null;
			BigDecimal cumIncome = null;
			
			// Day rate sum - used for calculating running average of day rates (note: day rate != ri below)
			BigDecimal dayRatePurchaseAndReinvestWeightedSum = BigDecimal.ZERO;
			BigDecimal dayRatePurchaseAndReinvestSumOfWeights = BigDecimal.ZERO;
			
			while (thisDate.isBefore(miningEndDate) || thisDate.isEqual(miningEndDate)) {
				// purchase contracts only purchased this day (in USD)
				BigDecimal purchase = null;
				// reinvestment contracts done this day (in USD)
				BigDecimal reinvestment = null;
				// sum of contract total values for contracts that are active this day (both purchased and reinvested)
				BigDecimal sumContractsPurchaseAndReinvest = null;
				// for this day, allocation of all purchased contracts for this day (excl. reinvestment contracts)
				BigDecimal basisPurchase = null;
				// for this day, allocation of all purchased+reinvestment contracts for this day
				BigDecimal basisPurchaseAndReinvest = null;
				// mining income for this day from all contracts
				BigDecimal income = null;
				// combined earning hash rate (GH/s) for this day
				Long hashrate = null;
				// USD/Coin price
				BigDecimal usdPerCoin = null;
				// yield in COIN (e.g., BTC) per EH/s - shows mining profitability for the day
				BigDecimal yield = null;
				// day's simple rate of return on this day's return against this day's basisPurchaseAndReinvest
				BigDecimal dayRatePurchaseAndReinvest = null;
				// avg daily simple rate of return seen so far
				BigDecimal avgDayRatePurchaseAndReinvest = null;
				// day's simple rate of return on this day's return against this day's basisPurchase
				// Note: this rate will grow over time with reinvestment, so the latest value is more useful than average
				BigDecimal dayRatePurchase = null;

				
				// determine the purchase / reinvestment basis for this day
				// determine the sum total of active contracts during this day
				for (MiningContract mc : mcList) {
					MiningContractType mcType = mc.getType();
					LocalDate acqDate = mc.getAcquisitionDate();
					if (acqDate.isEqual(thisDate)) {
						if (mcType == MiningContractType.PURCHASE) {
							purchase = mc.getTotalAmountUsd();
						}
						else if (mcType == MiningContractType.REINVESTMENT) {
							reinvestment = mc.getTotalAmountUsd();
						}
					}
					LocalDate mcStartBoundary = mc.getStartDate().minusDays(1);
					LocalDate mcEndBoundary = mc.getEndDate().plusDays(1);
					// if thisDate falls within the contract (inclusive), add to per-day statistics
					if (thisDate.isAfter(mcStartBoundary) && thisDate.isBefore(mcEndBoundary)) {
						if (basisPurchaseAndReinvest == null) {
							basisPurchaseAndReinvest = BigDecimal.ZERO;
						}
						basisPurchaseAndReinvest = basisPurchaseAndReinvest.add(mc.getPerDayAmountUsd());
						if (mcType == MiningContractType.PURCHASE) {
							if (basisPurchase == null) {
								basisPurchase = BigDecimal.ZERO;
							}
							basisPurchase = basisPurchase.add(mc.getPerDayAmountUsd());
						}
						
						if (sumContractsPurchaseAndReinvest == null) {
							sumContractsPurchaseAndReinvest = BigDecimal.ZERO;
						}
						sumContractsPurchaseAndReinvest = sumContractsPurchaseAndReinvest.add(mc.getTotalAmountUsd());
					}
				}
				if (basisPurchase != null) {
					if (cumBasisPurchase == null) {
						cumBasisPurchase = BigDecimal.ZERO;
					}
					cumBasisPurchase = cumBasisPurchase.add(basisPurchase);
				}
				
				// determine the income, hashrate, yield, and APR/APY statistics for this day
				income = dateToIncomeUsdMap.get(thisDate);
				if (income != null) {
					if (cumIncome == null) {
						cumIncome = BigDecimal.ZERO;
					}
					cumIncome = cumIncome.add(income);
				}
				hashrate = dateToHashrateMap.get(thisDate);
				
				BigDecimal incomeCoin = dateToIncomeCoinMap.get(thisDate);
				if (incomeCoin != null && hashrate != null) {
					// convert from GH/s to EH/s (a factor of 1000000000)
					yield = incomeCoin.multiply(new BigDecimal(1000000000)).divide(new BigDecimal(hashrate), PRECISION);
				}
				if (income != null && incomeCoin != null) {
					usdPerCoin = income.divide(incomeCoin, PRECISION);
				}
				
				// rates of return
				if (income != null && basisPurchaseAndReinvest != null && sumContractsPurchaseAndReinvest != null) {
					// Day Rate (Purchase & Reinvestment based)
					dayRatePurchaseAndReinvest = income.divide(basisPurchaseAndReinvest, PRECISION).subtract(BigDecimal.ONE);
					
					// Weighted Avg Day Rate (Purchase & Reinvestment based)
					dayRatePurchaseAndReinvestWeightedSum = dayRatePurchaseAndReinvestWeightedSum.add(
							dayRatePurchaseAndReinvest.multiply(income));
					dayRatePurchaseAndReinvestSumOfWeights = dayRatePurchaseAndReinvestSumOfWeights.add(income);
					avgDayRatePurchaseAndReinvest = dayRatePurchaseAndReinvestWeightedSum.divide(dayRatePurchaseAndReinvestSumOfWeights, PRECISION);
					
					// Day Rate (Purchase only based)
					dayRatePurchase = income.divide(basisPurchase, PRECISION).subtract(BigDecimal.ONE);
				}
				
				MiningEntry me = new MiningEntry(thisDate, purchase, reinvestment, basisPurchase, cumBasisPurchase, basisPurchaseAndReinvest,
						income, cumIncome, hashrate, usdPerCoin, yield, dayRatePurchaseAndReinvest, avgDayRatePurchaseAndReinvest, 
						dayRatePurchase);
				result.add(me);
				
				
				thisDate = thisDate.plusDays(1);
			}
		}
		else {
			System.out.println("INFO: Mining compute found no mining contracts");
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
			
			printer.printRecord(
					CapitalGainEntry.COL_TAX_YEAR,
					CapitalGainEntry.COL_TERM,
					CapitalGainEntry.COL_DATE_ACQ,
					CapitalGainEntry.COL_DATE_DISP,
					CapitalGainEntry.COL_ASSET_AMNT,
					CapitalGainEntry.COL_PROCEEDS,
					CapitalGainEntry.COL_COST_BASIS,
					CapitalGainEntry.COL_GAIN);
			
			for (CapitalGainEntry cge : cgeList) {
				printer.printRecord(
						cge.getTaxYearStr(),
						cge.getTermStr(),
						cge.getDateAcquiredStr(),
						cge.getDateDisposedStr(),
						cge.getAssetAmntStr(),
						cge.getProceedsStr(),
						cge.getCostBasisStr(),
						cge.getGainStr());
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
			
			printer.printRecord(
					IncomeEntry.COL_TAX_YEAR,
					IncomeEntry.COL_ORD_INC_USD,
					IncomeEntry.COL_CAPGAIN_SHORTTERM,
					IncomeEntry.COL_CAPGAIN_LONGTERM,
					IncomeEntry.COL_MNG_INC_USD,
					IncomeEntry.COL_MNG_EXP_USD,
					IncomeEntry.COL_MNG_AMORT_EXP_USD);
			
			for (IncomeEntry ie : ieList) {
				printer.printRecord(
						ie.getTaxYear(),
						ie.getOrdIncomeStr(),
						ie.getShortTermCapGainsStr(),
						ie.getLongTermCapGainsStr(),
						ie.getMngIncomeStr(),
						ie.getMngExpenseStr(),
						ie.getMngAmortExpenseStr());
			}
			
			printer.close(true);
		}
		catch (IOException ioExc) {
			throw new ControllerException(ioExc.getMessage());
		}
	}
	
	
	private static void writeMiningEntries(List<MiningEntry> meList, File outputFile) throws ControllerException {
		if (meList == null || outputFile == null) {
			throw new ControllerException("Mining entries or output file is null");
		}
		if (outputFile.exists()) {
			throw new ControllerException("Could not write to output file "+outputFile.getAbsolutePath()+" as it already exists");
		}
		
		try {
			CSVPrinter printer = new CSVPrinter(new FileWriter(outputFile), CSV_FORMAT);
			
			printer.printRecord(
					MiningEntry.COL_YEAR,
					MiningEntry.COL_DATE,
					MiningEntry.COL_PURCHASE_USD,
					MiningEntry.COL_REINVEST_USD,
					MiningEntry.COL_DAY_BASIS_P_USD,
					MiningEntry.COL_DAY_BASIS_PR_USD,
					MiningEntry.COL_DAY_INCOME_USD,
					MiningEntry.COL_DAY_HASH_RATE,
					MiningEntry.COL_USD_PER_COIN,
					MiningEntry.COL_DAY_MINING_YIELD,
					MiningEntry.COL_DAY_RATE_PR,
					MiningEntry.COL_AVG_DAY_RATE_PR,
					MiningEntry.COL_DAY_RATE_P
			);
			
			for (MiningEntry me : meList) {
				printer.printRecord(
						me.getYearStr(),
						me.getDateStr(),
						me.getPurchaseStr(),
						me.getReinvestmentStr(),
						me.getDayBasisPurchaseStr(),
						me.getDayBasisPurchaseAndReinvestStr(),
						me.getDayIncomeStr(),
						me.getHashrateStr(),
						me.getUsdPerCoinStr(),
						me.getYieldStr(),
						me.getDayRatePurchaseAndReinvestStr(),
						me.getAvgDayRatePurchaseAndReinvestStr(),
						me.getDayRatePurchaseStr()
				);
			}
			
			printer.close(true);
		}
		catch (IOException ioExc) {
			throw new ControllerException(ioExc.getMessage());
		}
	}
	
	
}
