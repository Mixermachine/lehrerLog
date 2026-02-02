package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.data.SchoolClassDto
import de.aarondietz.lehrerlog.logging.logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for SchoolClass data (online-first).
 */
class SchoolClassRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage,
    private val baseUrl: String
) {
    private val logger by lazy { logger() }
    private val classesState = MutableStateFlow<List<SchoolClassDto>>(emptyList())

    /**
     * Get classes from in-memory state.
     */
    suspend fun getClassesFlow(): Flow<List<SchoolClassDto>> {
        return classesState.asStateFlow()
    }

    /**
     * Load classes from server and update in-memory state.
     */
    suspend fun refreshClasses(schoolId: String): Result<List<SchoolClassDto>> {
        return try {
            val classes = httpClient.get("$baseUrl/api/classes") {
                parameter("schoolId", schoolId)
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<List<SchoolClassDto>>()

            classesState.value = classes

            Result.success(classes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new class on the server.
     */
    suspend fun createClass(
        name: String,
        alternativeName: String? = null
    ): Result<SchoolClassDto> {
        logger.d { "createClass called: name=$name, alternativeName=$alternativeName" }
        return try {
            val schoolClass = httpClient.post("$baseUrl/api/classes") {
                contentType(ContentType.Application.Json)
                setBody(
                    de.aarondietz.lehrerlog.data.CreateSchoolClassRequest(
                        name = name,
                        alternativeName = alternativeName
                    )
                )
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<SchoolClassDto>()

            classesState.value = classesState.value + schoolClass

            Result.success(schoolClass)
        } catch (e: Exception) {
            logger.e(e) { "Failed to create class: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Delete a class on the server.
     */
    suspend fun deleteClass(classId: String): Result<Unit> {
        return try {
            httpClient.delete("$baseUrl/api/classes/$classId") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }
            classesState.value = classesState.value.filterNot { it.id == classId }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
