package com.malai.test.acceptance.kensa.fixtures

import java.sql.Connection
import java.sql.DriverManager

object DatabaseConnection {
    fun createConnection(databaseUrl: String): Connection = DriverManager.getConnection(databaseUrl, "malai", "malai")
}
