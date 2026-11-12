# MoneyApp

Personal, fully offline money-management app using envelope (zero-based) budgeting:
every dollar gets assigned to a category. Single user, no accounts, no backend.
Kotlin Multiplatform + Compose Multiplatform. Targets: Android and iOS (both in scope).

## Scope (do not add features beyond this)
- Budget screen, Add Transaction, Accounts, Move Money, and a home screen widget
  on both Android and iOS.
- NO bank sync, NO network calls, NO analytics, NO login, NO investments, NO reports.
- Ask before adding any new feature or dependency not listed here.

## Tech
- UI: Compose Multiplatform + Material 3 (shared in commonMain)
- DB: SQLDelight (local SQLite). Schema lives in commonMain.
- State: ViewModel + StateFlow (androidx lifecycle viewmodel for KMP)
- Navigation: simple bottom navigation, 3 tabs: Budget, Accounts, Settings
- Widgets: Android = Jetpack Glance (androidApp). iOS = SwiftUI WidgetKit extension
  (iosApp). Compose cannot draw widgets, so the widget UIs are written per platform.
- Tests: kotlin.test in commonTest for all budget math

## Money rules
- Amounts are Long in minor units (cents). Never Double/Float for money.
- Format only at the UI edge (one shared formatter). USD only for now,
  but keep currency code on the account so riel can be added later.
- Outflow = negative, inflow = positive in storage.

## Budget model (envelope method)
- Ready to Assign = total inflow to on-budget accounts − sum of all assigned amounts.
- Category Available = previous month's Available (rollover) + Assigned this month + Activity this month.
- Activity = sum of that category's transactions in the month.
- Overspent category (Available < 0) must be visible and fixable via Move Money.
- Moving money = decrease one category's Assigned, increase another's, in one DB transaction.

## UI guidelines (inspired by envelope-budgeting apps, original look)
Do not copy any other app's name, logo, icons, or exact colors. Use our own branding.
General feel: clean, calm, lots of whitespace, big readable numbers, one-handed use.
Screens are shared in commonMain and must look and behave the same on Android and iOS.

### Budget screen (home)
- Top: month selector with previous/next arrows.
- Below it, a "Ready to Assign" banner: green when 0, amber when positive (money
  still unassigned), red when negative (over-assigned).
- Then collapsible category groups (e.g. Bills, Needs, Wants, Savings). Each row shows
  category name, Assigned, and Available.
- "Available" is a colored pill: green = funded, amber = underfunded, red = overspent,
  gray = zero.
- Tap a row to edit Assigned with a numeric keypad bottom sheet. Long-press or a
  menu action opens Move Money.
- Floating action button (+) to add a transaction.

### Add Transaction
- Big amount at the top, typed with the system decimal keyboard (focused on open).
- Toggle: Expense / Income. Fields: Payee, Category, Account, Date (default today), Memo.
- Save button always visible. Must work in under ~10 seconds for the common case.

### Accounts
- List of accounts with balances and a total at the top.
- Tap an account to see its transaction list (newest first). Swipe to delete, tap to edit.

### Move Money
- Bottom sheet: From category, To category, Amount. Show each category's
  Available so overspending is easy to fix.

### Widgets
- Small: "Ready to Assign" amount.
- Medium: Ready to Assign plus the top 3 categories by Available.
- Widgets never read the database. After any data change, the shared code writes a
  small summary (Ready to Assign + top 3 categories by Available) through an
  expect/actual WidgetSnapshotStore:
  - Android: SharedPreferences, then GlanceAppWidget.updateAll
  - iOS: App Group UserDefaults, then WidgetCenter.shared.reloadAllTimelines()
- Tapping a widget opens the Add Transaction screen.

### Theme
- Material 3, light and dark mode, follow the system setting.
- Accessible contrast. Color is never the only signal: also show text or an icon for overspent.

## Code conventions
- Packages: data/ (SQLDelight, repositories), domain/ (budget math, models),
  ui/ (screens, components, theme), widget/ (snapshot store, expect/actual).
- Platform code lives in androidMain / iosMain only when it must (database driver,
  widget snapshot store, platform entry points).
- Keep functions small. No business logic inside composables.
- Every budget calculation has a unit test.
- Don't leave TODOs or placeholder code silently. List open issues at the end of your reply.

## Commands
- Day-to-day tests (fast, Android only): the Android unit test task for :shared
- Day-to-day build: ./gradlew :androidApp:assembleDebug
- Install on a connected Android phone: ./gradlew :androidApp:installDebug
- iOS: build/test only when asked or when a feature is finished (Kotlin/Native builds
  are slow). Use the iosSimulatorArm64 tasks, or run the iosApp from Xcode.
- Never run ./gradlew build or allTests unless asked.
- Run the relevant checks before saying a task is done, and report the real results.
  If something could not be verified from the terminal (for example anything that
  needs Xcode or a physical device), say so.

## Working style
- Make small steps and keep the app runnable after each one.
- Explain any Gradle or dependency change in one or two lines.
- If something is ambiguous, ask instead of guessing.