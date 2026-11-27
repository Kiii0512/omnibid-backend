package com.omnibid.dto;

import com.omnibid.dto.auth.RegisterRequest;
import com.omnibid.dto.user.ChangePasswordRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordUtf8ByteValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void registerPassword_Enforces72Utf8ByteLimit() {
        String okAscii = "Aa1" + "x".repeat(69); // 72 bytes
        String overAscii = "Aa1" + "x".repeat(70); // 73 bytes
        // Each 'ệ' is 3 bytes in UTF-8: 23 * 3 + 3 = 72 bytes (26 chars)
        String okUtf8 = "Aa1" + "ệ".repeat(23);
        // 24 * 3 + 3 = 75 bytes (only 27 chars, would pass @Size(max=72)!)
        String overUtf8 = "Aa1" + "ệ".repeat(24);

        assertThat(validator.validate(RegisterRequest.builder().fullName("A").email("a@b.com").password(okAscii).build())).isEmpty();
        assertThat(validator.validate(RegisterRequest.builder().fullName("A").email("a@b.com").password(overAscii).build())).isNotEmpty();
        assertThat(validator.validate(RegisterRequest.builder().fullName("A").email("a@b.com").password(okUtf8).build())).isEmpty();
        assertThat(validator.validate(RegisterRequest.builder().fullName("A").email("a@b.com").password(overUtf8).build())).isNotEmpty();
    }

    @Test
    void changePassword_Enforces72Utf8ByteLimit() {
        String okUtf8 = "Aa1" + "ệ".repeat(23);
        String overUtf8 = "Aa1" + "ệ".repeat(24);

        assertThat(validator.validate(new ChangePasswordRequest("OldPass1", okUtf8, okUtf8))).isEmpty();
        assertThat(validator.validate(new ChangePasswordRequest("OldPass1", overUtf8, overUtf8))).isNotEmpty();
    }
}
