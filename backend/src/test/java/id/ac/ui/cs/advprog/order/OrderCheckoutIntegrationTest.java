package id.ac.ui.cs.advprog.order;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderCheckoutIntegrationTest {

    private static final MockWebServer INVENTORY_SERVER = new MockWebServer();
    private static final MockWebServer WALLET_SERVER = new MockWebServer();
    private static final MockWebServer VOUCHER_SERVER = new MockWebServer();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("services.inventory.base-url", () -> INVENTORY_SERVER.url("/").toString());
        registry.add("services.wallet.base-url", () -> WALLET_SERVER.url("/").toString());
        registry.add("services.voucher.base-url", () -> VOUCHER_SERVER.url("/").toString());
    }

    @BeforeAll
    static void startServers() throws IOException {
        INVENTORY_SERVER.start();
        WALLET_SERVER.start();
        VOUCHER_SERVER.start();

        INVENTORY_SERVER.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/api/products/inventory/11111111-1111-1111-1111-111111111111")) {
                    return json("""
                            {
                              "id": "11111111-1111-1111-1111-111111111111",
                              "name": "Nike SB Dunk Low Travis Scott",
                              "price": 4500000.00,
                              "stock": 5,
                              "jastiperId": "2001"
                            }
                            """);
                }
                if ("/api/products/inventory/reduce-stock".equals(path) || "/api/products/inventory/restore-stock".equals(path)) {
                    return json("""
                            {
                              "id": "11111111-1111-1111-1111-111111111111",
                              "stock": 4
                            }
                            """);
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        WALLET_SERVER.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if ("/wallet/balance".equals(path)) {
                    return json("""
                            {
                              "userId": 1001,
                              "balance": 5000000.00,
                              "currency": "IDR"
                            }
                            """);
                }
                if ("/wallet/deduct".equals(path) || "/wallet/refund".equals(path)) {
                    return json("""
                            {
                              "userId": 1001,
                              "balance": 950000.00,
                              "currency": "IDR"
                            }
                            """);
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        VOUCHER_SERVER.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if ("/vouchers/validate".equals(path)) {
                    return json("""
                            {
                              "valid": true,
                              "code": "MILESTONE10",
                              "orderAmount": 4500000.00,
                              "discountAmount": 450000.00,
                              "message": "ok"
                            }
                            """);
                }
                if ("/vouchers/claim".equals(path)) {
                    return json("""
                            {
                              "success": true,
                              "idempotent": false,
                              "code": "MILESTONE10",
                              "orderId": "1",
                              "orderAmount": 4500000.00,
                              "discountApplied": 450000.00,
                              "quotaRemaining": 9,
                              "message": "ok"
                            }
                            """);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
    }

    @AfterAll
    static void shutdownServers() throws IOException {
        INVENTORY_SERVER.shutdown();
        WALLET_SERVER.shutdown();
        VOUCHER_SERVER.shutdown();
    }

    @Test
    void checkoutShouldPersistPaidOrderUsingRealServiceClients() throws Exception {
        mockMvc.perform(post("/orders/checkout")
                        .with(user("1001").roles("TITIPER"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "address": "Jl. Mawar No. 1, Jakarta",
                                  "voucherCode": "MILESTONE10",
                                  "items": [
                                    {
                                      "productId": "11111111-1111-1111-1111-111111111111",
                                      "qty": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.discountTotal").value(450000.00))
                .andExpect(jsonPath("$.data.totalPaid").value(4050000.00));

        mockMvc.perform(get("/orders/my")
                        .with(user("1001").roles("TITIPER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PAID"));
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
