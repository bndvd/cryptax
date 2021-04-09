package bdn.cryptax.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

import org.apache.commons.csv.CSVRecord;

import bdn.cryptax.model.TransactionException.TransactionExceptionType;

public class Transaction {

	public static final String COL_TXN_ACCT = "Acct";
	public static final String COL_TXN_DTTM = "UTC Dttm";
	public static final String COL_TXN_TYPE = "Txn Type";
	public static final String COL_TXN_SRC = "Src";
	public static final String COL_TXN_DEST = "Dest";
	public static final String COL_TXN_COIN_AMNT = "Txn COIN";
	public static final String COL_TXN_USD_AMNT = "Txn USD";
	public static final String COL_TXN_USD_PER_UNIT = "Txn USD/COIN";
	public static final String COL_TXN_FEE_COIN = "Txn Fee COIN";
	public static final String COL_TXN_BRKR_FEE_USD = "Brkr Fee USD";
	public static final String COL_TERM_MOS = "Term Months";
	public static final String COL_TXN_HASHRATE = "Hash Rate (GH/s)";

	public static enum TransactionType {
		ACQUIRE, DISPOSE, TRANSFER, INCOME, MNG_INCOME, MNG_PURCHASE, MNG_REINVEST
	}
	
	public static HashMap<String, TransactionType> typeStrToEnum = new HashMap<>();
	static {
		typeStrToEnum.put("acq", TransactionType.ACQUIRE);
		typeStrToEnum.put("disp", TransactionType.DISPOSE);
		typeStrToEnum.put("tran", TransactionType.TRANSFER);
		typeStrToEnum.put("inc", TransactionType.INCOME);
		typeStrToEnum.put("minc", TransactionType.MNG_INCOME);
		typeStrToEnum.put("mpur", TransactionType.MNG_PURCHASE);
		typeStrToEnum.put("mre", TransactionType.MNG_REINVEST);
	}
	

	private static final DateTimeFormatter DTF_DASH = DateTimeFormatter.ofPattern("yyyy-M-d H:mm");
	private static final DateTimeFormatter DTF_SLASH = DateTimeFormatter.ofPattern("M/d/yyyy H:mm");
	private static final DateTimeFormatter DTF_YEAR = DateTimeFormatter.ofPattern("yyyy");
	
