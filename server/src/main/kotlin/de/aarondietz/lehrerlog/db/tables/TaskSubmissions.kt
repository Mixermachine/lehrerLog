package de.aarondietz.lehrerlog.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.util.*

object TaskSubmissions : Table("task_submissions") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }.uniqueIndex()
    val taskId = reference("task_id", Tasks)
    val studentId = reference("student_id", Students)
    val submissionType = varchar("submission_type", 20)
    val grade = decimal("grade", 5, 2).nullable()
    val note = text("note").nullable()
    val lateStatus = varchar("late_status", 32).default("ON_TIME")
    val latePeriodId = reference("late_period_id", LatePeriods).nullable()
    val decidedBy = reference("decided_by", Users).nullable()
    val decidedAt = timestampWithTimeZone("decided_at").nullable()
    val submittedAt = timestampWithTimeZone("submitted_at").defaultExpression(CurrentTimestampWithTimeZone)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)
    val version = long("version").default(1)

    init {
        uniqueIndex(taskId, studentId)
        index(false, taskId)
        index(false, studentId)
    }

    override val primaryKey = PrimaryKey(taskId, studentId)
}
