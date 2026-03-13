package id.ac.ui.cs.advprog.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class CheckoutRequest {

    @NotEmpty(message = "items must not be empty")
    @Valid
    private List<CheckoutItemRequest> items;

    @NotBlank(message = "address is required")
    private String address;

    // milestone 25%: field ada, boleh kosong/null, diskon 0
    private String voucherCode;

    public List<CheckoutItemRequest> getItems() { return items; }
    public void setItems(List<CheckoutItemRequest> items) { this.items = items; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }
}