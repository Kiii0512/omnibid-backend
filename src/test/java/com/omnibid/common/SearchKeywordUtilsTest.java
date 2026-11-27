package com.omnibid.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchKeywordUtilsTest {

    @Test
    void escapeLike_EscapesWildcardsAndBackslash() {
        assertThat(SearchKeywordUtils.escapeLike(null)).isNull();
        assertThat(SearchKeywordUtils.escapeLike("   ")).isNull();
        assertThat(SearchKeywordUtils.escapeLike("  Seiko  ")).isEqualTo("Seiko");
        assertThat(SearchKeywordUtils.escapeLike("100%_off\\deal")).isEqualTo("100\\%\\_off\\\\deal");
        assertThat(SearchKeywordUtils.escapeLike("%")).isEqualTo("\\%");
        assertThat(SearchKeywordUtils.escapeLike("___")).isEqualTo("\\_\\_\\_");
    }
}
