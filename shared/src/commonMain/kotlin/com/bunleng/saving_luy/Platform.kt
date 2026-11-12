package com.bunleng.saving_luy

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform