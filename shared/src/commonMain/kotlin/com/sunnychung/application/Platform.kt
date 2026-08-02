package com.sunnychung.application

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform