package de.aarondietz.lehrerlog.data

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class Task(
    val id: String = Uuid.random().toString(),
    val name: String,

)