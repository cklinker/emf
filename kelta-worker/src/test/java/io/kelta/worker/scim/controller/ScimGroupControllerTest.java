package io.kelta.worker.scim.controller;

import io.kelta.worker.scim.model.*;
import io.kelta.worker.scim.service.ScimGroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ScimGroupController")
class ScimGroupControllerTest {

    private ScimGroupService groupService;
    private ScimGroupController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        groupService = mock(ScimGroupService.class);
        controller = new ScimGroupController(groupService);
        request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("example.com");
        request.setServerPort(443);
    }

    @Test
    @DisplayName("listGroups returns 200 with list response")
    void listGroupsReturns200() {
        ScimListResponse<ScimGroup> listResponse = new ScimListResponse<>(List.of(), 0, 1, 0);
        when(groupService.listGroups(eq("t1"), isNull(), eq(1), eq(100), anyString()))
                .thenReturn(listResponse);

        var response = controller.listGroups("t1", null, 1, 100, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalResults()).isEqualTo(0);
    }

    @Test
    @DisplayName("createGroup returns 201")
    void createGroupReturns201() {
        ScimGroup input = new ScimGroup();
        input.setDisplayName("Engineering");

        ScimGroup created = new ScimGroup();
        created.setId("group-1");
        created.setDisplayName("Engineering");
        when(groupService.createGroup(eq("t1"), any(), anyString())).thenReturn(created);

        var response = controller.createGroup("t1", input, request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getDisplayName()).isEqualTo("Engineering");
    }

    @Test
    @DisplayName("deleteGroup returns 204")
    void deleteGroupReturns204() {
        doNothing().when(groupService).deleteGroup("t1", "group-1");

        var response = controller.deleteGroup("t1", "group-1");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(groupService).deleteGroup("t1", "group-1");
    }
}
