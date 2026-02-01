package de.aarondietz.lehrerlog.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.util.*

object SubmissionFiles : Table("submission_files") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }.uniqueIndex()
    val submissionId = reference("submission_id", TaskSubmissions.id)
    val objectKey = text("object_key")
    val sizeBytes = long("size_bytes")
    val mimeType = text("mime_type")
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
