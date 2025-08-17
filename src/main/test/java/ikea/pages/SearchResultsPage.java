package ikea.pages;

import base.BaseWebTest;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

public class SearchResultsPage {
    private final WebDriver driver;
    private final WebDriverWait wait;

    // Grid container
    private static final By RESULTS_CONTAINER = By.cssSelector(
            "#product-list, section#product-list, .plp-product-list__products, [data-testid='plp-product-list__products']");

    // Individual product cards (IKEA uses several class/testid variants)
    private static final By PRODUCT_CARD = By.cssSelector(
            "[data-testid='plp-product-card'], .plp-mastercard, div[data-ref-id][data-product-name]");

    // The clickable link inside the card that navigates to PDP
    private static final By CARD_PRIMARY_LINK = By.cssSelector(
            "a[href*='/p/'], a.plp-product__link, a[data-product-card-link]");

    // PDP signals
    private static final By PDP_TITLE = By.cssSelector("main h1, h1[itemprop='name']");
    private static final By PDP_ADD_BUTTON_CSS = By.cssSelector("button[data-testid*='add-to'], button[data-testid*='add-to-cart']");
    private static final By PDP_ADD_BUTTON_XPATH = By.xpath(
            "//button[contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'add to bag') or " +
                    "contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'add to cart')]");

    public SearchResultsPage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Duration.ofSeconds(25));
        wait.until(ExpectedConditions.presenceOfElementLocated(RESULTS_CONTAINER));
    }

    /** Open the Nth result card (1-based) and return the product page. */
    public ProductPage openNthResult(int n) {
        if (n < 1) throw new IllegalArgumentException("Result index must be >= 1");

        dismissSoftOverlays();

        WebElement container = wait.until(ExpectedConditions.presenceOfElementLocated(RESULTS_CONTAINER));

        // Keep scrolling until we have at least n CLICKABLE cards (cards that contain a PDP link)
        List<WebElement> clickableCards = clickableCards(container);
        int guard = 0;
        while (clickableCards.size() < n && guard++ < 20) {
            ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, Math.max(900, window.innerHeight));");
            sleep(250);
            clickableCards = clickableCards(container);
        }
        if (clickableCards.size() < n) {
            throw new NoSuchElementException("Only found " + clickableCards.size() + " clickable result cards; need " + n);
        }

        WebElement card = clickableCards.get(n - 1);
        WebElement link = findCardLink(card);

        // Scroll into view and click the INNER LINK; if intercepted, fall back to JS navigation
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", link);
        try {
            wait.until(ExpectedConditions.elementToBeClickable(link)).click();
        } catch (Exception e) {
            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", link);
            } catch (Exception ignored) {
                // hard fallback: navigate using the href
                String href = link.getAttribute("href");
                if (href != null && !href.isEmpty()) {
                    ((JavascriptExecutor) driver).executeScript("window.location.href = arguments[0];", href);
                } else {
                    throw e;
                }
            }
        }

        // Wait for a solid PDP signal (URL or key elements)
        wait.until(ExpectedConditions.or(
                ExpectedConditions.urlMatches(".*/p/.*"),
                ExpectedConditions.presenceOfElementLocated(PDP_TITLE),
                ExpectedConditions.presenceOfElementLocated(PDP_ADD_BUTTON_CSS),
                ExpectedConditions.presenceOfElementLocated(PDP_ADD_BUTTON_XPATH)
        ));

        BaseWebTest.stepOk(driver, "Open result #" + n);
        return new ProductPage(driver);
    }

    // ---------------- helpers ----------------

    /** Cards that are displayed AND contain a PDP link. */
    private List<WebElement> clickableCards(WebElement container) {
        return container.findElements(PRODUCT_CARD).stream()
                .filter(el -> {
                    try { return el.isDisplayed() && findCardLink(el) != null; }
                    catch (StaleElementReferenceException e) { return false; }
                })
                .collect(Collectors.toList());
    }

    /** First visible PDP link inside a card, or null if none. */
    private WebElement findCardLink(WebElement card) {
        try {
            List<WebElement> links = card.findElements(CARD_PRIMARY_LINK);
            for (WebElement a : links) {
                if (a.isDisplayed()) return a;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void dismissSoftOverlays() {
        try {
            // Cookie/privacy footer that can cover the grid
            for (WebElement b : driver.findElements(By.xpath(
                    "//button[normalize-space()='Ok' or contains(.,'Your privacy choices') or contains(.,'Accept')]"))) {
                if (b.isDisplayed()) { try { b.click(); } catch (Exception ignored) {} }
            }
        } catch (Exception ignored) {}
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
