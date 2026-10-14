package com.example.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shop.auth.AppUser;
import com.example.shop.auth.AppUserRepository;
import com.example.shop.auth.Role;
import com.example.shop.inventory.InventoryService;
import com.example.shop.inventory.MovementType;
import com.example.shop.inventory.StockMovementRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Export → edit in "Excel" (Apache POI) → import, against H2. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:excel;DB_CLOSE_DELAY=-1", "app.seed-demo-data=false"})
@AutoConfigureMockMvc
class ProductExcelIntegrationTest {

    private static final RequestPostProcessor ADMIN = user("admin@example.com");

    @Autowired private MockMvc mvc;
    @Autowired private AppUserRepository users;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private StockMovementRepository movements;
    @Autowired private InventoryService inventory;
    @Autowired private PlatformTransactionManager transactions;

    private Long teeId;

    @BeforeEach
    void setUp() {
        movements.deleteAll();
        products.deleteAll();
        categories.deleteAll();
        users.deleteAll();
        users.save(new AppUser("admin@example.com", "{noop}x", "Admin", Role.ADMIN));
        users.save(new AppUser("buyer@example.com", "{noop}x", "Khách", Role.CUSTOMER));
        Category tops = categories.save(new Category("Áo", "ao", null, 1));
        categories.save(new Category("Quần", "quan", null, 2));
        Product tee = new Product("Áo thun", "ao-thun", tops);
        tee.setPrice(199_000);
        tee.getImages().add("https://example.com/a.jpg");
        tee.addVariant("M", "Đen", 10);
        teeId = products.save(tee).getId();
        inventory.run(null); // SKU + opening balance, as at startup
    }

    private Workbook exported() throws Exception {
        byte[] bytes = mvc.perform(get("/api/admin/products/export").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private ResultActions upload(Workbook wb) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return mvc.perform(multipart("/api/admin/products/import")
                .file(new MockMultipartFile("file", "san-pham.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray()))
                .with(ADMIN).with(csrf()));
    }

    private static void set(Row row, int col, String value) {
        row.createCell(col).setCellValue(value);
    }

    @Test
    void exportHasOneRowPerVariant() throws Exception {
        Sheet sheet = exported().getSheet(ProductExcelService.SHEET);
        assertThat(sheet.getRow(0).getCell(ProductExcelService.NAME).getStringCellValue()).isEqualTo("Tên sản phẩm *");
        assertThat(sheet.getLastRowNum()).isEqualTo(1);
        Row row = sheet.getRow(1);
        assertThat((long) row.getCell(ProductExcelService.ID).getNumericCellValue()).isEqualTo(teeId);
        assertThat(row.getCell(ProductExcelService.CATEGORY).getStringCellValue()).isEqualTo("ao");
        assertThat(row.getCell(ProductExcelService.SKU).getStringCellValue()).isEqualTo("AO-THUN-DEN-M");
    }

    @Test
    void importUpdatesCreatesAndKeepsStockRulesOfTheWarehouse() throws Exception {
        Workbook wb = exported();
        Sheet sheet = wb.getSheet(ProductExcelService.SHEET);
        Row existing = sheet.getRow(1);
        set(existing, ProductExcelService.PRICE, "179.000");
        set(existing, ProductExcelService.STOCK, "999"); // ignored: existing stock only changes in the warehouse
        // A new variant of the same product (no variant id), with initial stock.
        Row newVariant = sheet.createRow(2);
        for (int c = 0; c <= ProductExcelService.DESCRIPTION; c++) {
            if (existing.getCell(c) != null) {
                set(newVariant, c, new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(existing.getCell(c)));
            }
        }
        set(newVariant, ProductExcelService.SIZE, "L");
        set(newVariant, ProductExcelService.COLOR, "Đen");
        set(newVariant, ProductExcelService.STOCK, "7");
        // A brand-new product in another category, given by its name.
        Row newProduct = sheet.createRow(3);
        set(newProduct, ProductExcelService.NAME, "Quần short");
        set(newProduct, ProductExcelService.CATEGORY, "Quần");
        set(newProduct, ProductExcelService.PRICE, "250000");
        set(newProduct, ProductExcelService.SIZE, "M");
        set(newProduct, ProductExcelService.COLOR, "Be");
        set(newProduct, ProductExcelService.STOCK, "5");

        upload(wb).andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.rows").value(3));

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Product tee = products.findById(teeId).orElseThrow();
            assertThat(tee.getPrice()).isEqualTo(179_000);
            assertThat(tee.getVariants()).hasSize(2);
            assertThat(tee.getVariants()).filteredOn(v -> v.getSize().equals("M")).first()
                    .extracting(ProductVariant::getStock).isEqualTo(10);
            ProductVariant large = tee.getVariants().stream().filter(v -> v.getSize().equals("L")).findFirst()
                    .orElseThrow();
            assertThat(large.getStock()).isEqualTo(7);
            assertThat(movements.findAll()).anyMatch(m -> m.getVariantId().equals(large.getId())
                    && m.getType() == MovementType.INITIAL && m.getQuantityChange() == 7);
            assertThat(products.findAll())
                    .anyMatch(p -> p.getSlug().equals("quan-short") && p.getCategory().getSlug().equals("quan"));
        });
    }

    @Test
    void anyErrorRejectsTheWholeFileWithRowNumbers() throws Exception {
        Workbook wb = exported();
        Sheet sheet = wb.getSheet(ProductExcelService.SHEET);
        set(sheet.getRow(1), ProductExcelService.PRICE, "179000"); // valid change that must NOT be applied
        Row bad = sheet.createRow(2);
        set(bad, ProductExcelService.NAME, "Sai danh mục");
        set(bad, ProductExcelService.CATEGORY, "khong-co");
        set(bad, ProductExcelService.PRICE, "abc");
        set(bad, ProductExcelService.SIZE, "M");
        set(bad, ProductExcelService.COLOR, "Đỏ");

        upload(wb).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.rowErrors", hasSize(2)))
                .andExpect(jsonPath("$.rowErrors[0].row").value(3));

        assertThat(products.findById(teeId).orElseThrow().getPrice()).isEqualTo(199_000);
        assertThat(products.count()).isEqualTo(1);
    }

    @Test
    void importAndExportNeedTheProductsSection() throws Exception {
        mvc.perform(get("/api/admin/products/export").with(user("buyer@example.com"))).andExpect(status().isForbidden());
    }
}
