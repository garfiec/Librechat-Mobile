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
        if #available(iOS 27.1, *) {
            let traits: [UITrait] = UITraitCollection.systemTraitsAffectingVerticalBarEdge + [UITraitHorizontalSizeClass.self]
            registerForTraitChanges(traits) { (controller: ShortcutHostController, _: UITraitCollection) in
                controller.pushWindowFeatures()
            }
            // Folding activates the division region without a layout pass, so ask for one as well, in
            // case the region only updates after this handler.
            view.addInteraction(UIHingeInteraction { [weak self] _, _ in
                self?.pushWindowFeatures()
                self?.view.setNeedsLayout()
            })
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        pushWindowFeatures()
    }

    /// Hands Compose the vertical bar column, the system zone at its top, the active fold and the size
    /// class (`LocalWindowFeatures`). Before iOS 27.1 it reports nothing, so Compose keeps its defaults.
    private func pushWindowFeatures() {
        guard #available(iOS 27.1, *) else { return }
        let bounds = view.bounds
        let insets = view.safeAreaInsets
        var topLeftZone: Double = 0
        var topRightZone: Double = 0
        // The zone above the bars is the occlusion region in the column's top corner; the camera
        // ring inside it is a region of its own that doesn't reach the top.
        for region in view.reservedRegions(kind: .occlusion) where region.isActive && region.frame.minY <= 0.5 {
            if region.frame.minX <= 0.5 { topLeftZone = max(topLeftZone, Double(region.frame.maxY)) }
            if region.frame.maxX >= bounds.width - 0.5 { topRightZone = max(topRightZone, Double(region.frame.maxY)) }
        }
        let fold = view.reservedRegions(kind: .division).first { $0.isActive }
        IosWindowFeatures.shared.update(
            barEdge: Int32(traitCollection.verticalBarEdge.rawValue),
            leftInset: Double(insets.left),
            rightInset: Double(insets.right),
            topLeftZone: topLeftZone,
            topRightZone: topRightZone,
            foldActive: fold != nil,
            foldX: Double(fold?.frame.minX ?? 0),
            foldY: Double(fold?.frame.minY ?? 0),
            foldWidth: Double(fold?.frame.width ?? 0),
            foldHeight: Double(fold?.frame.height ?? 0),
            foldMarginTop: Double(fold?.margins.top ?? 0),
            foldMarginLeft: Double(fold?.margins.left ?? 0),
            foldMarginBottom: Double(fold?.margins.bottom ?? 0),
            foldMarginRight: Double(fold?.margins.right ?? 0),
            regularWidth: traitCollection.horizontalSizeClass == .regular
        )
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
