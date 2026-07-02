package com.rrbrambley.flashcards.backend.flags

import com.rrbrambley.flashcards.backend.auth.Permission
import com.rrbrambley.flashcards.backend.routes.pathLong
import com.rrbrambley.flashcards.backend.routes.pathString
import com.rrbrambley.flashcards.backend.routes.requirePermission
import com.rrbrambley.flashcards.backend.routes.userId
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** The caller's resolved feature flags (FLA-174). Clients also receive these on `MeResponse`; this
 *  lets a client refresh flag state without re-authenticating. */
fun Route.flagRoutes() {
    get("/flags") {
        call.respond(FeatureFlagService.flagsFor(call.userId()))
    }
}

/** Admin feature-flag management (FLA-175) — every endpoint gated on [Permission.MANAGE_FEATURE_FLAGS]. */
fun Route.adminFlagRoutes() {
    route("/admin/flags") {
        get {
            call.requirePermission(Permission.MANAGE_FEATURE_FLAGS)
            call.respond(FeatureFlagRepository.listFlags())
        }
        patch("/{key}") {
            call.requirePermission(Permission.MANAGE_FEATURE_FLAGS)
            val request = call.receive<SetFlagEnabledRequest>()
            call.respond(FeatureFlagRepository.setGlobal(call.pathString("key"), request.enabled))
        }
        put("/{key}/users/{userId}") {
            call.requirePermission(Permission.MANAGE_FEATURE_FLAGS)
            val request = call.receive<SetFlagOverrideRequest>()
            val updated = FeatureFlagRepository.setUserOverride(
                call.pathString("key"),
                call.pathLong("userId"),
                request.enabled,
            )
            call.respond(updated)
        }
        delete("/{key}/users/{userId}") {
            call.requirePermission(Permission.MANAGE_FEATURE_FLAGS)
            call.respond(FeatureFlagRepository.clearUserOverride(call.pathString("key"), call.pathLong("userId")))
        }
        put("/{key}/roles/{roleKey}") {
            call.requirePermission(Permission.MANAGE_FEATURE_FLAGS)
            val request = call.receive<SetFlagOverrideRequest>()
            val updated = FeatureFlagRepository.setRoleOverride(
                call.pathString("key"),
                call.pathString("roleKey"),
                request.enabled,
            )
            call.respond(updated)
        }
        delete("/{key}/roles/{roleKey}") {
            call.requirePermission(Permission.MANAGE_FEATURE_FLAGS)
            call.respond(FeatureFlagRepository.clearRoleOverride(call.pathString("key"), call.pathString("roleKey")))
        }
    }
}
