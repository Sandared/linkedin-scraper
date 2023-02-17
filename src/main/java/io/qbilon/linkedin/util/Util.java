package io.qbilon.linkedin.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;

public class Util {

    private boolean verbose;
    private int delay;
    private int variance;
    private Random rand = new Random();
    private long startTime = System.currentTimeMillis();
    private int count = 0;
    private int size = 0;

    public Util(boolean verbose, int delay) {
        this.verbose = verbose;
        this.delay = delay;
        this.variance = delay / 10;
    }

    public void buttonWithInput(Page page, String visibleButtonText, String visibleInputText,
            List<String> textsToType) {
        page.waitForSelector("text=\"" + visibleButtonText + "\"");
        Locator button = page.locator("text=\"" + visibleButtonText + "\"");
        button.click();
        wait(1000);
        Locator input = page.locator("input[placeholder=\"" + visibleInputText + "\"]");
        BoundingBox box = input.boundingBox();

        for (String textToType : textsToType) {
            page.mouse().click(box.x + box.width / 2, box.y + box.height / 2);
            wait(500);
            page.keyboard().insertText(textToType);

            wait(1000);

            page.mouse().click(box.x + box.width / 2, box.y + box.height * 1.5);

            wait(1000);
        }

        // This will find several elements, as each filter has the "Ergebnisse anzeigen"
        Locator submitButtons = page.locator("text=\"Ergebnisse anzeigen\"");
        Locator submitButton = submitButtons.all().stream().filter(Locator::isVisible).findFirst().get();
        submitButton.click();
    }

    public void buttonWithMultiSelection(Page page, String visibleButtonText, List<String> selectionIds) {
        page.waitForSelector("text=\"" + visibleButtonText + "\"");
        Locator button = page.locator("text=\"" + visibleButtonText + "\"");
        button.click();
        wait(1000);

        for (String id : selectionIds) {
            Locator input = page.locator("#" + id);
            BoundingBox box = input.boundingBox();
            page.mouse().click(box.x + box.width / 2, box.y + box.height / 2);
            wait(500);
        }
        // This will find several elements, as each filter has the "Ergebnisse anzeigen"
        Locator submitButtons = page.locator("text=\"Ergebnisse anzeigen\"");
        Locator submitButton = submitButtons.all().stream().filter(Locator::isVisible).findFirst().get();
        submitButton.click();
    }

    public Map<String, List<String>> urlParams(URL url) {
        if (url.getQuery() == null || url.getQuery().isBlank()) {
            return Collections.emptyMap();
        }
        return Arrays.stream(url.getQuery().split("&"))
                .map(param -> splitQueryParameter(param))
                .collect(Collectors.groupingBy(SimpleImmutableEntry::getKey, LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private SimpleImmutableEntry<String, String> splitQueryParameter(String it) {
        final int idx = it.indexOf("=");
        final String key = idx > 0 ? it.substring(0, idx) : it;
        final String value = idx > 0 && it.length() > idx + 1 ? it.substring(idx + 1) : null;
        return new SimpleImmutableEntry<>(
                URLDecoder.decode(key, StandardCharsets.UTF_8),
                URLDecoder.decode(value, StandardCharsets.UTF_8));
    }

    public Browser createBrowser(Playwright playwright, Path pathToContext) {
        System.out.println(progress() + "Starting browser ...");
        Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
        if (!pathToContext.toFile().exists()) {
            // create the file
            try {
                Files.createDirectories(pathToContext.getParent());
                Files.createFile(pathToContext);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        BrowserContext context = browser.newContext(new Browser.NewContextOptions().setStorageStatePath(pathToContext));
        context.setDefaultTimeout(5000.0);
        return browser;
    }

    public void touchFile(Path filePath) {
        if (!filePath.toFile().exists()) {
            // create the file
            try {
                Files.createDirectories(filePath.getParent());
                Files.createFile(filePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public Page loginToLinkedIn(BrowserContext context, String email, String password) {
        Page page = context.newPage();
        page.navigate("https://www.linkedin.com/feed");

        if (page.url().startsWith("https://www.linkedin.com/signup/")) {
            System.out.println(progress() + "Detected redirect, logging in as user with provided credentials...");
            // we were redirected to login -> so login again
            page.navigate("https://www.linkedin.com/login");
            page.locator("#username").fill(email);
            page.locator("#password").fill(password);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Einloggen").setExact(true)).click();
        }

        return page;
    }

    public String createUrl(String baseUrl, Map<String, List<String>> params, List<String> usedParams) {
        StringBuilder sb = new StringBuilder();
        sb.append(baseUrl + "?");
        List<String> processedParams = usedParams.stream().map(param -> {
            if(param.contains("=")) {
                // it's a static param, just add it
                return param;
            } else {
                return param + "=" + String.join(",", params.get(param));
            }
        }).collect(Collectors.toList());
        sb.append(String.join("&", processedParams));
        return sb.toString();
    }

    public boolean isEmptySearchPage(Page page){
        page.waitForSelector(".search-results-container");
        Locator resultItems = page.locator(".search-results-container").locator("li.reusable-search__result-container");
        if(resultItems.count() == 0) {
            System.out.println(progress() + "Detected empty search page!");
            return true;
        }
        return false;
    }

    public void doWait() {
        boolean addition = rand.nextBoolean();
        int variation = rand.nextInt(variance);
        int waitTime = delay;
        if(addition) {
            waitTime = waitTime + variation;
        } else {
            waitTime = waitTime - variation;
        }
        wait(waitTime);
    }

    private void wait(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public String stackTraceToString(Exception e) {
        if(verbose) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            return sw.toString();
        } 
        return e.getMessage();
    }

    public String progress() {
        return progress(count, size);
    }

    public String progress(int count, int size) {
        // save for internal messages
        this.count = count;
        this.size = size;

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - startTime;
        return "(" + count + "/" + size + ") - " + toTime(elapsed) + " | ";
    }

    private String toTime(long millis) {
        return String.format("%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) -
                        TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
    }

    public void debug(Locator loc) {
        if(loc != null && loc.count() > 0) {
            for (Locator l : loc.all()) {
                l.evaluate("(ele) => (ele.style.border = '3px solid red')");
            }
        }
    }

}
