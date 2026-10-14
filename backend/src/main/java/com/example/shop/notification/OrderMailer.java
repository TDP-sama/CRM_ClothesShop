package com.example.shop.notification;

import com.example.shop.order.CustomerOrder;
import com.example.shop.order.OrderEvents;
import com.example.shop.order.OrderItem;
import com.example.shop.order.OrderRepository;
import com.example.shop.order.OrderStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.text.NumberFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

/**
 * Emails the customer when an order is placed and when its status changes. Runs after the order transaction has
 * committed and on a separate thread, so a slow or failing mail server never affects checkout. Does nothing when no
 * SMTP server is configured ({@code spring.mail.host}), e.g. on a deployment without email.
 */
@Component
public class OrderMailer {

    private static final Logger log = LoggerFactory.getLogger(OrderMailer.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final OrderRepository orders;
    private final String from;
    private final String baseUrl;

    public OrderMailer(ObjectProvider<JavaMailSender> mailSender, OrderRepository orders,
            @Value("${app.mail.from}") String from, @Value("${app.base-url}") String baseUrl) {
        this.mailSender = mailSender;
        this.orders = orders;
        this.from = from;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onPlaced(OrderEvents.Placed event) {
        orders.findById(event.orderId()).ifPresent(order -> send(order,
                "Xác nhận đơn hàng " + order.getCode(),
                "Cảm ơn bạn đã đặt hàng tại MODA! Chúng tôi đã nhận được đơn hàng của bạn và sẽ liên hệ để xác nhận sớm nhất."));
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onStatusChanged(OrderEvents.StatusChanged event) {
        String message = switch (event.status()) {
            case CONFIRMED -> "Đơn hàng của bạn đã được xác nhận và đang được chuẩn bị.";
            case SHIPPING -> "Đơn hàng của bạn đã được giao cho đơn vị vận chuyển và đang trên đường đến bạn.";
            case DELIVERED -> "Đơn hàng đã được giao thành công. Cảm ơn bạn đã mua sắm! Bạn có thể đánh giá sản phẩm ngay bây giờ.";
            case CANCELLED -> "Đơn hàng của bạn đã được hủy. Nếu đây là nhầm lẫn, vui lòng liên hệ cửa hàng.";
            case PENDING -> null;
        };
        if (message == null) {
            return;
        }
        orders.findById(event.orderId()).ifPresent(order -> send(order,
                "Đơn hàng %s: %s".formatted(order.getCode(), statusLabel(event.status())), message));
    }

    private void send(CustomerOrder order, String subject, String message) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.debug("No mail server configured; skipping \"{}\"", subject);
            return;
        }
        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(order.getUser().getEmail());
            helper.setSubject(subject);
            helper.setText(plainText(order, message), html(order, message));
            sender.send(mime);
            log.info("Sent \"{}\" to {}", subject, order.getUser().getEmail());
        } catch (MessagingException | MailException e) {
            log.warn("Could not send \"{}\": {}", subject, e.getMessage());
        }
    }

    private String plainText(CustomerOrder order, String message) {
        StringBuilder text = new StringBuilder()
                .append("Xin chào ").append(order.getRecipientName()).append(",\n\n")
                .append(message).append("\n\n")
                .append("Mã đơn: ").append(order.getCode()).append('\n');
        for (OrderItem item : order.getItems()) {
            text.append("- ").append(item.getProductName()).append(" (").append(item.getColor()).append(" / ")
                    .append(item.getSize()).append(") x").append(item.getQuantity()).append(": ")
                    .append(money(item.getLineTotal())).append('\n');
        }
        return text.append("Phí vận chuyển: ").append(money(order.getShippingFee())).append('\n')
                .append("Tổng cộng: ").append(money(order.getTotal())).append(" (thanh toán khi nhận hàng)\n")
                .append("Giao đến: ").append(order.getAddress()).append("\n\n")
                .append("Xem chi tiết: ").append(orderUrl(order)).append('\n')
                .toString();
    }

    private String html(CustomerOrder order, String message) {
        StringBuilder rows = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            rows.append("<tr><td style=\"padding:6px 0\">").append(esc(item.getProductName()))
                    .append("<br><span style=\"color:#6b7280;font-size:13px\">").append(esc(item.getColor()))
                    .append(" / ").append(esc(item.getSize())).append(" &times; ").append(item.getQuantity())
                    .append("</span></td><td style=\"padding:6px 0;text-align:right\">")
                    .append(money(item.getLineTotal())).append("</td></tr>");
        }
        return """
                <div style="font-family:Arial,sans-serif;color:#18181b;max-width:560px;margin:auto">
                  <h2 style="letter-spacing:0.15em">MODA</h2>
                  <p>Xin chào %s,</p>
                  <p>%s</p>
                  <p><strong>Mã đơn:</strong> %s</p>
                  <table style="width:100%%;border-collapse:collapse;border-top:1px solid #e5e7eb">%s
                    <tr><td style="padding:6px 0;border-top:1px solid #e5e7eb">Phí vận chuyển</td>
                        <td style="padding:6px 0;border-top:1px solid #e5e7eb;text-align:right">%s</td></tr>
                    <tr><td style="padding:6px 0"><strong>Tổng cộng (thanh toán khi nhận)</strong></td>
                        <td style="padding:6px 0;text-align:right"><strong>%s</strong></td></tr>
                  </table>
                  <p><strong>Giao đến:</strong> %s – %s<br>%s</p>
                  <p><a href="%s" style="display:inline-block;padding:10px 18px;background:#18181b;color:#fff;
                     text-decoration:none;border-radius:6px">Xem đơn hàng</a></p>
                </div>
                """.formatted(esc(order.getRecipientName()), esc(message), order.getCode(), rows,
                money(order.getShippingFee()), money(order.getTotal()), esc(order.getRecipientName()),
                esc(order.getPhone()), esc(order.getAddress()), orderUrl(order));
    }

    private String orderUrl(CustomerOrder order) {
        return baseUrl + "/tai-khoan/don-hang/" + order.getId();
    }

    private static String statusLabel(OrderStatus status) {
        return switch (status) {
            case PENDING -> "Chờ xác nhận";
            case CONFIRMED -> "Đã xác nhận";
            case SHIPPING -> "Đang giao";
            case DELIVERED -> "Đã giao";
            case CANCELLED -> "Đã hủy";
        };
    }

    private static String money(long amount) {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " ₫";
    }

    private static String esc(String text) {
        return text == null ? "" : HtmlUtils.htmlEscape(text);
    }
}
