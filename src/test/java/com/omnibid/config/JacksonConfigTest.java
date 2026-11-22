package com.omnibid.config;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.SortWhitelist;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonConfigTest {

    private record TimePayload(Instant at) {
    }

    private JsonMapper buildMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().instantDisplayZoneCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    void serializeInstantUsesAsiaHoChiMinhOffset() {
        JsonMapper mapper = buildMapper();
        Instant utc = Instant.parse("2026-11-20T02:00:00Z");

        String json = mapper.writeValueAsString(new TimePayload(utc));

        assertThat(json).isEqualTo("{\"at\":\"2026-11-20T09:00:00+07:00\"}");
    }

    @Test
    void deserializeInstantAcceptsUtcAndOffset() {
        JsonMapper mapper = buildMapper();
        Instant expected = Instant.parse("2026-11-20T02:00:00Z");

        TimePayload fromUtc = mapper.readValue("{\"at\":\"2026-11-20T02:00:00Z\"}", TimePayload.class);
        TimePayload fromPlus7 = mapper.readValue("{\"at\":\"2026-11-20T09:00:00+07:00\"}", TimePayload.class);

        assertThat(fromUtc.at()).isEqualTo(expected);
        assertThat(fromPlus7.at()).isEqualTo(expected);
    }

    @Test
    void sortWhitelistRejectsUnknownField() {
        PageRequest valid = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "endTime"));
        SortWhitelist.validateDirections(valid, Set.of("endTime,asc", "createdAt,desc"));

        PageRequest invalid = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "endTime"));
        assertThatThrownBy(() -> SortWhitelist.validateDirections(invalid, Set.of("endTime,asc", "createdAt,desc")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
