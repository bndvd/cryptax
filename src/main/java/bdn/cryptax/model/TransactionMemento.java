package bdn.cryptax.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionMemento {
	// Dttm of transaction
	public LocalDateTime dttm = null;
	// Coin amount of transaction
	public BigDecimal coinAmnt = null;
	// Effective USD/Coin unit rate (this includes broker fees used to acquire the coin)
	public BigDecimal effUsdPerUnit = null;
	
	public TransactionMemento() {}
	
	@Override
	public String toString() {
		return new String("" + dttm + " Coin:" + coinAmnt + " USD/Coin:" + effUsdPerUnit);
	}
}

