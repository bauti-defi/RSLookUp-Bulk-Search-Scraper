package org.baiocchi.bulk.rslookupscraper.worker;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.baiocchi.bulk.rslookupscraper.Engine;
import org.baiocchi.bulk.rslookupscraper.util.Constants;

public abstract class Worker implements Runnable {
	private Timer failsafeTimer;
	private final int id;
	private static final DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	private String previousLog = "";
	protected volatile boolean running;

	enum Type {
		VERBOSE, ERROR, LOG;
	}

	public Worker(int id) {
		this.id = id;
	}

	public int getID() {
		return id;
	}

	protected void setFailsafeTimer(TimerTask task, long delay, long interval) {
		this.failsafeTimer = new Timer();
		failsafeTimer.scheduleAtFixedRate(task, delay, interval);
		print("Failsafe timer set!", Type.VERBOSE);
	}

	protected void print(String message, Type type) {
		if (!previousLog.equalsIgnoreCase(message)) {
			Date date = new Date();
			System.out.println(
					"[WORKER: " + id + "][>>" + type.toString() + "<<][" + dateFormat.format(date) + "]" + message);
			if (Constants.LOGGING) {
				log(message, type);
			}
			previousLog = message;
		}
	}

	private void log(String log, Type type) {
		if (Constants.LOGGING) {
			Date date = new Date();
			String newLog = "_____________________________________________________\n[WORKER: " + id + "][>>"
					+ type.toString() + "<<][" + dateFormat.format(date) + "]\n" + log
					+ "\n^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^\n";
			Engine.getInstance().processLog(newLog);
		}
	}

	protected void log(String log) {
		log(log, Type.LOG);
	}

}
