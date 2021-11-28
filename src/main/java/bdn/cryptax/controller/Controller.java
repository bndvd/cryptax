package bdn.cryptax.controller;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FilenameUtils;

import bdn.cryptax.model.CapitalGainEntry;
import bdn.cryptax.model.GainEntry;
import bdn.cryptax.model.GainEntry.GainTerm;
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
import bdn.cryptax.model.UnrealizedCostBasisEntry;
import bdn.cryptax.model.UnrealizedGainEntry;

public class Controller {
	
	public static enum CostBasisType {
		FIFO
	}
	private static final CSVFormat CSV_FORMAT = CSVFormat.EXCEL;
	private static final String CHARSET_UTF8 = "UTF-8";
	private static final BigDecimal THRESHOLD_DECIMAL_EQUALING_ZERO = new BigDecimal("0.000000000000000000000001");
	private static final MathContext PRECISION = new MathContext(34, RoundingMode.HALF_UP);
	
	private static Set<String> USD_STABLECOINS = new HashSet<>();
	static {
		USD_STABLECOINS.add("USDC");
		USD_STABLECOINS.add("USDT");
		USD_STABLECOINS.add("BUSD");
		USD_STABLECOINS.add("DAI");
		USD_STABLECOINS.add("UST");
		USD_STABLECOINS.add("PAX");
		USD_STABLECOINS.add("HUSD");
		USD_STABLECOINS.add("TUSD");
		USD_STABLECOINS.add("GUSD");
	}
	

