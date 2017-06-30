package org.baiocchi.bulk.rslookupscraper;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import javax.swing.JFileChooser;
import javax.swing.JPanel;

import org.baiocchi.bulk.rslookupscraper.util.Account;
import org.baiocchi.bulk.rslookupscraper.util.AccountBlock;
import org.baiocchi.bulk.rslookupscraper.util.Constants;
import org.baiocchi.bulk.rslookupscraper.util.DataBlock;
import org.baiocchi.bulk.rslookupscraper.worker.AccountChecker;
import org.baiocchi.bulk.rslookupscraper.worker.DataSaver;
import org.baiocchi.bulk.rslookupscraper.worker.Logger;

public class Engine {

	private static Engine instance;
	private final LinkedBlockingQueue<AccountBlock> blocks;
	private final ArrayList<Thread> workers;
	private final DataSaver dataSaver;
	private String nameToStartAt = "";
	private int numberToStartAt = 0;
	private int workerCount = 3;
	private double accountCount = 0;
	private volatile double accountsChecked = 0;
	private final long startTime;
	private Logger logger;

	private Engine() {
		System.setProperty("https.protocols", "TLSv1.2");
		startTime = System.currentTimeMillis();
		blocks = new LinkedBlockingQueue<AccountBlock>();
		workers = new ArrayList<Thread>();
		if (!Constants.TESTING) {
			Console console = System.console();
			this.workerCount = Integer.parseInt(console.readLine("How many worker threads?"));
			nameToStartAt = console.readLine(
					"What username would you like to start scraping from? (Leave blank to start from the top)");
			if (nameToStartAt.equalsIgnoreCase("")) {
				String number = console.readLine("What number username would you like to start at?");
				if (!number.equalsIgnoreCase("")) {
					numberToStartAt = Integer.parseInt(number);
				}
			}
			JFileChooser fileChooser = new JFileChooser();
			fileChooser.setDialogTitle("Select directory to save results to!");
			fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			fileChooser.setAcceptAllFileFilterUsed(false);
			fileChooser.showOpenDialog(new JPanel());
			File selectedFile = fileChooser.getSelectedFile();
			if (Constants.LOGGING) {
				logger = new Logger(0, selectedFile);
			}
			dataSaver = new DataSaver(1, selectedFile);
		} else {
			if (Constants.LOGGING) {
				logger = new Logger(0, Constants.SAVE_DIRECTORY);
			}
			dataSaver = new DataSaver(1, Constants.SAVE_DIRECTORY);
		}
		if (Constants.LOGGING) {
			workers.add(new Thread(logger));
		}
		workers.add(new Thread(dataSaver));
	}

	public void start() {
		loadblocks();
		createWorkers();
		startWorkers();
	}

	private void loadblocks() {
		System.out.println("Loading accounts...");
		boolean noLimits = nameToStartAt.equalsIgnoreCase("") && numberToStartAt == 0;
		JFileChooser fileChooser = new JFileChooser();
		if (!Constants.TESTING) {
			fileChooser.setDialogTitle("Select username file!");
			fileChooser.setAcceptAllFileFilterUsed(false);
			fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			fileChooser.showOpenDialog(new JPanel());
		}
		try (BufferedReader reader = new BufferedReader(new FileReader(
				(Constants.TESTING ? Constants.ACCOUNTS_FILE : fileChooser.getSelectedFile().getAbsolutePath())))) {
			int lineCount = 1;
			String line;
			AccountBlock block = null;
			while ((line = reader.readLine()) != null) {
				String name = line.trim();
				if (!noLimits && (nameToStartAt.equalsIgnoreCase(name) || numberToStartAt == lineCount)) {
					noLimits = true;
				}
				if(name.length()<=3){
					lineCount++;
					continue;
				}
				if (noLimits) {
					if (block == null) {
						block = new AccountBlock(Constants.BLOCK_LIMIT);
					} else if (block.isFull()) {
						blocks.add(block);
						block = new AccountBlock(Constants.BLOCK_LIMIT);
					}
					// Integer.parseInt(indexs[0]), indexs[1], (indexs.length >
					// 2 ? indexs[2] : "")
					block.addAccount(new Account(0, name, ""));
				}
				lineCount++;
			}
		} catch (IOException e) {
			System.out.println("Failed to load accounts!");
			e.printStackTrace();
			System.exit(0);
		}
		accountCount = blocks.size() * Constants.BLOCK_LIMIT;
		System.out.println("Accounts loaded!");
	}

	private void createWorkers() {
		System.out.println("Creating workers...");
		for (int i = 2; i < (workerCount + 2); i++) {
			workers.add(new Thread(new AccountChecker(i)));
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Workers created!");
	}

	private void startWorkers() {
		System.out.println("Starting workers...");
		for (Thread thread : workers) {
			thread.start();
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Workers started!");
	}

	public void processLog(String log) {
		logger.processLog(log);
	}

	public void processData(DataBlock block) {
		dataSaver.processData(block);
		accountsChecked += Constants.BLOCK_LIMIT;
		System.out.println(getProgressString());
	}

	private String getProgressString() {
		double percentDone = accountsChecked / accountCount;
		int starCount = (int) (50 * percentDone);
		StringBuilder builder = new StringBuilder();
		builder.append("PROGRESS: [");
		for (int count = 0; count < 50; count++) {
			if (count <= starCount) {
				builder.append("*");
			} else {
				builder.append("_");
			}
		}
		builder.append("]\nDONE: " + (percentDone * 100) + "%, " + accountsChecked + "("
				+ getPerHour((int) accountsChecked) + " P/Hr)");
		return builder.toString();
	}

	private int getPerHour(final int gained) {
		return (int) ((gained) * 3600000D / (System.currentTimeMillis() - startTime));
	}

	public LinkedBlockingQueue<AccountBlock> getBlocks() {
		return blocks;
	}

	public static Engine getInstance() {
		return instance == null ? instance = new Engine() : instance;
	}

}
