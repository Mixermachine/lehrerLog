package de.aarondietz.lehrerlog.ui.util

import de.aarondietz.lehrerlog.auth.AuthResult
import de.aarondietz.lehrerlog.data.repository.FileUploadResult
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.utils.io.errors.*
import lehrerlog.composeapp.generated.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorMapperTest {

    @Test
    fun authResultError_mapsStatusCode401() {
        val error = AuthResult.Error("Unauthorized", 401)
        assertEquals(Res.string.error_auth_invalid_credentials, error.toStringResource())
    }

    @Test
    fun authResultError_mapsStatusCode403() {
        val error = AuthResult.Error("Forbidden", 403)
        assertEquals(Res.string.error_resource_forbidden, error.toStringResource())
    }

    @Test
    fun authResultError_mapsStatusCode404() {
        val error = AuthResult.Error("Not Found", 404)
        assertEquals(Res.string.error_resource_not_found, error.toStringResource())
    }

    @Test
    fun authResultError_mapsStatusCode409() {
        val error = AuthResult.Error("Conflict", 409)
        assertEquals(Res.string.error_resource_conflict, error.toStringResource())
    }

    @Test
    fun authResultError_mapsEmailPasswordRequiredMessage() {
        val error = AuthResult.Error("Email and password are required", 400)
        assertEquals(Res.string.error_auth_email_required, error.toStringResource())
    }

    @Test
    fun authResultError_mapsInvalidCredentialsMessage() {
        val error = AuthResult.Error("Invalid credentials", 401)
        assertEquals(Res.string.error_auth_invalid_credentials, error.toStringResource())
    }

    @Test
    fun authResultError_mapsSchoolCodeRequiredMessage() {
        val error = AuthResult.Error("School code is required", 400)
        assertEquals(Res.string.error_auth_school_required, error.toStringResource())
    }

    @Test
    fun authResultError_mapsInvalidSchoolCodeMessage() {
        val error = AuthResult.Error("Invalid school code", 400)
        assertEquals(Res.string.error_auth_school_invalid, error.toStringResource())
    }

    @Test
    fun authResultError_mapsPasswordRequirementsMessage() {
        val error = AuthResult.Error("Password does not meet requirements", 400)
        assertEquals(Res.string.error_auth_password_policy, error.toStringResource())
    }

    @Test
    fun authResultError_mapsSessionExpiredMessage() {
        val error = AuthResult.Error("Session expired", 401)
        assertEquals(Res.string.error_auth_session_expired, error.toStringResource())
    }

    @Test
    fun authResultError_mapsEmailAlreadyExistsMessage() {
        val error = AuthResult.Error("Email already in use", 409)
        assertEquals(Res.string.error_auth_email_exists, error.toStringResource())
    }

    @Test
    fun authResultError_mapsNetworkErrorMessage() {
        val error = AuthResult.Error("Network error occurred", 0)
        assertEquals(Res.string.error_network_general, error.toStringResource())
    }

    @Test
    fun authResultError_fallsBackToGenericError() {
        val error = AuthResult.Error("Unknown error", 500)
        assertEquals(Res.string.error_generic, error.toStringResource())
    }

    @Test
    fun throwable_mapsIOException() {
        val exception = IOException("Connection failed")
        assertEquals(Res.string.error_network_general, exception.toStringResource())
    }

    @Test
    fun throwable_fallsBackToGenericError() {
        val exception = IllegalStateException("Something went wrong")
        assertEquals(Res.string.error_generic, exception.toStringResource())
    }

    @Test
    fun resultGetErrorResource_returnsNullOnSuccess() {
        val result = Result.success("data")
        assertEquals(null, result.getErrorResource())
    }

    @Test
    fun resultGetErrorResource_returnsResourceOnFailure() {
        val result = Result.failure<String>(IOException("Network error"))
        assertEquals(Res.string.error_network_general, result.getErrorResource())
    }

    @Test
    fun fileUploadResultFileTooLarge_mapsCorrectly() {
        val result = FileUploadResult.FileTooLarge(100)
        assertEquals(Res.string.error_file_too_large, result.toStringResource())
    }

    @Test
    fun fileUploadResultQuotaExceeded_mapsCorrectly() {
        val result = FileUploadResult.QuotaExceeded
        assertEquals(Res.string.error_file_quota_exceeded, result.toStringResource())
    }

    @Test
    fun fileUploadResultError_mapsCorrectly() {
        val result = FileUploadResult.Error("Upload failed")
        assertEquals(Res.string.error_file_upload_failed, result.toStringResource())
    }
}
