import SDWebImageSwiftUI
import SwiftUI

/// Renders a front-of-card image from a URL — raster or **SVG** (the Flags of the World cards) — with a
/// loading placeholder, a graceful failure, and on-disk caching for smooth practice. SVG support
/// comes from `SDImageSVGCoder`, registered at app launch in `FlashcardsApp`.
struct RemoteCardImage: View {
    let url: String
    /// Called once the image settles — loaded or failed. Lets a timed practice run resume its
    /// countdown, paused while the prompt image loads (#314). `WebImage`'s onSuccess fires for cached
    /// images too (with a cache-type), so this covers the already-cached case without extra handling.
    var onReady: (() -> Void)?

    var body: some View {
        WebImage(
            url: URL(string: url),
            // Rasterize SVGs at a crisp card size rather than their (often tiny) intrinsic size.
            context: [.imageThumbnailPixelSize: CGSize(width: 600, height: 600)]
        ) { image in
            // The content closure runs only once the image has loaded (the placeholder shows until
            // then), so its appearance is a reliable "settled" signal — more so than `.onSuccess`, which
            // can be missed with this content-closure initializer. Covers the cached case too (content
            // appears immediately). Failures fall through to `.onFailure` below.
            image.resizable().scaledToFit()
                .onAppear { onReady?() }
        } placeholder: {
            ProgressView()
        }
        .onFailure { _ in onReady?() }
        .transition(.fade)
        .accessibilityHidden(true)
    }
}
