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


	public void setType(MiningContractType type) {
		this.type = type;
	}


	public LocalDate getAcquisitionDate() {
		return acquisitionDate;
	}


	public void setAcquisitionDate(LocalDate acquisitionDate) {
		this.acquisitionDate = acquisitionDate;
	}


	public LocalDate getStartDate() {
		return startDate;
	}


	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}


	public LocalDate getEndDate() {
		return endDate;
	}


	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}


	public BigDecimal getTotalAmountUsd() {
		return totalAmountUsd;
	}


	public void setTotalAmountUsd(BigDecimal totalAmountUsd) {
		this.totalAmountUsd = totalAmountUsd;
	}


	public BigDecimal getPerDayAmountUsd() {
		return perDayAmountUsd;
	}


	public void setPerDayAmountUsd(BigDecimal perDayAmountUsd) {
		this.perDayAmountUsd = perDayAmountUsd;
	}
	
	
	
}
