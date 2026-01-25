package de.aarondietz.lehrerlog.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object TeacherLatePolicy : Table("teacher_late_policy") {
    val teacherId = reference("teacher_id", Users, onDelete = ReferenceOption.CASCADE)
    val threshold = integer("threshold")
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(teacherId)
}

object LatePeriods : UUIDTable("late_periods") {
    val teacherId = reference("teacher_id", Users, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 200)
    val startsAt = timestampWithTimeZone("starts_at")
    val endsAt = timestampWithTimeZone("ends_at").nullable()
    val isActive = bool("is_active").default(false)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
}

object StudentLateStats : Table("student_late_stats") {
    val teacherId = reference("teacher_id", Users, onDelete = ReferenceOption.CASCADE)
    val studentId = reference("student_id", Students, onDelete = ReferenceOption.CASCADE)
    val periodId = reference("period_id", LatePeriods, onDelete = ReferenceOption.CASCADE)
    val totalMissed = integer("total_missed")
    val missedSincePunishment = integer("missed_since_punishment")
    val punishmentRequired = bool("punishment_required")
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(teacherId, studentId, periodId)
}

object PunishmentRecords : UUIDTable("punishment_records") {
    val teacherId = reference("teacher_id", Users, onDelete = ReferenceOption.CASCADE)
    val studentId = reference("student_id", Students, onDelete = ReferenceOption.CASCADE)
    val periodId = reference("period_id", LatePeriods, onDelete = ReferenceOption.CASCADE)
    val triggeredAt = timestampWithTimeZone("triggered_at")
    val resolvedAt = timestampWithTimeZone("resolved_at").nullable()
    val resolvedBy = reference("resolved_by", Users).nullable()
    val note = text("note").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
}
