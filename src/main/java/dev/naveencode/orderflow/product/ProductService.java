package dev.naveencode.orderflow.product;

import dev.naveencode.orderflow.common.PageRequests;
import dev.naveencode.orderflow.common.PageResponse;
import dev.naveencode.orderflow.error.ApiException;
import dev.naveencode.orderflow.error.ErrorCode;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final Set<String> SORTS = Set.of("name", "sku", "unitPrice", "createdAt");

    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(
            String query,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean active,
            int page,
            int size,
            String sort) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "minPrice cannot be greater than maxPrice");
        }
        Specification<Product> specification = (root, cq, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("sku")), like)));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("unitPrice"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("unitPrice"), maxPrice));
            }
            predicates.add(cb.equal(root.get("active"), active == null || active));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var result = products.findAll(specification, PageRequests.of(page, size, sort, SORTS, "name"));
        return PageResponse.from(result.map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return ProductResponse.from(require(id));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String sku = request.sku().trim();
        if (products.existsBySku(sku)) {
            throw new ApiException(ErrorCode.SKU_TAKEN, "A product with this SKU already exists");
        }
        var product = new Product(
                sku,
                request.name().trim(),
                blankToNull(request.description()),
                request.unitPrice(),
                request.stockQuantity());
        try {
            products.saveAndFlush(product);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.SKU_TAKEN, "A product with this SKU already exists");
        }
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        var product = require(id);
        product.update(
                request.name().trim(),
                blankToNull(request.description()),
                request.unitPrice(),
                request.stockQuantity(),
                request.active());
        return ProductResponse.from(product);
    }

    private Product require(UUID id) {
        return products.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found"));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
