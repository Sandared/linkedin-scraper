//DEPS com.microsoft.playwright:playwright:1.30.0
//DEPS info.picocli:picocli:4.7.1
//DEPS eu.easyrpa:easy-rpa-openframework-excel:1.0.0
//DEPS org.apache.logging.log4j:log4j-core:2.19.0

//SOURCES util/Util.java
//SOURCES util/SecondLvlDomains.java
//SOURCES util/LinkShortener.java
//SOURCES model/Company.java

//JAVAC_OPTIONS -encoding UTF8

package io.qbilon.linkedin;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import eu.easyrpa.openframework.excel.ExcelDocument;
import io.qbilon.linkedin.model.Company;
import io.qbilon.linkedin.util.LinkShortener;
import io.qbilon.linkedin.util.SecondLvlDomains;
import io.qbilon.linkedin.util.Util;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "scrapeCompanies", mixinStandardHelpOptions = true, version = "scrapeCompanies 1.0", description = "Scrapes companies from LinkedIn for a given set of locations, industries and sizes")
public class CompanyScraper implements Callable<Integer> {
    @Option(names = { "-e", "--email" }, description = "The email to be used for login in LinkedIn")
    private String email;
    @Option(names = { "-p", "--password" }, description = "The password to be used for login in LinkedIn")
    private String password;
    @Option(names = { "-l",
            "--locations" }, description = "The locations of companies that should be searched for. Use it like this: -l Deutschland -l Frankreich")
    private List<String> locations;
    @Option(names = { "-i",
            "--industries" }, description = "The industries of companies that should be searched for. Use it like this -i Fertigung -i Maschinenbau")
    private List<String> industries;
    @Option(names = { "-s",
            "--sizes" }, description = "The sizes of companies that should be searched for. Use it like this: -s 1000 -s 5000")
    private List<String> sizes;
    @Option(names = { "-v",
            "--verbose" }, description = "Toggles verbose mode, e.g., prints exceptions")
    private boolean verbose;
    @Option(names = {
            "-limit" }, description = "An optional limit to use for the maximum amount of companies to scrape", defaultValue = "-1")
    private int limit;

    private List<String> translatedSizes;

    private Path currentDir = Paths.get("").toAbsolutePath();
    private Path pathToContext = currentDir.resolve("state.json").toAbsolutePath();
    private Path pathToExcel = currentDir.resolve("companies.xlsx").toAbsolutePath();
    private SecondLvlDomains slds = new SecondLvlDomains();
    private LinkShortener shortener = new LinkShortener();

    private Map<String, String> companySizesMap = Map.of(
            "10", "companySize-B",
            "50", "companySize-C",
            "200", "companySize-D",
            "500", "companySize-E",
            "1000", "companySize-F",
            "5000", "companySize-G",
            "10000", "companySize-H",
            "10001+", "companySize-I");

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CompanyScraper()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        validateInput();

        System.out.println();
        System.out.println("Starting LinkedIn Company Scraper in directory " + currentDir + " with:");
        System.out.println("\temail = " + email);
        System.out.println("\tpassword = " + password);
        System.out.println("\tlocations = " + String.join(", ", locations));
        System.out.println("\tindustries = " + String.join(", ", industries));
        System.out.println("\tsizes = " + String.join(", ", sizes));
        System.out.println("\tlimit = " + limit);
        System.out.println();

