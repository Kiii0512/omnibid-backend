package com.omnibid.service;

import com.omnibid.common.AppException;
import com.omnibid.dto.category.CategoryRequest;
import com.omnibid.dto.category.CategoryResponse;
import com.omnibid.entity.Category;
import com.omnibid.repository.CategoryRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long ADMIN_ID = 9L;
    private final CurrentUser adminUser = new CurrentUser(ADMIN_ID, true);
    private final CurrentUser normalUser = new CurrentUser(2L, false);

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private AuditService auditService;

    private CategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryServiceImpl(categoryRepository, productRepository, auditService);
    }

    @Test
    @DisplayName("UT-ADM-15: Tạo danh mục — name = 'Đồng hồ Cổ' -> slug = 'dong-ho-co'; audit CATEGORY_CREATE")
    void createCategory_VietnameseName_GeneratesCleanSlugAndAudits() {
        when(categoryRepository.existsBySlug("dong-ho-co")).thenReturn(false);
        Category saved = Category.builder()
                .id(1L)
                .name("Đồng hồ Cổ")
                .slug("dong-ho-co")
                .iconUrl("https://example.com/icon.png")
                .build();
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        CategoryRequest request = new CategoryRequest("Đồng hồ Cổ", "https://example.com/icon.png");
        CategoryResponse response = categoryService.createCategory(adminUser, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.slug()).isEqualTo("dong-ho-co");
        assertThat(response.name()).isEqualTo("Đồng hồ Cổ");

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("dong-ho-co");

        verify(auditService).log(ADMIN_ID, "CATEGORY_CREATE", "CATEGORY", 1L, "name=Đồng hồ Cổ");
    }

    @Test
    @DisplayName("UT-ADM-16: Danh mục trùng / đang dùng — slug đã tồn tại -> trùng -> lỗi; xóa danh mục có Product -> 409 CATEGORY_IN_USE")
    void createCategory_DuplicateSlug_And_DeleteCategory_InUse_ThrowsExpectedExceptions() {
        // Case 1: slug đã tồn tại
        when(categoryRepository.existsBySlug("dong-ho-co")).thenReturn(true);
        CategoryRequest request = new CategoryRequest("Đồng hồ Cổ", null);

        assertThatThrownBy(() -> categoryService.createCategory(adminUser, request))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("VALIDATION_ERROR") && ((AppException) e).getStatus() == HttpStatus.CONFLICT);

        // Case 2: Xóa danh mục có sản phẩm tham chiếu
        Category cat = Category.builder().id(5L).name("Dien thoai").slug("dien-thoai").build();
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(cat));
        when(productRepository.existsByCategoryId(5L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.deleteCategory(adminUser, 5L))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("CATEGORY_IN_USE") && ((AppException) e).getStatus() == HttpStatus.CONFLICT);

        // Case 3: Xóa danh mục không còn sản phẩm -> thành công
        when(productRepository.existsByCategoryId(5L)).thenReturn(false);
        categoryService.deleteCategory(adminUser, 5L);

        verify(categoryRepository).delete(cat);
        verify(auditService).log(ADMIN_ID, "CATEGORY_DELETE", "CATEGORY", 5L, "name=Dien thoai");
    }

    @Test
    @DisplayName("Normal user cannot modify categories -> 403 FORBIDDEN")
    void categoryModification_NormalUser_ThrowsForbidden() {
        CategoryRequest request = new CategoryRequest("May tinh", null);
        assertThatThrownBy(() -> categoryService.createCategory(normalUser, request))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("FORBIDDEN"));
    }
}
