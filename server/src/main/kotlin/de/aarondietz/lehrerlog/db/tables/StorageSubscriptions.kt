package de.aarondietz.lehrerlog.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.util.*

object StorageSubscriptions : Table("storage_subscriptions") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }.uniqueIndex()
    val ownerType = varchar("owner_type", 20)
    val ownerId = uuid("owner_id")
    val planId = reference("plan_id", StoragePlans.id)
    val active = bool("active").default(true)
    val startsAt = timestampWithTimeZone("starts_at").defaultExpression(CurrentTimestampWithTimeZone)
    val endsAt = timestampWithTimeZone("ends_at").nullable()

    init {
        index(false, ownerType, ownerId)
    }

    override val primaryKey = PrimaryKey(id)
}
