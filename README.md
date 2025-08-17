# Test Automation Framework — Architecture & Flows

## 1) Framework topology

```mermaid
graph TD
  T[JUnit 5 Tests] -->|calls| POM[Page Objects]
  T --> API[API layer (RestAssured)]
  POM --> CORE[Core utils: BaseWebTest, waits, helpers]
  CORE --> WD[WebDriver (Chrome/Firefox)]
  T --> RPT[Reporting (ExtentReports)]
  CORE --> RPT
  T --> CFG[Config (system props/.env)]
  CFG --> WD
  CFG --> API
  WD -.screenshots.-> RPT

sequenceDiagram
  participant T as Test (IkeaCartFlowTest)
  participant H as HomePage
  participant SR as SearchResultsPage
  participant P as ProductPage
  participant C as CartPage

  T->>H: open()
  T->>H: searchFor("sofa")
  H-->>SR: new SearchResultsPage(driver)
  T->>SR: openNthResult(1)
  SR-->>P: ProductPage
  T->>P: addToBag()
  T->>P: closeAddToCartPanelIfOpen()

  T->>P: searchFromHeader("table")
  P-->>SR: SearchResultsPage
  T->>SR: openNthResult(3)
  SR-->>P: ProductPage
  T->>P: addToBag()
  T->>P: closeAddToCartPanelIfOpen()

  T->>P: goToCart()
  P-->>C: CartPage
  T->>C: getItemCount(), expandDiscount(), enterDiscountCode(), applyDiscount()

flowchart TD
  S([openNthResult(n)]) --> V{n >= 1?}
  V -- no --> E[throw IllegalArgumentException]
  V -- yes --> O[dismissSoftOverlays()]
  O --> C[waitFor RESULTS_CONTAINER]
  C --> L[collect visible PRODUCT_CARDs]
  L --> K{cards.size < n?}
  K -- yes --> SC[scroll to lazy-load] --> L
  K -- no --> T[scrollIntoView(target card)]
  T --> CL{Selenium click ok?}
  CL -- yes --> W
  CL -- no --> J[JS click(target)] --> W
  W[wait PDP signal: /p/ URL OR title OR add button] --> R[return new ProductPage]

flowchart TD
  A([searchFromHeader(term)]) --> D[dismissOverlays()]
  D --> M[closeAddToCartPanelIfOpen()]
  M --> G[extra: wait until sheet/backdrop gone]
  G --> T[scrollTo top]
  T --> F[ensure header search form visible]
  F --> H{input aria-hidden?}
  H -- yes --> X[click search icon to expand]
  H -- no --> I
  X --> I[wait until input is interactable]
  I --> TY[type term + ENTER (fallback: JS set + ENTER)]
  TY --> R[BaseWebTest.stepOk + return SearchResultsPage]

sequenceDiagram
  participant JT as JUnit Test
  participant S as https://api.restful-api.dev/objects

  JT->>S: GET /objects (Accept: JSON)
  S-->>JT: 200 OK (JSON array)

  JT->>JT: extract list
  JT->>JT: assert IDs unique
  JT->>JT: assert every name contains "Apple"
  JT->>JT: log pass/fail name lists

flowchart LR
  subgraph BaseWebTest
    B1[BeforeEach: init driver, create Extent test]
    B2[stepOk/stepError: attach screenshots]
    B3[AfterEach: quit driver, flush report]
  end
  B1 --> B2 --> B3

The test opens the site, searches ‘sofa’, opens the 1st result, adds it to the bag, closes the mini-cart safely, then uses the header search to look for ‘table’, opens the 3rd result, adds it, closes the mini-cart again, goes to the cart, counts items, and tries a coupon.

Most of our ‘logic’ is actually about timing and state. After you add to bag, IKEA shows a modal that steals focus; if you don’t close and wait for the page to be interactive again, the search box won’t accept keys. Search results lazy-load, so we scroll until we really have N cards before clicking. Cart can be slow to render items, so we wait for rows, then count them. That’s it: one test flow, each page responsible for just its own actions, and a few smart waits to avoid flakiness.”

When executing the test ensure that only one test configuration exsit at run -> edit configurations