	private String txnAcct = null;
	private LocalDateTime txnDttm = null;
	private TransactionType txnType = null;
	private String txnSrc = null;
	private String txnDest = null;
	private BigDecimal txnCoinAmnt = null;
	private BigDecimal txnUsdAmnt = null;
	private BigDecimal txnUsdPerUnit = null;
	private BigDecimal txnFeeCoin = null;
	private BigDecimal txnBrkrFeeUsd = null;
	private Long termMos = null;
	private Long txnHashrate = null;
	
	
	public Transaction(CSVRecord csvRecord) throws TransactionException {
		if (csvRecord == null) {
			throw new TransactionException(TransactionExceptionType.EMPTY_DATA, "Empty CSV Record");
		}
		if (!csvRecord.isConsistent()) {
			throw new TransactionException(TransactionExceptionType.INVALID_DATA, "Inconsistent CSV Record #"+csvRecord.getRecordNumber());
		}
		
		// Optional field (empty string if not specified)
		try {
			String csvTxnAcct = csvRecord.get(COL_TXN_ACCT);
			if (csvTxnAcct != null) {
				txnAcct = csvTxnAcct.trim();
			}
			else {
				txnAcct = "";
			}
		}
		catch (Exception exc) {
			txnAcct = "";
		}
		
		// Mandatory field
		try {
			String csvTxnDttm = csvRecord.get(COL_TXN_DTTM);
			if (csvTxnDttm.contains("-")) {
				txnDttm = LocalDateTime.parse(csvTxnDttm, DTF_DASH);
			}
			else {
				txnDttm = LocalDateTime.parse(csvTxnDttm, DTF_SLASH);
			}
		}
		catch (Exception exc) {
			throw new TransactionException(TransactionExceptionType.INVALID_DATA, "Unparsable COL_TXN_DTTM in CSV Record #"+
					csvRecord.getRecordNumber() +": "+exc.getMessage());
		}
		
		// Mandatory field
		try {
			String csvTxnType = csvRecord.get(COL_TXN_TYPE);
			if (csvTxnType != null && !csvTxnType.trim().equals("")) {
				txnType = typeStrToEnum.get(csvTxnType);
			}
			if (txnType == null) {
				throw new TransactionException(TransactionExceptionType.EMPTY_DATA, "CSV Record #"+csvRecord.getRecordNumber()+
						" contained empty COL_TXN_TYPE");
			}
		}
		catch (TransactionException tExc) {
			throw tExc;
		}
		catch (Exception exc) {
			throw new TransactionException(TransactionExceptionType.INVALID_DATA, "Unparsable COL_TXN_TYPE in CSV Record #"+
					csvRecord.getRecordNumber() +": "+exc.getMessage());
		}
		
		// Optional field (mandatory if not of MNG_PURCHASE type)
		try {
			String csvTxnCoinAmnt = csvRecord.get(COL_TXN_COIN_AMNT);
			if (csvTxnCoinAmnt != null && !csvTxnCoinAmnt.trim().equals("")) {
				txnCoinAmnt = new BigDecimal(csvTxnCoinAmnt);
			}
		}
		catch (Exception exc) {
			throw new TransactionException(TransactionExceptionType.INVALID_DATA, "Unparsable COL_TXN_COIN_AMNT in CSV Record #"+
					csvRecord.getRecordNumber() +": "+exc.getMessage());
		}
		
		// Optional field
		try {
			String csvTxnUsdAmnt = csvRecord.get(COL_TXN_USD_AMNT);
			if (csvTxnUsdAmnt != null && !csvTxnUsdAmnt.trim().equals("")) {
				txnUsdAmnt = new BigDecimal(csvTxnUsdAmnt);
			}
		}
		catch (Exception exc) {
			throw new TransactionException(TransactionExceptionType.INVALID_DATA, "Unparsable COL_TXN_USD_AMNT in CSV Record #"+
					csvRecord.getRecordNumber() +": "+exc.getMessage());
		}
		
		// Optional field
		try {
			String csvTxnUsdPerUnit = csvRecord.get(COL_TXN_USD_PER_UNIT);
			if (csvTxnUsdPerUnit != null && !csvTxnUsdPerUnit.trim().equals("")) {
				txnUsdPerUnit = new BigDecimal(csvTxnUsdPerUnit);
			}
		}
		catch (Exception exc) {
			throw new TransactionException(TransactionExceptionType.INVALID_DATA, "Unparsable COL_TXN_USD_PER_UNIT in CSV Record #"+
					csvRecord.getRecordNumber() +": "+exc.getMessage());
		}
		
		// Optional field
		try {
			String csvTxnFeeCoin = csvRecord.get(COL_TXN_FEE_COIN);
			if (csvTxnFeeCoin != null && !csvTxnFeeCoin.trim().equals("")) {
				txnFeeCoin = new BigDecimal(csvTxnFeeCoin);
			}
		}
		catch (Exception exc) {
			throw new TransactionException(TransactionExceptionType.INVALID_DATA, "Unparsable COL_TXN_FEE_COIN in CSV Record #"+
					csvRecord.getRecordNumber() +": "+exc.getMessage());
		}
		
		// Optional field
		try {
			String csvTxnBrkrFeeUsd = csvRecord.get(COL_TXN_BRKR_FEE_USD);
			if (csvTxnBrkrFeeUsd != null && !csvTxnBrkrFeeUsd.trim().equals("")) {
				txnBrkrFeeUsd = new BigDecimal(csvTxnBrkrFeeUsd);
			}
		}
		catch (Exception exc) {
			throw new TransactionException(TransactionExceptionType.INVALID_DATA, "Unparsable COL_TXN_BRKR_FEE_USD in CSV Record #"+
					csvRecord.getRecordNumber() +": "+exc.getMessage());
		}
		
		// Optional field (the presence of the column is also optional)
		try {
			String csvTermMos = csvRecord.get(COL_TERM_MOS);
			if (csvTermMos != null && !csvTermMos.trim().equals("")) {
				termMos = Long.parseLong(csvTermMos);
			}
		}
		catch (Exception exc) {
			termMos = null;
		}
		
		// Optional field (the presence of the column is also optional)
		try {
			String csvTxnSrc = csvRecord.get(COL_TXN_SRC);
			if (csvTxnSrc != null) {
				txnSrc = csvTxnSrc.trim();
			}
		}
		catch (Exception exc) {
			txnSrc = null;
		}
		
		// Optional field (the presence of the column is also optional)
		try {
			String csvTxnDest = csvRecord.get(COL_TXN_DEST);
			if (csvTxnDest != null) {
				txnDest = csvTxnDest.trim();
			}
		}
		catch (Exception exc) {
			txnDest = null;
		}
		
		// Optional field (the presence of the column is also optional)
		try {
			String csvTxnHashrate = csvRecord.get(COL_TXN_HASHRATE);
			if (csvTxnHashrate != null && !csvTxnHashrate.trim().equals("")) {
				txnHashrate = Long.parseLong(csvTxnHashrate);
			}
		}
		catch (Exception exc) {
			txnHashrate = null;
		}
		
		// Validate for required combinations of fields
		if (txnType == TransactionType.ACQUIRE || txnType == TransactionType.INCOME || txnType == TransactionType.DISPOSE ||
				txnType == TransactionType.MNG_INCOME) {
			if ((txnCoinAmnt == null) || (txnUsdAmnt == null && txnUsdPerUnit == null)) {
				throw new TransactionException(TransactionExceptionType.INVALID_DATA, "CSV Record #"+
						csvRecord.getRecordNumber() +" has empty COL_TXN_COIN_AMNT or COL_TXN_USD_AMNT and COL_TXN_USD_PER_UNIT");
			}
		}
		else if (txnType == TransactionType.TRANSFER) {
			if ((txnCoinAmnt == null) || (txnFeeCoin != null && (txnUsdPerUnit == null && txnUsdAmnt == null))) {
				throw new TransactionException(TransactionExceptionType.INVALID_DATA, "CSV Record #" + csvRecord.getRecordNumber() +
						" has empty COL_TXN_COIN_AMNT or non-empty COL_TXN_FEE_COIN but empty COL_TXN_USD_AMNT and COL_TXN_USD_PER_UNIT");
			}
		}
		else if (txnType == TransactionType.MNG_PURCHASE || txnType == TransactionType.MNG_REINVEST) {
			if (termMos == null || txnHashrate == null) {
				throw new TransactionException(TransactionExceptionType.INVALID_DATA, "CSV Record #"+csvRecord.getRecordNumber()+
						" contained empty COL_TERM_MOS or COL_TXN_HASHRATE");
			}
			if (txnCoinAmnt == null && txnUsdAmnt == null) {
				throw new TransactionException(TransactionExceptionType.INVALID_DATA, "CSV Record #"+csvRecord.getRecordNumber()+
						" contained empty COL_TXN_COIN_AMNT and COL_TXN_USD_AMNT");
			}
			if ((txnCoinAmnt != null) && (txnUsdAmnt == null && txnUsdPerUnit == null)) {
				throw new TransactionException(TransactionExceptionType.INVALID_DATA, "CSV Record #"+
						csvRecord.getRecordNumber() +" has empty COL_TXN_USD_AMNT and COL_TXN_USD_PER_UNIT");
			}
		}
	}


