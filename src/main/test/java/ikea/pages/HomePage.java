// HomePage.java
package ikea.pages;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;
import base.BaseWebTest;

public class HomePage {
    private final WebDriver driver;
    private final WebDriverWait wait;

    private static final By SEARCH_INPUT_BY_ID   = By.id("ikea-search-input");      // primary
    private static final By SEARCH_INPUT_BY_NAME = By.name("q");                    // fallback 1
    private static final By SEARCH_INPUT_SCOPED  = By.cssSelector(                  // fallback 2
            "form[role='search'] input[type='search']");

    private static final By COOKIE_OK = By.xpath("//button[normalize-space()='Ok' or contains(.,'Accept')]");

    // -------- logging + screenshots (minimal) --------
    private static final Logger LOG = Logger.getLogger(HomePage.class.getName());
    private static final Path SHOTS_DIR = Paths.get("target", "screenshots");
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    public HomePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    public HomePage open() {
        driver.get("https://www.ikea.com/us/en/");
        acceptCookiesIfPresent();
        stepOk("Open home");
        return this;
    }

    public HomePage searchFor(String query) {
        try {
            WebElement input = waitFirstVisible(SEARCH_INPUT_BY_ID, SEARCH_INPUT_BY_NAME, SEARCH_INPUT_SCOPED);

            // Make sure the field is in view and not hidden by the sticky header
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center'}); window.scrollBy(0, -80);", input);

            // Try to click; if something overlays it, fallback to JS-click
            try {
                new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.elementToBeClickable(input))
                        .click();
            } catch (ElementClickInterceptedException ignored) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", input);
            }
            stepOk("Click search box");

            // Clear and type without relying on focus
            try {
                input.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
            } catch (InvalidElementStateException e) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].value='';", input);
            }

            input.sendKeys(query, Keys.ENTER);
            stepOk("Submit search '" + query + "'");
            BaseWebTest.stepOk(driver, "Submit search: " + query);
            return this;

        } catch (Throwable t) {
            stepError("Search '" + query + "'", t);
            throw t;
        }
    }

    private WebElement waitFirstVisible(By... locators) {
        for (By by : locators) {
            try {
                return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
            } catch (TimeoutException ignored) {}
        }
        throw new NoSuchElementException("Search input not found by ID/name/css");
    }

    private void acceptCookiesIfPresent() {
        try {
            WebElement ok = driver.findElement(COOKIE_OK);
            if (ok.isDisplayed()) ok.click();
            stepOk("Accept cookies (if shown)");
        } catch (NoSuchElementException ignored) {
            // not present; no screenshot here to avoid spam
        } catch (Throwable t) {
            stepError("Accept cookies", t);
        }
    }

    // --------- tiny helpers (local to this page) ---------
    private Path takeScreenshot(String nameHint) {
        try {
            Files.createDirectories(SHOTS_DIR);
            String safe = nameHint.replaceAll("[^a-zA-Z0-9_.-]", "_");
            Path file = SHOTS_DIR.resolve("HomePage_" + safe + "_" + TS.format(LocalDateTime.now()) + ".png");
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Files.write(file, png);
            return file.toAbsolutePath();
        } catch (Exception e) {
            LOG.warning("Screenshot failed: " + e);
            return null;
        }
    }

    private void stepOk(String label) {
        Path p = takeScreenshot(label);
        if (p != null) LOG.info("STEP OK: " + label + " -> " + p);
        else           LOG.info("STEP OK: " + label);
    }

    private void stepError(String label, Throwable t) {
        Path p = takeScreenshot("ERROR_" + label);
        if (p != null) LOG.severe("STEP FAIL: " + label + " -> " + t + " " + p);
        else           LOG.severe("STEP FAIL: " + label + " -> " + t);
    }
}
