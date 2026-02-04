package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateUiPageRequest;
import com.emf.controlplane.dto.UiMenuItemRequest;
import com.emf.controlplane.dto.UpdateUiMenuRequest;
import com.emf.controlplane.dto.UpdateUiPageRequest;
import com.emf.controlplane.entity.UiMenu;
import com.emf.controlplane.entity.UiMenuItem;
import com.emf.controlplane.entity.UiPage;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.UiMenuRepository;
import com.emf.controlplane.repository.UiPageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UiConfigService.
 * Tests CRUD operations for UI pages and menus, and bootstrap configuration.
 *
 * Requirements tested:
 * - 5.1: Return bootstrap configuration including pages and menus
 * - 5.2: Return list of UI pages
 * - 5.3: Create UI page with valid data
 * - 5.4: Update UI page and persist changes
 * - 5.5: Return list of UI menus
 * - 5.6: Update UI menu and persist changes
 */
@ExtendWith(MockitoExtension.class)
class UiConfigServiceTest {

    @Mock
    private UiPageRepository pageRepository;

    @Mock
    private UiMenuRepository menuRepository;

    private UiConfigService uiConfigService;

    @BeforeEach
    void setUp() {
        uiConfigService = new UiConfigService(pageRepository, menuRepository, null);  // ConfigEventPublisher is optional in tests
    }

    @Nested
    @DisplayName("getBootstrapConfig")
    class GetBootstrapConfigTests {

        @Test
        @DisplayName("should return bootstrap config with pages and menus")
        void shouldReturnBootstrapConfigWithPagesAndMenus() {
            // Given
            UiPage page1 = createTestPage("page-1", "Dashboard", "/dashboard");
            UiPage page2 = createTestPage("page-2", "Settings", "/settings");
            UiMenu menu1 = createTestMenu("menu-1", "Main Menu");

            when(pageRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(page1, page2));
            when(menuRepository.findAllWithItemsOrderByNameAsc()).thenReturn(List.of(menu1));

            // When
            UiConfigService.BootstrapConfig result = uiConfigService.getBootstrapConfig();

            // Then
            assertThat(result.getPages()).hasSize(2);
            assertThat(result.getMenus()).hasSize(1);
        }

        @Test
        @DisplayName("should return empty lists when no pages or menus exist")
        void shouldReturnEmptyListsWhenNoPagesOrMenus() {
            // Given
            when(pageRepository.findByActiveTrueOrderByNameAsc()).thenReturn(Collections.emptyList());
            when(menuRepository.findAllWithItemsOrderByNameAsc()).thenReturn(Collections.emptyList());

            // When
            UiConfigService.BootstrapConfig result = uiConfigService.getBootstrapConfig();

            // Then
            assertThat(result.getPages()).isEmpty();
            assertThat(result.getMenus()).isEmpty();
        }
    }

    @Nested
    @DisplayName("listPages")
    class ListPagesTests {

        @Test
        @DisplayName("should return list of active pages ordered by name")
        void shouldReturnActivePagesOrderedByName() {
            // Given
            UiPage page1 = createTestPage("page-1", "Dashboard", "/dashboard");
            UiPage page2 = createTestPage("page-2", "Settings", "/settings");

            when(pageRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(page1, page2));

            // When
            List<UiPage> result = uiConfigService.listPages();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("Dashboard");
            assertThat(result.get(1).getName()).isEqualTo("Settings");
            verify(pageRepository).findByActiveTrueOrderByNameAsc();
        }

