package de.aarondietz.lehrerlog.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class SchoolClass @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.random().toString(),
    val name: String,
    val students: SnapshotStateList<Student> = mutableStateListOf()
)