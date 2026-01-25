package de.aarondietz.lehrerlog.data

import kotlinx.serialization.Serializable

@Serializable
data class ParentInviteCreateRequest(
    val studentId: String,
    val expiresAt: String? = null
)

@Serializable
data class ParentInviteRedeemRequest(
    val code: String,
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String
)

@Serializable
data class ParentInviteCreateResponse(
    val invite: ParentInviteDto,
    val code: String
)

@Serializable
data class ParentInviteDto(
    val id: String,
    val studentId: String,
    val status: ParentInviteStatus,
    val expiresAt: String,
    val createdAt: String
)

@Serializable
data class ParentLinkDto(
    val id: String,
    val parentUserId: String,
    val studentId: String,
    val status: ParentLinkStatus,
    val createdAt: String,
    val revokedAt: String?
)

@Serializable
enum class ParentInviteStatus {
    ACTIVE,
    USED,
    EXPIRED,
    REVOKED
}

@Serializable
enum class ParentLinkStatus {
    PENDING,
    ACTIVE,
    REVOKED
}
