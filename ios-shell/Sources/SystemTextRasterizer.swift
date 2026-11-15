import CoreText
import GameMain
import UIKit

/// Draws in-game text with iOS's own text engine, for the scripts the game's two Latin-only fonts
/// have no letters for (Japanese, Korean, Chinese, Hindi, Arabic, Persian, Thai, Russian...) -
/// the iOS counterpart of android-shell's PlatformText.kt. KorGE's text view (game.scene.GameText)
/// asks for a string through GameMain's GameTextBridge and shows the image it gets back.
///
/// White text on a transparent image at scale 1 (one point is one pixel), one line per "\n", in
/// the system font, with `pad` transparent pixels on every side.
final class SystemTextRasterizer: NSObject, GameMain.GameTextRasterizer {
    private static let pad: CGFloat = 2

    func rasterize(text: String, pixelSize: Double, bold: Bool, languageTag: String) -> GameMain.GameTextRaster? {
        let font = UIFont.systemFont(ofSize: CGFloat(pixelSize), weight: bold ? .bold : .regular)
        var attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.white]
        if !languageTag.isEmpty {
            // Chinese and Japanese share code points; the language picks the shapes.
            attributes[NSAttributedString.Key(kCTLanguageAttributeName as String)] = languageTag
        }
        let lines = text.components(separatedBy: "\n")
        let lineHeight = ceil(font.lineHeight)
        var width: CGFloat = 0
        for line in lines {
            width = max(width, (line as NSString).size(withAttributes: attributes).width)
        }
        let height = lineHeight * CGFloat(lines.count)
        let pad = SystemTextRasterizer.pad
        let size = CGSize(width: max(ceil(width), 1) + pad * 2, height: max(height, 1) + pad * 2)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            for (index, line) in lines.enumerated() {
                (line as NSString).draw(
                    at: CGPoint(x: pad, y: pad + CGFloat(index) * lineHeight),
                    withAttributes: attributes
                )
            }
        }
        return GameMain.GameTextRaster(
            image: image, pad: Int32(pad), textWidth: Double(width), textHeight: Double(height)
        )
    }

    /// Call before ShellAppDelegate boots KorGE, so the first loading screen already has it.
    static func register() {
        GameMain.GameTextBridge.shared.rasterizer = SystemTextRasterizer()
    }
}
