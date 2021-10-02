package bdn.cryptax.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CapitalGainEntry extends GainEntry {

	public CapitalGainEntry(LocalDate dateAcquired, LocalDate dateDisposed, String brokerAcquired, String brokerDisposed,
			BigDecimal assetAmnt, BigDecimal proceeds, BigDecimal costBasis, BigDecimal gain) {
		super(dateAcquired, dateDisposed, brokerAcquired, brokerDisposed, assetAmnt, proceeds, costBasis, gain);
	}


}
