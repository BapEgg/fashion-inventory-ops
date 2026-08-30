package com.bapegg.stockpilot.explanation;

import com.bapegg.stockpilot.analysis.InventoryExceptionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The AI boundary (business-rules.md section 7, AGENTS.md): AI is optional and must
 * never block the deterministic API. Each disabled/unconfigured/unimplemented state is
 * reported explicitly rather than as an error, and an invalid or non-actionable id still
 * 404s the same way the exception detail endpoint does.
 */
class ExplanationServiceTest {

    private final InventoryExceptionService inventoryExceptionService = mock(InventoryExceptionService.class);

    @Test
    void reportsUnavailableWhenAiIsDisabled() {
        ExplanationService service = new ExplanationService(
                inventoryExceptionService, new AiProperties(false, "", "", "", ""));

        ExplanationResponse response = service.explain(1L);

        assertFalse(response.available());
        assertEquals("AI_DISABLED", response.reason());
        assertNull(response.explanation());
        verify(inventoryExceptionService).getExceptionDetail(1L);
    }

    @Test
    void reportsUnavailableWhenAiIsEnabledButUnconfigured() {
        ExplanationService service = new ExplanationService(
                inventoryExceptionService, new AiProperties(true, "openai", "", "key", "gpt"));

        ExplanationResponse response = service.explain(1L);

        assertFalse(response.available());
        assertEquals("AI_UNCONFIGURED", response.reason());
    }

    @Test
    void reportsProviderNotImplementedWhenAiIsEnabledAndFullyConfigured() {
        ExplanationService service = new ExplanationService(
                inventoryExceptionService,
                new AiProperties(true, "openai", "https://example.invalid", "key", "gpt"));

        ExplanationResponse response = service.explain(1L);

        assertFalse(response.available());
        assertEquals("AI_PROVIDER_NOT_IMPLEMENTED", response.reason());
    }

    @Test
    void propagatesNotFoundForAnInvalidOrNonActionableId() {
        ExplanationService service = new ExplanationService(
                inventoryExceptionService, new AiProperties(false, "", "", "", ""));
        when(inventoryExceptionService.getExceptionDetail(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No inventory exception found for id 99"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.explain(99L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
