package com.rrbrambley.flashcards.backend.admin

import com.rrbrambley.flashcards.backend.auth.Permission
import com.rrbrambley.flashcards.backend.routes.pageCursor
import com.rrbrambley.flashcards.backend.routes.pageLimit
import com.rrbrambley.flashcards.backend.routes.pathLong
import com.rrbrambley.flashcards.backend.routes.requirePermission
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Admin RBAC management — every endpoint is gated on [Permission.MANAGE_ROLES], so non-admins get a
 * 403. Manages the runtime user↔role assignments; the role/permission catalog itself stays
 * code-defined (`auth/Rbac.kt`) and is exposed read-only via GET /admin/roles.
 */
fun Route.adminRoutes() {
    route("/admin") {
        get("/users") {
            call.requirePermission(Permission.MANAGE_ROLES)
            val query = call.request.queryParameters["q"]
            call.respond(AdminRepository.listUsers(call.pageLimit(), call.pageCursor(), query))
        }
        get("/roles") {
            call.requirePermission(Permission.MANAGE_ROLES)
            call.respond(AdminRepository.roleCatalog())
        }
        post("/users/{id}/roles") {
            call.requirePermission(Permission.MANAGE_ROLES)
            val request = call.receive<GrantRoleRequest>()
            call.respond(AdminRepository.grantRole(call.pathLong("id"), request.role))
        }
        delete("/users/{id}/roles/{roleKey}") {
            call.requirePermission(Permission.MANAGE_ROLES)
            val roleKey = call.parameters["roleKey"] ?: throw IllegalArgumentException("Missing role key")
            call.respond(AdminRepository.revokeRole(call.pathLong("id"), roleKey))
        }
    }
}
