@file:Suppress("LocalVariableName", "VariableNaming")

package doist.ffs.routes

import doist.ffs.db.capturingLastInsertId
import doist.ffs.db.projects
import doist.ffs.plugins.database
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail

const val PATH_PROJECTS = "/projects"

@Suppress("FunctionName")
fun PATH_PROJECT(id: Any) = "$PATH_PROJECTS/$id"

fun Application.installProjectRoutes() {
    routing {
        route(PATH_PROJECTS) {
            createProject()
            getProjects()
            getProject()
            updateProject()
            deleteProject()
        }
    }
}

/**
 * Create a new project.
 *
 * On success, responds `201 Created` with an empty body.
 *
 * | Parameter         | Required | Description             |
 * | ----------------- | -------- | ----------------------- |
 * | `organization_id` | Yes      | ID of the organization. |
 * | `name`            | Yes      | Name of the project.    |
 */
private fun Route.createProject() = post {
    val params = call.receiveParameters()
    val organizationId = params.getOrFail<Long>("organization_id")
    val name = params.getOrFail("name")
    val id = database.capturingLastInsertId {
        projects.insert(organization_id = organizationId, name = name)
    }
    call.run {
        response.header(HttpHeaders.Location, PATH_PROJECT(id))
        respond(HttpStatusCode.Created)
    }
}

/**
 * Lists existing projects for the organization.
 *
 * On success, responds `200 OK` with a JSON array containing all projects for the organization.
 *
 * | Parameter         | Required | Description             |
 * | ----------------- | -------- | ----------------------- |
 * | `organization_id` | Yes      | ID of the organization. |
 */
private fun Route.getProjects() = get {
    val organizationId = call.request.queryParameters.getOrFail<Long>("organization_id")
    val projects =
        database.projects.selectByOrganization(organizationId).executeAsList()
    call.respond(HttpStatusCode.OK, projects)
}

/**
 * Get an existing project.
 *
 * On success, responds `200 OK` with a JSON object for the project.
 *
 * | Parameter | Required | Description        |
 * | --------- | -------- | ------------------ |
 * | `id`      | Yes      | ID of the project. |
 */
private fun Route.getProject() = get("{id}") {
    val id = call.parameters.getOrFail<Long>("id")
    val project = database.projects.select(id = id).executeAsOneOrNull()
        ?: throw NotFoundException()
    call.respond(HttpStatusCode.OK, project)
}

/**
 * Update a project.
 *
 * On success, responds `204 No Content` with an empty body.
 *
 * | Parameter | Required | Description          |
 * | --------- | -------- | -------------------- |
 * | `id`      | Yes      | ID of the project.   |
 * | `name`    | No       | Name of the project. |
 */
private fun Route.updateProject() = put("{id}") {
    val id = call.parameters.getOrFail<Long>("id")
    val name = call.receiveParameters()["name"]
    database.projects.run {
        val project = select(id = id).executeAsOneOrNull() ?: throw NotFoundException()
        update(id = id, name = name ?: project.name)
    }
    call.respond(HttpStatusCode.NoContent)
}

/**
 * Delete a project.
 *
 * On success, responds `204 No Content` with an empty body.
 *
 * | Parameter | Required | Description        |
 * | --------- | -------- | ------------------ |
 * | `id`      | Yes      | ID of the project. |
 */
private fun Route.deleteProject() = delete("{id}") {
    val id = call.parameters.getOrFail<Long>("id")
    database.projects.delete(id = id)
    call.respond(HttpStatusCode.NoContent)
}
