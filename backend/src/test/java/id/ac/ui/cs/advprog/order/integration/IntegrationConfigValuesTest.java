package id.ac.ui.cs.advprog.order.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IntegrationConfigValuesTest {

    @Test
    void sanitizeShouldHandleNullBomAndWhitespace() {
        assertEquals("", IntegrationConfigValues.sanitize(null));
        assertEquals("secret", IntegrationConfigValues.sanitize("\uFEFF secret "));
    }
}
