package bdn.cryptax.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class UnrealizedGainEntry extends GainEntry {

	public UnrealizedGainEntry(LocalDate dateAcquired, String brokerAcquired, BigDecimal assetAmnt, BigDecimal costBasis) {
		super(dateAcquired, null, brokerAcquired, null, assetAmnt, null, costBasis, null);
	}


}