	public static void process(String inputFileName) throws ControllerException {
		if (inputFileName == null) {
			throw new ControllerException("Input/output file name inputs are null/insufficient");
		}
		
		File inputFile = new File(inputFileName);
		File folder = inputFile.getAbsoluteFile().getParentFile();
		if (!inputFile.exists() || !folder.exists()) {
			throw new ControllerException("Input file " + inputFileName + " or folder "+folder+" does not exist");
		}
		
		System.out.println("INFO: Initiating cost basis processing. Input file: "+inputFile.getAbsolutePath());
		
		Map<String, List<Transaction>> tListMap = readTransactions(inputFile);
		if (tListMap == null || tListMap.isEmpty()) {
			throw new ControllerException("Input file contained no transactions");
		}
		System.out.println("INFO: Read "+tListMap.size()+" transactions from input file");
		
		String[] accts = getAccounts(tListMap);
		// if no accounts were defined we'll add a default account with name ""; every transaction will belong to this account
		if (accts == null) {
			accts = new String[1];
			accts[0] = "";
		}
		
		Map<String, List<GainEntry>> geListMap = computeGains(CostBasisType.FIFO, tListMap);
		if (geListMap == null) {
			throw new ControllerException("Gains computation failed (returned null)");
		}
		// count entries
		int geCount = 0;
		for (String acct : geListMap.keySet()) {
			List<GainEntry> geList = geListMap.get(acct);
			if (geList != null) {
				geCount += geList.size();
			}
		}
		System.out.println("INFO: Computed "+geCount+" gain entries");
		
		UnrealizedCostBasisEntry ucbe = computeUnrealizedCostBasis(geListMap);
		if (ucbe == null) {
			throw new ControllerException("Unrealized cost basis computation failed (returned null)");
		}
		
		
		List<IncomeEntry> ieList = computeIncomeAndExpenses(tListMap, accts, geListMap);
		if (ieList == null) {
			throw new ControllerException("Income computation failed (returned null)");
		}
		System.out.println("INFO: Computed "+ieList.size()+" income entries");
		
		
		Map<String, List<MiningEntry>> meListMap = computeMining(tListMap);
		if (meListMap == null) {
			throw new ControllerException("Mining computation failed (returned null)");
		}
		int meCount = 0;
		for (String acct : meListMap.keySet()) {
			List<MiningEntry> meList = meListMap.get(acct);
			if (meList != null) {
				meCount += meList.size();
			}
		}
		System.out.println("INFO: Computed "+meCount+" mining entries");
		
		
		
		String fileBaseName = FilenameUtils.getBaseName(inputFileName);
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
		String now = LocalDateTime.now().format(dtf);

		Set<String> geAcctSet = geListMap.keySet();
		for (String acct : geAcctSet) {
			List<GainEntry> geList = geListMap.get(acct);
			if (geList == null || geList.isEmpty()) {
				System.err.println("ERROR: Skipping writing gains for account "+acct+" because ge list was null/empty");
				continue;
			}
			if (USD_STABLECOINS.contains(acct)) {
				System.out.println("INFO: Skipping writing gains for account "+acct+" because acct considered USD-STABLECOIN");
				continue;
			}
			
			String outFileNameCostBasis = fileBaseName + "_cb_" + (acct.equals("") ? "" : acct + "_") + now + ".csv";
			File outputFileCostBasis = new File(folder, outFileNameCostBasis);
			writeGainEntries(geList, outputFileCostBasis);
			System.out.println("INFO: Wrote "+geList.size()+" gains entries to "+outputFileCostBasis.getAbsolutePath());
		}
		
		
		String outFileNameUnrealizedCostBasis = fileBaseName + "_ucb_" + now + ".csv";
		File outFileUnrealizedCostBasis = new File(folder, outFileNameUnrealizedCostBasis);
		writeUnrealizedCostBasis(ucbe, outFileUnrealizedCostBasis);
		System.out.println("INFO: Wrote unrealized cost basis to "+outFileUnrealizedCostBasis.getAbsolutePath());
		
		String outFileNameIncome = fileBaseName + "_inc_" + now + ".csv";
		File outputFileIncome = new File(folder, outFileNameIncome);
		writeIncomeEntries(ieList, outputFileIncome);
		System.out.println("INFO: Wrote "+ieList.size()+" income entries to "+outputFileIncome.getAbsolutePath());
		
		Set<String> meAcctSet = meListMap.keySet();
		for (String acct : meAcctSet) {
			List<MiningEntry> meList = meListMap.get(acct);
			if (meList == null || meList.isEmpty()) {
				System.err.println("ERROR: Skipping writing mining for account "+acct+" because me list was null/empty");
				continue;
			}
			
			String outFileNameMining = fileBaseName + "_min_" + (acct.equals("") ? "" : acct + "_") + now + ".csv";
			File outputFileMining = new File(folder, outFileNameMining);
			writeMiningEntries(meList, outputFileMining);
			System.out.println("INFO: Wrote "+meList.size()+" mining entries to "+outputFileMining.getAbsolutePath());
		}
		
	}
	
	
	private static Map<String, List<Transaction>> readTransactions(File inputFile) throws ControllerException {
		Map<String, List<Transaction>> result = new HashMap<>();
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
				String acct = t.getTxnAcct();
				if (acct == null) {
					// account "" is the default account, if one is not defined
					acct = "";
				}
				acct = acct.trim();
				
				List<Transaction> tList = result.get(acct);
				if (tList == null) {
					tList = new ArrayList<>();
					result.put(acct, tList);
				}
				
				tList.add(t);
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
	
	
	private static String[] getAccounts(Map<String, List<Transaction>> tListMap) {
		if (tListMap == null || tListMap.isEmpty()) {
			return null;
		}
		
		Set<String> acctSet = tListMap.keySet();
		String[] result = new String[acctSet.size()];
		int index = 0;
		for (String a : acctSet) {
			result[index] = a;
			index++;
		}
		Arrays.sort(result, Comparator.naturalOrder());
		
		return result;
	}
	
	
	private static Map<String, List<GainEntry>> computeGains(CostBasisType cbType, Map<String, List<Transaction>> tListMap)
			throws ControllerException {
		if (tListMap == null || tListMap.isEmpty()) {
			return null;
		}
		if (cbType != CostBasisType.FIFO) {
			throw new ControllerException("Could not compute gains due to an unsupported cost basis type. Only FIFO is supported.");
		}
		
		Map<String, List<GainEntry>> result = new HashMap<>();
		
		Set<String> acctSet = tListMap.keySet();
		for (String acct : acctSet) {
			List<Transaction> tList = tListMap.get(acct);
			if (tList == null || tList.isEmpty()) {
				continue;
			}
			
			List<GainEntry> geList = new ArrayList<>();
			
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
					tm.src = t.getTxnSrc();
					tm.dest = t.getTxnDest();
					
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
						throw new ControllerException("Encountered unexpected null data in txn USD or USD/unit in calculating gains acq/inc at Transaction dttm "
								+ t.getTxnDttm());
					}
					
					tmQueue.add(tm);
				}
				else if (tType == TransactionType.TRANSFER || tType == TransactionType.DISPOSE || tType == TransactionType.MNG_PURCHASE ||
						tType == TransactionType.MNG_REINVEST) {
					TransactionMemento dispTM = new TransactionMemento();
					dispTM.dttm = t.getTxnDttm();
					dispTM.coinAmnt = BigDecimal.ZERO;
					dispTM.src = t.getTxnSrc();
					dispTM.dest = t.getTxnDest();
					
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
						throw new ControllerException("Encountered unexpected null data in txn USD or USD/unit in calculating gains tran/disp"
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
						
						// if non-zero gain/loss, then add (some "pass-through" transactions may yield zero cap gain, ignore them)
						if (gainUsd.abs().compareTo(THRESHOLD_DECIMAL_EQUALING_ZERO) > 0) {
							GainEntry ge = new CapitalGainEntry(acqTM.dttm.toLocalDate(), dispTM.dttm.toLocalDate(), 
									acqTM.dest, dispTM.src, minCoinAmnt, proceedsUsd, costBasisUsd, gainUsd);
							geList.add(ge);
						}
						
						dispTM.coinAmnt = dispTM.coinAmnt.subtract(minCoinAmnt);
						acqTM.coinAmnt = acqTM.coinAmnt.subtract(minCoinAmnt);
						
						if (acqTM.coinAmnt.compareTo(THRESHOLD_DECIMAL_EQUALING_ZERO) <= 0) {
							tmQueue.remove(0);
						}
					}
				}
			}
			
