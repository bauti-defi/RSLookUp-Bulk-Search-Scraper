package org.baiocchi.bulk.rslookupscraper.worker;

public abstract class Worker implements Runnable {
	private final int id;
	private String previousLog = "";

	enum Type {
		VERBOSE, ERROR;
	}

	public Worker(int id) {
		this.id = id;
		log("Created!", Type.VERBOSE);
	}

	public int getID() {
		return id;
	}

	protected void log(String message, Type type) {
		if (!previousLog.equalsIgnoreCase(message)) {
			System.out.println("[WORKER: " + id + "][>>" + type.toString() + "<<]" + message);
			previousLog = message;
		}
	}

}
