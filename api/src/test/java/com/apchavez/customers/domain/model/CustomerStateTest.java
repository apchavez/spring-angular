package com.apchavez.customers.domain.model;

import com.apchavez.customers.domain.exception.ClienteDominioInvalidoException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerStateTest {

    @Test
    void fromString_active_returnsActive() {
        assertThat(CustomerState.fromString("ACTIVE")).isEqualTo(CustomerState.ACTIVE);
    }

    @Test
    void fromString_inactive_returnsInactive() {
        assertThat(CustomerState.fromString("INACTIVE")).isEqualTo(CustomerState.INACTIVE);
    }

    @Test
    void fromString_isCaseInsensitive() {
        assertThat(CustomerState.fromString("active")).isEqualTo(CustomerState.ACTIVE);
        assertThat(CustomerState.fromString("InAcTiVe")).isEqualTo(CustomerState.INACTIVE);
    }

    @Test
    void fromString_null_throwsDomainException() {
        assertThatThrownBy(() -> CustomerState.fromString(null))
                .isInstanceOf(ClienteDominioInvalidoException.class)
                .hasMessage("El estado debe ser 'ACTIVE' o 'INACTIVE'");
    }

    @Test
    void fromString_unknownValue_throwsDomainException() {
        assertThatThrownBy(() -> CustomerState.fromString("BOGUS"))
                .isInstanceOf(ClienteDominioInvalidoException.class)
                .hasMessage("El estado debe ser 'ACTIVE' o 'INACTIVE'");
    }
}
