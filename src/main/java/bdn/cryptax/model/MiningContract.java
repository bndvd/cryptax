package bdn.cryptax.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class MiningContract {

	public static enum MiningContractType {
		PURCHASE, REINVESTMENT
	}
	
	private MiningContractType type;
	private LocalDate acquisitionDate;
	// start and end dates inclusive (start date is 1 day after contract purchase date, so it's effective on startDate)
	private LocalDate startDate;
	private LocalDate endDate;
	private BigDecimal totalAmountUsd;
	private BigDecimal perDayAmountUsd;
	
	
	public MiningContract(MiningContractType type, LocalDate acquisitionDate, LocalDate startDate, LocalDate endDate, BigDecimal totalAmountUsd,
			BigDecimal perDayAmountUsd) {
		this.type = type;
		this.acquisitionDate = acquisitionDate;
		this.startDate = startDate;
		this.endDate = endDate;
		this.totalAmountUsd = totalAmountUsd;
		this.perDayAmountUsd = perDayAmountUsd;
	}


	public MiningContractType getType() {
		return type;
	}


	public LocalDate getAcquisitionDate() {
		return acquisitionDate;
	}


	public LocalDate getStartDate() {
		return startDate;
	}


	public LocalDate getEndDate() {
		return endDate;
	}


	public BigDecimal getTotalAmountUsd() {
		return totalAmountUsd;
	}


	public BigDecimal getPerDayAmountUsd() {
		return perDayAmountUsd;
	}


	
	
}
