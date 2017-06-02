package org.baiocchi.bulk.rslookupscraper.util;

import java.util.ArrayList;

public class DataBlock {

	private final ArrayList<Data> block;

	public DataBlock() {
		this.block = new ArrayList<Data>();
	}

	public void put(Data data) {
		if (!block.contains(data)) {
			block.add(data);
		}
	}

	public ArrayList<Data> getData() {
		return block;
	}

	public boolean isEmpty() {
		return block.isEmpty();
	}

}
