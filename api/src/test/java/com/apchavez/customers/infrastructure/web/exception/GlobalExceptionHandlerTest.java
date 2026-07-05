package com.apchavez.customers.infrastructure.web.exception;

import com.apchavez.customers.domain.exception.ClienteDominioInvalidoException;
import com.apchavez.customers.domain.exception.ClienteNoEncontradoException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler} — exercises every exception-to-HTTP-response
 * mapping branch directly, independent of the web stack that would normally trigger them.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ── ClienteNoEncontradoException → 404 ───────────────────────────────────

    @Test
    void handleNoEncontrado_returns404_withMessage() {
        ClienteNoEncontradoException ex = new ClienteNoEncontradoException(42);

        ResponseEntity<ErrorResponse> response = handler.handleNoEncontrado(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("Not Found");
        assertThat(response.getBody().mensaje()).contains("42");
    }

    // ── ClienteDominioInvalidoException → 422 ────────────────────────────────

    @Test
    void handleInvalido_returns422_withDomainMessage() {
        ClienteDominioInvalidoException ex =
                new ClienteDominioInvalidoException("La edad debe ser mayor que cero");

        ResponseEntity<ErrorResponse> response = handler.handleInvalido(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(422);
        assertThat(response.getBody().error()).isEqualTo("Unprocessable Entity");
        assertThat(response.getBody().mensaje()).isEqualTo("La edad debe ser mayor que cero");
    }

    // ── WebExchangeBindException → 400 with field errors ─────────────────────

    @Test
    void handleValidation_returns400_withFieldErrors() {
        WebExchangeBindException ex = mock(WebExchangeBindException.class);
        org.springframework.validation.BindingResult bindingResult =
                mock(org.springframework.validation.BindingResult.class);
        org.springframework.validation.FieldError fieldError =
                new org.springframework.validation.FieldError("dto", "nombre", "El nombre es requerido");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errores()).hasSize(1);
        assertThat(response.getBody().errores().get(0).campo()).isEqualTo("nombre");
        assertThat(response.getBody().errores().get(0).mensaje()).isEqualTo("El nombre es requerido");
    }

    // ── HandlerMethodValidationException → 400 with field errors ─────────────

    @Test
    void handleMethodValidation_returns400_withFieldErrors() throws NoSuchMethodException {
        Method targetMethod = SampleTarget.class.getMethod("someMethod", Integer.class);
        MethodParameter parameter = new MethodParameter(targetMethod, 0);
        DefaultMessageSourceResolvable error =
                new DefaultMessageSourceResolvable(new String[]{"id.invalid"}, "El ID debe ser mayor que cero");
        ParameterValidationResult parameterResult =
                new ParameterValidationResult(parameter, -1, List.of(error), null, null, null, null);
        MethodValidationResult validationResult =
                MethodValidationResult.create(new SampleTarget(), targetMethod, List.of(parameterResult));
        HandlerMethodValidationException ex = new HandlerMethodValidationException(validationResult);

        ResponseEntity<ErrorResponse> response = handler.handleMethodValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errores()).hasSize(1);
        assertThat(response.getBody().errores().get(0).campo()).isEqualTo("someMethod");
        assertThat(response.getBody().errores().get(0).mensaje()).isEqualTo("El ID debe ser mayor que cero");
    }

    static class SampleTarget {
        public void someMethod(Integer id) {
            // used only as a reflection target for building a HandlerMethodValidationException
        }
    }

    // ── ConstraintViolationException → 400 with field errors ─────────────────

    @Test
    void handleConstraintViolation_returns400_withFieldErrors() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("findById.id");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("El ID debe ser mayor que cero");
        ConstraintViolationException ex =
                new ConstraintViolationException("Violación de constraint", Set.of(violation));

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errores()).hasSize(1);
        assertThat(response.getBody().errores().get(0).campo()).isEqualTo("findById.id");
        assertThat(response.getBody().errores().get(0).mensaje()).isEqualTo("El ID debe ser mayor que cero");
    }

    // ── Generic Exception → 500, without leaking internal details ────────────

    @Test
    void handleGeneric_returns500_withoutLeakingExceptionDetails() {
        RuntimeException ex = new RuntimeException("Connection refused: could not connect to db at 10.0.0.5:5432");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().error()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().mensaje())
                .isEqualTo("Error interno del servidor")
                .doesNotContain("10.0.0.5");
    }
}
