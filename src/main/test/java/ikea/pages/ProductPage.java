package ikea.pages;

import base.BaseWebTest;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

public class ProductPage {
    private final WebDriver driver;
    private final WebDriverWait wait;

    // Header search
    private static final By HEADER_SEARCH_FORM   = By.cssSelector("form[role='search']");
    private static final By HEADER_SEARCH_INPUT  = By.id("ikea-search-input");
    private static final By HEADER_SEARCH_ICON   = By.cssSelector("form[role='search'] .search-box-svg-icon, [data-testid='search-box-icon']");
    private static final By BODY = By.tagName("body");

    // Add-to-bag
    private final By addToBagBtn = By.xpath(
            "//button[.//span[normalize-space()='Add to bag'] or normalize-space()='Add to bag']");

    // Minicart sheet (both div/aside renderings) + close
    private final By addToCartPanel = By.cssSelector("div.rec-modal-wrapper--open, aside.rec-modal-wrapper--open");
    private final By addToCartClose = By.cssSelector(
            "button.rec-modal-header__close, .rec-modal-header [aria-label*='Close'], [aria-label*='close']");

    public ProductPage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Duration.ofSeconds(25));
    }

    public ProductPage addToBag() {
        By[] addCandidates = new By[] {
                By.xpath("//button[contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'add to bag')]"),
                By.cssSelector("button[data-testid*='add-to-cart']")
        };
        for (By by : addCandidates) {
            for (WebElement b : driver.findElements(by)) {
                if (b.isDisplayed() && b.isEnabled()) {
                    wait.until(ExpectedConditions.elementToBeClickable(b)).click();
                    BaseWebTest.stepOk(driver, "Add to bag");
                    return this;
                }
            }
        }
        throw new NoSuchElementException("Add to bag not found");
    }

    /** Public so tests/pages can explicitly close the minicart when needed. */
    public void closeAddToCartPanelIfOpen() {
        long t0 = System.currentTimeMillis();
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(2));
            WebDriverWait w         = new WebDriverWait(driver, Duration.ofSeconds(10));

            // Give the minicart a brief chance to appear (avoid racing).
            try { shortWait.until(ExpectedConditions.presenceOfElementLocated(addToCartPanel)); }
            catch (TimeoutException ignored) {}

            boolean present = !driver.findElements(addToCartPanel).isEmpty();
            if (present) {
                WebElement panel = w.until(ExpectedConditions.visibilityOfElementLocated(addToCartPanel));
                try {
                    WebElement close = w.until(ExpectedConditions.elementToBeClickable(addToCartClose));
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", close);
                    close.click();
                } catch (Exception clickProblem) {
                    new Actions(driver).sendKeys(Keys.ESCAPE).perform();
                }
                w.until(ExpectedConditions.invisibilityOfElementLocated(addToCartPanel));
            }

            // Regardless of presence, wait until page is interactable and no sheet is visible.
            new WebDriverWait(driver, Duration.ofSeconds(12)).until(d -> {
                try {
                    WebElement body = d.findElement(BODY);
                    String ready = body.getAttribute("data-shopping-available");
                    boolean sheetGone = d.findElements(addToCartPanel).stream().noneMatch(el -> {
                        try { return el.isDisplayed(); } catch (StaleElementReferenceException e) { return false; }
                    });
                    return "true".equalsIgnoreCase(ready) && sheetGone;
                } catch (Exception e) { return false; }
            });

            BaseWebTest.stepOk(driver, "Minicart handled (present=" + present + ")");
        } catch (Exception e) {
            BaseWebTest.stepError(driver, "Minicart handling", e);
        } finally {
            // ALWAYS pause here when enabled (see BaseWebTest.pause properties)
            BaseWebTest.pause(driver, "After minicart handling (" + (System.currentTimeMillis() - t0) + " ms)");
        }
    }



    // ---------------------- NEW: stronger “sheet closed” guarantees ----------------------

    /** Any visible rec sheet/backdrop/focus-lock still on the page? */
    private boolean isCartSheetVisible() {
        return driver.findElements(By.cssSelector(
                        "div.rec-modal-wrapper--open, aside.rec-modal-wrapper--open, " +
                                ".rec-modal-wrapper__backdrop, .rec-scope-focus-lock"))
                .stream().anyMatch(el -> {
                    try { return el.isDisplayed(); } catch (StaleElementReferenceException e) { return false; }
                });
    }

    /** Click close if needed, then wait until ALL sheet/backdrop markers are truly gone. */
    private void closeCartAndWaitGone() {
        // try to click close if something looks open
        if (isCartSheetVisible()) {
            try {
                WebElement close = new WebDriverWait(driver, Duration.ofSeconds(8))
                        .until(ExpectedConditions.elementToBeClickable(addToCartClose));
                close.click();
            } catch (TimeoutException ignored) {
            } catch (Exception e) {
                BaseWebTest.stepError(driver, "Close minicart panel (strong)", e);
            }
        }
        // wait until nothing remains visible
        new WebDriverWait(driver, Duration.ofSeconds(12))
                .until(d -> !isCartSheetVisible());

        // tiny settle for final animation frame
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
    }

    // -------------------------------------------------------------------------------------

    /**
     * From the product page, safely search again using the header box:
     * - ensure minicart is closed
     * - open/focus the header search
     * - wait until input is interactable (aria-hidden=false)
     * - type and submit
     */
    public SearchResultsPage searchFromHeader(String term) {
        // 1) make sure overlays/panels are gone
        dismissOverlays();
        closeAddToCartPanelIfOpen();  // keep existing behavior
        closeCartAndWaitGone();       // NEW: stronger wait to avoid race

        // 2) make sure we’re at the top so the header is visible
        try { ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);"); } catch (Exception ignored) {}

        // 3) open the header search if needed
        WebElement form = wait.until(ExpectedConditions.visibilityOfElementLocated(HEADER_SEARCH_FORM));
        WebElement input = driver.findElement(HEADER_SEARCH_INPUT);

        // If the input is aria-hidden, click the icon to expand it
        if ("true".equalsIgnoreCase(safeAttr(input, "aria-hidden"))) {
            driver.findElement(HEADER_SEARCH_ICON).click();
        }

        // 4) wait until the input is truly interactable: displayed, enabled, and not aria-hidden
        wait.until((ExpectedCondition<Boolean>) d -> {
            WebElement i = d.findElement(HEADER_SEARCH_INPUT);
            String hidden = safeAttr(i, "aria-hidden");
            return i.isDisplayed() && i.isEnabled() && (hidden == null || "false".equalsIgnoreCase(hidden));
        });
        input = driver.findElement(HEADER_SEARCH_INPUT); // re-find to avoid staleness
        wait.until(ExpectedConditions.elementToBeClickable(input));

        // 5) type + submit, with JS fallback if Selenium complains
        try {
            input.click();
            input.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
            input.sendKeys(term, Keys.ENTER);
        } catch (ElementNotInteractableException e) {
            // fallback: set value via JS and press Enter
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].value=''; arguments[0].dispatchEvent(new Event('input',{bubbles:true}));", input);
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].value=arguments[1]; arguments[0].dispatchEvent(new Event('input',{bubbles:true}));",
                    input, term);
            new Actions(driver).sendKeys(Keys.ENTER).perform();
        }

        BaseWebTest.stepOk(driver, "Submit search: " + term);
        return new SearchResultsPage(driver);
    }

    public CartPage goToCart() {
        dismissOverlays();
        closeAddToCartPanelIfOpen();

        By[] cartCandidates = new By[] {
                By.cssSelector("a[href*='/shoppingcart'], a[href*='/cart']"),
                By.cssSelector("[aria-label*='Shopping bag'], [aria-label*='Cart']"),
                By.cssSelector("button[aria-label*='Shopping bag'], button[aria-label*='Cart']")
        };

        for (By by : cartCandidates) {
            List<WebElement> els = driver.findElements(by);
            for (WebElement el : els) {
                if (!el.isDisplayed()) continue;
                try {
                    wait.until(ExpectedConditions.elementToBeClickable(el));
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", el);
                    el.click();
                    BaseWebTest.stepOk(driver, "Go to cart");
                    return new CartPage(driver);
                } catch (ElementClickInterceptedException e) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
                    BaseWebTest.stepOk(driver, "Go to cart (JS click)");
                    return new CartPage(driver);
                }
            }
        }

        driver.get("https://www.ikea.com/us/en/shoppingcart/");
        BaseWebTest.stepOk(driver, "Go to cart (direct)");
        return new CartPage(driver);
    }

    private void dismissOverlays() {
        for (WebElement b : driver.findElements(
                By.xpath("//button[normalize-space()='Ok' or contains(.,'Accept') or contains(.,'accept')]"))) {
            if (b.isDisplayed()) { try { b.click(); } catch (Exception ignored) {} }
        }
        try { new Actions(driver).sendKeys(Keys.ESCAPE).perform(); } catch (Exception ignored) {}
    }

    private static String safeAttr(WebElement el, String name) {
        try { return el.getAttribute(name); } catch (Exception e) { return null; }
    }
}
