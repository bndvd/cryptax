package bdn.cryptax.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class MiningEntry {

	public static final String COL_YEAR = "Year";
	public static final String COL_DATE = "Date";
	public static final String COL_PURCHASE_USD = "Purchase (P)";
	public static final String COL_REINVEST_USD = "Reinvestment (R)";
	public static final String COL_DAY_BASIS_P_USD = "Day Basis (P)";
	public static final String COL_CUM_BASIS_P_USD = "Cum Basis (P)";
	public static final String COL_DAY_BASIS_PR_USD = "Day Basis (P&R)";
	public static final String COL_DAY_INCOME_USD = "Day Income";
	public static final String COL_CUM_INCOME_USD = "Cum Income";
	public static final String COL_DAY_GAIN_P_USD = "Gain (P)";
	public static final String COL_DAY_GAIN_PR_USD = "Gain (P&R)";
	public static final String COL_DAY_HASH_RATE = "Hashrate (GH/s)";
	public static final String COL_USD_PER_COIN = "USD/Coin";
	public static final String COL_DAY_MINING_YIELD = "Yield (Coin/EH/s)";
	public static final String COL_DAY_RATE_PR = "Day Rate (P&R)";
	public static final String COL_AVG_DAY_RATE_PR = "Avg Rate (P&R)";
	public static final String COL_DAY_RATE_P = "Day Rate (P)";



	private static final DateTimeFormatter DTF_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter DTF_YEAR = DateTimeFormatter.ofPattern("yyyy");
	
	private LocalDate date;
	private BigDecimal purchase;
	private BigDecimal reinvestment;
	private BigDecimal dayBasisPurchase;
	private BigDecimal cumBasisPurchase;
	private BigDecimal dayBasisPurchaseAndReinvest;
	private BigDecimal dayIncome;
	private BigDecimal cumIncome;
	private Long hashrate;
	private BigDecimal usdPerCoin;
	private BigDecimal yield;
	private BigDecimal dayRatePurchaseAndReinvest;
	private BigDecimal avgDayRatePurchaseAndReinvest;
	private BigDecimal dayRatePurchase;
	
	
	public MiningEntry(LocalDate date, BigDecimal purchase, BigDecimal reinvestment, BigDecimal dayBasisPurchase,
			BigDecimal cumBasisPurchase, BigDecimal dayBasisPurchaseAndReinvest, BigDecimal dayIncome, BigDecimal cumIncome,
			Long hashrate, BigDecimal usdPerCoin, BigDecimal yield, BigDecimal dayRatePurchaseAndReinvest,
			BigDecimal avgDayRatePurchaseAndReinvest, BigDecimal dayRatePurchase) {
		this.date = date;
		this.purchase = purchase;
		this.reinvestment = reinvestment;
		this.dayBasisPurchase = dayBasisPurchase;
		this.cumBasisPurchase = cumBasisPurchase;
		this.dayBasisPurchaseAndReinvest = dayBasisPurchaseAndReinvest;
		this.dayIncome = dayIncome;
		this.cumIncome = cumIncome;
		this.hashrate = hashrate;
		this.usdPerCoin = usdPerCoin;
		this.yield = yield;
		this.dayRatePurchaseAndReinvest = dayRatePurchaseAndReinvest;
		this.avgDayRatePurchaseAndReinvest = avgDayRatePurchaseAndReinvest;
		this.dayRatePurchase = dayRatePurchase;
	}


	public LocalDate getDate() {
		return date;
	}
	
	public String getDateStr() {
		return (date != null) ? date.format(DTF_DATE) : "";
	}

	public String getYearStr() {
		return (date != null) ? date.format(DTF_YEAR) : "";
	}


	public BigDecimal getPurchase() {
		return purchase;
	}

	public String getPurchaseStr() {
		return (purchase != null) ? purchase.toPlainString() : "";
	}
	
	public BigDecimal getReinvestment() {
		return reinvestment;
	}


	public String getReinvestmentStr() {
		return (reinvestment != null) ? reinvestment.toPlainString() : "";
	}
	
	
	public BigDecimal getDayBasisPurchase() {
		return dayBasisPurchase;
	}


	public String getDayBasisPurchaseStr() {
		return (dayBasisPurchase != null) ? dayBasisPurchase.toPlainString() : "";
	}
	
	
	public BigDecimal getCumBasisPurchase() {
		return cumBasisPurchase;
	}


	public String getCumBasisPurchaseStr() {
		return (cumBasisPurchase != null) ? cumBasisPurchase.toPlainString() : "";
	}
	
	
	public BigDecimal getDayBasisPurchaseAndReinvest() {
		return dayBasisPurchaseAndReinvest;
	}


	public String getDayBasisPurchaseAndReinvestStr() {
		return (dayBasisPurchaseAndReinvest != null) ? dayBasisPurchaseAndReinvest.toPlainString() : "";
	}
	
	
	public BigDecimal getDayIncome() {
		return dayIncome;
	}


	public String getDayIncomeStr() {
		return (dayIncome != null) ? dayIncome.toPlainString() : "";
	}
	
	
	public BigDecimal getCumIncome() {
		return cumIncome;
	}


	public String getCumIncomeStr() {
		return (cumIncome != null) ? cumIncome.toPlainString() : "";
	}
	
	
	public BigDecimal getGainPurchase() {
		if (dayIncome == null || dayBasisPurchase == null) {
			return null;
		}
		return dayIncome.subtract(dayBasisPurchase);
	}
	
	
	public String getGainPurchaseStr() {
		BigDecimal gain = getGainPurchase();
		return (gain != null) ? gain.toPlainString() : "";
	}
	
	
	public BigDecimal getGainPurchaseAndReinvest() {
		if (dayIncome == null || dayBasisPurchaseAndReinvest == null) {
			return null;
		}
		return dayIncome.subtract(dayBasisPurchaseAndReinvest);
	}
	
	
	public String getGainPurchaseAndReinvestStr() {
		BigDecimal gain = getGainPurchaseAndReinvest();
		return (gain != null) ? gain.toPlainString() : "";
	}
	
	
	public Long getHashrate() {
		return hashrate;
	}


	public String getHashrateStr() {
		return (hashrate != null) ? hashrate.toString() : "";
	}
	
	
	public BigDecimal getUsdPerCoin() {
		return usdPerCoin;
	}


	public String getUsdPerCoinStr() {
		return (usdPerCoin != null) ? usdPerCoin.toPlainString() : "";
	}
	
	
	public BigDecimal getYield() {
		return yield;
	}


	public String getYieldStr() {
		return (yield != null) ? yield.toPlainString() : "";
	}
	
	
	public BigDecimal getDayRatePurchaseAndReinvest() {
		return dayRatePurchaseAndReinvest;
	}


	public String getDayRatePurchaseAndReinvestStr() {
		return (dayRatePurchaseAndReinvest != null) ? dayRatePurchaseAndReinvest.toPlainString() : "";
	}
	
	
	public BigDecimal getAvgDayRatePurchaseAndReinvest() {
		return avgDayRatePurchaseAndReinvest;
	}


	public String getAvgDayRatePurchaseAndReinvestStr() {
		return (avgDayRatePurchaseAndReinvest != null) ? avgDayRatePurchaseAndReinvest.toPlainString() : "";
	}
	
	
	public BigDecimal getDayRatePurchase() {
		return dayRatePurchase;
	}


	public String getDayRatePurchaseStr() {
		return (dayRatePurchase != null) ? dayRatePurchase.toPlainString() : "";
	}
	
	
	

}