        @Test
        @DisplayName("should return empty list when no active pages exist")
        void shouldReturnEmptyListWhenNoPages() {
            // Given
            when(pageRepository.findByActiveTrueOrderByNameAsc()).thenReturn(Collections.emptyList());

            // When
            List<UiPage> result = uiConfigService.listPages();

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("createPage")
    class CreatePageTests {

        @Test
        @DisplayName("should create page with valid configuration")
        void shouldCreatePageWithValidConfiguration() {
            // Given
            CreateUiPageRequest request = new CreateUiPageRequest("Dashboard", "/dashboard");
            request.setTitle("Dashboard Page");
            request.setConfig("{\"layout\": \"grid\"}");

            when(pageRepository.existsByPathAndActiveTrue("/dashboard")).thenReturn(false);
            when(pageRepository.save(any(UiPage.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UiPage result = uiConfigService.createPage(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getName()).isEqualTo("Dashboard");
            assertThat(result.getPath()).isEqualTo("/dashboard");
            assertThat(result.getTitle()).isEqualTo("Dashboard Page");
            assertThat(result.getConfig()).isEqualTo("{\"layout\": \"grid\"}");
            assertThat(result.isActive()).isTrue();

            verify(pageRepository).save(any(UiPage.class));
        }

        @Test
        @DisplayName("should create page with minimal required fields")
        void shouldCreatePageWithMinimalFields() {
            // Given
            CreateUiPageRequest request = new CreateUiPageRequest("Dashboard", "/dashboard");

            when(pageRepository.existsByPathAndActiveTrue("/dashboard")).thenReturn(false);
            when(pageRepository.save(any(UiPage.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UiPage result = uiConfigService.createPage(request);

            // Then
            assertThat(result.getName()).isEqualTo("Dashboard");
            assertThat(result.getPath()).isEqualTo("/dashboard");
            assertThat(result.getTitle()).isNull();
            assertThat(result.getConfig()).isNull();
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when path already exists")
        void shouldThrowExceptionWhenPathExists() {
            // Given
            CreateUiPageRequest request = new CreateUiPageRequest("Dashboard", "/existing-path");

            when(pageRepository.existsByPathAndActiveTrue("/existing-path")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> uiConfigService.createPage(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("UiPage")
                    .hasMessageContaining("path")
                    .hasMessageContaining("/existing-path");

            verify(pageRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updatePage")
    class UpdatePageTests {

        @Test
        @DisplayName("should update page name")
        void shouldUpdatePageName() {
            // Given
            String pageId = "page-1";
            UiPage existingPage = createTestPage(pageId, "Old Name", "/dashboard");
            UpdateUiPageRequest request = new UpdateUiPageRequest();
            request.setName("New Name");

            when(pageRepository.findByIdAndActiveTrue(pageId)).thenReturn(Optional.of(existingPage));
            when(pageRepository.save(any(UiPage.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UiPage result = uiConfigService.updatePage(pageId, request);

            // Then
            assertThat(result.getName()).isEqualTo("New Name");
            verify(pageRepository).save(any(UiPage.class));
        }

        @Test
        @DisplayName("should update page path")
        void shouldUpdatePagePath() {
            // Given
            String pageId = "page-1";
            UiPage existingPage = createTestPage(pageId, "Dashboard", "/old-path");
            UpdateUiPageRequest request = new UpdateUiPageRequest();
            request.setPath("/new-path");

            when(pageRepository.findByIdAndActiveTrue(pageId)).thenReturn(Optional.of(existingPage));
            when(pageRepository.existsByPathAndActiveTrue("/new-path")).thenReturn(false);
            when(pageRepository.save(any(UiPage.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UiPage result = uiConfigService.updatePage(pageId, request);

            // Then
            assertThat(result.getPath()).isEqualTo("/new-path");
        }

        @Test
        @DisplayName("should update multiple fields at once")
        void shouldUpdateMultipleFields() {
            // Given
            String pageId = "page-1";
            UiPage existingPage = createTestPage(pageId, "Old Name", "/old-path");
            UpdateUiPageRequest request = new UpdateUiPageRequest("New Name", "/new-path", "New Title");
            request.setConfig("{\"new\": \"config\"}");

            when(pageRepository.findByIdAndActiveTrue(pageId)).thenReturn(Optional.of(existingPage));
            when(pageRepository.existsByPathAndActiveTrue("/new-path")).thenReturn(false);
            when(pageRepository.save(any(UiPage.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UiPage result = uiConfigService.updatePage(pageId, request);

            // Then
            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(result.getPath()).isEqualTo("/new-path");
            assertThat(result.getTitle()).isEqualTo("New Title");
            assertThat(result.getConfig()).isEqualTo("{\"new\": \"config\"}");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when page not found")
        void shouldThrowExceptionWhenNotFound() {
            // Given
            String pageId = "nonexistent-id";
            UpdateUiPageRequest request = new UpdateUiPageRequest();
            request.setName("New Name");

            when(pageRepository.findByIdAndActiveTrue(pageId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> uiConfigService.updatePage(pageId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("UiPage")
                    .hasMessageContaining(pageId);

            verify(pageRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when new path already exists")
        void shouldThrowExceptionWhenNewPathExists() {
            // Given
            String pageId = "page-1";
            UiPage existingPage = createTestPage(pageId, "Dashboard", "/old-path");
            UpdateUiPageRequest request = new UpdateUiPageRequest();
            request.setPath("/existing-path");

            when(pageRepository.findByIdAndActiveTrue(pageId)).thenReturn(Optional.of(existingPage));
            when(pageRepository.existsByPathAndActiveTrue("/existing-path")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> uiConfigService.updatePage(pageId, request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("/existing-path");

            verify(pageRepository, never()).save(any());
        }

        @Test
        @DisplayName("should allow update with same path")
        void shouldAllowUpdateWithSamePath() {
            // Given
            String pageId = "page-1";
            UiPage existingPage = createTestPage(pageId, "Dashboard", "/same-path");
            UpdateUiPageRequest request = new UpdateUiPageRequest();
            request.setPath("/same-path");
            request.setTitle("Updated Title");

            when(pageRepository.findByIdAndActiveTrue(pageId)).thenReturn(Optional.of(existingPage));
            when(pageRepository.save(any(UiPage.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UiPage result = uiConfigService.updatePage(pageId, request);

            // Then
            assertThat(result.getPath()).isEqualTo("/same-path");
            assertThat(result.getTitle()).isEqualTo("Updated Title");
            // Should not check for duplicate since path is unchanged
            verify(pageRepository, never()).existsByPathAndActiveTrue(anyString());
        }

        @Test
        @DisplayName("should update active status")
        void shouldUpdateActiveStatus() {
            // Given
            String pageId = "page-1";
            UiPage existingPage = createTestPage(pageId, "Dashboard", "/dashboard");
            existingPage.setActive(true);
            UpdateUiPageRequest request = new UpdateUiPageRequest();
            request.setActive(false);

            when(pageRepository.findByIdAndActiveTrue(pageId)).thenReturn(Optional.of(existingPage));
            when(pageRepository.save(any(UiPage.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UiPage result = uiConfigService.updatePage(pageId, request);

            // Then
            assertThat(result.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("listMenus")
    class ListMenusTests {

        @Test
        @DisplayName("should return list of menus ordered by name")
        void shouldReturnMenusOrderedByName() {
            // Given
            UiMenu menu1 = createTestMenu("menu-1", "Admin Menu");
            UiMenu menu2 = createTestMenu("menu-2", "Main Menu");

            when(menuRepository.findAllByOrderByNameAsc()).thenReturn(List.of(menu1, menu2));

            // When
            List<UiMenu> result = uiConfigService.listMenus();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("Admin Menu");
            assertThat(result.get(1).getName()).isEqualTo("Main Menu");
            verify(menuRepository).findAllByOrderByNameAsc();
        }

        @Test
        @DisplayName("should return empty list when no menus exist")
        void shouldReturnEmptyListWhenNoMenus() {
            // Given
            when(menuRepository.findAllByOrderByNameAsc()).thenReturn(Collections.emptyList());

            // When
            List<UiMenu> result = uiConfigService.listMenus();

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateMenu")
    class UpdateMenuTests {

        @Test
        @DisplayName("should update menu name")
        void shouldUpdateMenuName() {
            // Given
            String menuId = "menu-1";
            UiMenu existingMenu = createTestMenu(menuId, "Old Name");
            UpdateUiMenuRequest request = new UpdateUiMenuRequest();
            request.setName("New Name");

            when(menuRepository.findById(menuId)).thenReturn(Optional.of(existingMenu));
            when(menuRepository.existsByName("New Name")).thenReturn(false);
            when(menuRepository.save(any(UiMenu.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UiMenu result = uiConfigService.updateMenu(menuId, request);

            // Then
            assertThat(result.getName()).isEqualTo("New Name");
            verify(menuRepository).save(any(UiMenu.class));
        }

        @Test
        @DisplayName("should update menu description")
        void shouldUpdateMenuDescription() {
            // Given
            String menuId = "menu-1";
            UiMenu existingMenu = createTestMenu(menuId, "Main Menu");
            UpdateUiMenuRequest request = new UpdateUiMenuRequest();
            request.setDescription("Updated description");

            when(menuRepository.findById(menuId)).thenReturn(Optional.of(existingMenu));
            when(menuRepository.save(any(UiMenu.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UiMenu result = uiConfigService.updateMenu(menuId, request);

            // Then
            assertThat(result.getDescription()).isEqualTo("Updated description");
        }

        @Test
        @DisplayName("should update menu items")
        void shouldUpdateMenuItems() {
            // Given
            String menuId = "menu-1";
            UiMenu existingMenu = createTestMenu(menuId, "Main Menu");
            UiMenuItem existingItem = new UiMenuItem("Old Item", "/old", 0);
            existingMenu.addItem(existingItem);

            List<UiMenuItemRequest> newItems = List.of(
                    new UiMenuItemRequest("Dashboard", "/dashboard", 0),
                    new UiMenuItemRequest("Settings", "/settings", 1)
            );
            UpdateUiMenuRequest request = new UpdateUiMenuRequest();
            request.setItems(newItems);

            when(menuRepository.findById(menuId)).thenReturn(Optional.of(existingMenu));
            when(menuRepository.save(any(UiMenu.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UiMenu result = uiConfigService.updateMenu(menuId, request);

            // Then
            // Old item should be marked inactive, new items should be added
            assertThat(result.getItems()).hasSizeGreaterThanOrEqualTo(2);
            verify(menuRepository).save(any(UiMenu.class));
        }

        @Test
        @DisplayName("should update existing menu item by ID")
        void shouldUpdateExistingMenuItemById() {
            // Given
            String menuId = "menu-1";
            UiMenu existingMenu = createTestMenu(menuId, "Main Menu");
            UiMenuItem existingItem = new UiMenuItem("Old Label", "/old-path", 0);
            String existingItemId = existingItem.getId();
            existingMenu.addItem(existingItem);

            UiMenuItemRequest updateRequest = new UiMenuItemRequest(
                    existingItemId, "New Label", "/new-path", "new-icon", 1, true
            );
            UpdateUiMenuRequest request = new UpdateUiMenuRequest();
            request.setItems(List.of(updateRequest));

            when(menuRepository.findById(menuId)).thenReturn(Optional.of(existingMenu));
            when(menuRepository.save(any(UiMenu.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UiMenu result = uiConfigService.updateMenu(menuId, request);

            // Then
            UiMenuItem updatedItem = result.getItems().stream()
                    .filter(item -> item.getId().equals(existingItemId))
                    .findFirst()
                    .orElseThrow();
            assertThat(updatedItem.getLabel()).isEqualTo("New Label");
            assertThat(updatedItem.getPath()).isEqualTo("/new-path");
            assertThat(updatedItem.getIcon()).isEqualTo("new-icon");
            assertThat(updatedItem.getDisplayOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when menu not found")
        void shouldThrowExceptionWhenNotFound() {
            // Given
            String menuId = "nonexistent-id";
            UpdateUiMenuRequest request = new UpdateUiMenuRequest();
            request.setName("New Name");

            when(menuRepository.findById(menuId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> uiConfigService.updateMenu(menuId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("UiMenu")
                    .hasMessageContaining(menuId);

            verify(menuRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when new name already exists")
        void shouldThrowExceptionWhenNewNameExists() {
            // Given
            String menuId = "menu-1";
            UiMenu existingMenu = createTestMenu(menuId, "Old Name");
            UpdateUiMenuRequest request = new UpdateUiMenuRequest();
            request.setName("Existing Name");

            when(menuRepository.findById(menuId)).thenReturn(Optional.of(existingMenu));
            when(menuRepository.existsByName("Existing Name")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> uiConfigService.updateMenu(menuId, request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Existing Name");

            verify(menuRepository, never()).save(any());
        }

        @Test
        @DisplayName("should allow update with same name")
        void shouldAllowUpdateWithSameName() {
            // Given
            String menuId = "menu-1";
            UiMenu existingMenu = createTestMenu(menuId, "Same Name");
            UpdateUiMenuRequest request = new UpdateUiMenuRequest();
            request.setName("Same Name");
            request.setDescription("Updated description");

            when(menuRepository.findById(menuId)).thenReturn(Optional.of(existingMenu));
            when(menuRepository.save(any(UiMenu.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UiMenu result = uiConfigService.updateMenu(menuId, request);

            // Then
            assertThat(result.getName()).isEqualTo("Same Name");
            assertThat(result.getDescription()).isEqualTo("Updated description");
            // Should not check for duplicate since name is unchanged
            verify(menuRepository, never()).existsByName(anyString());
        }
    }

    @Nested
    @DisplayName("getPage")
    class GetPageTests {

        @Test
        @DisplayName("should return page when found and active")
        void shouldReturnPageWhenFoundAndActive() {
            // Given
            String pageId = "page-1";
            UiPage page = createTestPage(pageId, "Dashboard", "/dashboard");

            when(pageRepository.findByIdAndActiveTrue(pageId)).thenReturn(Optional.of(page));

            // When
            UiPage result = uiConfigService.getPage(pageId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(pageId);
            assertThat(result.getName()).isEqualTo("Dashboard");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when page not found")
        void shouldThrowExceptionWhenNotFound() {
            // Given
            String pageId = "nonexistent-id";

            when(pageRepository.findByIdAndActiveTrue(pageId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> uiConfigService.getPage(pageId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("UiPage")
                    .hasMessageContaining(pageId);
        }
    }

    @Nested
    @DisplayName("getMenu")
    class GetMenuTests {

        @Test
        @DisplayName("should return menu when found")
        void shouldReturnMenuWhenFound() {
            // Given
            String menuId = "menu-1";
            UiMenu menu = createTestMenu(menuId, "Main Menu");

            when(menuRepository.findById(menuId)).thenReturn(Optional.of(menu));

            // When
            UiMenu result = uiConfigService.getMenu(menuId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(menuId);
            assertThat(result.getName()).isEqualTo("Main Menu");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when menu not found")
        void shouldThrowExceptionWhenNotFound() {
            // Given
            String menuId = "nonexistent-id";

            when(menuRepository.findById(menuId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> uiConfigService.getMenu(menuId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("UiMenu")
                    .hasMessageContaining(menuId);
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
