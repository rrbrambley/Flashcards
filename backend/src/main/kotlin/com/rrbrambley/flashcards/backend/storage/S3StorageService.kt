package com.rrbrambley.flashcards.backend.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.net.url.Url
import java.util.UUID

/**
 * Uploads to S3 (or any S3-compatible endpoint) and returns a CDN URL.
 * Credentials come from the default AWS chain (env / ~/.aws / instance role).
 *
 * @param endpoint optional S3 endpoint override (e.g. a local MinIO); null = real AWS.
 */
class S3StorageService(private val bucket: String, private val cdnBaseUrl: String, region: String, endpoint: String?) :
    StorageService {

    private val client = S3Client {
        this.region = region
        if (!endpoint.isNullOrBlank()) {
            endpointUrl = Url.parse(endpoint)
            forcePathStyle = true // required for MinIO / path-style endpoints
        }
    }

    override suspend fun upload(bytes: ByteArray, contentType: String, extension: String): String {
        val key = "images/${UUID.randomUUID()}.$extension"
        client.putObject {
            this.bucket = this@S3StorageService.bucket
            this.key = key
            this.body = ByteStream.fromBytes(bytes)
            this.contentType = contentType
        }
        return "${cdnBaseUrl.trimEnd('/')}/$key"
    }
}
