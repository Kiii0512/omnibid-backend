package com.omnibid.dto;

import com.omnibid.dto.admin.AdminCancelAuctionRequest;
import com.omnibid.dto.admin.AdminCancelOrderRequest;
import com.omnibid.dto.auction.CancelAuctionRequest;
import com.omnibid.dto.product.ProductCreateRequest;
import com.omnibid.dto.product.ProductUpdateRequest;
import com.omnibid.enums.ProductCondition;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DtoSchemaLengthValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void cancelReason_Allows255CharsAndRejects256Chars() {
        String ok = "a".repeat(255);
        String tooLong = "a".repeat(256);

        assertThat(validator.validate(new AdminCancelAuctionRequest(ok))).isEmpty();
        assertThat(validator.validate(new AdminCancelAuctionRequest(tooLong))).isNotEmpty();

        assertThat(validator.validate(new AdminCancelOrderRequest(ok))).isEmpty();
        assertThat(validator.validate(new AdminCancelOrderRequest(tooLong))).isNotEmpty();

        assertThat(validator.validate(new CancelAuctionRequest(ok))).isEmpty();
        assertThat(validator.validate(new CancelAuctionRequest(tooLong))).isNotEmpty();
    }

    @Test
    void productDescription_RejectsOver10000Chars() {
        String ok = "d".repeat(10_000);
        String tooLong = "d".repeat(10_001);

        assertThat(validator.validate(new ProductCreateRequest(1L, "Title", ok, ProductCondition.NEW))).isEmpty();
        assertThat(validator.validate(new ProductCreateRequest(1L, "Title", tooLong, ProductCondition.NEW))).isNotEmpty();

        assertThat(validator.validate(new ProductUpdateRequest(null, null, ok, null))).isEmpty();
        assertThat(validator.validate(new ProductUpdateRequest(null, null, tooLong, null))).isNotEmpty();
    }
}
