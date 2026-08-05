package com.sunnychung.application.easytransfer.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferUriValidationTest {
    @Test
    fun acceptsWebAndCustomProtocols() {
        assertTrue("https://example.com".isValidTransferUri())
        assertTrue("mailto:hello@example.com".isValidTransferUri())
        assertTrue("tel:+85212345678".isValidTransferUri())
        assertTrue("my-app+preview://document/42".isValidTransferUri())
    }

    @Test
    fun rejectsValuesWithoutACompleteScheme() {
        assertFalse("example.com".isValidTransferUri())
        assertFalse("1app:value".isValidTransferUri())
        assertFalse("my app:value".isValidTransferUri())
        assertFalse("custom:".isValidTransferUri())
    }
}

