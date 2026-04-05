package io.kelta.worker.scim.service;

import io.kelta.worker.scim.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ScimGroupService {

    private static final Logger log = LoggerFactory.getLogger(ScimGroupService.class);

    private static final Map<String, String> GROUP_ATTR_MAP = Map.of(
            "displayname", "ug.name",
            "externalid", "ug.oidc_group_name"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ScimFilterParser filterParser;

    public ScimGroupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.filterParser = new ScimFilterParser(GROUP_ATTR_MAP);
    }

    public ScimListResponse<ScimGroup> listGroups(String tenantId, String filter,
                                                   int startIndex, int count, String baseUrl) {
        ScimFilterParser.ParsedFilter parsed = filterParser.parse(filter);

        List<Object> countParams = new ArrayList<>();
        countParams.add(tenantId);
        countParams.addAll(parsed.params());

        String countSql = "SELECT COUNT(*) FROM user_group ug WHERE ug.tenant_id = ? AND " + parsed.sql();
        int total = jdbcTemplate.queryForObject(countSql, Integer.class, countParams.toArray());

        List<Object> queryParams = new ArrayList<>();
        queryParams.add(tenantId);
        queryParams.addAll(parsed.params());

        int offset = Math.max(0, startIndex - 1);
        String sql = "SELECT ug.id, ug.name, ug.description, ug.oidc_group_name, "
                + "ug.created_at, ug.updated_at "
                + "FROM user_group ug WHERE ug.tenant_id = ? AND " + parsed.sql()
                + " ORDER BY ug.created_at ASC LIMIT ? OFFSET ?";
        queryParams.add(count);
        queryParams.add(offset);

        List<ScimGroup> groups = jdbcTemplate.query(sql, (rs, rowNum) -> mapGroup(rs, tenantId, baseUrl),
                queryParams.toArray());

        return new ScimListResponse<>(groups, total, startIndex, groups.size());
    }

    public ScimGroup getGroup(String tenantId, String groupId, String baseUrl) {
        String sql = "SELECT ug.id, ug.name, ug.description, ug.oidc_group_name, "
                + "ug.created_at, ug.updated_at "
                + "FROM user_group ug WHERE ug.tenant_id = ? AND ug.id = ?";
        List<ScimGroup> groups = jdbcTemplate.query(sql, (rs, rowNum) -> mapGroup(rs, tenantId, baseUrl),
                tenantId, groupId);
        if (groups.isEmpty()) {
            throw new ScimException(HttpStatus.NOT_FOUND, "Group " + groupId + " not found");
        }
        return groups.get(0);
    }

    @Transactional
    public ScimGroup createGroup(String tenantId, ScimGroup request, String baseUrl) {
        if (request.getDisplayName() == null || request.getDisplayName().isBlank()) {
            throw new ScimException(HttpStatus.BAD_REQUEST, "displayName is required");
        }

        // Check for existing group with same name
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_group WHERE tenant_id = ? AND name = ?",
                Integer.class, tenantId, request.getDisplayName());
        if (existing != null && existing > 0) {
            throw new ScimException(HttpStatus.CONFLICT,
                    "Group with name " + request.getDisplayName() + " already exists", "uniqueness");
        }

        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO user_group (id, tenant_id, name, description, group_type, source, "
                        + "oidc_group_name, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'PUBLIC', 'SCIM', ?, NOW(), NOW())",
                id, tenantId, request.getDisplayName(), null, request.getExternalId());

        // Add members if provided
        if (request.getMembers() != null) {
            for (ScimMember member : request.getMembers()) {
                addMember(id, member);
            }
        }

        log.info("SCIM: Created group {} ({}) in tenant {}", id, request.getDisplayName(), tenantId);
        return getGroup(tenantId, id, baseUrl);
    }

    @Transactional
    public ScimGroup replaceGroup(String tenantId, String groupId, ScimGroup request, String baseUrl) {
        getGroup(tenantId, groupId, baseUrl);

        jdbcTemplate.update(
                "UPDATE user_group SET name = ?, oidc_group_name = ?, updated_at = NOW() "
                        + "WHERE tenant_id = ? AND id = ?",
                request.getDisplayName(), request.getExternalId(), tenantId, groupId);

        // Replace all memberships
        jdbcTemplate.update("DELETE FROM group_membership WHERE group_id = ?", groupId);
        if (request.getMembers() != null) {
            for (ScimMember member : request.getMembers()) {
                addMember(groupId, member);
            }
        }

        log.info("SCIM: Replaced group {} in tenant {}", groupId, tenantId);
        return getGroup(tenantId, groupId, baseUrl);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public ScimGroup patchGroup(String tenantId, String groupId, ScimPatchOp patchOp, String baseUrl) {
        getGroup(tenantId, groupId, baseUrl);

        if (patchOp.getOperations() == null) {
            throw new ScimException(HttpStatus.BAD_REQUEST, "Operations list is required");
        }

        for (ScimPatchOp.Operation op : patchOp.getOperations()) {
            String operation = op.getOp().toLowerCase(Locale.ROOT);
            String path = op.getPath() != null ? op.getPath().toLowerCase(Locale.ROOT) : "";

            switch (operation) {
                case "replace" -> {
                    if ("displayname".equals(path)) {
                        jdbcTemplate.update(
                                "UPDATE user_group SET name = ?, updated_at = NOW() WHERE tenant_id = ? AND id = ?",
                                String.valueOf(op.getValue()), tenantId, groupId);
                    }
                }
                case "add" -> {
                    if ("members".equals(path) && op.getValue() instanceof List<?> members) {
                        for (Object m : members) {
                            if (m instanceof Map<?, ?> memberMap) {
                                ScimMember member = new ScimMember();
                                member.setValue(String.valueOf(memberMap.get("value")));
                                addMember(groupId, member);
                            }
                        }
                    }
                }
                case "remove" -> {
                    if (path.startsWith("members[value eq")) {
                        // Parse: members[value eq "userId"]
                        String memberId = extractValueFromBracketFilter(path);
                        if (memberId != null) {
                            jdbcTemplate.update(
                                    "DELETE FROM group_membership WHERE group_id = ? AND member_id = ?",
                                    groupId, memberId);
                        }
                    } else if ("members".equals(path) && op.getValue() instanceof List<?> members) {
                        for (Object m : members) {
                            if (m instanceof Map<?, ?> memberMap) {
                                String memberId = String.valueOf(memberMap.get("value"));
                                jdbcTemplate.update(
                                        "DELETE FROM group_membership WHERE group_id = ? AND member_id = ?",
                                        groupId, memberId);
                            }
                        }
                    }
                }
                default -> log.warn("SCIM: Ignoring unsupported group PATCH op: {}", operation);
            }
        }

        log.info("SCIM: Patched group {} in tenant {}", groupId, tenantId);
        return getGroup(tenantId, groupId, baseUrl);
    }

    @Transactional
    public void deleteGroup(String tenantId, String groupId) {
        // Delete memberships first, then the group
        jdbcTemplate.update("DELETE FROM group_membership WHERE group_id = ?", groupId);
        int rows = jdbcTemplate.update(
                "DELETE FROM user_group WHERE tenant_id = ? AND id = ?", tenantId, groupId);
        if (rows == 0) {
            throw new ScimException(HttpStatus.NOT_FOUND, "Group " + groupId + " not found");
        }
        log.info("SCIM: Deleted group {} in tenant {}", groupId, tenantId);
    }

    private void addMember(String groupId, ScimMember member) {
        if (member.getValue() == null) return;
        String memberType = "User".equalsIgnoreCase(member.getType()) ? "USER" : "USER";
        if ("Group".equalsIgnoreCase(member.getType())) {
            memberType = "GROUP";
        }
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO group_membership (id, group_id, member_type, member_id, created_at) "
                        + "VALUES (?, ?, ?, ?, NOW()) "
                        + "ON CONFLICT (group_id, member_type, member_id) DO NOTHING",
                id, groupId, memberType, member.getValue());
    }

    private ScimGroup mapGroup(ResultSet rs, String tenantId, String baseUrl) throws SQLException {
        String groupId = rs.getString("id");
        ScimGroup group = new ScimGroup();
        group.setId(groupId);
        group.setDisplayName(rs.getString("name"));
        group.setExternalId(rs.getString("oidc_group_name"));

        // Load members
        List<ScimMember> members = jdbcTemplate.query(
                "SELECT gm.member_id, gm.member_type, "
                        + "CASE WHEN gm.member_type = 'USER' THEN pu.email ELSE ug2.name END AS display "
                        + "FROM group_membership gm "
                        + "LEFT JOIN platform_user pu ON gm.member_type = 'USER' AND pu.id = gm.member_id "
                        + "LEFT JOIN user_group ug2 ON gm.member_type = 'GROUP' AND ug2.id = gm.member_id "
                        + "WHERE gm.group_id = ?",
                (mrs, rowNum) -> {
                    String type = "USER".equals(mrs.getString("member_type")) ? "User" : "Group";
                    String refPath = "User".equals(type) ? "/scim/v2/Users/" : "/scim/v2/Groups/";
                    return new ScimMember(
                            mrs.getString("member_id"),
                            baseUrl + refPath + mrs.getString("member_id"),
                            mrs.getString("display"),
                            type);
                },
                groupId);
        group.setMembers(members.isEmpty() ? null : members);

        group.setMeta(new ScimMeta("Group",
                formatTimestamp(rs.getObject("created_at", OffsetDateTime.class)),
                formatTimestamp(rs.getObject("updated_at", OffsetDateTime.class)),
                baseUrl + "/scim/v2/Groups/" + groupId));

        return group;
    }

    private static String extractValueFromBracketFilter(String path) {
        // Parse: members[value eq "some-id"]
        int start = path.indexOf('"');
        int end = path.lastIndexOf('"');
        if (start >= 0 && end > start) {
            return path.substring(start + 1, end);
        }
        return null;
    }

    private static String formatTimestamp(OffsetDateTime ts) {
        if (ts == null) return null;
        return ts.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
