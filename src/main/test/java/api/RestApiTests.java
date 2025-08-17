package api;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class RestApiTests {

    @Test
    void getObjects_reportAppleMatches_andFailOnNonAppleNames() {
        var json = given()
                .baseUri("https://api.restful-api.dev")
                .accept(ContentType.JSON)
                .when().get("/objects")
                .then().statusCode(200)
                .extract().jsonPath();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = json.getList("$");

        // --- 1) IDs must be unique ---
        List<String> ids = items.stream().map(m -> String.valueOf(m.get("id"))).toList();
        Map<String, Long> idCounts = ids.stream()
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
        List<String> duplicateIds = idCounts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();
        assertThat(duplicateIds)
                .as("Duplicate IDs found: %s", duplicateIds)
                .isEmpty();

        // --- 2) Partition names by the Apple criterion ---
        List<String> names = items.stream()
                .map(m -> (m.get("name") == null) ? null : String.valueOf(m.get("name")))
                .toList();

        List<String> appleNames = names.stream()
                .filter(n -> n != null && n.toLowerCase().contains("apple"))
                .toList();

        // everything else (null/blank or no "apple")
        List<String> nonAppleNames = names.stream()
                .filter(n -> n == null || !n.toLowerCase().contains("apple"))
                .toList();

        // --- 3) Print a concise report to the console ---
        System.out.println("=== Names that CONTAIN 'Apple' (" + appleNames.size() + ") ===");
        appleNames.forEach(n -> System.out.println("  ✔ " + n));

        System.out.println("=== Names that DO NOT contain 'Apple' (" + nonAppleNames.size() + ") ===");
        nonAppleNames.forEach(n -> System.out.println("  ✘ " + n));

        // --- 4) Assert based on the assignment rule ---
        // If the assignment expects ALL names to contain "Apple", enforce zero offenders:
        assertThat(nonAppleNames)
                .as("Names missing 'Apple': %s", nonAppleNames)
                .isEmpty();

        // If you specifically expect two offenders, use this instead:
        // int expectedOffenders = 2;
        // assertThat(nonAppleNames.size())
        //        .as("Expected %s names without 'Apple' but got %s: %s",
        //            expectedOffenders, nonAppleNames.size(), nonAppleNames)
        //        .isEqualTo(expectedOffenders);
    }
}