        try (Playwright playwright = Playwright.create()) {
            run(playwright);
        } catch (Exception e) {
            if (verbose) {
                e.printStackTrace();
            }
            System.out.println(
                    "Something went wrong. Sometimes this is due to a timing error. In this case just retry it. If the error persists, you could toggle verbose mode with '-v' in order to get more information");
            return 1;
        }
        return 0;
    }

    private void validateInput() {
        // only used for local testing!
        if (email == null || email.isBlank()) {
            System.out.println("You need to provide an email!");
            System.exit(1);
        }
        if (password == null || password.isBlank()) {
            System.out.println("You need to provide a password!");
            System.exit(1);
        }
        if (locations == null || locations.isEmpty()) {
            System.out.println("You need to provide at least one location!");
            System.exit(1);
        }
        if (industries == null || industries.isEmpty()) {
            System.out.println("You need to provide at least one industry!");
            System.exit(1);
        }
        if (sizes == null || sizes.isEmpty()) {
            System.out.println("You need to provide at least one company size!");
            System.exit(1);
        }
        translatedSizes = sizes.stream().map(size -> companySizesMap.get(size)).collect(Collectors.toList());
    }

    private void run(Playwright playwright) throws MalformedURLException, ParseException {
        Browser browser = Util.createBrowser(playwright, pathToContext);
        BrowserContext context = browser.contexts().get(0);
        Page page = Util.loginToLinkedIn(context, email, password);

        navigateToInitialSearchPage(page);

        int currentPage = 1;
        Map<String, List<String>> urlParams = Util.urlParams(new URL(page.url()));
        List<Company> companies = new ArrayList<>();

        System.out.println();
        while (!Util.isEmptySearchPage(page) && (limit == -1 || limit >= companies.size())) {
            System.out.println("Scraping raw data for company search page " + currentPage);
            scrapeRawCompanies(page, companies);
            currentPage++;
            page.navigate(createCompanySearchUrl(urlParams, currentPage));
            Util.wait(1000, 100);
        }

        System.out.println();
        int currentCount = 1;
        long start = System.currentTimeMillis();
        for (Company company : companies) {
            long currentTime = System.currentTimeMillis();
            long elapsed = currentTime - start;
            System.out.println("(" + currentCount + "/" + companies.size() + ") - " + toTime(elapsed) + " Scraping augmented data for "
                    + company.getName() + ". ");
            scrapeAugmentedCompany(page, company);
            Util.wait(1000, 100);
            currentCount++;
        }

        Util.touchFile(pathToExcel);
        ExcelDocument doc = new ExcelDocument();
        doc.getActiveSheet().insertTable("A1", companies);
        doc.saveAs(pathToExcel.toString());
        doc.close();

        // Save current browser state
        context.storageState(new BrowserContext.StorageStateOptions().setPath(pathToContext));
        context.close();
        browser.close();

        System.out.println();
        System.out.println("Finished Scraping!");
        System.out.println("Please review the domains of the scraped companies under " + pathToExcel.toString()
                + "! They might contain link shortener links");
    }

    private String toTime(long millis) {
        return String.format("%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) -
                        TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
    }

    private void scrapeAugmentedCompany(Page page, Company company) throws MalformedURLException, ParseException {
        try {
            page.navigate(company.getLink());
            page.waitForSelector("dl.overflow-hidden");
            Locator infoTable = page.locator("dl.overflow-hidden");
            // get all children via xpath
            Locator infos = infoTable.locator("xpath=*");
            String currentHeading = "";
            for (Locator info : infos.all()) {
                String tagName = info.elementHandle().getProperty("tagName").toString();
                if (tagName.equalsIgnoreCase("dt")) {
                    currentHeading = info.innerText().trim();
                } else {
                    if ("Branche".equalsIgnoreCase(currentHeading)) {
                        company.setIndustry(info.innerText().trim());
                    }
                    if ("Größe".equalsIgnoreCase(currentHeading)) {
                        String text = info.innerText().trim();
                        if (text.contains(" auf LinkedIn")) {
                            company.setEmployeesOnLinkedIn(text.substring(0, text.indexOf(" auf LinkedIn")));
                        } else {
                            company.setSize(text);
                        }
                    }
                    if ("Website".equalsIgnoreCase(currentHeading)) {
                        company.setDomain(getDomain(info.innerText().trim()));
                    }
                }
            }
            String domain = company.getDomain();
            if (shortener.contains(domain)) {
                System.out.println("\tWARNING: Detected link shortener for domain of " + company.getName());
            }
        } catch (Exception e) {
            System.out.println(
                    "\tSomething went wrong while fetching augmented data for " + company.getName() + "! Skipt it!");
            if (verbose) {
                e.printStackTrace();
            }
        }
    }

    private String getDomain(String urlString) throws MalformedURLException {
        URL url = new URL(urlString);
        String host = url.getHost();
        String[] segments = host.split("\\.");
        // take the last two segments
        String seg1 = segments[segments.length - 2];
        String seg2 = segments[segments.length - 1];
        String domain = seg1 + "." + seg2;
        if (segments.length > 2) {
            // maybe its a second level domain?
            if (slds.contains(domain)) {
                // take the last three segments
                domain = segments[segments.length - 3] + "." + domain;
            }
        }
        return domain;
    }

    private void scrapeRawCompanies(Page page, List<Company> companies) {
        page.waitForSelector(".search-results-container");
        Locator resultContainer = page.locator(".search-results-container");
        Locator resultItems = resultContainer.locator("li.reusable-search__result-container");
        for (Locator resultItem : resultItems.all()) {
            try {
                if (limit == -1 || limit >= companies.size()) {
                    Company company = new Company();
                    Locator titleSpan = resultItem.locator("span.entity-result__title-text");
                    company.setName(titleSpan.textContent().trim());
                    company.setLink(titleSpan.locator("a").getAttribute("href") + "about");
                    companies.add(company);
                } else {
                    System.out.println("Reached limit of " + limit + " companies!");
                    break;
                }
            } catch (Exception e) {
                System.out.println("Something went wrong during try to fetch a raw company dataset. Skip it!");
                if (verbose) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String createCompanySearchUrl(Map<String, List<String>> urlParams, int currentPage) {
        String result = Util.createUrl(
                "https://www.linkedin.com/search/results/companies/",
                urlParams,
                List.of(
                        "companyHqGeo",
                        "companySize",
                        "industryCompanyVertical",
                        "origin=FACETED_SEARCH",
                        "page=" + currentPage,
                        "sid"));
        return result;
    }

    private void navigateToInitialSearchPage(Page page) {
        System.out.println("Navigating to search page ...");
        page.navigate("https://www.linkedin.com/search/results/companies/?origin=SWITCH_SEARCH_VERTICAL");
        Util.buttonWithInput(page, "Standorte", "Ort hinzufügen", locations);
        Util.buttonWithInput(page, "Branche", "Branche hinzufügen", industries);
        Util.buttonWithMultiSelection(page, "Unternehmensgröße", translatedSizes);
        // if we do not wait until all parts are in the url the url is incorrect
        page.waitForURL(url -> url.contains("companySize") &&
                url.contains("industryCompanyVertical") &&
                url.contains("companyHqGeo") &&
                url.contains("sid"));
    }
}
