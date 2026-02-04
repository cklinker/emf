package com.emf.controlplane.controller;

import com.emf.controlplane.dto.*;
import com.emf.controlplane.entity.UiMenu;
import com.emf.controlplane.entity.UiMenuItem;
import com.emf.controlplane.entity.UiPage;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.OidcProviderRepository;
import com.emf.controlplane.service.UiConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UiConfigController.
 * Tests REST endpoints for UI configuration management.
 *
 * Requirements tested:
 * - 5.1: GET /ui/config/bootstrap returns UI configuration
 * - 5.2: GET /ui/pages returns list of UI pages
 * - 5.3: POST /ui/pages creates new UI page
 * - 5.4: PUT /ui/pages/{id} updates UI page
 * - 5.5: GET /ui/menus returns list of UI menus
 * - 5.6: PUT /ui/menus/{id} updates UI menu
 */
@ExtendWith(MockitoExtension.class)
class UiConfigControllerTest {

    @Mock
    private UiConfigService uiConfigService;

    @Mock
    private OidcProviderRepository oidcProviderRepository;

    private UiConfigController uiConfigController;

    @BeforeEach
    void setUp() {
        uiConfigController = new UiConfigController(uiConfigService, oidcProviderRepository);
    }

    @Nested
    @DisplayName("GET /ui/config/bootstrap")
    class GetBootstrapConfigTests {

        @Test
        @DisplayName("should return bootstrap config with pages and menus")
        void shouldReturnBootstrapConfig() {
            // Given
            UiPage page = createTestPage("page-1", "Dashboard", "/dashboard");
            UiMenu menu = createTestMenu("menu-1", "Main Menu");

            UiConfigService.BootstrapConfig config = new UiConfigService.BootstrapConfig(
                    List.of(page), List.of(menu)
            );

            when(uiConfigService.getBootstrapConfig()).thenReturn(config);
            when(oidcProviderRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<BootstrapConfigDto> response = uiConfigController.getBootstrapConfig();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getPages()).hasSize(1);
            assertThat(response.getBody().getMenus()).hasSize(1);
            assertThat(response.getBody().getTheme()).isNotNull();
            assertThat(response.getBody().getBranding()).isNotNull();
            assertThat(response.getBody().getFeatures()).isNotNull();
            assertThat(response.getBody().getOidcProviders()).isNotNull();
        }

