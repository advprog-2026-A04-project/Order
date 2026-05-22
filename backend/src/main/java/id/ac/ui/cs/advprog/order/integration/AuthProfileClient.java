package id.ac.ui.cs.advprog.order.integration;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AuthProfileClient {

    private static final Logger log = LoggerFactory.getLogger(AuthProfileClient.class);

    private final RestClient restClient;
    private final String internalToken;

    public AuthProfileClient(
            @Value("${services.auth-profile.base-url}") String baseUrl,
            @Value("${app.internal-token}") String internalToken
    ) {
        this.restClient = RestClient.builder().baseUrl(IntegrationConfigValues.sanitize(baseUrl)).build();
        this.internalToken = IntegrationConfigValues.sanitize(internalToken);
    }

    public void recordJastiperCompletedOrder(Long jastiperId) {
        if (jastiperId == null) {
            return;
        }
        post("/profile/internal/jastipers/{id}/completed-order", jastiperId, Map.of());
    }

    public void recordJastiperRating(Long jastiperId, int rating) {
        if (jastiperId == null) {
            return;
        }
        post("/profile/internal/jastipers/{id}/rating", jastiperId, Map.of("rating", rating));
    }

    private void post(String path, Long jastiperId, Map<String, Object> body) {
        try {
            restClient.post()
                    .uri(path, jastiperId)
                    .header("X-Internal-Token", internalToken)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Auth/Profile reputation update failed for jastiper {} on {}.", jastiperId, path);
        }
    }
}
