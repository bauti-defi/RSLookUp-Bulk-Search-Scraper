package org.baiocchi.bulk.rslookupscraper.util;

public class Data {

	private String database;
	private String username;
	private String password = "";
	private String email = "";
	private String IP = "";

	public Data() {
	}

	public String getUsername() {
		return username;
	}

	public String getDatabase() {
		return database;
	}

	public String getPassword() {
		return password;
	}

	public String getEmail() {
		return email;
	}

	public String getIP() {
		return IP;
	}

	public void setDatabase(String database) {
		this.database = database;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public void setIP(String IP) {
		this.IP = IP;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Data) {
			Data data = (Data) o;
			return data.getUsername().equals(database) && data.getUsername().equals(username)
					&& data.getEmail().equals(email) && data.getPassword().equals(password) && data.getIP().equals(IP);
		}
		return false;
	}

	@Override
	public String toString() {
		return database + "," + username + "," + email + "," + password + "," + IP;
	}

}
