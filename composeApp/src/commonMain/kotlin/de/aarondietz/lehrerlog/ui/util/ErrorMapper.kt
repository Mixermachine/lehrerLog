package de.aarondietz.lehrerlog.ui.util

import de.aarondietz.lehrerlog.auth.AuthResult
import de.aarondietz.lehrerlog.data.repository.FileUploadResult
import io.ktor.client.plugins.*
import io.ktor.utils.io.errors.*
import kotlinx.io.IOException
import lehrerlog.composeapp.generated.resources.*
import lehrerlog.composeapp.generated.resources.Res
import lehrerlog.composeapp.generated.resources.error_auth_email_exists
import lehrerlog.composeapp.generated.resources.error_auth_email_required
import lehrerlog.composeapp.generated.resources.error_auth_invalid_credentials
import org.jetbrains.compose.resources.StringResource

/**
 * Maps AuthResult.Error to localized string resource.
 * Checks HTTP status codes and known server error message patterns.
 */
fun AuthResult.Error.toStringResource(): StringResource {
    return when {
        // Known server error message patterns (check first for specificity)
        message.contains("Email and password are required", ignoreCase = true) ->
            Res.string.error_auth_email_required
        message.contains("Invalid credentials", ignoreCase = true) ->
            Res.string.error_auth_invalid_credentials
        message.contains("School code", ignoreCase = true) && message.contains("required", ignoreCase = true) ->
            Res.string.error_auth_school_required
        message.contains("Invalid school code", ignoreCase = true) ->
            Res.string.error_auth_school_invalid
        message.contains("Password does not meet requirements", ignoreCase = true) ->
            Res.string.error_auth_password_policy
        message.contains("Session expired", ignoreCase = true) ->
            Res.string.error_auth_session_expired
        message.contains("Email already", ignoreCase = true) ->
            Res.string.error_auth_email_exists
        message.contains("Network error", ignoreCase = true) ->
            Res.string.error_network_general

        // HTTP status codes (fallback when message doesn't match)
        code == 401 -> Res.string.error_auth_invalid_credentials
        code == 403 -> Res.string.error_resource_forbidden
        code == 404 -> Res.string.error_resource_not_found
        code == 409 -> Res.string.error_resource_conflict

        // Generic fallback
        else -> Res.string.error_generic
    }
}

/**
 * Maps standard exceptions to localized string resources.
 * Used with Result<T>.exceptionOrNull()
 */
fun Throwable.toStringResource(): StringResource {
    return when (this) {
        is ClientRequestException -> {
            when (response.status.value) {
                401 -> Res.string.error_auth_session_expired
                403 -> Res.string.error_resource_forbidden
                404 -> Res.string.error_resource_not_found
                409 -> Res.string.error_resource_conflict
                else -> Res.string.error_server_generic
            }
        }
        is ServerResponseException -> Res.string.error_server_generic
        is IOException -> Res.string.error_network_general
        else -> Res.string.error_generic
    }
}

/**
 * Extension for Result<T> to get error string resource.
 */
fun <T> Result<T>.getErrorResource(): StringResource? {
    return exceptionOrNull()?.toStringResource()
}

/**
 * Maps FileUploadResult to localized string resource.
 */
fun FileUploadResult.toStringResource(): StringResource {
    return when (this) {
        is FileUploadResult.FileTooLarge -> Res.string.error_file_too_large
        is FileUploadResult.QuotaExceeded -> Res.string.error_file_quota_exceeded
        is FileUploadResult.Error -> Res.string.error_file_upload_failed
        is FileUploadResult.Success -> throw IllegalStateException("Success is not an error")
    }
}
