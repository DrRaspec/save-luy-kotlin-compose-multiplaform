import XCTest

/// Walks through the main flows like a user would and saves a screenshot after each step.
/// test1 expects a fresh install (empty Cash account, Ready to Assign $0.00); the others
/// run after it (XCTest runs methods in name order) and work with whatever data exists.
/// Set TEST_RUNNER_SCREENSHOT_DIR when running xcodebuild to also write PNGs to that folder.
final class SavingLuyUITests: XCTestCase {

    private let app = XCUIApplication()
    private var step = 0

    override func setUp() {
        continueAfterFailure = false
        app.launch()
    }

    func test1MainFlows() {
        // Budget screen on a fresh install.
        XCTAssertTrue(element("All Money Assigned").waitForExistence(timeout: 20))
        snapshot("budget-empty")

        // Add $500 income: it goes to Ready to Assign.
        element("+").tap()
        XCTAssertTrue(element("New Transaction").waitForExistence(timeout: 5))
        element("Income").tap()
        app.typeText("500")
        snapshot("add-income")
        element("Save").tap()
        XCTAssertTrue(element("$500.00").waitForExistence(timeout: 5))
        snapshot("budget-500-to-assign")

        // Assign $200 to Food and $100 to Savings with the keypad sheet.
        assign("Food", digits: "20000")
        snapshot("assign-food")
        assign("Savings", digits: "10000")
        XCTAssertTrue(element("$200.00").waitForExistence(timeout: 5)) // Ready to Assign left

        // Spend $250 on Food: Food becomes overspent by $50.
        element("+").tap()
        XCTAssertTrue(element("New Transaction").waitForExistence(timeout: 5))
        app.typeText("250")
        elementStarting("Choose").tap()
        element("Food").tap()
        snapshot("add-expense")
        element("Save").tap()
        XCTAssertTrue(elementContaining("-$50.00").waitForExistence(timeout: 5))
        snapshot("budget-food-overspent")

        // Move Money: long-press the overspent category; the $50 needed is filled in.
        element("Food").press(forDuration: 1.2)
        XCTAssertTrue(element("Move Money").waitForExistence(timeout: 5))
        elementStarting("Choose").tap()
        element("Savings").tap()
        snapshot("move-money")
        element("Move").tap()
        XCTAssertTrue(element("Move Money").waitForNonExistence(timeout: 5))
        XCTAssertFalse(elementContaining("-$50.00").exists) // Food no longer overspent
        XCTAssertTrue(element("$200.00").exists) // Ready to Assign unchanged
        snapshot("budget-after-move")

        // Accounts: balance, transactions, swipe to delete, undo.
        element("Accounts").tap()
        XCTAssertTrue(element("$250.00").waitForExistence(timeout: 5)) // 500 income - 250 expense
        snapshot("accounts")
        element("Cash").tap()
        XCTAssertTrue(element("No payee").waitForExistence(timeout: 5))
        snapshot("cash-transactions")
        element("No payee").swipeLeft()
        XCTAssertTrue(element("Transaction deleted").waitForExistence(timeout: 5))
        snapshot("deleted-snackbar")
        element("Undo").tap()
        XCTAssertTrue(element("No payee").waitForExistence(timeout: 5))
        snapshot("after-undo")

        // Back arrow returns to the account list.
        element("Back").tap()
        XCTAssertTrue(element("Total across accounts").waitForExistence(timeout: 5))

        // Settings tab.
        element("Settings").tap()
        XCTAssertTrue(element("Your data").waitForExistence(timeout: 5))
        snapshot("settings")

        // Widget tap URL opens Add Transaction.
        app.open(URL(string: "savingluy://add-transaction")!)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        if springboard.buttons["Open"].waitForExistence(timeout: 3) {
            springboard.buttons["Open"].tap()
        }
        XCTAssertTrue(element("New Transaction").waitForExistence(timeout: 10))
        snapshot("opened-from-widget-url")
        element("Back").tap()
    }

    /// The iOS edge swipe acts as Back on Add Transaction.
    func test2BackSwipeClosesAddTransaction() {
        XCTAssertTrue(element("+").waitForExistence(timeout: 20))
        element("+").tap()
        XCTAssertTrue(element("New Transaction").waitForExistence(timeout: 5))
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.0, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.5)))
        XCTAssertTrue(element("New Transaction").waitForNonExistence(timeout: 5))
        XCTAssertTrue(element("+").exists)
        snapshot("back-swipe-closed-form")
    }

    /// Adds the widget from the home screen gallery, checks it shows the app's numbers,
    /// then taps it and expects Add Transaction.
    func test3HomeScreenWidget() {
        XCTAssertTrue(element("+").waitForExistence(timeout: 20)) // app ran, snapshot written
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(springboard.wait(for: .runningForeground, timeout: 5))

        // Enter edit mode on an empty part of the home screen.
        springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.55)).press(forDuration: 1.5)
        let edit = springboard.buttons["Edit"]
        if edit.waitForExistence(timeout: 5) {
            edit.tap()
            tapFirst(in: springboard, labels: ["Add Widget", "Add Widgets"])
        } else {
            tapFirst(in: springboard, labels: ["Add Widget", "Add Widgets", "Add"])
        }

        let search = springboard.searchFields.firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 5), "widget gallery search field")
        search.tap()
        search.typeText("SavingLuy")
        let result = springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS[c] %@", "SavingLuy")).element(boundBy: 1)
        XCTAssertTrue(result.waitForExistence(timeout: 5), "SavingLuy in widget gallery")
        result.tap()
        snapshot(springboard, "widget-gallery")
        tapFirst(in: springboard, labels: ["Add Widget"])
        tapFirst(in: springboard, labels: ["Done"], required: false)

        let widgetText = springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", "Ready to Assign")).firstMatch
        XCTAssertTrue(widgetText.waitForExistence(timeout: 10), "widget on home screen")
        snapshot(springboard, "widget-on-home-screen")

        widgetText.tap()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
        XCTAssertTrue(element("New Transaction").waitForExistence(timeout: 10))
        snapshot("opened-from-widget-tap")
    }

    // MARK: - Helpers

    private func tapFirst(in target: XCUIApplication, labels: [String], required: Bool = true) {
        for label in labels {
            let button = target.buttons[label]
            if button.waitForExistence(timeout: 3) {
                button.tap()
                return
            }
        }
        if required { XCTFail("None of \(labels) found") }
    }

    /// Tap a category row and type its Assigned amount (in cents) on the keypad sheet.
    private func assign(_ category: String, digits: String) {
        element(category).tap()
        XCTAssertTrue(element("Assign to \(category)").waitForExistence(timeout: 5))
        for digit in digits { element(String(digit)).tap() }
        element("Save").tap()
        XCTAssertTrue(element("Assign to \(category)").waitForNonExistence(timeout: 5))
    }

    private func element(_ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func elementStarting(_ prefix: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", prefix)).firstMatch
    }

    private func elementContaining(_ text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
    }

    private func snapshot(_ name: String) {
        snapshot(app, name)
    }

    private func snapshot(_ target: XCUIApplication, _ name: String) {
        step += 1
        let fileName = String(format: "%@-%02d-%@", name.hasPrefix("widget") ? "w" : "s", step, name)
        let screenshot = target.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = fileName
        attachment.lifetime = .keepAlways
        add(attachment)
        if let dir = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"] {
            try? screenshot.pngRepresentation.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(fileName).png"))
        }
    }
}
