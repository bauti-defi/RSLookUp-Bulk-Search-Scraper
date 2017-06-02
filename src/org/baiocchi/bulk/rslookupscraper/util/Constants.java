package org.baiocchi.bulk.rslookupscraper.util;

public class Constants {

	public final static String SAVE_DIRECTORY = "/home/bautista/Desktop/RSPS Account Hacker";
	public final static String ACCOUNTS_FILE = "/home/bautista/Desktop/RSPS Account Hacker/High Scores/aloraHighscores.csv";
	public final static String LOGIN_URL = "https://rslookup.com/login";
	public final static String SEARCH_URL = "https://rslookup.com/bulk";
	public final static String TERMS_URL = "https://rslookup.com/terms";
	public final static String I_AGREE_XPATH = "//*[@id=\"services\"]/div/div/form/button";
	public final static String USERNAME_FIELD_XPATH = "/html/body/div[1]/form/input[2]";
	public final static String PASSWORD_FIELD_XPATH = "/html/body/div[1]/form/input[3]";
	public final static String USERNAME = "bautista";
	public final static String PASSWORD = "bautista";
	public final static String SEARCH_FIELD_XPATH = "//*[@id=\"query\"]";
	public final static String SEARCH_RESULTS_TABLE_XPATH = "//*[@id=\"results\"]/table";
	public final static String SEARCH_BUTTON_XPATH = "//button[1]";
	public final static int BLOCK_LIMIT = 20;
	public final static boolean TESTING = true;
}
