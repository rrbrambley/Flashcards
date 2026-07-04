package com.rrbrambley.flashcards.shared.domain

import com.rrbrambley.flashcards.shared.api.ApiError
import kotlin.test.Test
import kotlin.test.assertEquals

class ActionErrorTest {

    @Test
    fun from_mapsStatusesToCases() {
        assertEquals(ActionError.RateLimit, ActionError.from(ApiError.Client(429, "slow down")))
        assertEquals(ActionError.Locked, ActionError.from(ApiError.Client(403, "locked")))
        assertEquals(ActionError.Rejected("bad"), ActionError.from(ApiError.Client(400, "bad")))
        assertEquals(ActionError.Rejected("nope"), ActionError.from(ApiError.Validation("nope")))
        assertEquals(ActionError.Rejected("dupe"), ActionError.from(ApiError.Conflict("dupe")))
        assertEquals(ActionError.Generic, ActionError.from(ApiError.Client(418, "teapot")))
        assertEquals(ActionError.Generic, ActionError.from(ApiError.Server(500, "boom")))
    }

    @Test
    fun fromThrowable_nonApiError_isGeneric() {
        assertEquals(ActionError.Generic, ActionError.fromThrowable(RuntimeException("network")))
        assertEquals(ActionError.RateLimit, ActionError.fromThrowable(ApiError.Client(429, null)))
    }
}
