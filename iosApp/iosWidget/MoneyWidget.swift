import SwiftUI
import WidgetKit

/// Keys and App Group must match WidgetSnapshotKeys in the shared Kotlin code.
private enum SnapshotKeys {
    static let appGroup = "group.com.bunleng.saving_luy"
    static let readyToAssign = "ready_to_assign"
    static let readyToAssignText = "ready_to_assign_text"
    static let categoryCount = "category_count"
    static func categoryName(_ index: Int) -> String { "category_\(index)_name" }
    static func categoryAvailable(_ index: Int) -> String { "category_\(index)_available" }
    static func categoryAvailableText(_ index: Int) -> String { "category_\(index)_available_text" }
}

struct WidgetCategory: Hashable {
    let name: String
    let available: Int
    let availableText: String
}

struct MoneyEntry: TimelineEntry {
    let date: Date
    /// Nil until the app has written its first snapshot.
    let readyToAssign: Int?
    let readyToAssignText: String
    let categories: [WidgetCategory]

    /// Reads the summary the app saved. Amounts arrive pre-formatted by the app.
    static func load() -> MoneyEntry {
        let defaults = UserDefaults(suiteName: SnapshotKeys.appGroup) ?? .standard
        guard defaults.object(forKey: SnapshotKeys.readyToAssign) != nil else {
            return MoneyEntry(date: Date(), readyToAssign: nil, readyToAssignText: "—", categories: [])
        }
        let count = defaults.integer(forKey: SnapshotKeys.categoryCount)
        let categories = (0..<count).map { index in
            WidgetCategory(
                name: defaults.string(forKey: SnapshotKeys.categoryName(index)) ?? "",
                available: defaults.integer(forKey: SnapshotKeys.categoryAvailable(index)),
                availableText: defaults.string(forKey: SnapshotKeys.categoryAvailableText(index)) ?? ""
            )
        }
        return MoneyEntry(
            date: Date(),
            readyToAssign: defaults.integer(forKey: SnapshotKeys.readyToAssign),
            readyToAssignText: defaults.string(forKey: SnapshotKeys.readyToAssignText) ?? "",
            categories: categories
        )
    }

    static let preview = MoneyEntry(
        date: Date(),
        readyToAssign: 12_500,
        readyToAssignText: "$125.00",
        categories: [
            WidgetCategory(name: "Rent", available: 90_000, availableText: "$900.00"),
            WidgetCategory(name: "Food", available: 15_000, availableText: "$150.00"),
            WidgetCategory(name: "Fun", available: -2_000, availableText: "-$20.00"),
        ]
    )
}

/// The app reloads timelines after every data change, so no scheduled refresh is needed.
struct MoneyProvider: TimelineProvider {
    func placeholder(in context: Context) -> MoneyEntry { .preview }

    func getSnapshot(in context: Context, completion: @escaping (MoneyEntry) -> Void) {
        completion(context.isPreview ? .preview : .load())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MoneyEntry>) -> Void) {
        completion(Timeline(entries: [.load()], policy: .never))
    }
}

/// Green = funded / fully assigned, amber = money still to assign, red = negative.
private func statusColor(_ amount: Int, positiveIsWarning: Bool = false) -> Color {
    if amount < 0 { return .red }
    if amount > 0 && positiveIsWarning { return .orange }
    return .green
}

struct ReadyToAssignView: View {
    let entry: MoneyEntry

    private var label: String {
        guard let amount = entry.readyToAssign else { return "Open the app to start" }
        if amount < 0 { return "⚠ Over-assigned" }
        if amount > 0 { return "Ready to Assign" }
        return "All money assigned"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(entry.readyToAssignText)
                .font(.title2.bold())
                .foregroundStyle(statusColor(entry.readyToAssign ?? 0, positiveIsWarning: true))
                .minimumScaleFactor(0.5)
                .lineLimit(1)
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
    }
}

struct MoneyWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: MoneyEntry

    var body: some View {
        Group {
            if family == .systemMedium && !entry.categories.isEmpty {
                HStack(spacing: 16) {
                    ReadyToAssignView(entry: entry)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    VStack(alignment: .leading, spacing: 6) {
                        ForEach(entry.categories, id: \.self) { category in
                            HStack {
                                Text(category.name).lineLimit(1)
                                Spacer()
                                Text((category.available < 0 ? "⚠ " : "") + category.availableText)
                                    .foregroundStyle(statusColor(category.available))
                                    .lineLimit(1)
                            }
                            .font(.caption)
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            } else {
                ReadyToAssignView(entry: entry)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .containerBackground(.fill.tertiary, for: .widget)
        .widgetURL(URL(string: "savingluy://add-transaction"))
    }
}

@main
struct MoneyWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "MoneyWidget", provider: MoneyProvider()) { entry in
            MoneyWidgetView(entry: entry)
        }
        .configurationDisplayName("Ready to Assign")
        .description("Ready to Assign and your top categories. Tap to add a transaction.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
