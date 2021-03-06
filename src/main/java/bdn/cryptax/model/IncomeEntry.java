package bdn.cryptax.model;

import java.math.BigDecimal;

public class IncomeEntry {

	public static final String COL_TAX_YEAR = "Tax Year";
	public static final String COL_ORD_INC_USD = "Ordinary Income";
	public static final String COL_CAPGAIN_SHORTTERM = "Short-Term Cap Gain";
	public static final String COL_CAPGAIN_LONGTERM = "Long-Term Cap Gain";
	public static final String COL_MNG_INC_USD = "Mining Income";
	public static final String COL_MNG_EXP_USD = "Mining Expense";
	public static final String COL_MNG_AMORT_EXP_USD = "Mining Amortized Expense";


	private String taxYear;
	private BigDecimal ordIncome;
	private BigDecimal shortTermCapGains;
	private BigDecimal longTermCapGains;
	private BigDecimal mngIncome;
	private BigDecimal mngExpense;
	private BigDecimal mngAmortExpense;

	

	public IncomeEntry(String taxYear, BigDecimal ordIncome, BigDecimal shortTermCapGains, BigDecimal longTermCapGains,
			BigDecimal mngIncome, BigDecimal mngExpense, BigDecimal mngAmortExpense) {
		this.taxYear = taxYear;
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



	public BigDecimal getOrdIncome() {
		return ordIncome;
	}
	
	
	public String getOrdIncomeStr() {
		return (ordIncome != null) ? ordIncome.toPlainString() : "";
	}



	public void setOrdIncome(BigDecimal ordIncome) {
		this.ordIncome = ordIncome;
	}



	public BigDecimal getShortTermCapGains() {
		return shortTermCapGains;
	}


	
	public String getShortTermCapGainsStr() {
		return (shortTermCapGains != null) ? shortTermCapGains.toPlainString() : "";
	}



	public void setShortTermCapGains(BigDecimal shortTermCapGains) {
		this.shortTermCapGains = shortTermCapGains;
	}



	public BigDecimal getLongTermCapGains() {
		return longTermCapGains;
	}



	public String getLongTermCapGainsStr() {
		return (longTermCapGains != null) ? longTermCapGains.toPlainString() : "";
	}



	public void setLongTermCapGains(BigDecimal longTermCapGains) {
		this.longTermCapGains = longTermCapGains;
	}



	public BigDecimal getMngIncome() {
		return mngIncome;
	}


	public String getMngIncomeStr() {
		return (mngIncome != null) ? mngIncome.toPlainString() : "";
	}


	public void setMngIncome(BigDecimal mngIncome) {
		this.mngIncome = mngIncome;
	}



	public BigDecimal getMngExpense() {
		return mngExpense;
	}
	

	public String getMngExpenseStr() {
		return (mngExpense != null) ? mngExpense.toPlainString() : "";
	}
	


	public void setMngExpense(BigDecimal mngExpense) {
		this.mngExpense = mngExpense;
	}



	public BigDecimal getMngAmortExpense() {
		return mngAmortExpense;
	}



	public String getMngAmortExpenseStr() {
		return (mngAmortExpense != null) ? mngAmortExpense.toPlainString() : "";
	}
	


	public void setMngAmortExpense(BigDecimal mngAmortExpense) {
		this.mngAmortExpense = mngAmortExpense;
	}



	@Override
	public String toString() {
		return new String(taxYear + " " + ordIncome + " " + mngIncome + " " + mngExpense + " " + mngAmortExpense);
	}
	
}
