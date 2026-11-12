package com.bunleng.saving_luy

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import com.bunleng.saving_luy.data.db.MoneyDatabase

actual fun createTestDriver(): SqlDriver = inMemoryDriver(MoneyDatabase.Schema)
