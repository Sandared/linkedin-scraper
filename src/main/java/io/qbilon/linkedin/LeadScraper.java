//DEPS com.microsoft.playwright:playwright:1.30.0
//DEPS info.picocli:picocli:4.7.1
//DEPS eu.easyrpa:easy-rpa-openframework-excel:1.0.0
//DEPS org.apache.logging.log4j:log4j-core:2.19.0

//SOURCES util/Util.java
//SOURCES util/SpecialChars.java
//SOURCES util/JobDescriptors.java
//SOURCES util/RemovableNameSegments.java

//SOURCES model/Lead.java
//SOURCES model/Company.java
//SOURCES model/Contact.java

//JAVAC_OPTIONS -encoding UTF8

package io.qbilon.linkedin;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import eu.easyrpa.openframework.excel.ExcelDocument;
import eu.easyrpa.openframework.excel.Sheet;
import eu.easyrpa.openframework.excel.Table;
import io.qbilon.linkedin.model.Company;
import io.qbilon.linkedin.model.Contact;
import io.qbilon.linkedin.model.Lead;
import io.qbilon.linkedin.util.JobDescriptors;
import io.qbilon.linkedin.util.RemovableNameSegments;
import io.qbilon.linkedin.util.SpecialChars;
import io.qbilon.linkedin.util.Util;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "scrapeLeads", mixinStandardHelpOptions = true, version = "scrapeLeads 1.0", description = "Scrapes potential Leads from LinkedIn for a given set of companies and search terms")
public class LeadScraper implements Callable<Integer> {
    @Option(names = { "-e", "--email" }, description = "The email to be used for login in LinkedIn")
    private String email;
    @Option(names = { "-p", "--password" }, description = "The password to be used for login in LinkedIn")
    private String password;
    @Option(names = { "-l",
            "--locations" }, description = "The locations that should be searched for for the leads. Use it like this: -l Deutschland -l Frankreich")
    private List<String> locations;
    @Option(names = { "-c",
            "--companies" }, description = "The excel with the companies to use for the search. use it like this: -c path/to/excel.xlsx")
    private File companiesExcelFile;
    @Option(names = { "-d",
            "--duplicates" }, description = "The excel with the emails of already existing leads used to filter duplicated ones. use it like this: d- path/to/excel.xlsx")
    private File duplicatesExcelFile;
    @Option(names = { "-s",
            "--search" }, description = "The search terms and the number of possible leads to be included for the search (-1 means all possible). Use it like this: -s it=35 -s architect=20 -s test=-1")
    private Map<String, Integer> searchTerms;
    @Option(names = { "-v",
            "--verbose" }, description = "Toggles verbose mode, e.g., prints exceptions")
    private boolean verbose;
    @Option(names = { "-a",
            "--augment" }, description = "Toggles augmenting mode, i.e., all leads will be augmented with additional data like their last 3 jobs. This takes more time to scrape though")
    private boolean augment;


    private Path currentDir = Paths.get("").toAbsolutePath();
    private Path pathToContext = currentDir.resolve("state.json").toAbsolutePath();
    private Path pathToLeadExcel = currentDir.resolve("leads.xlsx").toAbsolutePath();
    private Path pathToAugmentedLeadExcel = currentDir.resolve("augmentedleads.xlsx").toAbsolutePath();
    private SpecialChars specialChars = new SpecialChars();
    private RemovableNameSegments removableSegments = new RemovableNameSegments();
    private JobDescriptors jobDescriptors = new JobDescriptors();

    private List<String> errors = new ArrayList<>();

