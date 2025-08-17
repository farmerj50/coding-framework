package ikea.pages;

import base.BaseWebTest;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

public class CartPage {
    private final WebDriver driver;
    private final WebDriverWait wait;

    // --- Locators that are stable on IKEA US cart page ---
    private static final By CART_LINE_QTY_INPUTS =
            By.cssSelector(".cart-ingka-quantity-stepper__input, input[id^='cart-ingka-quantity-stepper']");

    // “Apply discount code” accordion header & content
    private static final By COUPON_ACCORDION_BUTTON =
            By.cssSelector("button[aria-controls^='SEC_cart-coupon']");
    private static final By COUPON_PANEL =
            By.cssSelector("#SEC_cart-coupon, [id^='SEC_cart-coupon']");

    // Input & submit inside the coupon panel
    private static final By COUPON_INPUT =
            By.cssSelector("#discountcode-input, input[aria-label*='Discount code' i]");
    private static final By COUPON_APPLY_BTN =
            By.cssSelector("#SEC_cart-coupon form button[type='submit'], [id^='SEC_cart-coupon'] form button[type='submit']");

    // Error state after submit (any one of these will do)
    private static final By COUPON_ERROR_REGION =
            By.cssSelector("#SEC_cart-coupon [role='alert'], #SEC_cart-coupon .error, #SEC_cart-coupon [aria-live]");

    public CartPage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Duration.ofSeconds(25));
        // Ensure cart has rendered
        wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(CART_LINE_QTY_INPUTS));
    }

    // --- Assertions/queries ---

    /** Sums quantities from all visible line items. */
    public int getItemCount() {
        int total = 0;
        List<WebElement> qtyInputs = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(CART_LINE_QTY_INPUTS));
        for (WebElement input : qtyInputs) {
            try {
                // The stepper uses type="text" with numeric value
                String v = input.getAttribute("value");
                if (v == null || v.isBlank()) v = input.getDomProperty("value");
                int n = Integer.parseInt(v.trim());
                total += Math.max(n, 0);
            } catch (Exception ignored) {}
        }
        BaseWebTest.stepOk(driver, "Cart items counted = " + total);
        return total;
    }

    // --- Discount flow ---

    /** Expands the "Apply discount code" section if collapsed. */
    public CartPage expandDiscount() {
        // Scroll the header into view and click if needed
        WebElement headerBtn = wait.until(ExpectedConditions.elementToBeClickable(COUPON_ACCORDION_BUTTON));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", headerBtn);

        // If not expanded, click
        String expanded = headerBtn.getAttribute("aria-expanded");
        if (!"true".equalsIgnoreCase(expanded)) {
            headerBtn.click();
        }

        // Wait for the panel to be actually visible
        wait.until(ExpectedConditions.visibilityOfElementLocated(COUPON_PANEL));
        BaseWebTest.stepOk(driver, "Open discount section");
        return this;
    }

    /** Types a discount/promo code into the input (with a JS fallback). */
    public CartPage enterDiscountCode(String code) {
        WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(COUPON_INPUT));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", input);

        try {
            input.click();
            input.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
            input.sendKeys(code);
        } catch (ElementNotInteractableException e) {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].value=''; arguments[0].dispatchEvent(new Event('input',{bubbles:true}))", input);
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].value=arguments[1]; arguments[0].dispatchEvent(new Event('input',{bubbles:true}))",
                    input, code);
        }
        BaseWebTest.stepOk(driver, "Enter code");
        return this;
    }

    /** Clicks the Apply button for the discount. */
    public CartPage applyDiscount() {
        WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(COUPON_APPLY_BTN));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", btn);
        try {
            btn.click();
        } catch (ElementClickInterceptedException e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
        }
        BaseWebTest.stepOk(driver, "Apply discount");
        return this;
    }

    /**
     * Returns true if the coupon looks invalid:
     *  - input gets aria-invalid="true", or
     *  - an error/alert region appears in the coupon panel.
     */
    public boolean invalidCouponErrorShown() {
        try {
            // Give IKEA a moment to validate client/server-side and reflect state
            WebElement input = wait.until(ExpectedConditions.presenceOfElementLocated(COUPON_INPUT));
            boolean bad = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> "true".equalsIgnoreCase(input.getAttribute("aria-invalid"))
                            || !d.findElements(COUPON_ERROR_REGION).isEmpty());
            return bad;
        } catch (TimeoutException te) {
            return false;
        }
    }
}
