package de.aarondietz.lehrerlog.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object StudentClasses : Table("student_classes") {
    val studentId = reference("student_id", Students)
    val schoolClassId = reference("school_class_id", SchoolClasses)
    val validFrom = timestampWithTimeZone("valid_from")
    val validTill = timestampWithTimeZone("valid_till").nullable()
    val version = long("version").default(1)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(studentId, schoolClassId, validFrom)
}
