package com.whereareyou.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommandProtocolTest {

    @Test
    fun `documented example encodes to the documented wire string`() {
        val payload = CommandPayload(command = CommandCode.STATUS_REQUEST, requestId = "4821", authCode = "7314")
        assertEquals("ک1|1|4821|7314", CommandProtocol.encode(payload))
    }

    @Test
    fun `round trips status request`() {
        val payload = CommandPayload(command = CommandCode.STATUS_REQUEST, requestId = "4821", authCode = "7314")
        val decoded = CommandProtocol.decode(CommandProtocol.encode(payload))
        assertEquals(CommandDecodeResult.Success(payload), decoded)
    }

    @Test
    fun `round trips emergency callback request`() {
        val payload = CommandPayload(command = CommandCode.EMERGENCY_CALLBACK_REQUEST, requestId = "9001", authCode = "7314")
        val decoded = CommandProtocol.decode(CommandProtocol.encode(payload))
        assertEquals(CommandDecodeResult.Success(payload), decoded)
    }

    @Test
    fun `unsupported version is rejected`() {
        val result = CommandProtocol.decode("ک9|1|4821|7314")
        assertIs<CommandDecodeResult.Failure>(result)
    }

    @Test
    fun `unknown command code is rejected`() {
        val result = CommandProtocol.decode("ک1|99|4821|7314")
        assertIs<CommandDecodeResult.Failure>(result)
    }

    @Test
    fun `malformed field count is rejected`() {
        val result = CommandProtocol.decode("ک1|1|4821")
        assertIs<CommandDecodeResult.Failure>(result)
    }

    @Test
    fun `blank auth code is rejected`() {
        val result = CommandProtocol.decode("ک1|1|4821|")
        assertIs<CommandDecodeResult.Failure>(result)
    }
}
