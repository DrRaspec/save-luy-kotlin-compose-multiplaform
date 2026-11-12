package com.bunleng.saving_luy.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.bunleng.saving_luy.data.db.MoneyDatabase

fun createDatabaseDriver(context: Context): SqlDriver =
    AndroidSqliteDriver(MoneyDatabase.Schema, context, DATABASE_NAME)
