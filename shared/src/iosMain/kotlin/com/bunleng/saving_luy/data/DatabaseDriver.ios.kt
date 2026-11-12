package com.bunleng.saving_luy.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.bunleng.saving_luy.data.db.MoneyDatabase

fun createDatabaseDriver(): SqlDriver = NativeSqliteDriver(MoneyDatabase.Schema, DATABASE_NAME)
