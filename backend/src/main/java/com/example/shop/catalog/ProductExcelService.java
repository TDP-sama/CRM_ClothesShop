package com.example.shop.catalog;

import com.example.shop.catalog.CatalogDtos.ProductUpsert;
import com.example.shop.catalog.CatalogDtos.VariantUpsert;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Product import/export as Excel (.xlsx), one row per variant. Import goes through {@link AdminCatalogService}, so it
 * applies exactly the same rules as the admin form (validation, slugs, variant reconciliation, initial stock through
 * the stock ledger). It is all-or-nothing: the whole file is checked first and any error changes nothing.
 */
@Service
public class ProductExcelService {

    public static final String SHEET = "San pham";

    // Column order of the products sheet; headers marked * are required.
    static final int ID = 0;
    static final int NAME = 1;
    static final int SLUG = 2;
    static final int CATEGORY = 3;
    static final int PRICE = 4;
    static final int COMPARE_AT = 5;
    static final int ACTIVE = 6;
    static final int FEATURED = 7;
    static final int DESCRIPTION = 8;
    static final int IMAGES = 9;
    static final int VARIANT_ID = 10;
    static final int SKU = 11;
    static final int SIZE = 12;
    static final int COLOR = 13;
    static final int STOCK = 14;
    static final String[] HEADERS = {"Mã SP", "Tên sản phẩm *", "Slug", "Danh mục *", "Giá bán *", "Giá gốc",
            "Đang bán", "Nổi bật", "Mô tả", "Ảnh (mỗi dòng 1 URL)", "Mã biến thể", "SKU", "Size *", "Màu *",
            "Tồn kho"};

    public record RowError(int row, String message) {
    }

    public record ImportResult(int created, int updated, int rows) {
    }

    /** The file has problems; nothing was changed. Rendered as 400 with the list of row errors. */
    public static class ExcelImportException extends RuntimeException {
        private final List<RowError> errors;

        public ExcelImportException(List<RowError> errors) {
            super("File Excel có %d lỗi".formatted(errors.size()));
            this.errors = List.copyOf(errors);
        }

        public List<RowError> getErrors() {
            return errors;
        }
    }

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final AdminCatalogService admin;
    private final Validator validator;

    public ProductExcelService(ProductRepository products, CategoryRepository categories, AdminCatalogService admin,
            Validator validator) {
        this.products = products;
        this.categories = categories;
        this.admin = admin;
        this.validator = validator;
    }

    // ---------------------------------------------------------------- Export

    @Transactional(readOnly = true)
    public byte[] export() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(wb);
            CellStyle wrap = wb.createCellStyle();
            wrap.setWrapText(true);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0"));

