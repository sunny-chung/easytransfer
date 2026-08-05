package com.sunnychung.application.easytransfer.ui

internal fun String.isValidTransferUri(): Boolean {
    val separator = indexOf(':')
    if (separator <= 0 || separator == lastIndex || !first().isLetter() || any(Char::isWhitespace)) return false
    return substring(1, separator).all { character ->
        character.isLetterOrDigit() || character == '+' || character == '-' || character == '.'
    }
}

