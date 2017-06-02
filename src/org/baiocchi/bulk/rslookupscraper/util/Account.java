package org.baiocchi.bulk.rslookupscraper.util;

public class Account {
	private final int rank;
	private final String username;
	private final String donorStatus;

	public Account(int rank, String username, String donorStatus) {
		this.rank = rank;
		this.username = username;
		this.donorStatus = donorStatus;
	}

	public String getDonorStatus() {
		return donorStatus;
	}

	public int getRank() {
		return rank;
	}

	public String getUsername() {
		return username;
	}

	@Override
	public String toString() {
		return rank + "::" + username + "::" + donorStatus;
	}

}
