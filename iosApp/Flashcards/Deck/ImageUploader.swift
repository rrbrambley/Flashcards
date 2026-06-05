import PhotosUI
import Shared
import SwiftUI

/// Uploads a picked front-of-card image via the shared API client, returning the stored CDN URL.
/// Mirrors Android's `AndroidImageUploader` (validate type/size → POST the bytes), with one
/// iOS-specific accommodation: PhotosPicker on a real iPhone usually hands back **HEIC**, which the
/// server doesn't accept, and camera photos routinely exceed the 5 MB cap. So an image that isn't
/// already a server-accepted type within the cap is transcoded to JPEG (downscaled to fit) before
/// upload — keeping the feature usable on-device while still matching Android for supported formats.
struct ImageUploader {
    let apiClient: FlashcardApiClient

    /// Shown on any upload failure (unreadable image, network, 503, rejected) — matches Android's copy.
    static let errorMessage = "Couldn't add the image. Use a JPEG, PNG, WebP or GIF under 5 MB and try again."

    /// Matches the backend's `/images` limit (5 MB) and accepted MIME types.
    private static let maxBytes = 5 * 1024 * 1024
    private static let serverTypes: Set<String> = ["image/jpeg", "image/png", "image/webp", "image/gif"]

    enum UploadError: Error { case unreadable }

    /// Loads the picked item's bytes, prepares a server-ready payload, and uploads it. Throws on a
    /// failed load or a failed upload (network / 503 / rejected) so the caller can show an error.
    func upload(item: PhotosPickerItem) async throws -> String {
        guard let data = try await item.loadTransferable(type: Data.self) else {
            throw UploadError.unreadable
        }
        return try await upload(data: data, mime: item.supportedContentTypes.first?.preferredMIMEType)
    }

    /// The upload core (separated from PhotosUI for testability): normalize → POST → return the URL.
    func upload(data: Data, mime: String?) async throws -> String {
        let (bytes, contentType) = serverReadyPayload(data: data, mime: mime)
        let ext = contentType.split(separator: "/").last.map(String.init) ?? "jpg"
        return try await apiClient.uploadImageData(
            data: bytes,
            filename: "image.\(ext)",
            contentType: contentType
        )
    }

    /// Already-supported, within-cap images upload as-is (parity with Android); anything else
    /// (HEIC, oversized) is re-encoded as JPEG.
    private func serverReadyPayload(data: Data, mime: String?) -> (Data, String) {
        if let mime, Self.serverTypes.contains(mime), data.count <= Self.maxBytes {
            return (data, mime)
        }
        if let jpeg = jpegUnderCap(from: data) {
            return (jpeg, "image/jpeg")
        }
        // Couldn't decode/transcode: send the original and let the server enforce its own limits.
        return (data, mime ?? "image/jpeg")
    }

    /// Re-encodes to JPEG under the size cap: cap the longest side, then drop quality as needed.
    private func jpegUnderCap(from data: Data) -> Data? {
        guard var image = UIImage(data: data) else { return nil }
        let maxDimension: CGFloat = 2048
        let longest = max(image.size.width, image.size.height)
        if longest > maxDimension, let scaled = image.resized(by: maxDimension / longest) {
            image = scaled
        }
        for quality in [CGFloat(0.85), 0.6, 0.4] {
            if let jpeg = image.jpegData(compressionQuality: quality), jpeg.count <= Self.maxBytes {
                return jpeg
            }
        }
        return image.jpegData(compressionQuality: 0.4)
    }
}

private extension UIImage {
    func resized(by factor: CGFloat) -> UIImage? {
        let newSize = CGSize(width: size.width * factor, height: size.height * factor)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        return renderer.image { _ in draw(in: CGRect(origin: .zero, size: newSize)) }
    }
}
