import UIKit

/// The window's permanent rootViewController. The Compose menu and KorGE gameplay take turns as its
/// only child (`show(_:)`), always laid out on a landscape-shaped stage.
///
/// iPadOS 26 stopped guaranteeing a landscape-only app a landscape window: with the device's
/// rotation lock off, an iPad held upright can launch it, or rotate it, into portrait anyway -
/// Info.plist's ~ipad orientations, UIRequiresFullScreen (deprecated in iPadOS 26, see Apple's
/// TN3192), the app delegate's .landscape mask and requestGeometryUpdate are all honoured on iPhone
/// and were all in place when an owner's iPad still opened the game portrait. So the shape is not
/// left to the system: whenever this view is taller than wide, the stage keeps its landscape size
/// and is turned a quarter, the way the system itself shows a landscape-only app on a portrait
/// device. Turning the iPad sideways then reads it upright; if the system follows by rotating the
/// interface, the view goes wide and the quarter turn comes off. Touches arrive in each child's own
/// coordinates (UIKit converts through the turn).
final class LandscapeHostViewController: UIViewController {
    private let stage = UIView()
    private(set) var current: UIViewController?

    /// Quarter turns (clockwise) applied to the stage: 0 when the view is wide, otherwise +1/-1.
    private(set) var quarterTurns = 0
    /// The landscape side the interface was last on. Portrait turns the stage to match it, so a
    /// player who tips the iPad upright mid-level sees the game stay put instead of flip.
    private var lastLandscape: UIInterfaceOrientation = .landscapeRight
    private var lastLoggedSize = CGSize.zero

    /// Called after a layout that changed the turn - the stage's safe area moved with it.
    var onTurnChanged: (() -> Void)?

    override func loadView() {
        let root = UIView()
        root.backgroundColor = .black
        stage.backgroundColor = .black
        root.addSubview(stage)
        view = root
    }

    /// Swaps the child. The outgoing one leaves the hierarchy completely (as it did when each was
    /// the window's rootViewController in turn): ads present from whatever is on screen.
    func show(_ child: UIViewController) {
        guard child !== current else { return }
        if let old = current {
            old.willMove(toParent: nil)
            old.view.removeFromSuperview()
            old.removeFromParent()
        }
        addChild(child)
        child.view.frame = stage.bounds
        child.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        stage.addSubview(child.view)
        child.didMove(toParent: self)
        current = child
        setNeedsUpdateOfHomeIndicatorAutoHidden()
        setNeedsUpdateOfScreenEdgesDeferringSystemGestures()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let size = view.bounds.size
        let interface = view.window?.windowScene?.interfaceOrientation ?? .unknown
        if interface.isLandscape { lastLandscape = interface }

        let turns: Int
        if size.width >= size.height {
            turns = 0
        } else {
            // +1 shows it the way a landscapeRight interface would; upside down flips the sign.
            let base = lastLandscape == .landscapeLeft ? -1 : 1
            turns = interface == .portraitUpsideDown ? -base : base
        }

        let long = max(size.width, size.height)
        let short = min(size.width, size.height)
        stage.transform = .identity
        stage.bounds = CGRect(x: 0, y: 0, width: long, height: short)
        stage.center = CGPoint(x: view.bounds.midX, y: view.bounds.midY)
        stage.transform = CGAffineTransform(rotationAngle: CGFloat(turns) * .pi / 2)

        if size != lastLoggedSize || turns != quarterTurns {
            lastLoggedSize = size
            // CI's iPad simulator step greps for this line.
            print("HOST: view=\(Int(size.width))x\(Int(size.height)) interface=\(interface.rawValue) quarterTurns=\(turns)")
        }
        if turns != quarterTurns {
            let wasWide = quarterTurns == 0
            quarterTurns = turns
            if wasWide { DispatchQueue.main.async { [weak self] in self?.requestLandscape() } }
            onTurnChanged?()
        }
    }

    /// The window's safe area as the stage sees it: under a quarter turn, the window's bottom (home
    /// indicator) is the stage's right or left edge.
    var stageSafeAreaInsets: UIEdgeInsets {
        let i = view.window?.safeAreaInsets ?? view.safeAreaInsets
        switch quarterTurns {
        case 1: return UIEdgeInsets(top: i.right, left: i.top, bottom: i.left, right: i.bottom)
        case -1: return UIEdgeInsets(top: i.left, left: i.bottom, bottom: i.right, right: i.top)
        default: return i
        }
    }

    /// Asks the scene for landscape once more on going tall - the system may honour it, which
    /// takes the quarter turn back off.
    func requestLandscape() {
        if #available(iOS 16.0, *) {
            view.window?.windowScene?.requestGeometryUpdate(.iOS(interfaceOrientations: .landscape)) { _ in }
            setNeedsUpdateOfSupportedInterfaceOrientations()
        } else {
            UIViewController.attemptRotationToDeviceOrientation()
        }
    }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask { .landscape }
    override var preferredInterfaceOrientationForPresentation: UIInterfaceOrientation { .landscapeRight }
    override var prefersStatusBarHidden: Bool { true }
    override var childForHomeIndicatorAutoHidden: UIViewController? { current }
    override var childForScreenEdgesDeferringSystemGestures: UIViewController? { current }
}
