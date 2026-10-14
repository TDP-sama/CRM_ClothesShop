package com.example.shop.catalog;

import com.example.shop.catalog.CatalogDtos.AdminProduct;
import com.example.shop.catalog.CatalogDtos.AdminProductRow;
import com.example.shop.catalog.CatalogDtos.CategoryDto;
import com.example.shop.catalog.CatalogDtos.CategoryUpsert;
import com.example.shop.catalog.CatalogDtos.ProductUpsert;
import com.example.shop.catalog.ProductExcelService.ImportResult;
import com.example.shop.common.PageResponse;
import com.example.shop.report.ReportService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Catalog management; {@code /api/admin/**} requires the ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminCatalogController {

    private final AdminCatalogService admin;
    private final ProductExcelService excel;

    public AdminCatalogController(AdminCatalogService admin, ProductExcelService excel) {
        this.admin = admin;
        this.excel = excel;
    }

    @GetMapping("/products")
    public PageResponse<AdminProductRow> products(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return admin.list(q, page, size);
    }

    @GetMapping("/products/{id}")
    public AdminProduct product(@PathVariable Long id) {
        return admin.get(id);
    }

    /** All products as an Excel file, one row per variant (see ProductExcelService). */
    @GetMapping("/products/export")
    public ResponseEntity<byte[]> exportProducts() {
        String fileName = "san-pham-%s.xlsx".formatted(LocalDate.now(ReportService.ZONE));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(excel.export());
    }

    /** Creates/updates products from an Excel file; all-or-nothing, errors are reported per row. */
    @PostMapping(path = "/products/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importProducts(Authentication auth, @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn file Excel");
        }
        try (InputStream in = file.getInputStream()) {
            return excel.importFile(in, auth.getName());
        }
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProduct create(Authentication auth, @Valid @RequestBody ProductUpsert request) {
        return admin.create(request, auth.getName());
    }

    @PutMapping("/products/{id}")
    public AdminProduct update(Authentication auth, @PathVariable Long id, @Valid @RequestBody ProductUpsert request) {
        return admin.update(id, request, auth.getName());
    }

    /** Hides the product from the store (soft delete). */
    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hide(@PathVariable Long id) {
        admin.hide(id);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createCategory(@Valid @RequestBody CategoryUpsert request) {
        return admin.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryUpsert request) {
        return admin.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        admin.deleteCategory(id);
    }
}
