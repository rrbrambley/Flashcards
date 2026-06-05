import SDWebImageSwiftUI
import SwiftUI

/// Renders a front-of-card image from a URL — raster or **SVG** (the Country Flags cards) — with a
/// loading placeholder, a graceful failure, and on-disk caching for smooth practice. SVG support
/// comes from `SDImageSVGCoder`, registered at app launch in `FlashcardsApp`.
struct RemoteCardImage: View {
    let url: String

    var body: some View {
        WebImage(
            url: URL(string: url),
            // Rasterize SVGs at a crisp card size rather than their (often tiny) intrinsic size.
            context: [.imageThumbnailPixelSize: CGSize(width: 600, height: 600)]
        ) { image in
            image.resizable().scaledToFit()
        } placeholder: {
            ProgressView()
        }
        .transition(.fade)
        .accessibilityHidden(true)
    }
}
