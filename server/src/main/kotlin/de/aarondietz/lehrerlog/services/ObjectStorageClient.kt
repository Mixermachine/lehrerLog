package de.aarondietz.lehrerlog.services

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.InputStream
import java.net.URI

class ObjectStorageClient private constructor(
    private val s3Client: S3Client,
    private val bucket: String
) {

    fun putObject(objectKey: String, contentType: String, sizeBytes: Long, input: InputStream) {
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .contentType(contentType)
            .contentLength(sizeBytes)
            .build()
        s3Client.putObject(request, RequestBody.fromInputStream(input, sizeBytes))
    }

    fun openObjectStream(objectKey: String): InputStream {
        val request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .build()
        return s3Client.getObject(request)
    }

    companion object {
        fun fromEnv(): ObjectStorageClient? {
            val endpoint = System.getenv("OBJECT_STORAGE_ENDPOINT")?.trim().orEmpty()
            val bucket = System.getenv("OBJECT_STORAGE_BUCKET")?.trim().orEmpty()
            if (endpoint.isBlank() && bucket.isBlank()) {
                return null
            }

            val region = System.getenv("OBJECT_STORAGE_REGION")?.trim().orEmpty().ifBlank { "garage" }
            val accessKey = System.getenv("OBJECT_STORAGE_ACCESS_KEY")?.trim().orEmpty()
            val secretKey = System.getenv("OBJECT_STORAGE_SECRET_KEY")?.trim().orEmpty()
            val pathStyle = System.getenv("OBJECT_STORAGE_PATH_STYLE")?.trim().orEmpty()
                .ifBlank { "true" }
                .equals("true", ignoreCase = true)

            if (endpoint.isBlank() || bucket.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
                throw IllegalStateException("Object storage configuration is incomplete.")
            }

            val s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyle)
                .build()

            val client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                    )
                )
                .serviceConfiguration(s3Config)
                .build()

            return ObjectStorageClient(client, bucket)
        }
    }
}
