package base;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.Status;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import reports.ExtentManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.logging.Logger;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseWebTest {
    protected WebDriver driver;
    protected WebDriverWait wait;

    // Instance logger (unchanged)
    protected final Logger log = Logger.getLogger(getClass().getName());
    // Static logger for static helpers
    private static final Logger LOG = Logger.getLogger(BaseWebTest.class.getName());

    // Extent
    private static final ExtentReports EXTENT = ExtentManager.getExtent();
    private static final ThreadLocal<ExtentTest> CURRENT = new ThreadLocal<>();

    // Report + screenshots location
    private static final Path REPORT_DIR = Paths.get("target", "extent");
    private static final Path SHOTS_DIR  = REPORT_DIR.resolve("screenshots");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    public static ExtentTest currentTest() { return CURRENT.get(); }

    @BeforeEach
    void create(TestInfo info) {
        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "false"));
        System.out.println(">>>HEADLESS= " + headless);

        ChromeOptions options = new ChromeOptions();
        if (headless) options.addArguments("--headless=new");
        options.addArguments("--window-size=1600,1200");

        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        // Register a test node so the report always has at least one test
        CURRENT.set(EXTENT.createTest(info.getDisplayName()));
    }

    @AfterEach
    void teardown() {
        if (driver != null) driver.quit();
        // Flush after each test so you always get a file
        ExtentManager.flush();
    }

    @AfterAll
    public static void flushExtent() {
        // Also flush at the end (harmless if already flushed)
        ExtentManager.flush();
        ExtentManager.openReport();
    }

    // ---------- helpers ----------
    protected WebElement waitVisible(By by) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    protected WebElement waitClickable(By by) {
        return wait.until(ExpectedConditions.elementToBeClickable(by));
    }

    protected void scrollIntoView(WebElement el) {
        ((JavascriptExecutor) driver)
                .executeScript("arguments[0].scrollIntoView({block:'center'}); window.scrollBy(0,-80);", el);
    }

    protected void jsClick(WebElement el) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }

    // ---------- screenshots ----------
    public static Path takeScreenshot(WebDriver drv, String nameHint) {
        try {
            Files.createDirectories(SHOTS_DIR);
            String safe = nameHint.replaceAll("[^a-zA-Z0-9_.-]", "_");
            Path file = SHOTS_DIR.resolve(safe + "_" + TS.format(LocalDateTime.now()) + ".png");
            byte[] png = ((TakesScreenshot) drv).getScreenshotAs(OutputType.BYTES);
            Files.write(file, png);
            return file.toAbsolutePath();
        } catch (Exception e) {
            LOG.warning("Screenshot failed: " + e);
            return null;
        }
    }

    // Make screenshot path relative to the report HTML so Extent can render it
    private static String toReportPath(Path shot) {
        try {
            String rel = REPORT_DIR.toAbsolutePath().relativize(shot.toAbsolutePath()).toString();
            return rel.replace("\\", "/"); // Windows -> web path
        } catch (Exception e) {
            return shot.toString();
        }
    }

    private static void logWithShot(ExtentTest t, Status st, String msg, Path shot) {
        if (t == null) return;
        try {
            if (shot != null) {
                t.log(st, msg, MediaEntityBuilder
                        .createScreenCaptureFromPath(toReportPath(shot))
                        .build());
            } else {
                t.log(st, msg);
            }
        } catch (Exception e) {
            t.log(st, msg + " (screenshot attach failed: " + e.getMessage() + ")");
        }
    }

    // ---------- per-step logging helpers (instance) ----------
    protected void stepOk(String label) {
        Path p = takeScreenshot(driver, label);
        log.info("STEP OK: " + label + (p != null ? " -> " + p : ""));
        logWithShot(CURRENT.get(), Status.PASS, label, p);
    }

    protected void stepError(String label, Throwable th) {
        Path p = takeScreenshot(driver, "ERROR_" + label);
        log.severe("STEP FAIL: " + label + " -> " + th + (p != null ? " " + p : ""));
        logWithShot(CURRENT.get(), Status.FAIL, label + " -> " + th, p);
    }

    // --- STATIC per-step loggers the pages can call directly ---
    public static void stepOk(WebDriver driver, String message) {
        Path p = takeScreenshot(driver, message);
        LOG.info("STEP OK: " + message + (p != null ? " -> " + p : ""));
        logWithShot(CURRENT.get(), Status.PASS, message, p);
    }

    public static void stepError(WebDriver driver, String message, Throwable t) {
        Path p = takeScreenshot(driver, "ERROR_" + message);
        LOG.severe("STEP FAIL: " + message + " -> " + t + (p != null ? " " + p : ""));
        logWithShot(CURRENT.get(), Status.FAIL, message + " : " + t.getClass().getSimpleName(), p);
    }

    // Optional pause helper (unchanged, just kept)
    public static void pause(WebDriver drv, String msg) {
        long sleepMs = Long.getLong("pause.ms", 0L);
        if (sleepMs > 0) {
            try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) {}
            return;
        }
        if (!Boolean.getBoolean("pauseAfterClose")) return;

        try {
            ((JavascriptExecutor) drv).executeScript(
                    "alert(arguments[0]);",
                    (msg == null ? "Paused" : msg) + "\n\nClick OK to continue…"
            );
            new WebDriverWait(drv, Duration.ofMinutes(5))
                    .until(ExpectedConditions.alertIsPresent());
            drv.switchTo().alert().accept();
        } catch (Exception ignored) { }
    }

    @RegisterExtension
    TestWatcher extentWatcher = new TestWatcher() {
        @Override
        public void testSuccessful(ExtensionContext context) {
            ExtentTest t = CURRENT.get();
            if (t != null) t.pass("Test passed");
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            ExtentTest t = CURRENT.get();
            if (t != null) t.fail(cause);
        }

        @Override
        public void testDisabled(ExtensionContext context, Optional<String> reason) {
            ExtentTest t = CURRENT.get();
            if (t != null) t.skip("Disabled: " + reason.orElse(""));
        }

        @Override
        public void testAborted(ExtensionContext context, Throwable cause) {
            ExtentTest t = CURRENT.get();
            if (t != null) t.skip("Aborted: " + cause);
        }
    };
}
