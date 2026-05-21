package id.ac.ui.cs.advprog.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ui.cs.advprog.order.common.GlobalExceptionHandler;
import id.ac.ui.cs.advprog.order.dto.OrderDetailResponse;
import id.ac.ui.cs.advprog.order.dto.OrderListItemResponse;
import id.ac.ui.cs.advprog.order.entity.OrderStatus;
import id.ac.ui.cs.advprog.order.service.OrderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerTest {

    private final OrderService orderService = mock(OrderService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void checkoutShouldReturnCreatedForTitiper() throws Exception {
        OrderDetailResponse response = new OrderDetailResponse();
        response.id = 5L;
        response.status = OrderStatus.PAID;
        response.totalPaid = new BigDecimal("225000");
        when(orderService.checkout(any(Long.class), any(), any())).thenReturn(response);

        mockMvc.perform(post("/orders/checkout")
                        .principal(authentication("7", "ROLE_TITIPER"))
                        .header("Idempotency-Key", "retry-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "address": "Jl. Mawar No. 1",
                                  "voucherCode": "MILESTONE10",
                                  "items": [
                                    { "productId": "P1", "qty": 1 }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.status").value("PAID"));

        verify(orderService).checkout(any(Long.class), any(), eq("retry-key"));
    }

    @Test
    void checkoutShouldRejectMissingAuthentication() throws Exception {
        mockMvc.perform(post("/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "address": "Jl. Mawar No. 1",
                                  "items": [
                                    { "productId": "P1", "qty": 1 }
                                  ]
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void checkoutShouldRejectWrongRole() throws Exception {
        mockMvc.perform(post("/orders/checkout")
                        .principal(authentication("7", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "address": "Jl. Mawar No. 1",
                                  "items": [
                                    { "productId": "P1", "qty": 1 }
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void myOrdersShouldReturnMappedResponse() throws Exception {
        when(orderService.listMyOrders(7L)).thenReturn(List.of(
                new OrderListItemResponse(
                        5L,
                        7L,
                        2001L,
                        OrderStatus.PAID,
                        new BigDecimal("225000"),
                        Instant.parse("2026-04-16T10:15:30Z"),
                        Instant.parse("2026-04-16T10:16:30Z")
                )
        ));

        mockMvc.perform(get("/orders/my").principal(authentication("7", "ROLE_TITIPER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(5))
                .andExpect(jsonPath("$.data[0].status").value("PAID"));
    }

    @Test
    void myActiveOrdersShouldReturnMappedResponse() throws Exception {
        when(orderService.listMyActiveOrders(7L)).thenReturn(List.of(
                new OrderListItemResponse(
                        5L,
                        7L,
                        2001L,
                        OrderStatus.SHIPPED,
                        new BigDecimal("225000"),
                        Instant.parse("2026-04-16T10:15:30Z"),
                        Instant.parse("2026-04-16T10:16:30Z")
                )
        ));

        mockMvc.perform(get("/orders/my/active").principal(authentication("7", "ROLE_TITIPER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("SHIPPED"));
    }

    @Test
    void detailShouldAllowAdminAccess() throws Exception {
        OrderDetailResponse response = new OrderDetailResponse();
        response.id = 9L;
        response.status = OrderStatus.PAID;
        response.totalPaid = new BigDecimal("4050000");
        response.voucherCode = "MILESTONE10";
        when(orderService.getDetail(9L, 1L, true, false)).thenReturn(response);

        mockMvc.perform(get("/orders/9").principal(authentication("1", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.voucherCode").value("MILESTONE10"));
    }

    @Test
    void checkoutShouldSurfaceValidationErrors() throws Exception {
        mockMvc.perform(post("/orders/checkout")
                        .principal(authentication("7", "ROLE_TITIPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InvalidCheckoutRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0]").exists());
    }

    @Test
    void updateStatusShouldAllowJastiper() throws Exception {
        OrderDetailResponse response = new OrderDetailResponse();
        response.id = 9L;
        response.status = OrderStatus.PURCHASED;
        when(orderService.updateStatus(9L, 2001L, false, OrderStatus.PURCHASED)).thenReturn(response);

        mockMvc.perform(patch("/orders/9/status")
                        .principal(authentication("2001", "ROLE_JASTIPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nextStatus": "PURCHASED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PURCHASED"));
    }

    @Test
    void cancelOrderShouldAllowJastiper() throws Exception {
        OrderDetailResponse response = new OrderDetailResponse();
        response.id = 9L;
        response.status = OrderStatus.CANCELLED;
        when(orderService.cancelOrder(9L, 2001L, false)).thenReturn(response);

        mockMvc.perform(post("/orders/9/cancel")
                        .principal(authentication("2001", "ROLE_JASTIPER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void submitRatingShouldAllowTitiper() throws Exception {
        OrderDetailResponse response = new OrderDetailResponse();
        response.id = 9L;
        response.status = OrderStatus.COMPLETED;
        when(orderService.submitRating(any(Long.class), any(Long.class), any())).thenReturn(response);

        mockMvc.perform(post("/orders/9/rating")
                        .principal(authentication("7", "ROLE_TITIPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productRating": 5,
                                  "jastiperRating": 4,
                                  "comment": "Great service"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    private static UsernamePasswordAuthenticationToken authentication(String userId, String role) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority(role))
        );
    }

    private static final class InvalidCheckoutRequest {
        public String address = "";
        public List<Object> items = List.of();
    }
}
