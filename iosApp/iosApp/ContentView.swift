import UIKit
import SwiftUI
import Shared
import WidgetKit

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController(reloadWidgets: {
            WidgetCenter.shared.reloadAllTimelines()
        })
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            // Widget taps open savingluy://add-transaction.
            .onOpenURL { url in
                if url.scheme == "savingluy" && url.host == "add-transaction" {
                    MainViewControllerKt.requestAddTransaction()
                }
            }
    }
}