import SDWebImageSVGCoder
import UIKit
import XCTest

/// Decodes every committed flag through the **same SVG coder the app registers**, and asserts each one
/// actually draws (#369, #370).
///
/// The point is the renderer, not our files: Apple's CoreSVG is a partial SVG implementation, and the
/// bugs it produced were invisible to anything that only inspected the SVG source. Cook Islands lost
/// its whole field and canton — 5% of the frame drawn — while Turks & Caicos dropped the red saltire
/// out of its Union Jack. Both shipped looking fine to every check we had.
///
/// These are deliberately **not** golden-image comparisons: anti-aliasing shifts between Xcode and iOS
/// versions, so stored reference PNGs produce failures that mean nothing and get ignored. Coverage and
/// colour presence are stable across versions and still catch a region that stopped drawing.
///
/// The complementary guard is `tools/country-flags`'s `npm run check`, which rejects the *constructs*
/// known to break renderers. That catches subtler damage this can't — Pitcairn's saltire was merely
/// mis-shaped, which no coverage metric would flag — so the two layers are worth having together.
final class FlagRenderingTests: XCTestCase {

    /// Nepal is the only non-rectangular national flag, so it legitimately leaves the frame part-empty.
    private static let expectedMinimumCoverage: [String: Double] = ["np": 0.4]
    private static let defaultMinimumCoverage = 0.9

    private func flagURLs() throws -> [URL] {
        let bundle = Bundle(for: type(of: self))
        guard let dir = bundle.url(forResource: "flags", withExtension: nil) else {
            throw XCTSkip("Flag resources missing from the test bundle — check project.yml")
        }
        let urls = try FileManager.default
            .contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "svg" }
        XCTAssertGreaterThan(urls.count, 200, "Expected the full seeded flag set")
        return urls.sorted { $0.lastPathComponent < $1.lastPathComponent }
    }

    /// Decodes through the app's SVG coder, then draws the result — the coder hands back a *vector*
    /// image with no bitmap of its own, so drawing it is both what produces pixels to inspect and what
    /// exercises the CoreSVG path that actually renders on screen.
    private func decode(_ url: URL) throws -> UIImage {
        let data = try Data(contentsOf: url)
        let vector = try XCTUnwrap(
            SDImageSVGCoder.shared.decodedImage(with: data, options: nil),
            "\(url.lastPathComponent) did not decode at all"
        )
        XCTAssertGreaterThan(vector.size.width, 0, "\(url.lastPathComponent) decoded to a zero-width image")

        let width: CGFloat = 600
        let size = CGSize(width: width, height: max(1, (width * vector.size.height / vector.size.width).rounded()))
        return UIGraphicsImageRenderer(size: size).image { _ in
            vector.draw(in: CGRect(origin: .zero, size: size))
        }
    }

    /// Fraction of sampled pixels that are opaque, and how many distinct colours appear.
    private func stats(_ image: UIImage) throws -> (coverage: Double, colours: Int) {
        let cgImage = try XCTUnwrap(image.cgImage, "decoded image had no bitmap")
        let width = cgImage.width, height = cgImage.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let context = CGContext(
            data: &pixels,
            width: width, height: height,
            bitsPerComponent: 8, bytesPerRow: width * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        )
        try XCTUnwrap(context).draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

        var colours = Set<UInt32>()
        var opaque = 0, total = 0
        for y in stride(from: 0, to: height, by: 3) {
            for x in stride(from: 0, to: width, by: 3) {
                let i = (y * width + x) * 4
                let (r, g, b, a) = (pixels[i], pixels[i + 1], pixels[i + 2], pixels[i + 3])
                colours.insert(UInt32(r) << 24 | UInt32(g) << 16 | UInt32(b) << 8 | UInt32(a))
                total += 1
                if a > 8 { opaque += 1 }
            }
        }
        return (total > 0 ? Double(opaque) / Double(total) : 0, colours.count)
    }

    /// The broad net: every flag decodes, draws more than one colour, and fills its frame.
    func testEveryFlagDrawsAFullFrame() throws {
        for url in try flagURLs() {
            try autoreleasepool {
                let code = url.deletingPathExtension().lastPathComponent
                let (coverage, colours) = try stats(try decode(url))
                let minimum = Self.expectedMinimumCoverage[code] ?? Self.defaultMinimumCoverage

                XCTAssertGreaterThan(colours, 1, "\(code) rendered as a single flat colour — nothing drew")
                XCTAssertGreaterThanOrEqual(
                    coverage, minimum,
                    "\(code) covered only \(Int(coverage * 100))% of its frame; a region failed to draw"
                )
            }
        }
    }

    /// Regression pin for Cook Islands, the failure that reached a device: its blue field and canton
    /// stopped drawing entirely, leaving only the ring of stars.
    ///
    /// Deliberately just the one. The obvious companion — "Turks & Caicos still has red in its Union
    /// Jack" — was tried and **passed against the known-broken file**: it lost only the diagonal
    /// saltire while keeping the red St George's cross, so a colour-presence check can't tell. Damage
    /// that subtle (and Pitcairn's merely mirrored saltire) is caught upstream by
    /// `tools/country-flags`'s `npm run check` rejecting the construct that causes it, not here.
    func testCookIslandsKeepsItsField() throws {
        let url = try XCTUnwrap(
            try flagURLs().first { $0.lastPathComponent == "ck.svg" },
            "ck.svg missing from the test bundle"
        )
        XCTAssertTrue(
            try contains(colour: (r: 1, g: 33, b: 105), in: try decode(url)),
            "Cook Islands is missing its blue field"
        )
    }

    /// Whether any sampled pixel is close to `colour` — tolerant of anti-aliasing and colour management.
    private func contains(colour: (r: Int, g: Int, b: Int), in image: UIImage) throws -> Bool {
        let cgImage = try XCTUnwrap(image.cgImage)
        let width = cgImage.width, height = cgImage.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let context = CGContext(
            data: &pixels,
            width: width, height: height,
            bitsPerComponent: 8, bytesPerRow: width * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        )
        try XCTUnwrap(context).draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

        let tolerance = 24
        for i in stride(from: 0, to: pixels.count, by: 4) {
            if abs(Int(pixels[i]) - colour.r) < tolerance,
               abs(Int(pixels[i + 1]) - colour.g) < tolerance,
               abs(Int(pixels[i + 2]) - colour.b) < tolerance {
                return true
            }
        }
        return false
    }
}
