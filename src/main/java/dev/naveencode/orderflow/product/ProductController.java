package dev.naveencode.orderflow.product;

import dev.naveencode.orderflow.common.PageResponse;
import dev.naveencode.orderflow.error.ProblemBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Catalog and inventory. Reads are public. Writes require ADMIN.")
public class ProductController {

    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    @Operation(summary = "List products", description = "Filters the catalog. Inactive products are hidden unless active=false.")
    public PageResponse<ProductResponse> list(
            @Parameter(description = "Case-insensitive match on name or SKU")
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Defaults to true")
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "name, sku, unitPrice, or createdAt. Direction asc or desc.")
            @RequestParam(defaultValue = "name,asc") String sort) {
        return products.list(q, minPrice, maxPrice, active, page, size, sort);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product")
    @ApiResponse(responseCode = "404", description = "Product not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemBody.class)))
    public ProductResponse get(@PathVariable UUID id) {
        return products.get(id);
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a product")
    @ApiResponse(responseCode = "201", description = "Product created")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        var product = products.create(request);
        return ResponseEntity.created(URI.create("/api/v1/products/" + product.id())).body(product);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Replace product details", description = "SKU is immutable. Stock updates use the row version for optimistic locking.")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return products.update(id, request);
    }
}
