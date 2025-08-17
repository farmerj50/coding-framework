package ikea;

import base.BaseWebTest;
import ikea.pages.CartPage;
import ikea.pages.HomePage;
import ikea.pages.ProductPage;
import ikea.pages.SearchResultsPage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IkeaCartFlowTest extends BaseWebTest {

    @Test
    void addSofaAndTable_applyInvalidCoupon() {
        HomePage home = new HomePage(driver).open();

        // 1) Search "sofa" → first result → add to bag
        home.searchFor("sofa");
        ProductPage sofa = new SearchResultsPage(driver).openNthResult(1);
        sofa.addToBag(); // opens minicart

        // 2) From that product page, search "table" using the header (handles closing minicart + waits)
        SearchResultsPage tableResults = sofa.searchFromHeader("table");
        ProductPage table = tableResults.openNthResult(3);
        table.addToBag();

        // 3) Go to cart and validate item count
        CartPage cart = table.goToCart();
        assertThat(cart.getItemCount())
                .as("Cart should contain 2 items")
                .isGreaterThanOrEqualTo(2);

        // 4) Apply random 15-char code, expect invalid coupon
        cart.expandDiscount()
                .enterDiscountCode(randomAlphaNum(15))
                .applyDiscount();

        assertThat(cart.invalidCouponErrorShown()).isTrue();
    }

    private static String randomAlphaNum(int n) {
        String s = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder b = new StringBuilder(n);
        for (int i = 0; i < n; i++) b.append(s.charAt((int)(Math.random() * s.length())));
        return b.toString();
    }
}
