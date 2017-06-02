package org.baiocchi.bulk.rslookupscraper.worker;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
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
	private int expandCounter = 0;
	private boolean running;

	public AccountChecker(int id) {
		super(id);
		client = getNewClient();
		running = true;
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
			case Constants.LOGIN_URL:
				log("Handling login...");
				final HtmlForm form = currentPage.getFirstByXPath("//form[@action='https://rslookup.com/login']");
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
							log("Failed to handle login!");
						}
						waitForJavascriptToExecute();
						log("Login handled!");
						break;
					}
					log("Failed to handle login!");
				}
				break;
			case Constants.SEARCH_URL:
				if (currentPage != null) {
					if (!handlingJavascript) {
						final HtmlTextArea searchField = currentPage.getElementByName("query");
						final HtmlButton searchButton = (HtmlButton) currentPage
								.getFirstByXPath(Constants.SEARCH_BUTTON_XPATH);
						if (searchField != null && searchField.isDisplayed()) {
							log("Searching...");
							StringBuilder searchString = new StringBuilder();
							for (final Account account : block.getAccounts()) {
								searchString.append(account.getUsername() + "\n");
							}
							try {
								searchField.type(searchString.toString());
								currentPage = searchButton.click();
							} catch (IOException e1) {
								e1.printStackTrace();
								log("Failed to search!");
								break;
							}
							waitForJavascriptToExecute();
							handlingJavascript = true;
							log("Search query finished!");
						}
					} else if (handlingJavascript) {
						if (currentPage.asText().toLowerCase().contains("view results...")) {
							if (expandCounter >= 7) {
								log("Detected an expantion error. Restarting client!");
								client = getNewClient();
								break;
							}
							final List<HtmlSpan> greenTexts = currentPage.getByXPath("//span");
							if (greenTexts != null && !greenTexts.isEmpty()) {
								log("Expanding tables...");
								for (final HtmlSpan greenText : greenTexts) {
									if (greenText.isDisplayed() && greenText.getId().toLowerCase().contains("result")
											&& !greenText.getId().toLowerCase().contains("hideresult")) {
										if (greenText.asXml().toString().contains("display")) {
											if (!greenText.asXml().toString().contains("incline")) {
												executeJavascript(greenText);
											}
										} else {
											executeJavascript(greenText);
										}
									}
								}
								waitForJavascriptToExecute();
								expandCounter++;
								log("Tables expanded!");
							}
						}
						if (currentPage.asText().toLowerCase().contains("search in hash db")) {
							log("Handling hashes...");
							final List<HtmlSpan> greenTexts = currentPage
									.getByXPath("//table[@class='table table-bordered']/tbody/tr/td/span");
							if (greenTexts != null && !greenTexts.isEmpty()) {
								for (final HtmlSpan greenText : greenTexts) {
									if (greenText.asText().equalsIgnoreCase("Search in hash DB")) {
										ScriptResult result = currentPage
												.executeJavaScript(greenText.getOnClickAttribute());
										currentPage = (HtmlPage) result.getNewPage();
									}
								}
								waitForJavascriptToExecute();
							}
							log("Hashes handled!");
						} else {
							log("Scraping results...");
							final List<HtmlTableBody> tableBodies = currentPage.getByXPath("//tbody");
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
															dataEntry.setDatabase(cell.asText());
														}
														break;
													case 1:
														if (!cell.asText().isEmpty()) {
															dataEntry.setUsername(cell.asText());
														}
														break;
													case 2:
														if (!cell.asText().isEmpty()) {
															dataEntry.setEmail(cell.asText());
														}
														break;
													case 3:
														if (!cell.asText().isEmpty()) {
															dataEntry.setPassword(cell.asText());
														}
														break;
													case 4:
														if (!cell.asText().isEmpty()) {
															dataEntry.setIP(cell.asText());
														}
														break;
													}
												}
											}
											block.put(dataEntry);
										}
									}
								}
								Engine.getInstance().processData(block);
								this.block = null;
								handlingJavascript = false;
							}
							log("Data scraped!");
						}
					}
				}
				break;
			default:
				try {
					log("Going back to search page...");
					currentPage = client.getPage(Constants.SEARCH_URL);
				} catch (FailingHttpStatusCodeException | IOException e) {
					e.printStackTrace();
				}
				break;
			}
		}
	}

	private void waitForJavascriptToExecute() {
		JavaScriptJobManager manager = currentPage.getEnclosingWindow().getJobManager();
		while (manager.getJobCount() > 0) {
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private void executeJavascript(HtmlElement element) {
		ScriptResult result = currentPage.executeJavaScript(element.getOnClickAttribute());
		currentPage = (HtmlPage) result.getNewPage();
	}

	private WebClient getNewClient() {
		expandCounter = 0;
		handlingJavascript = false;
		WebClient client = new WebClient(BrowserVersion.CHROME);
		setWebClientSettings(client);
		try {
			currentPage = client.getPage(Constants.SEARCH_URL);
		} catch (FailingHttpStatusCodeException | IOException e) {
			e.printStackTrace();
		}
		return client;
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
		webClient.setAjaxController(new AjaxController());
		webClient.waitForBackgroundJavaScript(1000);
		webClient.getOptions().setGeolocationEnabled(false);
		webClient.getOptions().setRedirectEnabled(true);
		webClient.getOptions().setAppletEnabled(false);
		webClient.getOptions().setPrintContentOnFailingStatusCode(false);
		webClient.getOptions().setCssEnabled(false);
		webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
		webClient.getOptions().setThrowExceptionOnScriptError(false);
		webClient.getOptions().setJavaScriptEnabled(true);
		webClient.getOptions().setDownloadImages(false);
		webClient.getCache().clear();
		webClient.setJavaScriptTimeout(30000);
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