            Sheet sheet = wb.createSheet(SHEET);
            Row head = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = head.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(header);
            }
            int r = 1;
            for (Product p : products.findAll(Sort.by("id"))) {
                List<ProductVariant> variants = p.getVariants().stream()
                        .sorted(Comparator.comparing(ProductVariant::getId)).toList();
                for (ProductVariant v : variants) {
                    Row row = sheet.createRow(r++);
                    row.createCell(ID).setCellValue(p.getId());
                    row.createCell(NAME).setCellValue(p.getName());
                    row.createCell(SLUG).setCellValue(p.getSlug());
                    row.createCell(CATEGORY).setCellValue(p.getCategory().getSlug());
                    numeric(row, PRICE, p.getPrice(), money);
                    if (p.getCompareAtPrice() != null) {
                        numeric(row, COMPARE_AT, p.getCompareAtPrice(), money);
                    }
                    row.createCell(ACTIVE).setCellValue(p.isActive() ? "Có" : "Không");
                    row.createCell(FEATURED).setCellValue(p.isFeatured() ? "Có" : "Không");
                    text(row, DESCRIPTION, p.getDescription(), wrap);
                    text(row, IMAGES, String.join("\n", p.getImages()), wrap);
                    row.createCell(VARIANT_ID).setCellValue(v.getId());
                    if (v.getSku() != null) {
                        row.createCell(SKU).setCellValue(v.getSku());
                    }
                    row.createCell(SIZE).setCellValue(v.getSize());
                    row.createCell(COLOR).setCellValue(v.getColor());
                    row.createCell(STOCK).setCellValue(v.getStock());
                }
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, r - 1), 0, HEADERS.length - 1));
            int[] widths = {8, 32, 26, 14, 12, 12, 10, 10, 40, 50, 12, 32, 9, 14, 10};
            for (int c = 0; c < widths.length; c++) {
                sheet.setColumnWidth(c, widths[c] * 256);
            }

            Sheet cats = wb.createSheet("Danh muc");
            Row catHead = cats.createRow(0);
            String[] catHeaders = {"Slug (dùng ở cột Danh mục)", "Tên danh mục"};
            for (int c = 0; c < catHeaders.length; c++) {
                Cell cell = catHead.createCell(c);
                cell.setCellValue(catHeaders[c]);
                cell.setCellStyle(header);
            }
            int cr = 1;
            for (Category c : categories.findAllByOrderBySortOrderAscNameAsc()) {
                Row row = cats.createRow(cr++);
                row.createCell(0).setCellValue(c.getSlug());
                row.createCell(1).setCellValue(c.getName());
            }
            cats.setColumnWidth(0, 30 * 256);
            cats.setColumnWidth(1, 30 * 256);

            Sheet help = wb.createSheet("Huong dan");
            String[] lines = {
                    "HƯỚNG DẪN NHẬP SẢN PHẨM",
                    "• Mỗi dòng là một biến thể (size + màu). Một sản phẩm có nhiều dòng, các cột thông tin sản phẩm lấy theo dòng đầu tiên.",
                    "• Cập nhật sản phẩm có sẵn: giữ nguyên cột \"Mã SP\". Tạo sản phẩm mới: để trống \"Mã SP\"; các dòng cùng Slug (hoặc cùng Tên) là cùng một sản phẩm.",
                    "• Danh mục: ghi slug hoặc tên danh mục (xem sheet \"Danh muc\").",
                    "• Đang bán / Nổi bật: Có hoặc Không. Ảnh: mỗi dòng một đường dẫn trong cùng ô.",
                    "• Biến thể: giữ \"Mã biến thể\" để cập nhật đúng biến thể. Biến thể của một sản phẩm KHÔNG còn trong file sẽ bị xóa khỏi sản phẩm đó.",
                    "• Tồn kho: chỉ dùng làm tồn ban đầu cho biến thể MỚI. Tồn kho của biến thể có sẵn không đổi khi nhập file; hãy nhập hàng hoặc điều chỉnh ở mục Kho hàng.",
                    "• Sản phẩm không có trong file được giữ nguyên. Cột SKU chỉ để xem.",
                    "• Nếu file có lỗi, hệ thống không thay đổi gì và liệt kê lỗi theo số dòng.",
            };
            for (int i = 0; i < lines.length; i++) {
                help.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            help.setColumnWidth(0, 140 * 256);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the Excel file", e);
        }
    }

    private static CellStyle headerStyle(Workbook wb) {
        Font bold = wb.createFont();
        bold.setBold(true);
        CellStyle style = wb.createCellStyle();
        style.setFont(bold);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private static void numeric(Row row, int col, long value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void text(Row row, int col, String value, CellStyle style) {
        if (value == null || value.isEmpty()) {
            return;
        }
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    // ---------------------------------------------------------------- Import

    /** A product being assembled from consecutive or matching rows. */
    private static final class Group {
        final int firstRow;
        final Long productId;
        final Map<String, String> fields;
        final List<VariantUpsert> variants = new ArrayList<>();

        Group(int firstRow, Long productId, Map<String, String> fields) {
            this.firstRow = firstRow;
            this.productId = productId;
            this.fields = fields;
        }
    }

    @Transactional
    public ImportResult importFile(InputStream in, String actorEmail) {
        List<RowError> errors = new ArrayList<>();
        Map<String, Group> groups = new LinkedHashMap<>();
        int rows = 0;

        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = Optional.ofNullable(wb.getSheet(SHEET)).orElse(wb.getSheetAt(0));
            DataFormatter fmt = new DataFormatter(Locale.ROOT);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                String[] v = read(row, fmt);
                if (Arrays.stream(v).allMatch(String::isEmpty)) {
                    continue;
                }
                rows++;
                int excelRow = r + 1;
                Long productId = null;
                if (!v[ID].isEmpty()) {
                    productId = parseLong(v[ID]);
                    if (productId == null || !products.existsById(productId)) {
                        errors.add(new RowError(excelRow, "Mã SP \"%s\" không tồn tại".formatted(v[ID])));
                        continue;
                    }
                }
                String key = productId != null ? "id:" + productId
                        : "new:" + (v[SLUG].isEmpty() ? v[NAME].toLowerCase(Locale.ROOT) : v[SLUG].toLowerCase(Locale.ROOT));
                Map<String, String> fields = Map.of("name", v[NAME], "slug", v[SLUG], "category", v[CATEGORY],
                        "price", v[PRICE], "compareAt", v[COMPARE_AT], "active", v[ACTIVE], "featured", v[FEATURED],
                        "description", v[DESCRIPTION], "images", v[IMAGES]);
                // Product-level columns come from the first row of each product.
                Long id = productId;
                Group target = groups.computeIfAbsent(key, k -> new Group(excelRow, id, fields));

                Integer stock = null;
                if (!v[STOCK].isEmpty()) {
                    Long parsed = parseLong(v[STOCK]);
                    if (parsed == null || parsed < 0 || parsed > 1_000_000) {
                        errors.add(new RowError(excelRow, "Tồn kho \"%s\" không hợp lệ".formatted(v[STOCK])));
                        continue;
                    }
                    stock = parsed.intValue();
                }
                Long variantId = v[VARIANT_ID].isEmpty() ? null : parseLong(v[VARIANT_ID]);
                target.variants.add(new VariantUpsert(variantId, v[SIZE], v[COLOR], stock == null ? 0 : stock));
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ExcelImportException ex) {
                throw ex;
            }
            throw new ExcelImportException(List.of(new RowError(0, "Không đọc được file. Hãy dùng file .xlsx xuất từ hệ thống")));
        }
        if (rows == 0) {
            throw new ExcelImportException(List.of(new RowError(0, "File không có dòng dữ liệu nào")));
        }

        // Build and validate every product before changing anything.
        Map<Group, ProductUpsert> upserts = new LinkedHashMap<>();
        for (Group g : groups.values()) {
            ProductUpsert upsert = toUpsert(g, errors);
            if (upsert == null) {
                continue;
            }
            for (ConstraintViolation<ProductUpsert> violation : validator.validate(upsert)) {
                errors.add(new RowError(g.firstRow, violation.getMessage()));
            }
            upserts.put(g, upsert);
        }
        if (!errors.isEmpty()) {
            throw new ExcelImportException(errors);
        }

        int created = 0;
        int updated = 0;
        for (Map.Entry<Group, ProductUpsert> e : upserts.entrySet()) {
            Group g = e.getKey();
            try {
                if (g.productId != null) {
                    admin.update(g.productId, e.getValue(), actorEmail);
                    updated++;
                } else {
                    admin.create(e.getValue(), actorEmail);
                    created++;
                }
            } catch (ResponseStatusException ex) {
                // Rolls back everything already applied in this import.
                throw new ExcelImportException(List.of(new RowError(g.firstRow, ex.getReason())));
            }
        }
        return new ImportResult(created, updated, rows);
    }

    private ProductUpsert toUpsert(Group g, List<RowError> errors) {
        Map<String, String> f = g.fields;
        int before = errors.size();
        Long price = f.get("price").isEmpty() ? null : parseMoney(f.get("price"));
        if (!f.get("price").isEmpty() && price == null) {
            errors.add(new RowError(g.firstRow, "Giá bán \"%s\" không hợp lệ".formatted(f.get("price"))));
        }
        Long compareAt = f.get("compareAt").isEmpty() ? null : parseMoney(f.get("compareAt"));
        if (!f.get("compareAt").isEmpty() && compareAt == null) {
            errors.add(new RowError(g.firstRow, "Giá gốc \"%s\" không hợp lệ".formatted(f.get("compareAt"))));
        }
        Long categoryId = null;
        if (!f.get("category").isEmpty()) {
            categoryId = findCategory(f.get("category")).map(Category::getId).orElse(null);
            if (categoryId == null) {
                errors.add(new RowError(g.firstRow, "Danh mục \"%s\" không tồn tại".formatted(f.get("category"))));
            }
        }
        Boolean active = yesNo(f.get("active"), true);
        Boolean featured = yesNo(f.get("featured"), false);
        if (active == null || featured == null) {
            errors.add(new RowError(g.firstRow, "Cột Đang bán / Nổi bật chỉ nhận Có hoặc Không"));
        }
        if (errors.size() > before) {
            return null;
        }
        List<String> images = Arrays.stream(f.get("images").split("[\\r\\n;]+|\\s+"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new ProductUpsert(f.get("name").isEmpty() ? null : f.get("name"),
                f.get("slug").isEmpty() ? null : f.get("slug"),
                f.get("description").isEmpty() ? null : f.get("description"),
                price, compareAt, categoryId, images, g.variants, active, featured);
    }

    private Optional<Category> findCategory(String value) {
        String wanted = value.trim().toLowerCase(Locale.ROOT);
        return categories.findAll().stream()
                .filter(c -> c.getSlug().equalsIgnoreCase(wanted) || c.getName().toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst();
    }

    private static String[] read(Row row, DataFormatter fmt) {
        String[] values = new String[HEADERS.length];
        for (int c = 0; c < HEADERS.length; c++) {
            Cell cell = row == null ? null : row.getCell(c);
            values[c] = cell == null ? "" : fmt.formatCellValue(cell).trim();
        }
        return values;
    }

    private static Long parseLong(String s) {
        try {
            return Long.parseLong(s.trim().replaceAll("\\.0+$", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Accepts 199000, 199.000, 199,000 and "199.000 ₫"; prices are whole đồng. */
    private static Long parseMoney(String s) {
        String digits = s.replaceAll("[\\s.,₫đĐ]", "");
        return digits.matches("\\d{1,12}") ? Long.parseLong(digits) : null;
    }

    private static Boolean yesNo(String s, boolean whenEmpty) {
        String v = s.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) {
            return whenEmpty;
        }
        return switch (v) {
            case "có", "co", "x", "true", "1", "yes" -> true;
            case "không", "khong", "false", "0", "no" -> false;
            default -> null;
        };
    }
}
