package de.aarondietz.lehrerlog.data

import kotlinx.serialization.Serializable

@Serializable
data class SchoolSearchResultDto(
    val code: String,
    val name: String,
    val city: String? = null,
    val postcode: String? = null,
    val state: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
