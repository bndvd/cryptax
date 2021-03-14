package bdn.cryptax.model;

import java.math.BigDecimal;
import java.util.Map;

public class IncomeEntry {

	public static final String COL_TAX_YEAR = "Tax Year";
	public static final String COL_ORD_INC_USD = "Ordinary Income";
	public static final String COL_CAPGAIN_SHORTTERM = "Short-Term Cap Gain";
	public static final String COL_CAPGAIN_LONGTERM = "Long-Term Cap Gain";
	public static final String COL_MNG_INC_USD = "Mining Income";
	public static final String COL_MNG_EXP_USD = "Mining Expense";
	public static final String COL_MNG_AMORT_EXP_USD = "Mining Amortized Expense";


	private String taxYear;
	private String[] accts;
	private Map<String, BigDecimal> ordIncome;
	private Map<String, BigDecimal> shortTermCapGains;
	private Map<String, BigDecimal> longTermCapGains;
	private Map<String, BigDecimal> mngIncome;
	private Map<String, BigDecimal> mngExpense;
	private Map<String, BigDecimal> mngAmortExpense;

	

	public IncomeEntry(String taxYear, String[] accts, Map<String, BigDecimal> ordIncome,
			Map<String, BigDecimal> shortTermCapGains, Map<String, BigDecimal> longTermCapGains,
			Map<String, BigDecimal> mngIncome, Map<String, BigDecimal> mngExpense, Map<String, BigDecimal> mngAmortExpense) {
		this.taxYear = taxYear;
		this.accts = accts;
		this.ordIncome = ordIncome;
		this.shortTermCapGains = shortTermCapGains;
		this.longTermCapGains = longTermCapGains;
		this.mngIncome = mngIncome;
		this.mngExpense = mngExpense;
		this.mngAmortExpense = mngAmortExpense;
	}



	public String getTaxYear() {
		return taxYear;
	}



	public void setTaxYear(String taxYear) {
		this.taxYear = taxYear;
	}



	public String[] getAccts() {
		return accts;
	}



	public BigDecimal getOrdIncome(String acct) {
		if (acct == null || ordIncome == null) {
			return null;
		}
		return ordIncome.get(acct);
	}
	
	
	public String getOrdIncomeStr(String acct) {
		BigDecimal result = getOrdIncome(acct);
		return (result != null) ? result.toPlainString() : "";
	}



	public BigDecimal getShortTermCapGains(String acct) {
		if (acct == null || shortTermCapGains == null) {
			return null;
		}
		return shortTermCapGains.get(acct);
	}


	
	public String getShortTermCapGainsStr(String acct) {
		BigDecimal result = getShortTermCapGains(acct);
		return (result != null) ? result.toPlainString() : "";
	}



	public BigDecimal getLongTermCapGains(String acct) {
		if (acct == null || longTermCapGains == null) {
			return null;
		}
		return longTermCapGains.get(acct);
	}



	public String getLongTermCapGainsStr(String acct) {
		BigDecimal result = getLongTermCapGains(acct);
		return (result != null) ? result.toPlainString() : "";
	}



	public BigDecimal getMngIncome(String acct) {
		if (acct == null || mngIncome == null) {
			return null;
		}
		return mngIncome.get(acct);
	}


	public String getMngIncomeStr(String acct) {
		BigDecimal result = getMngIncome(acct);
		return (result != null) ? result.toPlainString() : "";
	}


	public BigDecimal getMngExpense(String acct) {
		if (acct == null || mngExpense == null) {
			return null;
		}
		return mngExpense.get(acct);
	}
	

	public String getMngExpenseStr(String acct) {
		BigDecimal result = getMngExpense(acct);
		return (result != null) ? result.toPlainString() : "";
	}
	


	public BigDecimal getMngAmortExpense(String acct) {
		if (acct == null || mngAmortExpense == null) {
			return null;
		}
		return mngAmortExpense.get(acct);
	}



	public String getMngAmortExpenseStr(String acct) {
		BigDecimal result = getMngAmortExpense(acct);
		return (result != null) ? result.toPlainString() : "";
	}
	



	@Override
	public String toString() {
		return new String(taxYear + " " + ordIncome + " " + mngIncome + " " + mngExpense + " " + mngAmortExpense);
	}
	
}
