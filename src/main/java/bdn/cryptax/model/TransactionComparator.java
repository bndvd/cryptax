package bdn.cryptax.model;

import java.util.Comparator;

public class TransactionComparator implements Comparator<Transaction> {

	@Override
	public int compare(Transaction l, Transaction r) {
		if (l == null || r == null || l.getTxnDttm() == null || r.getTxnDttm() == null) {
			return 0;
		}
		return l.getTxnDttm().compareTo(r.getTxnDttm());
	}
}
