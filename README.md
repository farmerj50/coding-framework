
Test Automation Framework (UI + API)

End-to-end UI tests for IKEA.com (Selenium + JUnit 5) and a small API test suite (RestAssured).
Reports are generated with ExtentReports; screenshots are saved for each logged step.

What’s inside

UI (Selenium / JUnit 5)

ikea.IkeaCartFlowTest
Flow: search sofa → open 1st result → add to bag → search table → open 3rd result → add to bag → go to cart → try a random 15-char coupon → expect invalid coupon error.

API (RestAssured / JUnit 5)

api.RestApiTests#getObjects_validateUniqueIds_andNamesContainApple
GET https://api.restful-api.dev/objects, asserts IDs are unique, logs which names contain or do not contain “Apple”.

Requirements

Java 17+ (project known-good with OpenJDK 22)

Maven 3.8+

Google Chrome installed (driver auto-managed)

Internet access
# 1) clone
git clone https://github.com/farmerj50/<YOUR-REPO>.git
cd <YOUR-REPO>

# 2) run all tests headless (recommended first run)
mvn -Dheadless=true test
After a run, open the HTML report:

UI report: target/extent/index.html

Screenshots: target/extent/screenshots/
Run only a specific test

UI flow only
mvn -Dtest=ikea.IkeaCartFlowTest -Dheadless=true test
mvn -Dtest=api.RestApiTests test
mvn -Dtest=ikea.IkeaCartFlowTest#addSofaAndTable_applyInvalidCoupon test
