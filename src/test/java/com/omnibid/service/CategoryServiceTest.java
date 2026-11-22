package com.omnibid.service;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.dto.category.AdminCategoryResponse;
import com.omnibid.dto.category.CategoryRequest;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private CategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryServiceImpl(categoryRepository, productRepository);
    }

    @Test
    @DisplayName("UT-ADM-15: Tạo danh mục — name = 'Đồng hồ Cổ' -> slug = 'dong-ho-co'")
    void createCategory_VietnameseName_GeneratesCleanSlug() {
        when(categoryRepository.existsBySlug("dong-ho-co")).thenReturn(false);
        Category saved = Category.builder()
                .id(1L)
                .name("Đồng hồ Cổ")
                .slug("dong-ho-co")
                .icon("watch")
                .build();
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        CategoryRequest request = new CategoryRequest("Đồng hồ Cổ", null, "watch");
        AdminCategoryResponse response = categoryService.createCategory(adminUser, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.slug()).isEqualTo("dong-ho-co");
        assertThat(response.name()).isEqualTo("Đồng hồ Cổ");
        assertThat(response.icon()).isEqualTo("watch");
        assertThat(response.productCount()).isZero();

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("dong-ho-co");
    }

    @Test
    @DisplayName("UT-ADM-16: Danh mục trùng slug -> 400 VALIDATION_ERROR; xóa danh mục có Product -> 409 CATEGORY_IN_USE")
    void createCategory_DuplicateSlug_And_DeleteCategory_InUse_ThrowsExpectedExceptions() {
        when(categoryRepository.existsBySlug("dong-ho-co")).thenReturn(true);
        CategoryRequest request = new CategoryRequest("Đồng hồ Cổ", null, null);

        assertThatThrownBy(() -> categoryService.createCategory(adminUser, request))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode() == ErrorCode.VALIDATION_ERROR);

        Category cat = Category.builder().id(5L).name("Dien thoai").slug("dien-thoai").build();
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(cat));
        when(productRepository.existsByCategoryId(5L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.deleteCategory(adminUser, 5L))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode() == ErrorCode.CATEGORY_IN_USE);

        when(productRepository.existsByCategoryId(5L)).thenReturn(false);
        categoryService.deleteCategory(adminUser, 5L);

        verify(categoryRepository).delete(cat);
    }

    @Test
    @DisplayName("Normal user cannot modify categories -> 403 FORBIDDEN")
    void categoryModification_NormalUser_ThrowsForbidden() {
        CategoryRequest request = new CategoryRequest("May tinh", null, "laptop");
        assertThatThrownBy(() -> categoryService.createCategory(normalUser, request))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode() == ErrorCode.FORBIDDEN);
    }
}
