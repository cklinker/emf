package io.kelta.worker.scim.service;

import io.kelta.worker.scim.model.ScimException;
import io.kelta.worker.scim.model.ScimGroup;
import io.kelta.worker.scim.model.ScimPatchOp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ScimGroupService")
class ScimGroupServiceTest {

    private JdbcTemplate jdbcTemplate;
    private ScimGroupService service;

    private static final String TENANT_ID = "tenant-1";
    private static final String BASE_URL = "https://example.com";

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new ScimGroupService(jdbcTemplate);
    }

    @Test
    @DisplayName("getGroup throws 404 when group not found")
    void getGroupNotFound() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(TENANT_ID), eq("no-such-id")))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.getGroup(TENANT_ID, "no-such-id", BASE_URL))
                .isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("createGroup rejects missing displayName")
    void createGroupMissingName() {
        ScimGroup request = new ScimGroup();
        // no displayName

        assertThatThrownBy(() -> service.createGroup(TENANT_ID, request, BASE_URL))
                .isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("createGroup rejects duplicate name")
    void createGroupDuplicateName() {
        ScimGroup request = new ScimGroup();
        request.setDisplayName("Engineering");

        when(jdbcTemplate.queryForObject(contains("COUNT"), eq(Integer.class), eq(TENANT_ID), eq("Engineering")))
                .thenReturn(1);

        assertThatThrownBy(() -> service.createGroup(TENANT_ID, request, BASE_URL))
                .isInstanceOf(ScimException.class)
                .satisfies(ex -> {
                    ScimException scimEx = (ScimException) ex;
                    assertThat(scimEx.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(scimEx.getScimType()).isEqualTo("uniqueness");
                });
    }

    @Test
    @DisplayName("deleteGroup removes memberships and group")
    void deleteGroupSuccess() {
        when(jdbcTemplate.update(contains("group_membership"), eq("group-1")))
                .thenReturn(3);
        when(jdbcTemplate.update(contains("user_group"), eq(TENANT_ID), eq("group-1")))
                .thenReturn(1);

        service.deleteGroup(TENANT_ID, "group-1");

        verify(jdbcTemplate).update(contains("group_membership"), eq("group-1"));
        verify(jdbcTemplate).update(contains("user_group"), eq(TENANT_ID), eq("group-1"));
    }

    @Test
    @DisplayName("deleteGroup throws 404 for unknown group")
    void deleteGroupNotFound() {
        when(jdbcTemplate.update(contains("group_membership"), eq("no-such-id")))
                .thenReturn(0);
        when(jdbcTemplate.update(contains("user_group"), eq(TENANT_ID), eq("no-such-id")))
                .thenReturn(0);

        assertThatThrownBy(() -> service.deleteGroup(TENANT_ID, "no-such-id"))
                .isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("patchGroup rejects null operations")
    void patchGroupNullOperations() {
        ScimGroup mockGroup = new ScimGroup();
        mockGroup.setId("group-1");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(TENANT_ID), eq("group-1")))
                .thenReturn(List.of(mockGroup));

        ScimPatchOp patchOp = new ScimPatchOp();

        assertThatThrownBy(() -> service.patchGroup(TENANT_ID, "group-1", patchOp, BASE_URL))
                .isInstanceOf(ScimException.class)
                .satisfies(ex -> assertThat(((ScimException) ex).getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
