package org.baiocchi.bulk.rslookupscraper.worker;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.TimerTask;
import java.util.logging.Level;

import org.apache.commons.logging.LogFactory;
import org.baiocchi.bulk.rslookupscraper.Engine;
import org.baiocchi.bulk.rslookupscraper.util.Account;
import org.baiocchi.bulk.rslookupscraper.util.AccountBlock;
import org.baiocchi.bulk.rslookupscraper.util.Constants;
import org.baiocchi.bulk.rslookupscraper.util.Data;
import org.baiocchi.bulk.rslookupscraper.util.DataBlock;

import com.gargoylesoftware.htmlunit.AjaxController;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.CookieManager;
import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.ScriptResult;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSpan;
import com.gargoylesoftware.htmlunit.html.HtmlTableBody;
import com.gargoylesoftware.htmlunit.html.HtmlTableCell;
import com.gargoylesoftware.htmlunit.html.HtmlTableRow;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.gargoylesoftware.htmlunit.javascript.background.JavaScriptJobManager;
import com.gargoylesoftware.htmlunit.util.Cookie;

public class AccountChecker extends Worker {
	private WebClient client;
	private HtmlPage currentPage;
	private boolean handlingJavascript = false;
	private AccountBlock block = null;
	private int failsafe = 0;
	private boolean forceScrape;
	private int timerFlag = 0;
	private int currentTimerFlag = 0;

	public AccountChecker(int id) {
		super(id);
		startNewClient();
		running = true;
		setFailsafeTimer(new TimerTask() {

			@Override
			public void run() {
				if (timerFlag == currentTimerFlag) {
					running = false;
					print("Failsafe timer triggered!", Type.ERROR);
					log(currentPage.asXml());
					startNewClient();
					log(currentPage.asXml());
					running = true;
				}
				timerFlag = currentTimerFlag;
			}

		}, 180000, 180000);
	}

