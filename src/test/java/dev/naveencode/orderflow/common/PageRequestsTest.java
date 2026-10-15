package dev.naveencode.orderflow.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.naveencode.orderflow.error.ApiException;
import dev.naveencode.orderflow.error.ErrorCode;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class PageRequestsTest {

    @Test
    void parsesAnAllowListedSort() {
        var pageable = PageRequests.of(1, 10, "totalAmount,asc", Set.of("createdAt", "totalAmount"), "createdAt");

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("totalAmount").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void rejectsAnUnknownSortProperty() {
        assertThatThrownBy(() -> PageRequests.of(0, 20, "password,asc", Set.of("createdAt"), "createdAt"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void rejectsAPageSizeAboveTheCap() {
        assertThatThrownBy(() -> PageRequests.of(0, 101, "createdAt,desc", Set.of("createdAt"), "createdAt"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("size");
    }
}
