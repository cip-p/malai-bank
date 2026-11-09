package com.malai.cognitostub

import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class StubUser(
    val sub: UUID,
    val email: String,
    val name: String,
    val confirmed: Boolean,
    val groups: Set<String>,
)

class UserDirectory(private val file: Path) {

    private val objectMapper = jacksonObjectMapper()
    private val usersByEmail = ConcurrentHashMap<String, StubUser>()

    init {
        if (Files.exists(file)) {
            objectMapper.readValue<List<StubUser>>(Files.readString(file)).forEach { usersByEmail[key(it.email)] = it }
        }
    }

    fun find(email: String): StubUser? = usersByEmail[key(email)]

    fun findBySub(sub: UUID): StubUser? = usersByEmail.values.firstOrNull { it.sub == sub }

    fun all(): List<StubUser> = usersByEmail.values.sortedBy { it.email }

    fun save(user: StubUser): StubUser {
        usersByEmail[key(user.email)] = user
        persist()
        return user
    }

    fun remove(email: String): StubUser? = usersByEmail.remove(key(email))?.also { persist() }

    fun clear() {
        usersByEmail.clear()
        persist()
    }

    @Synchronized
    private fun persist() {
        Files.createDirectories(file.parent)
        Files.writeString(file, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(all()))
    }

    private fun key(email: String) = email.trim().lowercase()
}
