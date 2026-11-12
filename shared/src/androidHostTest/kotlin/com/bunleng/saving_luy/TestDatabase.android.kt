package com.bunleng.saving_luy

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.bunleng.saving_luy.data.db.MoneyDatabase

actual fun createTestDriver(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { MoneyDatabase.Schema.create(it) }
