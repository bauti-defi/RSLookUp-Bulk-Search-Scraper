package org.baiocchi.bulk.rslookupscraper.worker;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;

import org.baiocchi.bulk.rslookupscraper.util.Data;
import org.baiocchi.bulk.rslookupscraper.util.DataBlock;

public class DataSaver extends Worker {

	private LinkedBlockingQueue<DataBlock> data;
	private final String saveDirectory;

	public DataSaver(int id, File file) {
		super(id);
		this.saveDirectory = file.getAbsolutePath();
		data = new LinkedBlockingQueue<DataBlock>();
		running = true;
	}

	public DataSaver(int id, String filePath) {
		this(id, new File(filePath));
	}

	public void processData(DataBlock block) {
		try {
			data.put(block);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void write(String line) {
		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(saveDirectory + "/RSLookUp-Dump.txt", true), StandardCharsets.UTF_8));) {
			writer.write(line);
			writer.newLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		while (running) {
			DataBlock saveData = null;
			try {
				saveData = data.take();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			for (Data data : saveData.getData()) {
				write(data.toString());
			}
			print("Data saved to file!", Type.VERBOSE);
		}
	}

}
