package bdn.cryptax.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

public class CapitalGainEntry {

	public static final String COL_TAX_YEAR = "Tax Year";
	public static final String COL_TERM = "Term";
	public static final String COL_DATE_ACQ = "Date Acquired";
	public static final String COL_DATE_DISP = "Date Disposed";
	public static final String COL_ASSET_AMNT = "Coin Amount";
	public static final String COL_PROCEEDS = "Proceeds";
	public static final String COL_COST_BASIS = "Cost Basis";
	public static final String COL_GAIN = "Gain";
	
	public static enum CapitalGainTerm {
		SHORTTERM, LONGTERM, UNKNOWN
	}
	
	private static final DateTimeFormatter DTF_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter DTF_YEAR = DateTimeFormatter.ofPattern("yyyy");
	
	private static HashMap<CapitalGainTerm, String> TERM_MAP = new HashMap<>();
	static {
		TERM_MAP.put(CapitalGainTerm.SHORTTERM, "Short-Term");
		TERM_MAP.put(CapitalGainTerm.LONGTERM, "Long-Term");
		TERM_MAP.put(CapitalGainTerm.UNKNOWN, "Unknown");
	}
	
	
	private LocalDate dateAcquired;
	private LocalDate dateDisposed;
	private BigDecimal assetAmnt;
	private BigDecimal proceeds;
	private BigDecimal costBasis;
	private BigDecimal gain;
	

	public CapitalGainEntry(LocalDate dateAcquired, LocalDate dateDisposed, BigDecimal assetAmnt,
			BigDecimal proceeds, BigDecimal costBasis, BigDecimal gain) {
		this.assetAmnt = assetAmnt;
		this.dateAcquired = dateAcquired;
		this.dateDisposed = dateDisposed;
		this.proceeds = proceeds;
		this.costBasis = costBasis;
		this.gain = gain;
	}


	public CapitalGainTerm getTerm() {
		CapitalGainTerm result = CapitalGainTerm.UNKNOWN;
		
		if (dateAcquired != null && dateDisposed != null) {
			if (dateDisposed.minusYears(1).isAfter(dateAcquired)) {
				result = CapitalGainTerm.LONGTERM;
			}
			else {
				result = CapitalGainTerm.SHORTTERM;
			}
		}
		
		return result;
	}
	
	
	public String getTermStr() {
		return TERM_MAP.get(getTerm());
	}


	public BigDecimal getAssetAmnt() {
		return assetAmnt;
	}
	
	
	public String getAssetAmntStr() {
		return (assetAmnt != null) ? assetAmnt.toPlainString() : "";
	}


	public void setAssetAmnt(BigDecimal assetAmnt) {
		this.assetAmnt = assetAmnt;
	}


	public LocalDate getDateAcquired() {
		return dateAcquired;
	}


	public String getDateAcquiredStr() {
		return (dateAcquired != null) ? dateAcquired.format(DTF_DATE) : "";
	}


	public void setDateAcquired(LocalDate dateAcquired) {
		this.dateAcquired = dateAcquired;
	}


	public LocalDate getDateDisposed() {
		return dateDisposed;
	}


	public String getDateDisposedStr() {
		return (dateDisposed != null) ? dateDisposed.format(DTF_DATE) : "";
	}
	
	
	public String getTaxYearStr() {
		return (dateDisposed != null) ? dateDisposed.format(DTF_YEAR) : "";
	}


	public void setDateDisposed(LocalDate dateDisposed) {
		this.dateDisposed = dateDisposed;
	}


	public BigDecimal getProceeds() {
		return proceeds;
	}

	
	public String getProceedsStr() {
		return (proceeds != null) ? proceeds.toPlainString() : "";
	}

	
	public void setProceeds(BigDecimal proceeds) {
		this.proceeds = proceeds;
	}


	public BigDecimal getCostBasis() {
		return costBasis;
	}


	public String getCostBasisStr() {
		return (costBasis != null) ? costBasis.toPlainString() : "";
	}

	
	public void setCostBasis(BigDecimal costBasis) {
		this.costBasis = costBasis;
	}


	public BigDecimal getGain() {
		return gain;
	}


	public String getGainStr() {
		return (gain != null) ? gain.toPlainString() : "";
	}

	
	public void setGain(BigDecimal gain) {
		this.gain = gain;
	}
	
	
	@Override
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append(getTaxYearStr()).append(",")
			.append(getTermStr()).append(",")
			.append(getDateAcquiredStr()).append(",")
			.append(getDateDisposedStr()).append(",")
			.append(getProceedsStr()).append(",")
			.append(getCostBasisStr()).append(",")
			.append(getGainStr());
		
		return buf.toString();
	}
	
}