	@Override
	public void run() {
		while (running) {
			if (block == null || block.isEmpty()) {
				try {
					block = Engine.getInstance().getBlocks().take();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			switch (currentPage.getUrl().toExternalForm()) {
			case Constants.TERMS_URL:
				print("Handling terms page...", Type.VERBOSE);
				HtmlButton acceptButton = null;
				try {
					acceptButton = (HtmlButton) currentPage
							.getFirstByXPath("//button[@class='btn btn-success btn-lg']");
				} catch (ElementNotFoundException e) {
					print("Terms form not found!", Type.ERROR);
					continue;
				}
				if (acceptButton != null & acceptButton.isDisplayed()) {
					try {
						currentPage = acceptButton.click();
					} catch (IOException e) {
						print("Failed to handle terms page!", Type.ERROR);
						log(currentPage.asXml());
						e.printStackTrace();
					}
					// waitForJavascriptToExecute();
					currentTimerFlag++;
					print("Terms page handled!", Type.VERBOSE);
					break;
				}
				print("Failed to handle terms page!", Type.ERROR);
				break;
			case Constants.LOGIN_URL:
				print("Handling login...", Type.VERBOSE);
				HtmlForm form = null;
				try {
					form = currentPage.getFirstByXPath("//form[@action='https://rslookup.com/login']");
				} catch (ElementNotFoundException e) {
					print("Login form not found!", Type.ERROR);
					log(currentPage.asXml());
					continue;
				}
				if (form != null) {
					final HtmlTextInput usernameField = form.getInputByName("username");
					usernameField.setValueAttribute(Constants.USERNAME);
					final HtmlInput passwordField = form.getInputByName("password");
					passwordField.setValueAttribute(Constants.PASSWORD);
					final HtmlButton loginButton = (HtmlButton) form.getElementsByTagName("button").get(0);
					if (loginButton != null && loginButton.isDisplayed()) {
						try {
							currentPage = loginButton.click();
						} catch (IOException e1) {
							e1.printStackTrace();
							print("Failed to handle login!", Type.ERROR);
							log(currentPage.asXml());
						}
						waitForJavascriptToExecute();
						print("Login handled!", Type.VERBOSE);
						currentTimerFlag++;
						break;
					}
					print("Failed to handle login!", Type.ERROR);
				}
				break;
			case Constants.SEARCH_URL:
				if (currentPage != null && currentPage.isDisplayed()) {
					if (!handlingJavascript) {
						HtmlTextArea searchField = null;
						HtmlButton searchButton = null;
						try {
							searchField = currentPage.getElementByName("query");
							searchButton = (HtmlButton) currentPage.getFirstByXPath(Constants.SEARCH_BUTTON_XPATH);
						} catch (ElementNotFoundException e) {
							print("Search elements not found!", Type.ERROR);
							log(currentPage.asXml());
							continue;
						}
						if (searchField != null && searchField.isDisplayed()) {
							print("Searching...", Type.VERBOSE);
							StringBuilder searchString = new StringBuilder();
							for (final Account account : block.getAccounts()) {
								searchString.append(account.getUsername() + "\n");
							}
							log(searchString.toString());
							try {
								searchField.type(searchString.toString());
								if (searchButton != null && searchButton.isDisplayed()) {
									currentPage = searchButton.click();
									log("Clicked search!");
								}
							} catch (IOException e1) {
								e1.printStackTrace();
								print("Failed to search!", Type.ERROR);
								log(currentPage.asXml());
								break;
							}
							waitForJavascriptToExecute();
							if (currentPage.asText()
									.contains(block.getAccounts().get(block.getAccounts().size() - 1).getUsername())) {
								handlingJavascript = true;
								currentTimerFlag++;
								print("Search query finished!", Type.VERBOSE);
							}
						}
					} else if (handlingJavascript) {
						if (!forceScrape && currentPage.asXml().contains(
								" style=\"color: green; font-weight: bold; cursor: pointer; font-size: 10px;\"")) {
							print("Handling hashes...", Type.VERBOSE);
							if (failsafe > 5) {
								print("Error handling hashes! Initiating force scrape!", Type.ERROR);
								forceScrape = true;
								currentTimerFlag++;
								break;
							}
							List<HtmlSpan> greenTexts = null;
							try {
								greenTexts = currentPage
										.getByXPath("//table[@class='table table-bordered']/tbody/tr/td/span");
							} catch (ElementNotFoundException e) {
								print("Green texts not found!", Type.ERROR);
								log(currentPage.asXml());
								continue;
							}
							if (greenTexts != null && !greenTexts.isEmpty()) {
								for (final HtmlSpan greenText : greenTexts) {
									if (greenText.asXml().contains(
											" style=\"color: green; font-weight: bold; cursor: pointer; font-size: 10px;\"")) {
										executeJavascript(greenText);
									}
								}
								waitForJavascriptToExecute();
								failsafe++;
								currentTimerFlag++;
							}
							print("Hashes handled!", Type.VERBOSE);
						} else {
							print("Scraping results...", Type.VERBOSE);
							List<HtmlTableBody> tableBodies = null;
							try {
								tableBodies = currentPage.getByXPath("//tbody");
							} catch (ElementNotFoundException e) {
								print("Tables not found!", Type.ERROR);
								log(currentPage.asXml());
								continue;
							}
							if (tableBodies != null && !tableBodies.isEmpty()) {
								final DataBlock block = new DataBlock();
								for (final HtmlTableBody tableBody : tableBodies) {
									if (tableBody.isDisplayed()) {
										for (final HtmlTableRow row : tableBody.getRows()) {
											final Data dataEntry = new Data();
											for (final HtmlTableCell cell : row.getCells()) {
												if (cell.isDisplayed()) {
													switch (cell.getIndex()) {
													case 0:
														if (!cell.asText().isEmpty()) {
															if (cell.asText().length() <= 50) {
																dataEntry.setDatabase(cell.asText());
															} else {
																print("Database Cell format exception detected!",
																		Type.ERROR);
																log(cell.asText());
																continue;
															}
														}
														break;
													case 1:
														if (!cell.asText().isEmpty()) {
															if (cell.asText().length() <= 50) {
																dataEntry.setUsername(cell.asText());
															} else {
																print("Username Cell format exception detected!",
																		Type.ERROR);
																log(cell.asText());
																continue;
															}
														}
														break;
													case 2:
														if (!cell.asText().isEmpty()) {
															if (cell.asText().length() <= 50) {
																dataEntry.setEmail(cell.asText());
															} else {
																print("Email Cell format exception detected!",
																		Type.ERROR);
																log(cell.asText());
																continue;
															}
														}
														break;
													case 3:
														if (!cell.asText().isEmpty()) {
															if (cell.asText().length() <= 50) {
																dataEntry.setPassword(cell.asText());
															} else {
																print("Password Cell format exception detected!",
																		Type.ERROR);
																log(cell.asText());
																continue;
															}
														}
														break;
													case 4:
														if (!cell.asText().isEmpty()) {
															if (cell.asText().length() <= 50) {
																dataEntry.setIP(cell.asText());
															} else {
																print("IP Cell format exception detected!", Type.ERROR);
																log(cell.asText());
																continue;
															}
														}
														break;
													}
												}
											}
											block.put(dataEntry);
										}
									}
								}
								currentTimerFlag++;
								Engine.getInstance().processData(block);
								this.block = null;
								resetVariables();
							}
							print("Data scraped!", Type.VERBOSE);
						}
					}
				}
				break;
			default:
				currentTimerFlag++;
				log(currentPage.asXml());
				goToSearchPage();
				log(currentPage.asXml());
				break;
			}
		}
		do {
			print("Pausing!", Type.VERBOSE);
			sleep(3000);
		} while (!running);
		if (running) {
			print("Resuming!", Type.VERBOSE);
			run();
		}
	}

	private void goToSearchPage() {
		try {
			print("Going back to search page...", Type.VERBOSE);
			currentPage.getEnclosingWindow().getJobManager().removeAllJobs();
			currentPage = client.getPage(Constants.SEARCH_URL);
			waitForJavascriptToExecute();
			sleep(8000);
		} catch (FailingHttpStatusCodeException | IOException e) {
			e.printStackTrace();
		}
	}

	private void waitForJavascriptToExecute() {
		JavaScriptJobManager manager = currentPage.getEnclosingWindow().getJobManager();
		log("Waiting on javascript...");
		while (manager.getJobCount() > 0) {
			sleep(500);
		}
		log("Javascript finished!");
	}

	private void executeJavascript(HtmlElement element) {
		ScriptResult result = currentPage.executeJavaScript(element.getOnClickAttribute());
		currentPage = (HtmlPage) result.getNewPage();
	}

	private void resetVariables() {
		failsafe = 0;
		forceScrape = false;
		handlingJavascript = false;
	}

	private void startNewClient() {
		print("Starting new client...", Type.VERBOSE);
		resetVariables();
		if (client != null) {
			client.close();
		}
		WebClient client = new WebClient(BrowserVersion.BEST_SUPPORTED);
		setWebClientSettings(client);
		this.client = client;
		try {
			currentPage = this.client.getPage(Constants.SEARCH_URL);
		} catch (FailingHttpStatusCodeException | IOException e) {
			e.printStackTrace();
		}
		print("New client started!", Type.VERBOSE);
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void setWebClientSettings(WebClient webClient) {
		CookieManager cookieManager = new CookieManager();
		cookieManager.setCookiesEnabled(true);
		webClient.setCookieManager(cookieManager);
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.HOUR, 1);
		Cookie cookie = new Cookie("rslookup.com", "tos", "true", "/", cal.getTime(), false);
		cookieManager.addCookie(cookie);
		webClient.setAjaxController(new NicelyResynchronizingAjaxController());
		webClient.getOptions().setSSLClientProtocols(new String[] { "TLSv1.2" });
		webClient.setAjaxController(new AjaxController());
		webClient.getOptions().setGeolocationEnabled(false);
		webClient.getOptions().setUseInsecureSSL(true);
		webClient.getOptions().setTimeout(180000);
		webClient.getOptions().setRedirectEnabled(true);
		webClient.getOptions().setAppletEnabled(false);
		webClient.getOptions().setPrintContentOnFailingStatusCode(false);
		webClient.getOptions().setCssEnabled(false);
		webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
		webClient.getOptions().setThrowExceptionOnScriptError(false);
		webClient.getOptions().setJavaScriptEnabled(true);
		webClient.getOptions().setDownloadImages(false);
		webClient.getCache().clear();
		webClient.setJavaScriptTimeout(60000);
		webClient.getOptions().setPopupBlockerEnabled(true);
		webClient.getOptions().isDoNotTrackEnabled();
		webClient.getOptions().setUseInsecureSSL(true);
		LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log",
				"org.apache.commons.logging.impl.NoOpLog");
		java.util.logging.Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);
		java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
		java.util.logging.Logger.getLogger("org.apache").setLevel(Level.OFF);
		java.util.logging.Logger.getLogger("org.apache.http.client.protocol.ResponseProcessCookies")
				.setLevel(Level.OFF);

	}
}
