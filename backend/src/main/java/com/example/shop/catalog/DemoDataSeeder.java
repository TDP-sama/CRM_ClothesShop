package com.example.shop.catalog;

import com.example.shop.common.Slugs;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills an empty catalog with demo fashion products so the store looks real on first start.
 * Runs only when there are no products yet; disable with {@code SEED_DEMO_DATA=false}.
 */
@Component
@Order(1) // before InventoryService's backfill, which then gives the seeded variants SKUs and opening balances
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final List<String> TOP_SIZES = List.of("S", "M", "L", "XL");
    private static final List<String> DRESS_SIZES = List.of("S", "M", "L");

    private final boolean enabled;
    private final CategoryRepository categories;
    private final ProductRepository products;

    public DemoDataSeeder(@Value("${app.seed-demo-data:true}") boolean enabled, CategoryRepository categories,
            ProductRepository products) {
        this.enabled = enabled;
        this.categories = categories;
        this.products = products;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled || products.count() > 0) {
            return;
        }
        Category tops = category("Áo", "ao", "photo-1620799140408-edc6dcb6d633", 1);
        Category pants = category("Quần", "quan", "photo-1560243563-062bfc001d68", 2);
        Category dresses = category("Đầm & Váy", "dam-vay", "photo-1595777457583-95e059d581b8", 3);
        Category outerwear = category("Áo khoác", "ao-khoac", "photo-1520975954732-35dd22299614", 4);
        Category accessories = category("Phụ kiện", "phu-kien", "photo-1584917865442-de89df76afd3", 5);

        product(tops, "Áo thun cotton basic", 199_000L, 249_000L, true,
                "Áo thun 100% cotton compact 220gsm, form regular dễ mặc. Cổ bo rib dày dặn, không bai giãn sau nhiều lần giặt.",
                List.of("photo-1521572163474-6864f9cf17ab", "photo-1529374255404-311a2a4f1fd9"),
                TOP_SIZES, List.of("Trắng", "Đen"));
        product(tops, "Áo thun graphic Skeleton", 259_000L, null, false,
                "Áo thun in hình phong cách đường phố, mực in lụa bền màu. Chất cotton mềm, thấm hút tốt.",
                List.of("photo-1503341504253-dff4815485f1"), TOP_SIZES, List.of("Đen"));
        product(tops, "Áo thun in hình Original", 229_000L, null, false,
                "Áo thun oversize với hình in phong cách Nhật, chất liệu cotton dày dặn, đứng form.",
                List.of("photo-1576566588028-4147f3842f27"), TOP_SIZES, List.of("Kem"));
        product(tops, "Áo nỉ trơn cổ tròn", 389_000L, null, true,
                "Áo nỉ chân cua mặt trong lót bông nhẹ, giữ ấm vừa phải cho những ngày se lạnh.",
                List.of("photo-1620799140408-edc6dcb6d633"), TOP_SIZES, List.of("Trắng", "Xám"));
        product(tops, "Áo len móc tua rua", 459_000L, 559_000L, false,
                "Áo len móc thủ công dáng poncho, viền tua rua mềm mại, phối cùng áo hai dây hoặc croptop.",
                List.of("photo-1434389677669-e08b4cac3105"), List.of("Free size"), List.of("Kem"));
        product(tops, "Sơ mi denim chấm bi", 429_000L, null, false,
                "Sơ mi denim mỏng nhẹ họa tiết chấm bi, tay lỡ, phù hợp đi làm lẫn đi chơi.",
                List.of("photo-1596755094514-f87e34085b2c"), TOP_SIZES, List.of("Xanh denim"));
        product(tops, "Sơ mi công sở dài tay", 399_000L, null, true,
                "Sơ mi vải bamboo chống nhăn, co giãn nhẹ, đường may tinh tế. Lựa chọn chuẩn mực cho môi trường công sở.",
                List.of("photo-1602810318383-e386cc2a3ccf"), TOP_SIZES, List.of("Trắng", "Xanh nhạt", "Đỏ đô"));

        product(pants, "Quần jeans straight", 549_000L, null, true,
                "Quần jeans ống suông cạp cao, denim 12oz bền chắc, wash màu tự nhiên.",
                List.of("photo-1542272604-787c3835535d", "photo-1604176354204-9268737828e4"),
                List.of("28", "29", "30", "31", "32"), List.of("Xanh nhạt", "Đen"));
        product(pants, "Quần jeans slim tối màu", 529_000L, 629_000L, false,
                "Quần jeans slim fit co giãn 4 chiều, màu indigo đậm dễ phối đồ.",
                List.of("photo-1624378439575-d8705ad7ae80", "photo-1560243563-062bfc001d68"),
                List.of("28", "29", "30", "31", "32"), List.of("Xanh đậm"));
        product(pants, "Quần jogger nữ", 349_000L, null, false,
                "Quần jogger vải đũi mềm, cạp chun thoải mái, bo gấu gọn gàng.",
                List.of("photo-1594633312681-425c7b97ccd1"), DRESS_SIZES, List.of("Hồng", "Be"));
        product(pants, "Bộ nỉ jogger", 599_000L, 699_000L, false,
                "Set hoodie và quần jogger chất nỉ bông, phong cách năng động cho ngày cuối tuần.",
                List.of("photo-1515886657613-9f3515b0c78f"), TOP_SIZES, List.of("Vàng"));

        product(dresses, "Đầm maxi lụa đỏ", 789_000L, null, true,
                "Đầm maxi lụa satin dáng xòe, tôn dáng và bay bổng. Lý tưởng cho tiệc tối và dịp đặc biệt.",
                List.of("photo-1595777457583-95e059d581b8"), DRESS_SIZES, List.of("Đỏ"));
        product(dresses, "Đầm voan đi biển", 649_000L, 799_000L, false,
                "Đầm voan hai lớp nhẹ tênh, xẻ tà duyên dáng, hoàn hảo cho những chuyến du lịch biển.",
                List.of("photo-1539008835657-9e8e9680c956"), DRESS_SIZES, List.of("Xanh nhạt"));
        product(dresses, "Đầm trễ vai nhung", 699_000L, null, false,
                "Đầm nhung trễ vai ôm body, chất vải dày dặn, sang trọng.",
                List.of("photo-1566174053879-31528523f8ae"), DRESS_SIZES, List.of("Tím", "Đen"));

        product(outerwear, "Áo khoác da biker", 1_290_000L, null, true,
                "Áo khoác da PU cao cấp kiểu biker, khóa kim loại chắc chắn, lót trong mềm mại.",
                List.of("photo-1551028719-00167b16eac5", "photo-1520975954732-35dd22299614"),
                TOP_SIZES, List.of("Đen"));
        product(outerwear, "Áo bomber cổ bo", 690_000L, null, false,
                "Áo bomber gió hai lớp, chống nước nhẹ, cổ và tay bo len.",
                List.of("photo-1591047139829-d91aecb6caea"), TOP_SIZES, List.of("Cam đất", "Đen"));
        product(outerwear, "Áo khoác dạ dáng dài", 1_490_000L, 1_790_000L, true,
                "Áo khoác dạ pha len dáng dài qua gối, giữ ấm tốt, thanh lịch cho mùa đông.",
                List.of("photo-1483985988355-763728e1935b"), DRESS_SIZES, List.of("Đỏ đô"));
        product(outerwear, "Bộ vest nam slim fit", 1_990_000L, null, false,
                "Bộ vest hai mảnh slim fit, vải tuyết mưa ít nhăn, may đo đường nét gọn gàng.",
                List.of("photo-1617137968427-85924c800a22"), TOP_SIZES, List.of("Xanh navy"));

        product(accessories, "Túi xách da top handle", 890_000L, null, true,
                "Túi xách da bò thật, khóa kim loại mạ vàng, kèm dây đeo chéo có thể tháo rời.",
                List.of("photo-1584917865442-de89df76afd3"), List.of("Free size"), List.of("Đỏ"));

        log.info("Seeded demo catalog: {} categories, {} products", categories.count(), products.count());
    }

    private Category category(String name, String slug, String photo, int sortOrder) {
        return categories.save(new Category(name, slug, image(photo, 800), sortOrder));
    }

    private void product(Category category, String name, long price, Long compareAtPrice, boolean featured,
            String description, List<String> photos, List<String> sizes, List<String> colors) {
        Product p = new Product(name, Slugs.slugify(name), category);
        p.setPrice(price);
        p.setCompareAtPrice(compareAtPrice);
        p.setFeatured(featured);
        p.setDescription(description);
        photos.forEach(photo -> p.getImages().add(image(photo, 1000)));
        int i = name.length(); // deterministic, varied stock levels (including a few sold-out variants)
        for (String color : colors) {
            for (String size : sizes) {
                p.addVariant(size, color, (i++ * 7) % 23);
            }
        }
        products.save(p);
    }

    private static String image(String photoId, int width) {
        return "https://images.unsplash.com/%s?w=%d&q=80&auto=format&fit=crop".formatted(photoId, width);
    }
}