    public static void main(String[] args) {
        int exitCode = new CommandLine(new LeadScraper()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        validateInput();

        System.out.println();
        System.out.println("Starting LinkedIn Lead Scraper in directory " + currentDir + " with:");
        System.out.println("\temail = " + email);
        System.out.println("\tpassword = " + password);
        System.out.println("\tcompanies = " + companiesExcelFile.getAbsolutePath().toString());
        System.out.println("\tduplicates = " + duplicatesExcelFile.getAbsolutePath().toString());
        System.out.println("\tsearchTerms = ");
        System.out.println("\tverbose = " + verbose);
        System.out.println("\taugment = " + augment);
        for (Entry<String, Integer> entry : searchTerms.entrySet()) {
            System.out.println("\t\t" + entry.getKey() + " = " + entry.getValue());
        }
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
        if (companiesExcelFile == null || !companiesExcelFile.exists()) {
            System.out.println("You need to provide a company excel file!");
            System.exit(1);
        }
        if (duplicatesExcelFile == null || !duplicatesExcelFile.exists()) {
            System.out.println("You need to provide an excel file containing existing lead emails!");
            System.exit(1);
        }
        if (locations == null || locations.isEmpty()) {
            System.out.println("You need to provide at least one location!");
            System.exit(1);
        }
        if (searchTerms == null || searchTerms.isEmpty()) {
            System.out.println("You need to provide at least one search term!");
            System.exit(1);
        }
    }

    private void run(Playwright playwright) throws MalformedURLException {
        Browser browser = Util.createBrowser(playwright, pathToContext);
        BrowserContext context = browser.contexts().get(0);
        Page page = Util.loginToLinkedIn(context, email, password);

        ExcelDocument companiesExcel = new ExcelDocument(companiesExcelFile.getAbsolutePath().toString());
        Sheet companiesSheet = companiesExcel.getActiveSheet();
        Table<Company> companiesTable = companiesSheet.getTable("A1", Company.class);

        ExcelDocument contactsExcel = new ExcelDocument(duplicatesExcelFile.getAbsolutePath().toString());
        Sheet contactsSheet = contactsExcel.getActiveSheet();
        Table<Contact> contactsTable = contactsSheet.getTable("A1", Contact.class);

        Set<String> existingContacts = contactsTable.getRecords().stream().map(contact -> contact.getEmail()).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Lead> leads = scrapeAndSaveRawDeduplicatedLeads(page, companiesTable, existingContacts);

        if(augment) {
            augmentAndSaveScrapedLeads(page, leads);
        }
        
        // Save current browser state
        context.storageState(new BrowserContext.StorageStateOptions().setPath(pathToContext));
        context.close();
        browser.close();

        System.out.println();
        System.out.println("Finished Scraping!");
        if(errors.size() > 0) {
            System.out.println("\nERRORS:");
            for (String error : errors) {
                System.out.println("\t" + error);
            }
        }
        String path = "";
        if(augment) {
            path = pathToAugmentedLeadExcel.toString();
        } else {
            path = pathToLeadExcel.toString();
        }
        System.out.println("\nPlease review the scraped leads under " + path + "! They might contain compromised data or unfitting leads");
    }

    private void augmentAndSaveScrapedLeads(Page page, List<Lead> leads) {
        System.out.println("Augmenting scraped leads with additional job information.");
        Util.touchFile(pathToAugmentedLeadExcel);
        ExcelDocument doc = new ExcelDocument();
        long start = System.currentTimeMillis();
        int count = 1;
        for (Lead lead : leads) {
            try {
                System.out.println(Util.progress(start, count, leads.size()) + "Augmenting " + lead.getEmail());
                page.navigate(lead.getProfileLink());
                page.waitForSelector("section:has(> #experience)");
                Util.wait(500, 100);
                // select the parent of the experience div
                Locator experienceSection = page.locator("section:has(> #experience)");
                Locator stations = experienceSection.locator("> div.pvs-list__outer-container > ul.pvs-list > li");
    
                List<String> jobDescriptions = new ArrayList<>(); 
                int maxNumJobs = 4;
                jobs:for (Locator station : stations.all()) {
                    if(jobDescriptions.size() >= maxNumJobs) {
                        break;
                    }
                    Locator subStations = station.locator("div.pvs-entity--with-path");
                    if(subStations.count() > 0) {
                        // scrape the list of substations in a station card
                        for (Locator subStation : subStations.all()) {
                            try {
                                if(jobDescriptions.size() >= maxNumJobs) {
                                    break jobs;
                                }
                                Locator jobTitle = subStation.locator("a div:first-child");
                                jobDescriptions.add(jobTitle.textContent().trim());
                            } catch (Exception e) {
                                // ignore and skip
                            }
                        }
                    } else {
                        Locator jobTitle = station.locator("span.t-bold > span[aria-hidden]");
                        jobDescriptions.add(jobTitle.textContent().trim());
                    }
                }
    
                if(jobDescriptions.size() >= 1) {
                    if(!lead.getJobTitle().equals(jobDescriptions.get(0))) {
                        lead.setJobTitle(jobDescriptions.get(0));
                    }
                }
                if(jobDescriptions.size() >= 2) {
                    lead.setPreviousJobTitle1(jobDescriptions.get(1));
                }
                if(jobDescriptions.size() >= 3) {
                    lead.setPreviousJobTitle2(jobDescriptions.get(2));
                }
                if(jobDescriptions.size() >= 4) {
                    lead.setPreviousJobTitle3(jobDescriptions.get(3));
                }
            } catch (Exception e) {
                errors.add("Failed to augment lead " + lead.getFirstName() + " " + lead.getLastName() + "! Skip it.");
                if(verbose) {
                    errors.add(Util.stackTraceToString(e));
                }
            }
            count ++;
        }

        doc.getActiveSheet().insertTable("A1", leads);
        doc.saveAs(pathToAugmentedLeadExcel.toString());
        doc.close();
    }

    private List<Lead> scrapeAndSaveRawDeduplicatedLeads(Page page, Table<Company> companyTable, Set<String> existingContacts) {
        Map<String, Lead> allDeduplicatedLeads = new HashMap<>();
        Util.touchFile(pathToLeadExcel);
        ExcelDocument doc = new ExcelDocument();
        // Search all Companies for all searchterms
        for (Company company : companyTable) {
            try {
                System.out.println();
                navigateToInitialSearchPage(page, company, searchTerms.entrySet().iterator().next().getKey());
                Map<String, List<String>> urlParams = null;
                urlParams = Util.urlParams(new URL(page.url()));
                for (Entry<String, Integer> entry : searchTerms.entrySet()) {
                    Map<String, Lead> deduplicatedLeads = new HashMap<>();
                    Integer currentPage = 1;
                    Integer maxNrLeads = entry.getValue();
                    String searchTerm = entry.getKey();
                    try {
                        while (!Util.isEmptySearchPage(page) && (maxNrLeads == -1 || maxNrLeads > deduplicatedLeads.size())) {
                            System.out.println("Scraping raw lead data for '" +  company.getName() + "' and search term '" + searchTerm + "' on page " + currentPage);
                            scrapeRawLeads(page, company, deduplicatedLeads, existingContacts, maxNrLeads);
                            page.navigate(createLeadSearchUrl(urlParams, searchTerm, currentPage));
                            Util.wait(1000, 200);
                            currentPage++;
                            if (currentPage >= maxNrLeads) {
                                break;
                            }
                        }
                    } catch (Exception e) {
                        errors.add("Failed to scrape leads for '" + company.getName() + "' and search term '" + searchTerm + "' on page " + currentPage + "!. Skip it!");
                        if(verbose) {
                            errors.add(Util.stackTraceToString(e));
                        }
                    }
                    allDeduplicatedLeads.putAll(deduplicatedLeads);

                }
            } catch (Exception e) {
                errors.add("Failed to scrape leads for '" + company.getName() + "!. Skip it!");
                if(verbose) {
                    errors.add(Util.stackTraceToString(e));
                }
            }
        }

        List<Lead> leads = new ArrayList<>();
        leads.addAll(allDeduplicatedLeads.values());
        doc.getActiveSheet().insertTable("A1", leads);
        doc.saveAs(pathToLeadExcel.toString());
        doc.close();
        return leads;
    }

    private String createLeadSearchUrl(Map<String, List<String>> urlParams, String searchTerm, int currentPage) {
        String result = Util.createUrl(
            "https://www.linkedin.com/search/results/people/", 
            urlParams, 
            List.of(
                "currentCompany", 
                "geoUrn",
                "keywords=" + searchTerm,
                "origin=FACETED_SEARCH", 
                "page=" + currentPage, 
                "sid"
            )
        );
        return result;
    }

    private void scrapeRawLeads(Page page, Company company, Map<String, Lead> leads, Set<String> existingContacts, Integer maxNrLeads) {
        page.waitForSelector(".search-results-container");
        Locator resultContainer = page.locator(".search-results-container");
        Locator resultItems = resultContainer.locator("li.reusable-search__result-container");
        for (Locator resultItem : resultItems.all()) {
            if(maxNrLeads == -1 || maxNrLeads > leads.size()) {
                try {
                    Locator titleLink = resultItem.locator("span.entity-result__title-text > a");
                    Locator nameSpan = titleLink.locator("span[aria-hidden]");
                    Locator jobSpan = resultItem.locator(".entity-result__primary-subtitle");
                    
                    Lead lead = new Lead();
                    String link = titleLink.getAttribute("href");
                    lead.setProfileLink(link.substring(0, link.indexOf("?")));
                    setFirstAndLastName(lead, nameSpan.textContent().trim());
                    lead.setJobTitle(getJobTitle(company, jobSpan.textContent().trim()));
                    lead.setEmail(getEmail(lead, company));
                    lead.setIndustry(company.getIndustry());
    
                    if(!existingContacts.contains(lead.getEmail())) {
                        leads.put(lead.getEmail(), lead);
                    }
                } catch (Exception e) {
                    errors.add("Failed to scrape single lead data! Skip it.");
                    if(verbose) {
                       errors.add(Util.stackTraceToString(e));
                    }
                }
            } else {
                break;
            }
        }
    }

    
    private String getJobTitle(Company company, String jobTitle) {
        // remove often used "at XXX" job title phrases
        for (String descriptor : jobDescriptors.getJobDescriptors(company.getName())) {
            if(jobTitle.contains(descriptor)) {
                return jobTitle.replace(descriptor, "");
            }
        }
        return jobTitle;
    }

    private String getEmail(Lead lead, Company company) {
        String firstName = lead.getFirstName().toLowerCase().replace(".", "").replace(" ", ".");
        String lastName = lead.getLastName().toLowerCase().replace(".", "").replace(" ", ".");;
        String domain = company.getDomain();
        return firstName + "." + lastName + "@" + domain;
    }

    private void setFirstAndLastName(Lead lead, String name) { 
        String nameToLower = name.toLowerCase();
        // somehow replace special characters? at least äöüßéèêáàâôóòûùúû
        for (String special : specialChars.specials()) {
            nameToLower.replace(special, specialChars.replacementFor(special));
        }
        // replace unwanted name segments
        for (String removableSegment : removableSegments.removableSegments()) {
            nameToLower.replace(removableSegment, "");
        }

        // now split it into first name and last name
        String firstName = nameToLower.substring(0, nameToLower.lastIndexOf(" "));
        String lastName = nameToLower.substring(nameToLower.lastIndexOf(" ") + 1);
        lead.setFirstName(capitalize(firstName));
        lead.setLastName(capitalize(lastName));
    }

    private String capitalize(String str) {
        if(str == null || str.length()<=1) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    // https://www.linkedin.com/search/results/people/?currentCompany=["1043"]&geoUrn=["101282230"]&keywords=it&origin=GLOBAL_SEARCH_HEADER&sid=:lw
    private void navigateToInitialSearchPage(Page page, Company company, String searchTerm) {
        System.out.println("Navigating to search page ...");
        page.navigate("https://www.linkedin.com/search/results/people/?keywords=" + searchTerm + "&origin=SWITCH_SEARCH_VERTICAL");
        Util.buttonWithInput(page, "Standorte", "Ort hinzufügen", locations);
        Util.buttonWithInput(page, "Aktuelles Unternehmen", "Unternehmen hinzufügen", List.of(company.getName()));
        // if we do not wait until all parts are in the url the url is incorrect
        page.waitForURL(url -> url.contains("geoUrn") &&
                url.contains("currentCompany") &&
                url.contains("sid"));
    }
}
