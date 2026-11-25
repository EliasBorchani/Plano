package app.plano.server.groups

import app.plano.contract.CreateGroupRequest
import app.plano.contract.CreatedDto
import app.plano.contract.GroupDto
import app.plano.contract.GroupInviteDto
import app.plano.contract.GroupInvitePreviewDto
import app.plano.contract.GroupSummaryDto
import app.plano.contract.PersonDto
import app.plano.contract.Routes
import app.plano.contract.UpdateGroupInviteRequest
import app.plano.domain.groups.GroupError
import app.plano.domain.groups.GroupName
import app.plano.domain.groups.InviteLimits
import app.plano.server.ApiError
import app.plano.server.Db
import app.plano.server.auth.Auth
import app.plano.server.auth.requireUser
import app.plano.server.events.eventSummaries
import app.plano.server.events.guestEventOf
import app.plano.server.instant
import app.plano.server.invalid
import app.plano.server.newLinkToken
import app.plano.server.notFound
import app.plano.server.now
import app.plano.server.query
import app.plano.server.toSql
import app.plano.server.toUuidOr404
import app.plano.server.update
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.sql.Connection
import java.time.Clock
import java.util.UUID
import kotlin.time.Duration.Companion.days

/** Groups are for accounts: a guest lives in one browser and belongs to one event. */
private fun Connection.requireAccount(userId: UUID) {
    if (guestEventOf(userId) != null) throw ApiError(HttpStatusCode.Forbidden, "GUEST_CANNOT_USE_GROUPS")
}

/** 404 for a non-member, as for events: the group's existence stays private. */
private fun Connection.requireGroupMember(groupId: UUID, userId: UUID) {
    if (query("select 1 from group_member where group_id = ? and user_id = ?", groupId, userId) { 1 }.isEmpty()) notFound()
}

private fun Connection.loadGroup(groupId: UUID): GroupDto {
    val members = query(
        "select u.id, u.display_name from group_member m join app_user u on u.id = m.user_id " +
            "where m.group_id = ? order by m.joined_at",
        groupId,
    ) { PersonDto(it.getString(1), it.getString(2)) }
    return query(
        "select id, name, invite_token, invite_max_uses, invite_uses, invite_expires_at from user_group where id = ?",
        groupId,
    ) {
        GroupDto(
            id = it.getString(1),
            name = it.getString(2),
            members = members,
            invite = GroupInviteDto(it.getString(3), it.getInt(4), it.getInt(5), it.instant(6).toString()),
        )
    }.single()
}

