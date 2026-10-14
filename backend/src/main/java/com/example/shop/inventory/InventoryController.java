package com.example.shop.inventory;

import com.example.shop.common.PageResponse;
import com.example.shop.inventory.InventoryDtos.AdjustmentRequest;
import com.example.shop.inventory.InventoryDtos.InventoryItem;
import com.example.shop.inventory.InventoryDtos.MovementDto;
import com.example.shop.inventory.InventoryDtos.ReceiptDto;
import com.example.shop.inventory.InventoryDtos.ReceiptRequest;
import com.example.shop.inventory.InventoryDtos.SettingsUpdate;
import com.example.shop.inventory.InventoryDtos.StockStatus;
import com.example.shop.inventory.InventoryDtos.Summary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Warehouse management; requires the INVENTORY admin section (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin/inventory")
public class InventoryController {

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/items")
    public PageResponse<InventoryItem> items(@RequestParam(required = false) String q,
            @RequestParam(required = false) String category, @RequestParam(required = false) StockStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return inventory.items(q, category, status, page, size);
    }

    @GetMapping("/summary")
    public Summary summary() {
        return inventory.summary();
    }

    /** SKU and low-stock threshold; the stock itself only changes through receipts and adjustments. */
    @PatchMapping("/items/{variantId}")
    public InventoryItem updateSettings(@PathVariable Long variantId, @Valid @RequestBody SettingsUpdate request) {
        return inventory.updateSettings(variantId, request);
    }

    @GetMapping("/movements")
    public PageResponse<MovementDto> movements(@RequestParam(required = false) Long variantId,
            @RequestParam(required = false) MovementType type,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return inventory.movements(variantId, type, page, size);
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementDto adjust(Authentication auth, @Valid @RequestBody AdjustmentRequest request) {
        return inventory.adjust(request, auth.getName());
    }

    @GetMapping("/receipts")
    public PageResponse<ReceiptDto> receipts(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventory.receipts(page, size);
    }

    @GetMapping("/receipts/{id}")
    public ReceiptDto receipt(@PathVariable Long id) {
        return inventory.receipt(id);
    }

    @PostMapping("/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public ReceiptDto receive(Authentication auth, @Valid @RequestBody ReceiptRequest request) {
        return inventory.receive(request, auth.getName());
    }
}
