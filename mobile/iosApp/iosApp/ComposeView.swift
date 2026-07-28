import SwiftUI
import Shared

/// Bridges the Compose Multiplatform UI into SwiftUI.
///
/// Wraps `MainViewControllerKt.MainViewController()` which renders
/// the full CMP navigation shell (Chat, Agents, Files, Settings tabs)
/// with all 80+ shared composables.
///
/// Koin must be initialized before this view appears — handled in
/// `iOSApp.init()` via `IosKoinHelperKt.startIosKoin()`.
struct LibreChatComposeView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // No dynamic updates needed — CMP manages its own state internally
    }
}
