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
        ShortcutHostController(content: MainViewControllerKt.MainViewController())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // No dynamic updates needed — CMP manages its own state internally
    }
}

/// Hosts the Compose controller and adds the app's hardware-keyboard shortcuts.
///
/// Compose only sees a key while one of its nodes holds focus, so a shortcut handled in Compose is
/// lost after leaving a screen whose field had focus, or after a tap on empty space. A key command
/// here sits above the Compose view in the responder chain, so UIKit finds it whatever Compose has
/// focused, and it is matched before the key reaches Compose.
final class ShortcutHostController: UIViewController {
    private let content: UIViewController

    init(content: UIViewController) {
        self.content = content
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(content)
        content.view.frame = view.bounds
        content.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(content.view)
        content.didMove(toParent: self)
    }

    // Forward the status bar, home indicator and rotation to the Compose controller, or wrapping it loses them.
    override var childForStatusBarStyle: UIViewController? { content }
    override var childForStatusBarHidden: UIViewController? { content }
    override var childForHomeIndicatorAutoHidden: UIViewController? { content }
    override var childForScreenEdgesDeferringSystemGestures: UIViewController? { content }
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask { content.supportedInterfaceOrientations }

    override var keyCommands: [UIKeyCommand]? {
        // ⌘ on an Apple keyboard, Ctrl on others, as on Android.
        [UIKeyModifierFlags.command, .control].map { modifier in
            let command = UIKeyCommand(
                input: "f",
                modifierFlags: [modifier, .shift],
                action: #selector(openSearch)
            )
            command.wantsPriorityOverSystemBehavior = true
            return command
        }
    }

    @objc private func openSearch() {
        _ = MainViewControllerKt.openSearchShortcut()
    }
}