fun Route.groupRoutes(db: Db, auth: Auth, clock: Clock) {
    get(Routes.GROUPS) {
        val user = call.requireUser(auth)
        val groups = db.tx {
            query(
                "select g.id, g.name, (select count(*) from group_member c where c.group_id = g.id) " +
                    "from group_member m join user_group g on g.id = m.group_id where m.user_id = ? order by g.name",
                user.userId,
            ) { GroupSummaryDto(it.getString(1), it.getString(2), it.getInt(3)) }
        }
        call.respond(groups)
    }

    post(Routes.GROUPS) {
        val user = call.requireUser(auth)
        val raw = call.receive<CreateGroupRequest>().name
        GroupName.validate(raw)?.let { invalid(it.name) }
        val limits = InviteLimits()
        val id = db.tx {
            requireAccount(user.userId)
            val id = query(
                "insert into user_group (name, created_by, invite_token, invite_max_uses, invite_expires_at) " +
                    "values (?, ?, ?, ?, ?) returning id",
                GroupName.normalize(raw),
                user.userId,
                newLinkToken(),
                limits.maxUses,
                (clock.now() + limits.expiresInDays.days).toSql(),
            ) { it.getObject(1, UUID::class.java) }.single()
            update("insert into group_member (group_id, user_id) values (?, ?)", id, user.userId)
            id
        }
        call.respond(HttpStatusCode.Created, CreatedDto(id.toString()))
    }

    get(Routes.group("{groupId}")) {
        val user = call.requireUser(auth)
        val groupId = call.parameters["groupId"].toUuidOr404()
        call.respond(db.tx { requireGroupMember(groupId, user.userId); loadGroup(groupId) })
    }

    // Every member manages the link (owner, 2026-11-25). New limits restart the expiry from now.
    put(Routes.groupInvite("{groupId}")) {
        val user = call.requireUser(auth)
        val groupId = call.parameters["groupId"].toUuidOr404()
        val body = call.receive<UpdateGroupInviteRequest>()
        val limits = InviteLimits(body.maxUses, body.expiresInDays)
        limits.validate()?.let { invalid(it.name) }
        val group = db.tx {
            requireGroupMember(groupId, user.userId)
            val expiresAt = (clock.now() + limits.expiresInDays.days).toSql()
            if (body.newLink) {
                update(
                    "update user_group set invite_token = ?, invite_max_uses = ?, invite_uses = 0, invite_expires_at = ? where id = ?",
                    newLinkToken(),
                    limits.maxUses,
                    expiresAt,
                    groupId,
                )
            } else {
                update(
                    "update user_group set invite_max_uses = ?, invite_expires_at = ? where id = ?",
                    limits.maxUses,
                    expiresAt,
                    groupId,
                )
            }
            loadGroup(groupId)
        }
        call.respond(group)
    }

    // Any member removes anyone (owner, 2026-11-25); your own id is leaving. The last one out deletes the group.
    delete(Routes.groupMember("{groupId}", "{userId}")) {
        val user = call.requireUser(auth)
        val groupId = call.parameters["groupId"].toUuidOr404()
        val target = call.parameters["userId"].toUuidOr404()
        db.tx {
            requireGroupMember(groupId, user.userId)
            if (update("delete from group_member where group_id = ? and user_id = ?", groupId, target) == 0) notFound()
            update("delete from user_group g where g.id = ? and not exists (select 1 from group_member m where m.group_id = g.id)", groupId)
        }
        call.respond(HttpStatusCode.NoContent)
    }

    // PRD: an event shows in « events with this group » only to the members who took part in it.
    get(Routes.groupEvents("{groupId}")) {
        val user = call.requireUser(auth)
        val groupId = call.parameters["groupId"].toUuidOr404()
        call.respond(db.tx { requireGroupMember(groupId, user.userId); eventSummaries(user.userId, groupId) })
    }

    get(Routes.groupJoin("{token}")) {
        val token = call.parameters["token"] ?: notFound()
        val preview = db.tx {
            query(
                "select g.id, g.name, (select count(*) from group_member m where m.group_id = g.id) " +
                    "from user_group g where g.invite_token = ?",
                token,
            ) { GroupInvitePreviewDto(it.getString(1), it.getString(2), it.getInt(3)) }.firstOrNull()
        } ?: notFound()
        call.respond(preview)
    }

    post(Routes.groupJoin("{token}")) {
        val user = call.requireUser(auth)
        val token = call.parameters["token"] ?: notFound()
        val groupId = db.tx {
            requireAccount(user.userId)
            // Locked: two people using the last place at once cannot both get in.
            val (groupId, full, expired) = query(
                "select id, invite_uses >= invite_max_uses, invite_expires_at <= ? from user_group " +
                    "where invite_token = ? for update",
                clock.now().toSql(),
                token,
            ) { Triple(it.getObject(1, UUID::class.java), it.getBoolean(2), it.getBoolean(3)) }.firstOrNull() ?: notFound()
            val already = query("select 1 from group_member where group_id = ? and user_id = ?", groupId, user.userId) { 1 }.isNotEmpty()
            if (!already) {
                if (expired) throw ApiError(HttpStatusCode.Gone, GroupError.INVITE_EXPIRED.name)
                if (full) throw ApiError(HttpStatusCode.Gone, GroupError.INVITE_FULL.name)
                update("insert into group_member (group_id, user_id) values (?, ?)", groupId, user.userId)
                update("update user_group set invite_uses = invite_uses + 1 where id = ?", groupId)
            }
            groupId
        }
        call.respond(CreatedDto(groupId.toString()))
    }
}