	public String getTxnAcct() {
		return txnAcct;
	}


	public LocalDateTime getTxnDttm() {
		return txnDttm;
	}


	public String getTxnYearStr() {
		return txnDttm.format(DTF_YEAR);
	}
	
	
	public int getTxnYearInt() {
		return Integer.parseInt(getTxnYearStr());
	}


	public TransactionType getTxnType() {
		return txnType;
	}


	public String getTxnSrc() {
		return txnSrc;
	}


	public String getTxnDest() {
		return txnDest;
	}


	public BigDecimal getTxnCoinAmnt() {
		return txnCoinAmnt;
	}


	public BigDecimal getTxnUsdAmnt() {
		return txnUsdAmnt;
	}


	public BigDecimal getCalculatedTxnUsdAmnt() throws TransactionException {
		BigDecimal result = txnUsdAmnt;
		if (result == null) {
			if (txnCoinAmnt == null || txnUsdPerUnit == null) {
				throw new TransactionException(TransactionExceptionType.INVALID_DATA, "Transaction dated "+
						getTxnDttm() +" could not calculate TXN_USD_AMNT because of insufficient data");
			}
			result = txnCoinAmnt.multiply(txnUsdPerUnit);
		}
		return result;
	}


	public BigDecimal getTxnUsdPerUnit() {
		return txnUsdPerUnit;
	}


	public BigDecimal getTxnFeeCoin() {
		return txnFeeCoin;
	}


	public BigDecimal getTxnBrkrFeeUsd() {
		return txnBrkrFeeUsd;
	}


	public Long getTermMos() {
		return termMos;
	}


	public Long getTxnHashrate() {
		return txnHashrate;
	}


	@Override
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append(getTxnDttm()).append(",")
			.append(getTxnType()).append(",")
			.append(getTxnCoinAmnt()).append(",")
			.append(getTxnUsdAmnt()).append(",")
			.append(getTxnUsdPerUnit()).append(",")
			.append(getTxnFeeCoin()).append(",")
			.append(getTxnBrkrFeeUsd()).append(",")
			.append(getTermMos());
		
		return buf.toString();
	}

}