        @Test
        @DisplayName("should return empty lists when no pages or menus exist")
        void shouldReturnEmptyListsWhenNoPagesOrMenus() {
            // Given
            UiConfigService.BootstrapConfig config = new UiConfigService.BootstrapConfig(
                    Collections.emptyList(), Collections.emptyList()
            );

            when(uiConfigService.getBootstrapConfig()).thenReturn(config);
            when(oidcProviderRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<BootstrapConfigDto> response = uiConfigController.getBootstrapConfig();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getPages()).isEmpty();
            assertThat(response.getBody().getMenus()).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /ui/pages")
    class ListPagesTests {

        @Test
        @DisplayName("should return list of pages")
        void shouldReturnListOfPages() {
            // Given
            UiPage page1 = createTestPage("page-1", "Dashboard", "/dashboard");
            UiPage page2 = createTestPage("page-2", "Settings", "/settings");

            when(uiConfigService.listPages()).thenReturn(List.of(page1, page2));

            // When
            ResponseEntity<List<UiPageDto>> response = uiConfigController.listPages();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody().get(0).getName()).isEqualTo("Dashboard");
            assertThat(response.getBody().get(1).getName()).isEqualTo("Settings");
        }

        @Test
        @DisplayName("should return empty list when no pages exist")
        void shouldReturnEmptyListWhenNoPages() {
            // Given
            when(uiConfigService.listPages()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<UiPageDto>> response = uiConfigController.listPages();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("POST /ui/pages")
    class CreatePageTests {

        @Test
        @DisplayName("should create page and return 201")
        void shouldCreatePageAndReturn201() {
            // Given
            CreateUiPageRequest request = new CreateUiPageRequest("Dashboard", "/dashboard");
            UiPage createdPage = createTestPage("page-1", "Dashboard", "/dashboard");

            when(uiConfigService.createPage(request)).thenReturn(createdPage);

            // When
            ResponseEntity<UiPageDto> response = uiConfigController.createPage(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo("page-1");
            assertThat(response.getBody().getName()).isEqualTo("Dashboard");
            assertThat(response.getBody().getPath()).isEqualTo("/dashboard");
        }

        @Test
        @DisplayName("should propagate DuplicateResourceException")
        void shouldPropagateDuplicateResourceException() {
            // Given
            CreateUiPageRequest request = new CreateUiPageRequest("Dashboard", "/existing-path");

            when(uiConfigService.createPage(request))
                    .thenThrow(new DuplicateResourceException("UiPage", "path", "/existing-path"));

            // When/Then
            assertThatThrownBy(() -> uiConfigController.createPage(request))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    @Nested
    @DisplayName("PUT /ui/pages/{id}")
    class UpdatePageTests {

        @Test
        @DisplayName("should update page and return 200")
        void shouldUpdatePageAndReturn200() {
            // Given
            String pageId = "page-1";
            UpdateUiPageRequest request = new UpdateUiPageRequest();
            request.setName("Updated Dashboard");
            UiPage updatedPage = createTestPage(pageId, "Updated Dashboard", "/dashboard");

            when(uiConfigService.updatePage(eq(pageId), any(UpdateUiPageRequest.class)))
                    .thenReturn(updatedPage);

            // When
            ResponseEntity<UiPageDto> response = uiConfigController.updatePage(pageId, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getName()).isEqualTo("Updated Dashboard");
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException")
        void shouldPropagateResourceNotFoundException() {
            // Given
            String pageId = "nonexistent-id";
            UpdateUiPageRequest request = new UpdateUiPageRequest();
            request.setName("New Name");

            when(uiConfigService.updatePage(eq(pageId), any(UpdateUiPageRequest.class)))
                    .thenThrow(new ResourceNotFoundException("UiPage", pageId));

            // When/Then
            assertThatThrownBy(() -> uiConfigController.updatePage(pageId, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should propagate DuplicateResourceException for path conflict")
        void shouldPropagateDuplicateResourceExceptionForPathConflict() {
            // Given
            String pageId = "page-1";
            UpdateUiPageRequest request = new UpdateUiPageRequest();
            request.setPath("/existing-path");

            when(uiConfigService.updatePage(eq(pageId), any(UpdateUiPageRequest.class)))
                    .thenThrow(new DuplicateResourceException("UiPage", "path", "/existing-path"));

            // When/Then
            assertThatThrownBy(() -> uiConfigController.updatePage(pageId, request))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    @Nested
    @DisplayName("GET /ui/menus")
    class ListMenusTests {

        @Test
        @DisplayName("should return list of menus")
        void shouldReturnListOfMenus() {
            // Given
            UiMenu menu1 = createTestMenu("menu-1", "Admin Menu");
            UiMenu menu2 = createTestMenu("menu-2", "Main Menu");

            when(uiConfigService.listMenus()).thenReturn(List.of(menu1, menu2));

            // When
            ResponseEntity<List<UiMenuDto>> response = uiConfigController.listMenus();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody().get(0).getName()).isEqualTo("Admin Menu");
            assertThat(response.getBody().get(1).getName()).isEqualTo("Main Menu");
        }

        @Test
        @DisplayName("should return empty list when no menus exist")
        void shouldReturnEmptyListWhenNoMenus() {
            // Given
            when(uiConfigService.listMenus()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<UiMenuDto>> response = uiConfigController.listMenus();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should return menus with items")
        void shouldReturnMenusWithItems() {
            // Given
            UiMenu menu = createTestMenu("menu-1", "Main Menu");
            UiMenuItem item1 = new UiMenuItem("Dashboard", "/dashboard", 0);
            item1.setActive(true);
            UiMenuItem item2 = new UiMenuItem("Settings", "/settings", 1);
            item2.setActive(true);
            menu.addItem(item1);
            menu.addItem(item2);

            when(uiConfigService.listMenus()).thenReturn(List.of(menu));

            // When
            ResponseEntity<List<UiMenuDto>> response = uiConfigController.listMenus();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getItems()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("PUT /ui/menus/{id}")
    class UpdateMenuTests {

        @Test
        @DisplayName("should update menu and return 200")
        void shouldUpdateMenuAndReturn200() {
            // Given
            String menuId = "menu-1";
            UpdateUiMenuRequest request = new UpdateUiMenuRequest();
            request.setName("Updated Menu");
            UiMenu updatedMenu = createTestMenu(menuId, "Updated Menu");

            when(uiConfigService.updateMenu(eq(menuId), any(UpdateUiMenuRequest.class)))
                    .thenReturn(updatedMenu);

            // When
            ResponseEntity<UiMenuDto> response = uiConfigController.updateMenu(menuId, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getName()).isEqualTo("Updated Menu");
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException")
        void shouldPropagateResourceNotFoundException() {
            // Given
            String menuId = "nonexistent-id";
            UpdateUiMenuRequest request = new UpdateUiMenuRequest();
            request.setName("New Name");

            when(uiConfigService.updateMenu(eq(menuId), any(UpdateUiMenuRequest.class)))
                    .thenThrow(new ResourceNotFoundException("UiMenu", menuId));

            // When/Then
            assertThatThrownBy(() -> uiConfigController.updateMenu(menuId, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should propagate DuplicateResourceException for name conflict")
        void shouldPropagateDuplicateResourceExceptionForNameConflict() {
            // Given
            String menuId = "menu-1";
            UpdateUiMenuRequest request = new UpdateUiMenuRequest();
            request.setName("Existing Name");

            when(uiConfigService.updateMenu(eq(menuId), any(UpdateUiMenuRequest.class)))
                    .thenThrow(new DuplicateResourceException("UiMenu", "name", "Existing Name"));

            // When/Then
            assertThatThrownBy(() -> uiConfigController.updateMenu(menuId, request))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("should update menu with items")
        void shouldUpdateMenuWithItems() {
            // Given
            String menuId = "menu-1";
            List<UiMenuItemRequest> items = List.of(
                    new UiMenuItemRequest("Dashboard", "/dashboard", 0),
                    new UiMenuItemRequest("Settings", "/settings", 1)
            );
            UpdateUiMenuRequest request = new UpdateUiMenuRequest("Main Menu", "Main navigation", items);

            UiMenu updatedMenu = createTestMenu(menuId, "Main Menu");
            UiMenuItem item1 = new UiMenuItem("Dashboard", "/dashboard", 0);
            item1.setActive(true);
            UiMenuItem item2 = new UiMenuItem("Settings", "/settings", 1);
            item2.setActive(true);
            updatedMenu.addItem(item1);
            updatedMenu.addItem(item2);

            when(uiConfigService.updateMenu(eq(menuId), any(UpdateUiMenuRequest.class)))
                    .thenReturn(updatedMenu);

            // When
            ResponseEntity<UiMenuDto> response = uiConfigController.updateMenu(menuId, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getItems()).hasSize(2);
        }
    }

    // Helper methods to create test entities
    private UiPage createTestPage(String id, String name, String path) {
        UiPage page = new UiPage(name, path);
        page.setId(id);
        page.setActive(true);
        return page;
    }

    private UiMenu createTestMenu(String id, String name) {
        UiMenu menu = new UiMenu(name);
        menu.setId(id);
        menu.setItems(new ArrayList<>());
        return menu;
    }
}
