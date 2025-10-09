package de.aarondietz.lehrerlog.data

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class Student @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.Companion.random().toString(),
    val firstName: String,
    val lastName: String,
)