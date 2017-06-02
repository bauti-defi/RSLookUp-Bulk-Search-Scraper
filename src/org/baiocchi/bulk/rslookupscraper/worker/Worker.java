package org.baiocchi.bulk.rslookupscraper.worker;

public abstract class Worker implements Runnable {
	private final int id;
	private String previousLog = "";

	public Worker(int id) {
		this.id = id;
	}

	public int getID() {
		return id;
	}

	protected void log(String message) {
		if (!previousLog.equalsIgnoreCase(message)) {
			System.out.println("[WORKER: " + id + "] " + message);
			previousLog = message;
		}
	}

}
