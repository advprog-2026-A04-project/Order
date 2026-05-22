package id.ac.ui.cs.advprog.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import id.ac.ui.cs.advprog.order.common.GlobalExceptionHandler;
import id.ac.ui.cs.advprog.order.dto.OrderDetailResponse;
import id.ac.ui.cs.advprog.order.dto.OrderListItemResponse;
import id.ac.ui.cs.advprog.order.entity.OrderStatus;
import id.ac.ui.cs.advprog.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderControllerTest {
    private final OrderService orderService = mock(OrderService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── POST /orders/checkout ─────────────────────────────────────────────────

    @Nested
    class CheckoutEndpointTests {

        @Test
        void shouldReturnCreatedForTitiper() throws Exception {
            OrderDetailResponse response = paidDetailResponse(5L, "225000");
            when(orderService.checkout(any(Long.class), any(), nullable(String.class))).thenReturn(response);

            mockMvc.perform(post("/orders/checkout")
                            .principal(auth("7", "ROLE_TITIPER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(checkoutBody("P1", 1, "MILESTONE10")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(5))
                    .andExpect(jsonPath("$.data.status").value("PAID"));

            verify(orderService).checkout(eq(7L), any(), nullable(String.class));
        }

        @Test
        void shouldRejectWhenNoAuthentication() throws Exception {
            mockMvc.perform(post("/orders/checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(checkoutBody("P1", 1, null)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }

        @Test
        void shouldRejectWhenRoleIsAdmin() throws Exception {
            mockMvc.perform(post("/orders/checkout")
                            .principal(auth("1", "ROLE_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(checkoutBody("P1", 1, null)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        void shouldRejectWhenRoleIsJastiper() throws Exception {
            mockMvc.perform(post("/orders/checkout")
                            .principal(auth("2001", "ROLE_JASTIPER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(checkoutBody("P1", 1, null)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        void shouldReturn400WhenRequestBodyIsInvalid() throws Exception {
            mockMvc.perform(post("/orders/checkout")
                            .principal(auth("7", "ROLE_TITIPER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"address\":\"\",\"items\":[]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        void shouldSurfaceServiceExceptionAsConflict() throws Exception {
            when(orderService.checkout(any(), any(), nullable(String.class)))
                    .thenThrow(new ApiException(HttpStatus.CONFLICT,
                            ErrorCode.WALLET_INSUFFICIENT, "Wallet balance is insufficient."));

            mockMvc.perform(post("/orders/checkout")
                            .principal(auth("7", "ROLE_TITIPER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(checkoutBody("P1", 1, null)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("WALLET_INSUFFICIENT"));
        }
    }

    // ── GET /orders/my ────────────────────────────────────────────────────────

    @Nested
    class MyOrdersEndpointTests {

        @Test
        void shouldReturnOrderListForTitiper() throws Exception {
            when(orderService.listMyOrders(7L)).thenReturn(List.of(
                    listItemResponse(5L, OrderStatus.PAID, "225000")));

            mockMvc.perform(get("/orders/my")
                            .principal(auth("7", "ROLE_TITIPER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(5))
                    .andExpect(jsonPath("$.data[0].status").value("PAID"));
        }

        @Test
        void shouldRejectAdminFromMyOrders() throws Exception {
            mockMvc.perform(get("/orders/my")
                            .principal(auth("1", "ROLE_ADMIN")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldRejectUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/orders/my"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── GET /orders/my/active ─────────────────────────────────────────────────

    @Nested
    class MyActiveOrdersEndpointTests {

        @Test
        void shouldReturnActiveOrdersForTitiper() throws Exception {
            when(orderService.listActiveOrders(7L)).thenReturn(List.of(
                    listItemResponse(5L, OrderStatus.PAID, "225000"),
                    listItemResponse(6L, OrderStatus.PURCHASED, "100000")));

            mockMvc.perform(get("/orders/my/active")
                            .principal(auth("7", "ROLE_TITIPER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].status").value("PAID"))
                    .andExpect(jsonPath("$.data[1].status").value("PURCHASED"));
        }

        @Test
        void shouldReturnEmptyListWhenNoActiveOrders() throws Exception {
            when(orderService.listActiveOrders(7L)).thenReturn(List.of());

            mockMvc.perform(get("/orders/my/active")
                            .principal(auth("7", "ROLE_TITIPER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void shouldRejectJastiperFromActiveOrders() throws Exception {
            mockMvc.perform(get("/orders/my/active")
                            .principal(auth("2001", "ROLE_JASTIPER")))
                    .andExpect(status().isForbidden());
        }
    }

    // ── GET /orders/jastiper ──────────────────────────────────────────────────

    @Nested
    class JastiperOrdersEndpointTests {

        @Test
        void shouldReturnOrdersForJastiper() throws Exception {
            when(orderService.listJastiperOrders(2001L)).thenReturn(List.of(
                    listItemResponse(3L, OrderStatus.PAID, "300000"),
                    listItemResponse(4L, OrderStatus.PURCHASED, "200000")));

            mockMvc.perform(get("/orders/jastiper")
                            .principal(auth("2001", "ROLE_JASTIPER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        void shouldRejectTitiperFromJastiperEndpoint() throws Exception {
            mockMvc.perform(get("/orders/jastiper")
                            .principal(auth("7", "ROLE_TITIPER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldRejectUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/orders/jastiper"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── GET /orders/admin ─────────────────────────────────────────────────────

    @Nested
    class AdminOrdersEndpointTests {

        @Test
        void shouldReturnAllOrdersForAdmin() throws Exception {
            when(orderService.listAdminOrders()).thenReturn(List.of(
                    listItemResponse(1L, OrderStatus.PAID, "100000"),
                    listItemResponse(2L, OrderStatus.COMPLETED, "200000"),
                    listItemResponse(3L, OrderStatus.CANCELLED, "150000")));

            mockMvc.perform(get("/orders/admin")
                            .principal(auth("1", "ROLE_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(3));
        }

        @Test
        void shouldRejectTitiperFromAdminEndpoint() throws Exception {
            mockMvc.perform(get("/orders/admin")
                            .principal(auth("7", "ROLE_TITIPER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldRejectJastiperFromAdminEndpoint() throws Exception {
            mockMvc.perform(get("/orders/admin")
                            .principal(auth("2001", "ROLE_JASTIPER")))
                    .andExpect(status().isForbidden());
        }
    }

    // ── GET /orders/{id} ──────────────────────────────────────────────────────

    @Nested
    class DetailEndpointTests {

        @Test
        void shouldAllowTitiperToReadOwnOrder() throws Exception {
            OrderDetailResponse response = paidDetailResponse(9L, "225000");
            response.voucherCode = "MILESTONE10";
            when(orderService.getDetail(9L, 7L, false)).thenReturn(response);

            mockMvc.perform(get("/orders/9")
                            .principal(auth("7", "ROLE_TITIPER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(9))
                    .andExpect(jsonPath("$.data.voucherCode").value("MILESTONE10"));
        }

        @Test
        void shouldAllowAdminToReadAnyOrder() throws Exception {
            OrderDetailResponse response = paidDetailResponse(9L, "225000");
            when(orderService.getDetail(9L, 1L, true)).thenReturn(response);

            mockMvc.perform(get("/orders/9")
                            .principal(auth("1", "ROLE_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(9));
        }

        @Test
        void shouldAllowJastiperToReadOrder() throws Exception {
            OrderDetailResponse response = paidDetailResponse(9L, "225000");
            when(orderService.getDetail(9L, 2001L, false)).thenReturn(response);

            mockMvc.perform(get("/orders/9")
                            .principal(auth("2001", "ROLE_JASTIPER")))
                    .andExpect(status().isOk());
        }

        @Test
        void shouldReturn404WhenOrderNotFound() throws Exception {
            when(orderService.getDetail(eq(99L), any(), anyBoolean()))
                    .thenThrow(new ApiException(HttpStatus.NOT_FOUND,
                            ErrorCode.ORDER_NOT_FOUND, "Order not found."));

            mockMvc.perform(get("/orders/99")
                            .principal(auth("7", "ROLE_TITIPER")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));
        }

        @Test
        void shouldReturn403WhenBuyerAccessesDifferentOrder() throws Exception {
            when(orderService.getDetail(eq(9L), eq(7L), eq(false)))
                    .thenThrow(new ApiException(HttpStatus.FORBIDDEN,
                            ErrorCode.FORBIDDEN, "You do not have access."));

            mockMvc.perform(get("/orders/9")
                            .principal(auth("7", "ROLE_TITIPER")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
    }

    // ── PATCH /orders/{id}/status ─────────────────────────────────────────────

    @Nested
    class UpdateStatusEndpointTests {

        @Test
        void shouldAllowJastiperToAdvanceStatus() throws Exception {
            OrderDetailResponse response = paidDetailResponse(1L, "100000");
            response.status = OrderStatus.PURCHASED;
            when(orderService.updateStatus(eq(1L), eq(2001L),
                    eq(false), eq(true), eq(OrderStatus.PURCHASED)))
                    .thenReturn(response);

            mockMvc.perform(patch("/orders/1/status")
                            .principal(auth("2001", "ROLE_JASTIPER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nextStatus\":\"PURCHASED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PURCHASED"));
        }

        @Test
        void shouldAllowAdminToAdvanceStatus() throws Exception {
            OrderDetailResponse response = paidDetailResponse(1L, "100000");
            response.status = OrderStatus.PURCHASED;
            when(orderService.updateStatus(eq(1L), eq(1L),
                    eq(true), eq(false), eq(OrderStatus.PURCHASED)))
                    .thenReturn(response);

            mockMvc.perform(patch("/orders/1/status")
                            .principal(auth("1", "ROLE_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nextStatus\":\"PURCHASED\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void shouldAllowTitiperToConfirmCompleted() throws Exception {
            OrderDetailResponse response = paidDetailResponse(1L, "100000");
            response.status = OrderStatus.COMPLETED;
            when(orderService.updateStatus(eq(1L), eq(7L),
                    eq(false), eq(false), eq(OrderStatus.COMPLETED)))
                    .thenReturn(response);

            mockMvc.perform(patch("/orders/1/status")
                            .principal(auth("7", "ROLE_TITIPER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nextStatus\":\"COMPLETED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        }

        @Test
        void shouldReturn409WhenTransitionIsInvalid() throws Exception {
            when(orderService.updateStatus(any(), any(), anyBoolean(), anyBoolean(), any()))
                    .thenThrow(new ApiException(HttpStatus.CONFLICT,
                            ErrorCode.INVALID_STATUS_TRANSITION,
                            "Invalid status transition."));

            mockMvc.perform(patch("/orders/1/status")
                            .principal(auth("2001", "ROLE_JASTIPER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nextStatus\":\"COMPLETED\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));
        }

        @Test
        void shouldRejectUnauthenticatedRequest() throws Exception {
            mockMvc.perform(patch("/orders/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nextStatus\":\"PURCHASED\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── POST /orders/{id}/cancel ──────────────────────────────────────────────

    @Nested
    class CancelEndpointTests {

        @Test
        void shouldAllowJastiperToCancel() throws Exception {
            OrderDetailResponse response = paidDetailResponse(1L, "100000");
            response.status = OrderStatus.CANCELLED;
            response.refundDone = true;
            when(orderService.cancel(eq(1L), eq(2001L), eq(false), eq(true)))
                    .thenReturn(response);

            mockMvc.perform(post("/orders/1/cancel")
                            .principal(auth("2001", "ROLE_JASTIPER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.data.refundDone").value(true));
        }

        @Test
        void shouldAllowAdminToCancel() throws Exception {
            OrderDetailResponse response = paidDetailResponse(1L, "100000");
            response.status = OrderStatus.CANCELLED;
            when(orderService.cancel(eq(1L), eq(1L), eq(true), eq(false)))
                    .thenReturn(response);

            mockMvc.perform(post("/orders/1/cancel")
                            .principal(auth("1", "ROLE_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        }

        @Test
        void shouldRejectTitiperFromCancelling() throws Exception {
            mockMvc.perform(post("/orders/1/cancel")
                            .principal(auth("7", "ROLE_TITIPER")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        void shouldReturn409WhenCancelNotAllowed() throws Exception {
            when(orderService.cancel(any(), any(), anyBoolean(), anyBoolean()))
                    .thenThrow(new ApiException(HttpStatus.CONFLICT,
                            ErrorCode.CANCEL_NOT_ALLOWED,
                            "Order cannot be cancelled at this stage."));

            mockMvc.perform(post("/orders/1/cancel")
                            .principal(auth("2001", "ROLE_JASTIPER")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CANCEL_NOT_ALLOWED"));
        }

        @Test
        void shouldRejectUnauthenticatedRequest() throws Exception {
            mockMvc.perform(post("/orders/1/cancel"))
                    .andExpect(status().isUnauthorized());
        }
    }
    
    @Nested
    class RatingEndpointTests {

        @Test
        void shouldAllowTitiperToSubmitRating() throws Exception {
            OrderDetailResponse response = paidDetailResponse(1L, "100000");
            response.status = OrderStatus.COMPLETED;
            when(orderService.rate(eq(1L), eq(7L), any())).thenReturn(response);

            mockMvc.perform(post("/orders/1/rating")
                            .principal(auth("7", "ROLE_TITIPER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productRating\":5,\"jastiperRating\":4,\"comment\":\"Mantap!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(orderService).rate(eq(1L), eq(7L), any());
        }

        @Test
        void shouldRejectJastiperFromSubmittingRating() throws Exception {
            mockMvc.perform(post("/orders/1/rating")
                            .principal(auth("2001", "ROLE_JASTIPER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productRating\":5,\"jastiperRating\":4}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldRejectAdminFromSubmittingRating() throws Exception {
            mockMvc.perform(post("/orders/1/rating")
                            .principal(auth("1", "ROLE_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productRating\":5,\"jastiperRating\":4}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn409WhenOrderNotCompleted() throws Exception {
            when(orderService.rate(any(), any(), any()))
                    .thenThrow(new ApiException(HttpStatus.CONFLICT,
                            ErrorCode.RATING_ONLY_WHEN_COMPLETED,
                            "Rating is only allowed after the order is completed."));

            mockMvc.perform(post("/orders/1/rating")
                            .principal(auth("7", "ROLE_TITIPER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productRating\":5,\"jastiperRating\":4}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("RATING_ONLY_WHEN_COMPLETED"));
        }

        @Test
        void shouldReturn409WhenRatingAlreadyExists() throws Exception {
            when(orderService.rate(any(), any(), any()))
                    .thenThrow(new ApiException(HttpStatus.CONFLICT,
                            ErrorCode.RATING_ALREADY_EXISTS,
                            "Rating already exists."));

            mockMvc.perform(post("/orders/1/rating")
                            .principal(auth("7", "ROLE_TITIPER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productRating\":5,\"jastiperRating\":4}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("RATING_ALREADY_EXISTS"));
        }

        @Test
        void shouldRejectUnauthenticatedRequest() throws Exception {
            mockMvc.perform(post("/orders/1/rating")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productRating\":5,\"jastiperRating\":4}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private static UsernamePasswordAuthenticationToken auth(String userId, String role) {
        return new UsernamePasswordAuthenticationToken(
                userId, null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    private static OrderDetailResponse paidDetailResponse(Long id, String totalPaid) {
        OrderDetailResponse r = new OrderDetailResponse();
        r.id = id;
        r.status = OrderStatus.PAID;
        r.totalPaid = new BigDecimal(totalPaid);
        r.buyerId = 7L;
        r.items = List.of();
        return r;
    }

    private static OrderListItemResponse listItemResponse(Long id, OrderStatus status,
                                                           String totalPaid) {
        return new OrderListItemResponse(
                id, status, new BigDecimal(totalPaid),
                Instant.parse("2026-04-16T10:15:30Z"),
                null, false, List.of());
    }

    private static String checkoutBody(String productId, int qty, String voucherCode) {
        String voucher = voucherCode != null ? "\"" + voucherCode + "\"" : "null";
        return """
                {
                  "address": "Jl. Mawar No. 1",
                  "voucherCode": %s,
                  "items": [{ "productId": "%s", "qty": %d }]
                }
                """.formatted(voucher, productId, qty);
    }
}