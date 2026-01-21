package de.aarondietz.lehrerlog.database

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Test for DatabaseManager with async driver support.
 * Verifies that database initialization works correctly with suspend functions.
 *
 * Note: This is a simple smoke test that verifies the database manager can be instantiated
 * and getDatabase() can be called. Full integration tests with actual drivers are done
 * in platform-specific tests.
 */
class DatabaseManagerTest {

    @Test
    fun testDatabaseManagerInstantiation() = runTest {
        // Create a real driver factory for this platform
        val factory = DatabaseDriverFactory(context = null)
        val manager = DatabaseManager(factory)

        // Verify manager can be instantiated
        assertNotNull(manager, "DatabaseManager should be instantiated")
    }

    @Test
    fun testDatabaseInitialization() = runTest {
        // Create a real driver factory
        val factory = DatabaseDriverFactory(context = null)
        val manager = DatabaseManager(factory)

        // Get database - should initialize on first call
        val db1 = manager.getDatabase()
        assertNotNull(db1, "Database should be initialized")

        // Get database again - should return same instance
        val db2 = manager.getDatabase()
        assertNotNull(db2, "Should return database instance again")
    }
}
