package org.baiocchi.bulk.rslookupscraper.util;

import java.util.ArrayList;

public class AccountBlock {

	private final ArrayList<Account> block;
	private final int limit;

	public AccountBlock(int limit) {
		this.limit = limit;
		block = new ArrayList<Account>();
	}

	public void addAccount(Account account) {
		block.add(account);
	}

	public ArrayList<Account> getAccounts() {
		return block;
	}

	public Account pull() {
		if (isEmpty()) {
			return null;
		}
		Account account = block.get(0);
		block.remove(0);
		return account;
	}

	public int getLimit() {
		return limit;
	}

	public boolean isEmpty() {
		return block.isEmpty();
	}

	public boolean isFull() {
		return block.size() >= limit;
	}

}
