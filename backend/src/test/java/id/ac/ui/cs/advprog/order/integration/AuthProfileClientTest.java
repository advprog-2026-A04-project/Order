package id.ac.ui.cs.advprog.order.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import id.ac.ui.cs.advprog.order.common.ApiException;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthProfileClientTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void recordJastiperCompletedOrderShouldCallInternalEndpoint() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        AuthProfileClient client = new AuthProfileClient(server.url("/").toString(), "secret");

        client.recordJastiperCompletedOrder(2001L);

        RecordedRequest request = server.takeRequest();
        assertEquals("/profile/internal/jastipers/2001/completed-order", request.getPath());
        assertEquals("secret", request.getHeader("X-Internal-Token"));
    }

    @Test
    void recordJastiperRatingShouldSendRatingBody() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        AuthProfileClient client = new AuthProfileClient(server.url("/").toString(), "secret");

        client.recordJastiperRating(2001L, 5);

        RecordedRequest request = server.takeRequest();
        assertEquals("/profile/internal/jastipers/2001/rating", request.getPath());
        assertTrue(request.getBody().readUtf8().contains("\"rating\":5"));
    }

    @Test
    void nullJastiperIdShouldSkipRemoteCalls() {
        AuthProfileClient client = new AuthProfileClient(server.url("/").toString(), "secret");

        client.recordJastiperCompletedOrder(null);
        client.recordJastiperRating(null, 5);

        assertEquals(0, server.getRequestCount());
    }

    @Test
    void remoteFailureShouldRaiseApiException() {
        server.enqueue(new MockResponse().setResponseCode(503));
        AuthProfileClient client = new AuthProfileClient(server.url("/").toString(), "secret");

        assertThrows(ApiException.class, () -> client.recordJastiperRating(2001L, 5));
    }
}
