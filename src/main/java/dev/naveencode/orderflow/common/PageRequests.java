package dev.naveencode.orderflow.common;

import dev.naveencode.orderflow.error.ApiException;
import dev.naveencode.orderflow.error.ErrorCode;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageRequests {

    private PageRequests() {
    }

    public static Pageable of(int page, int size, String sort, Set<String> allowed, String defaultProperty) {
        if (page < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "size must be between 1 and 100");
        }
        return PageRequest.of(page, size, parse(sort, allowed, defaultProperty));
    }

    static Sort parse(String raw, Set<String> allowed, String defaultProperty) {
        if (raw == null || raw.isBlank()) {
            return Sort.by(Sort.Direction.DESC, defaultProperty);
        }
        String[] parts = raw.split(",", 2);
        String property = parts[0].trim();
        if (!allowed.contains(property)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unsupported sort property '" + property + "'");
        }
        if (parts.length == 1) {
            return Sort.by(Sort.Direction.DESC, property);
        }
        String direction = parts[1].trim().toLowerCase(Locale.ROOT);
        Sort.Direction resolved = switch (direction) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new ApiException(ErrorCode.VALIDATION_FAILED, "Sort direction must be asc or desc");
        };
        return Sort.by(resolved, property);
    }
}
