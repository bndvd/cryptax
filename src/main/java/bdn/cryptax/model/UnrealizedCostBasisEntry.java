package bdn.cryptax.model;

import java.math.BigDecimal;
import java.util.Map;

public class UnrealizedCostBasisEntry {

	public static final String COL_COSTBASIS_SHORTTERM = "Short-Term Cost Basis";
	public static final String COL_COSTBASIS_LONGTERM = "Long-Term Cost Basis";
	public static final String COL_COSTBASIS_AVG = "Average Cost Basis";


	private String[] accts;
	private Map<String, BigDecimal> shortTermCostBasis;
	private Map<String, BigDecimal> longTermCostBasis;
	private Map<String, BigDecimal> avgCostBasis;

	

	public UnrealizedCostBasisEntry(String[] accts, Map<String, BigDecimal> shortTermCostBasis, Map<String, BigDecimal> longTermCostBasis,
			Map<String, BigDecimal> avgCostBasis) {
		this.accts = accts;
		this.shortTermCostBasis = shortTermCostBasis;
		this.longTermCostBasis = longTermCostBasis;
		this.avgCostBasis = avgCostBasis;
	}



	public String[] getAccts() {
		return accts;
	}



	public BigDecimal getShortTermCostBasis(String acct) {
		if (acct == null || shortTermCostBasis == null) {
			return null;
		}
		return shortTermCostBasis.get(acct);
	}


	
	public String getShortTermCostBasisStr(String acct) {
		BigDecimal result = getShortTermCostBasis(acct);
		return (result != null) ? result.toPlainString() : "";
	}



	public BigDecimal getLongTermCostBasis(String acct) {
		if (acct == null || longTermCostBasis == null) {
			return null;
		}
		return longTermCostBasis.get(acct);
	}



	public String getLongTermCostBasisStr(String acct) {
		BigDecimal result = getLongTermCostBasis(acct);
		return (result != null) ? result.toPlainString() : "";
	}



	public BigDecimal getAvgCostBasis(String acct) {
		if (acct == null || avgCostBasis == null) {
			return null;
		}
		return avgCostBasis.get(acct);
	}



	public String getAvgCostBasisStr(String acct) {
		BigDecimal result = getAvgCostBasis(acct);
		return (result != null) ? result.toPlainString() : "";
	}



}
