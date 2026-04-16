package id.ac.ui.cs.advprog.order.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    @Test
    void corsConfigurationShouldSplitAllowedOrigins() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", "http://localhost:5173,https://example.com");

        CorsConfigurationSource source = config.corsConfigurationSource();
        CorsConfiguration corsConfiguration = ((UrlBasedCorsConfigurationSource) source)
                .getCorsConfigurations()
                .get("/**");

        assertEquals(2, corsConfiguration.getAllowedOrigins().size());
        assertTrue(corsConfiguration.getAllowedMethods().contains("PATCH"));
        assertFalse(Boolean.TRUE.equals(corsConfiguration.getAllowCredentials()));
    }
}
