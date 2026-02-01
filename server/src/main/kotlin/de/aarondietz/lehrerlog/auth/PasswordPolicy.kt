package de.aarondietz.lehrerlog.auth

object PasswordPolicy {
    private const val MIN_LENGTH = 12
    private const val ERROR_MESSAGE =
        "Password must be at least 12 characters and include uppercase, lowercase, number, and special character"

    fun validate(password: String): PasswordValidationResult {
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        val isValid = password.length >= MIN_LENGTH && hasUpper && hasLower && hasDigit && hasSpecial
        return if (isValid) {
            PasswordValidationResult(valid = true, message = null)
        } else {
            PasswordValidationResult(valid = false, message = ERROR_MESSAGE)
        }
    }
}

data class PasswordValidationResult(
    val valid: Boolean,
    val message: String?
)
