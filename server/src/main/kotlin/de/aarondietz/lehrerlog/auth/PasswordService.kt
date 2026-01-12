package de.aarondietz.lehrerlog.auth

import org.mindrot.jbcrypt.BCrypt

class PasswordService {
    companion object {
        private const val BCRYPT_ROUNDS = 12
    }

    fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_ROUNDS))
    }

    fun verifyPassword(password: String, hash: String): Boolean {
        return BCrypt.checkpw(password, hash)
    }
}
