package com.sunnychung.application.easytransfer

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