			// add any undisposed (unsold) assets as unrealized gain entries
			for (TransactionMemento tm : tmQueue) {
				BigDecimal costBasis = tm.coinAmnt.multiply(tm.effUsdPerUnit);
				UnrealizedGainEntry uge = new UnrealizedGainEntry(tm.dttm.toLocalDate(), tm.src, tm.coinAmnt, costBasis);
				geList.add(uge);
			}
			
			
			result.put(acct, geList);
		}
		
		return result;
	}
	

	private static UnrealizedCostBasisEntry computeUnrealizedCostBasis(Map<String, List<GainEntry>> geListMap) throws ControllerException {
		if (geListMap == null) {
			return null;
		}
		
		Set<String> acctSet = geListMap.keySet();
		String[] accts = new String[acctSet.size()];
		Map<String, BigDecimal> shortTermCostBasis = new HashMap<>();
		Map<String, BigDecimal> longTermCostBasis = new HashMap<>();
		Map<String, BigDecimal> avgCostBasis = new HashMap<>();
		
		LocalDate now = LocalDate.now();
		
		int i = 0;
		for (String acct : acctSet) {
			List<GainEntry> geList = geListMap.get(acct);
			if (geList == null || geList.isEmpty()) {
				continue;
			}
			
			BigDecimal shortTermCB = null;
			BigDecimal longTermCB = null;
			BigDecimal assetAmnt = null;
			
			for (GainEntry ge : geList) {
				if (ge instanceof UnrealizedGainEntry) {
					LocalDate dateAcq = ge.getDateAcquired();
					GainTerm term = GainEntry.getTerm(dateAcq, now);
					if (term == GainTerm.SHORTTERM) {
						if (shortTermCB == null) {
							shortTermCB = BigDecimal.ZERO;
						}
						shortTermCB = shortTermCB.add(ge.getCostBasis());
					}
					else if (term == GainTerm.LONGTERM) {
						if (longTermCB == null) {
							longTermCB = BigDecimal.ZERO;
						}
						longTermCB = longTermCB.add(ge.getCostBasis());
					}
					else {
						throw new ControllerException("Controller::computeUnrealizedCostBasis encountered unrealized gain entry whose term was UNKNOWN");
					}
					
					if (assetAmnt == null) {
						assetAmnt = BigDecimal.ZERO;
					}
					assetAmnt = assetAmnt.add(ge.getAssetAmnt());
				}
			}
			shortTermCostBasis.put(acct, shortTermCB);
			longTermCostBasis.put(acct, longTermCB);
			
			// calculate overall average cost basis (avg cost per asset unit)
			BigDecimal totalCostBasis = null;
			BigDecimal avgCB = null;
			if ((shortTermCB != null || longTermCB != null) && assetAmnt != null && assetAmnt.compareTo(BigDecimal.ZERO) != 0) {
				totalCostBasis = BigDecimal.ZERO;
				if (shortTermCB != null) {
					totalCostBasis = totalCostBasis.add(shortTermCB);
				}
				if (longTermCB != null) {
					totalCostBasis = totalCostBasis.add(longTermCB);
				}
				avgCB = totalCostBasis.divide(assetAmnt, PRECISION);
			}
			avgCostBasis.put(acct, avgCB);
			
			accts[i] = acct;
			i++;
		}
		
		UnrealizedCostBasisEntry result = new UnrealizedCostBasisEntry(accts, shortTermCostBasis, longTermCostBasis, avgCostBasis);
		
		return result;
	}
	
	
	private static List<IncomeEntry> computeIncomeAndExpenses(Map<String, List<Transaction>> tListMap, String[] accts, 
			Map<String, List<GainEntry>> geListMap) throws ControllerException {
		
		if (tListMap == null || accts == null || geListMap == null) {
			return null;
		}

		List<IncomeEntry> result = new ArrayList<>();
		
		
		// First take care of computing capital gain totals for each year (for each account)
		Map<Integer, Map<String, BigDecimal>> yearToAcctToShortTermCapGain = new HashMap<>();
		Map<Integer, Map<String, BigDecimal>> yearToAcctToLongTermCapGain = new HashMap<>();
		Set<String> acctSet = geListMap.keySet();
		int earliestCapGainYear = 0;
		for (String acct : acctSet) {
			List<GainEntry> geList = geListMap.get(acct);
			
			for (GainEntry ge : geList) {
				// only process capital gain entries (ignore unrealized income cost basis items)
				if (ge instanceof CapitalGainEntry) {
					int year = ge.getTaxYearInt();
					if (earliestCapGainYear == 0 || year < earliestCapGainYear) {
						earliestCapGainYear = year;
					}
					
					Map<String, BigDecimal> capGainMap = null;
					GainTerm term = ge.getTerm();
					if (term == GainTerm.SHORTTERM) {
						capGainMap = yearToAcctToShortTermCapGain.get(year);
						if (capGainMap == null) {
							capGainMap = new HashMap<>();
							yearToAcctToShortTermCapGain.put(year, capGainMap);
						}
					}
					else if (term == GainTerm.LONGTERM) {
						capGainMap = yearToAcctToLongTermCapGain.get(year);
						if (capGainMap == null) {
							capGainMap = new HashMap<>();
							yearToAcctToLongTermCapGain.put(year, capGainMap);
						}
					}
					// capGainMap should not be null, unless the term is not short-term or long-term
					if (capGainMap != null) {
						BigDecimal currCapGainInMap = capGainMap.get(acct);
						if (currCapGainInMap == null) {
							currCapGainInMap = BigDecimal.ZERO;
						}
						BigDecimal gain = ge.getGain();
						if (gain != null) {
							currCapGainInMap = currCapGainInMap.add(gain);
							capGainMap.put(acct, currCapGainInMap);
						}
					}
				}
			}
		}
		
		
		// Combine and sort all transactions in chronological order
		List<Transaction> tList = new ArrayList<>();
		acctSet = tListMap.keySet();
		for (String acct : acctSet) {
			List<Transaction> tl = tListMap.get(acct);
			if (tl != null) {
				tList.addAll(tl);
			}
		}
		TransactionComparator tc = new TransactionComparator();
		tList.sort(tc);
		
		int year = 0;
		Map<String, BigDecimal> acctToOrdIncomeUsdSum = null;
		Map<String, BigDecimal> acctToMngIncomeUsdSum = null;
		Map<String, BigDecimal> acctToMngExpenseUsdSum = null;
		Map<Integer, Map<String, BigDecimal>> yearToAcctToMngAmortExpenseUsdMap = new HashMap<>();
		
		for (Transaction t : tList) {
			try {
				Transaction.TransactionType tType = t.getTxnType();
				String tAcct = t.getTxnAcct();
				
				int tYear = t.getTxnYearInt();
				if (year == 0) {
					// first income transaction
					year = tYear;
					
					// if there were earlier years with capital gains, enter them first (this is not expected to happen!)
					while (earliestCapGainYear != 0 && earliestCapGainYear < year) {
						Map<String, BigDecimal> shortTermCapGain = yearToAcctToShortTermCapGain.remove(earliestCapGainYear);
						Map<String, BigDecimal> longTermCapGain = yearToAcctToLongTermCapGain.remove(earliestCapGainYear);
						IncomeEntry ie = new IncomeEntry(String.valueOf(earliestCapGainYear), accts, null, shortTermCapGain,
								longTermCapGain, null, null, null);
						result.add(ie);
						earliestCapGainYear++;
					}
				}
				
				BigDecimal tUsdAmnt = t.getCalculatedTxnUsdAmnt();
				
				// same year
				if (year == tYear) {
					if (tType == TransactionType.INCOME) {
						if (acctToOrdIncomeUsdSum == null) {
							acctToOrdIncomeUsdSum = new HashMap<>();
						}
						BigDecimal ordIncome = acctToOrdIncomeUsdSum.get(tAcct);
						if (ordIncome == null) {
							ordIncome = BigDecimal.ZERO;
						}
						ordIncome = ordIncome.add(tUsdAmnt);
						acctToOrdIncomeUsdSum.put(tAcct, ordIncome);
					}
					else if (tType == TransactionType.MNG_INCOME) {
						if (acctToMngIncomeUsdSum == null) {
							acctToMngIncomeUsdSum = new HashMap<>();
						}
						BigDecimal mngIncome = acctToMngIncomeUsdSum.get(tAcct);
						if (mngIncome == null) {
							mngIncome = BigDecimal.ZERO;
						}
						mngIncome = mngIncome.add(tUsdAmnt);
						acctToMngIncomeUsdSum.put(tAcct, mngIncome);
					}
					else if (tType == TransactionType.MNG_PURCHASE || tType == TransactionType.MNG_REINVEST) {
						if (acctToMngExpenseUsdSum == null) {
							acctToMngExpenseUsdSum = new HashMap<>();
						}
						BigDecimal mngExpense = acctToMngExpenseUsdSum.get(tAcct);
						if (mngExpense == null) {
							mngExpense = BigDecimal.ZERO;
						}
						mngExpense = mngExpense.add(tUsdAmnt);
						acctToMngExpenseUsdSum.put(tAcct, mngExpense);
						
						amortizeExpenses(yearToAcctToMngAmortExpenseUsdMap, tAcct, t.getTxnDttm().toLocalDate(), t.getTermMos(), tUsdAmnt);
					}
				}
				// new year
				else {
					Map<String, BigDecimal> amortExpenses = yearToAcctToMngAmortExpenseUsdMap.remove(year);
					Map<String, BigDecimal> shortTermCapGains = yearToAcctToShortTermCapGain.remove(year);
					Map<String, BigDecimal> longTermCapGains = yearToAcctToLongTermCapGain.remove(year);
					
					IncomeEntry ie = new IncomeEntry(String.valueOf(year), accts, acctToOrdIncomeUsdSum, shortTermCapGains,
							longTermCapGains, acctToMngIncomeUsdSum, acctToMngExpenseUsdSum, amortExpenses);
					result.add(ie);

					if (year > tYear) {
						// should not happen since we sorted transactions by dttm, but a defensive step to avoid an infinite loop
						throw new ControllerException("Encountered an out of order (earlier) Transaction dttm " + t.getTxnDttm());
					}
					
					year++;
					acctToOrdIncomeUsdSum = null;
					acctToMngIncomeUsdSum = null;
					acctToMngExpenseUsdSum = null;
					
					while (year < tYear) {
						amortExpenses = yearToAcctToMngAmortExpenseUsdMap.remove(year);
						shortTermCapGains = yearToAcctToShortTermCapGain.remove(year);
						longTermCapGains = yearToAcctToLongTermCapGain.remove(year);
						
						ie = new IncomeEntry(String.valueOf(year), accts, acctToOrdIncomeUsdSum, shortTermCapGains, longTermCapGains,
								acctToMngIncomeUsdSum, acctToMngExpenseUsdSum, amortExpenses);
						result.add(ie);
						year++;
					}
					
					if (tType == TransactionType.INCOME) {
						acctToOrdIncomeUsdSum = new HashMap<>();
						acctToOrdIncomeUsdSum.put(tAcct, tUsdAmnt);
					}
					else if (tType == TransactionType.MNG_INCOME) {
						acctToMngIncomeUsdSum = new HashMap<>();
						acctToMngIncomeUsdSum.put(tAcct, tUsdAmnt);
					}
					else if (tType == TransactionType.MNG_PURCHASE || tType == TransactionType.MNG_REINVEST) {
						acctToMngExpenseUsdSum = new HashMap<>();
						acctToMngExpenseUsdSum.put(tAcct, tUsdAmnt);
						amortizeExpenses(yearToAcctToMngAmortExpenseUsdMap, tAcct, t.getTxnDttm().toLocalDate(), t.getTermMos(), tUsdAmnt);
					}
				}
			}
			catch (TransactionException tExc) {
				throw new ControllerException("Income and expenses compute failed due to Transaction error: " + tExc.getMessage());
			}
		}
		while (acctToOrdIncomeUsdSum != null || acctToMngIncomeUsdSum != null || acctToMngExpenseUsdSum != null ||
				! yearToAcctToMngAmortExpenseUsdMap.isEmpty() || ! yearToAcctToShortTermCapGain.isEmpty() ||
				! yearToAcctToLongTermCapGain.isEmpty()) {
			
			Map<String, BigDecimal> amortExpenses = yearToAcctToMngAmortExpenseUsdMap.remove(year);
			Map<String, BigDecimal> shortTermCapGain = yearToAcctToShortTermCapGain.remove(year);
			Map<String, BigDecimal> longTermCapGain = yearToAcctToLongTermCapGain.remove(year);
			
			IncomeEntry ie = new IncomeEntry(String.valueOf(year), accts, acctToOrdIncomeUsdSum, shortTermCapGain, longTermCapGain,
					acctToMngIncomeUsdSum, acctToMngExpenseUsdSum, amortExpenses);
			result.add(ie);
			
			year++;
			acctToOrdIncomeUsdSum = null;
			acctToMngIncomeUsdSum = null;
			acctToMngExpenseUsdSum = null;
		}
		
		return result;
	}
	
	
	private static void amortizeExpenses(Map<Integer, Map<String, BigDecimal>> yearToAcctToExpenseMap, String acct, LocalDate startDate,
			Long lengthMos, BigDecimal totalExpense) {
		
		if (yearToAcctToExpenseMap == null || acct == null || startDate == null || lengthMos == null || totalExpense == null) {
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
			
			Map<String, BigDecimal> acctToExpenseMap = yearToAcctToExpenseMap.get(year);
			if (acctToExpenseMap == null) {
				acctToExpenseMap = new HashMap<>();
				yearToAcctToExpenseMap.put(year, acctToExpenseMap);
			}
			
			BigDecimal mapValue = acctToExpenseMap.get(acct);
			if (mapValue != null) {
				expenseInYear = expenseInYear.add(mapValue);
			}
			acctToExpenseMap.put(acct, expenseInYear);
			
			remAmortPeriod = remAmortPeriod - amortSegment;
			t1 = t2;
			t2 = t2.plusYears(1);
			if (endDate.isBefore(t2)) {
				t2 = endDate;
			}
		}
	}
	
	
	private static Map<String, List<MiningEntry>> computeMining(Map<String, List<Transaction>> tListMap) throws ControllerException {
		if (tListMap == null) {
			return null;
		}

		Map<String, List<MiningEntry>> result = new HashMap<>();
		
		Set<String> acctSet = tListMap.keySet();
		for (String acct : acctSet) {
			List<Transaction> tList = tListMap.get(acct);
			if (tList == null || tList.isEmpty()) {
				continue;
			}
			
			List<MiningEntry> meList = new ArrayList<>();
			
			// Sort all transactions in chronological order
			TransactionComparator tc = new TransactionComparator();
			tList.sort(tc);
			
			// Read in all the purchase and reinvestment contracts
			List<MiningContract> mcList = new ArrayList<>();
			LocalDate miningStartDate = null;
			LocalDate miningEndDate = null;
			Map<LocalDate, BigDecimal> dateToIncomeUsdMap = new HashMap<>();
			Map<LocalDate, Long> dateToHashrateMap = new HashMap<>();
			Map<LocalDate, BigDecimal> dateToIncomeCoinMap = new HashMap<>();
			
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
								if (purchase == null) {
									purchase = BigDecimal.ZERO;
								}
								purchase = purchase.add(mc.getTotalAmountUsd());
							}
							else if (mcType == MiningContractType.REINVESTMENT) {
								if (reinvestment == null) {
									reinvestment = BigDecimal.ZERO;
								}
								reinvestment = reinvestment.add(mc.getTotalAmountUsd());
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
					meList.add(me);
					
					
					thisDate = thisDate.plusDays(1);
				}
			}
			else {
				System.out.println("INFO: Mining compute found no mining contracts");
			}
			
			result.put(acct, meList);
		}
		
		return result;
	}
	
	
	private static void writeGainEntries(List<GainEntry> geList, File outputFile) throws ControllerException {
		if (geList == null || outputFile == null) {
			throw new ControllerException("Gain entries or output file is null");
		}
		if (outputFile.exists()) {
			throw new ControllerException("Could not write to output file "+outputFile.getAbsolutePath()+" as it already exists");
		}
		
		try {
			CSVPrinter printer = new CSVPrinter(new FileWriter(outputFile), CSV_FORMAT);
			
			printer.printRecord(
					GainEntry.COL_TAX_YEAR,
					GainEntry.COL_TERM,
					GainEntry.COL_DATE_ACQ,
					GainEntry.COL_DATE_DISP,
					GainEntry.COL_BRKR_ACQ,
					GainEntry.COL_BRKR_DISP,
					GainEntry.COL_ASSET_AMNT,
					GainEntry.COL_PROCEEDS,
					GainEntry.COL_COST_BASIS,
					GainEntry.COL_GAIN);
			
			for (GainEntry ge : geList) {
				printer.printRecord(
						ge.getTaxYearStr(),
						ge.getTermStr(),
						ge.getDateAcquiredStr(),
						ge.getDateDisposedStr(),
						ge.getBrokerAcquiredStr(),
						ge.getBrokerDisposedStr(),
						ge.getAssetAmntStr(),
						ge.getProceedsStr(),
						ge.getCostBasisStr(),
						ge.getGainStr());
			}
			
			printer.close(true);
		}
		catch (IOException ioExc) {
			throw new ControllerException(ioExc.getMessage());
		}
	}
	
	
	private static void writeUnrealizedCostBasis(UnrealizedCostBasisEntry ucbe, File outputFile) throws ControllerException {
		if (ucbe == null || outputFile == null) {
			throw new ControllerException("Unrealized cost basis entries or output file is null");
		}
		if (outputFile.exists()) {
			throw new ControllerException("Could not write to output file "+outputFile.getAbsolutePath()+" as it already exists");
		}
		if (ucbe.getAccts() == null || ucbe.getAccts().length < 1) {
			System.out.println("INFO: writeUnrealizedCostBasis skipped writing file because ucbe was empty.");
			return;
		}
		
		try {
			CSVPrinter printer = new CSVPrinter(new FileWriter(outputFile), CSV_FORMAT);
			
			// header row
			List<String> sortedAccts = new ArrayList<>();
			String[] acctArr = ucbe.getAccts();
			for (String a : acctArr) {
				// add accounts that are not USD stablecoins
				if (! USD_STABLECOINS.contains(a)) {
					sortedAccts.add(a);
				}
			}
			Collections.sort(sortedAccts);
			
			List<String> rowValues = new ArrayList<>();
			for (String a : sortedAccts) {
				rowValues.add("["+a+"] " + UnrealizedCostBasisEntry.COL_COSTBASIS_SHORTTERM);
				rowValues.add("["+a+"] " + UnrealizedCostBasisEntry.COL_COSTBASIS_LONGTERM);
				rowValues.add("["+a+"] " + UnrealizedCostBasisEntry.COL_COSTBASIS_AVG);
			}
			printer.printRecord((Object[]) rowValues.toArray(new String[rowValues.size()]));
			
			// data row
			rowValues.clear();
			for (String a : sortedAccts) {
				rowValues.add(ucbe.getShortTermCostBasisStr(a));
				rowValues.add(ucbe.getLongTermCostBasisStr(a));
				rowValues.add(ucbe.getAvgCostBasisStr(a));
			}
			printer.printRecord((Object[]) rowValues.toArray(new String[rowValues.size()]));
			
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
		
		// calculate which columns to display
		Set<String> acctSet = new HashSet<>();
		Map<String, Set<String>> colToAcctSet = new HashMap<>();
		colToAcctSet.put(IncomeEntry.COL_ORD_INC_USD, new HashSet<>());
		colToAcctSet.put(IncomeEntry.COL_CAPGAIN_SHORTTERM, new HashSet<>());
		colToAcctSet.put(IncomeEntry.COL_CAPGAIN_LONGTERM, new HashSet<>());
		colToAcctSet.put(IncomeEntry.COL_MNG_INC_USD, new HashSet<>());
		colToAcctSet.put(IncomeEntry.COL_MNG_EXP_USD, new HashSet<>());
		colToAcctSet.put(IncomeEntry.COL_MNG_AMORT_EXP_USD, new HashSet<>());
		for (IncomeEntry ie : ieList) {
			String[] acctArr = ie.getAccts();
			for (String a : acctArr) {
				acctSet.add(a);
				BigDecimal ordIncome = ie.getOrdIncome(a);
				if (ordIncome != null) {
					colToAcctSet.get(IncomeEntry.COL_ORD_INC_USD).add(a);
				}
				BigDecimal cgShortTerm = ie.getShortTermCapGains(a);
				if (cgShortTerm != null) {
					colToAcctSet.get(IncomeEntry.COL_CAPGAIN_SHORTTERM).add(a);
				}
				BigDecimal cgLongTerm = ie.getLongTermCapGains(a);
				if (cgLongTerm != null) {
					colToAcctSet.get(IncomeEntry.COL_CAPGAIN_LONGTERM).add(a);
				}
				BigDecimal mngIncome = ie.getMngIncome(a);
				if (mngIncome != null) {
					colToAcctSet.get(IncomeEntry.COL_MNG_INC_USD).add(a);
				}
				BigDecimal mngExpense = ie.getMngExpense(a);
				if (mngExpense != null) {
					colToAcctSet.get(IncomeEntry.COL_MNG_EXP_USD).add(a);
				}
				BigDecimal mngAmortExpense = ie.getMngAmortExpense(a);
				if (mngAmortExpense != null) {
					colToAcctSet.get(IncomeEntry.COL_MNG_AMORT_EXP_USD).add(a);
				}
			}
		}
		
		List<String> acctList = new ArrayList<>(acctSet);
		acctList.sort(Comparator.naturalOrder());
		
		
		try {
			CSVPrinter printer = new CSVPrinter(new FileWriter(outputFile), CSV_FORMAT);
			
			// Header
			List<String> rowValues = new ArrayList<>();
			rowValues.add(IncomeEntry.COL_TAX_YEAR);
			for (String a : acctList) {
				if (colToAcctSet.get(IncomeEntry.COL_ORD_INC_USD).contains(a)) {
					rowValues.add((a.equals("") ? "" : "["+a+"] ") + IncomeEntry.COL_ORD_INC_USD);
				}
				if (colToAcctSet.get(IncomeEntry.COL_CAPGAIN_SHORTTERM).contains(a) ||
						colToAcctSet.get(IncomeEntry.COL_CAPGAIN_LONGTERM).contains(a)) {
					rowValues.add((a.equals("") ? "" : "["+a+"] ") + IncomeEntry.COL_CAPGAIN_SHORTTERM);
					rowValues.add((a.equals("") ? "" : "["+a+"] ") + IncomeEntry.COL_CAPGAIN_LONGTERM);
				}
				if (colToAcctSet.get(IncomeEntry.COL_MNG_INC_USD).contains(a) ||
						colToAcctSet.get(IncomeEntry.COL_MNG_EXP_USD).contains(a) ||
						colToAcctSet.get(IncomeEntry.COL_MNG_AMORT_EXP_USD).contains(a)) {
					rowValues.add((a.equals("") ? "" : "["+a+"] ") + IncomeEntry.COL_MNG_INC_USD);
					rowValues.add((a.equals("") ? "" : "["+a+"] ") + IncomeEntry.COL_MNG_EXP_USD);
					rowValues.add((a.equals("") ? "" : "["+a+"] ") + IncomeEntry.COL_MNG_AMORT_EXP_USD);
				}
			}
			
			printer.printRecord((Object[]) rowValues.toArray(new String[rowValues.size()]));
			
			
			// Data rows
			for (IncomeEntry ie : ieList) {
				
				rowValues.clear();
				rowValues.add(ie.getTaxYear());
				for (String a : acctList) {
					if (colToAcctSet.get(IncomeEntry.COL_ORD_INC_USD).contains(a)) {
						rowValues.add(ie.getOrdIncomeStr(a));
					}
					if (colToAcctSet.get(IncomeEntry.COL_CAPGAIN_SHORTTERM).contains(a) ||
							colToAcctSet.get(IncomeEntry.COL_CAPGAIN_LONGTERM).contains(a)) {
						rowValues.add(ie.getShortTermCapGainsStr(a));
						rowValues.add(ie.getLongTermCapGainsStr(a));
					}
					if (colToAcctSet.get(IncomeEntry.COL_MNG_INC_USD).contains(a) ||
							colToAcctSet.get(IncomeEntry.COL_MNG_EXP_USD).contains(a) ||
							colToAcctSet.get(IncomeEntry.COL_MNG_AMORT_EXP_USD).contains(a)) {
						rowValues.add(ie.getMngIncomeStr(a));
						rowValues.add(ie.getMngExpenseStr(a));
						rowValues.add(ie.getMngAmortExpenseStr(a));
					}
				}
				
				printer.printRecord((Object[]) rowValues.toArray(new String[rowValues.size()]));
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
