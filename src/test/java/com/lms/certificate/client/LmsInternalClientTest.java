package com.lms.certificate.client;

import com.lms.certificate.common.exception.LmsBackendUnavailableException;
import com.lms.certificate.config.InternalConfig;
import com.lms.certificate.config.LmsBackendConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LmsInternalClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final LmsInternalClient client = new LmsInternalClient(restTemplate, lmsConfig(), internalConfig());

    @Test
    void rejectsAnEmptySuccessfulResponseInsteadOfReturningNull() {
        ResponseEntity<LmsInternalClient.UserDto> emptyResponse = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), ArgumentMatchers.<HttpEntity<?>>any(),
                eq(LmsInternalClient.UserDto.class))).thenReturn(emptyResponse);

        assertThatThrownBy(() -> client.getUser(UUID.randomUUID()))
                .isInstanceOf(LmsBackendUnavailableException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    void wrapsTransportFailuresAsAnLmsDependencyFailure() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), ArgumentMatchers.<HttpEntity<?>>any(),
                eq(LmsInternalClient.UserDto.class))).thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> client.getUser(UUID.randomUUID()))
                .isInstanceOf(LmsBackendUnavailableException.class)
                .hasMessageContaining("unavailable")
                .hasCauseInstanceOf(ResourceAccessException.class);
    }

    private static LmsBackendConfig lmsConfig() {
        LmsBackendConfig config = new LmsBackendConfig();
        config.setBaseUrl("http://lms-backend.test");
        return config;
    }

    private static InternalConfig internalConfig() {
        InternalConfig config = new InternalConfig();
        config.setServiceKey("service-key");
        return config;
    }
}
