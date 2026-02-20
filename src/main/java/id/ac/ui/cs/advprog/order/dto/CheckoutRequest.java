package id.ac.ui.cs.advprog.order.dto;

import java.util.List;

public class CheckoutRequest {
    private List<CheckoutItemRequest> items;
    private String address;
    private String voucherCode; // optional

    public List<CheckoutItemRequest> getItems() { return items; }
    public void setItems(List<CheckoutItemRequest> items) { this.items = items; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }
}