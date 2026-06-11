package com.seple.ThingsBoard_Bot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.config.SecurityProperties;
import com.seple.ThingsBoard_Bot.exception.UnprovisionedCustomerException;
import com.seple.ThingsBoard_Bot.repository.CustomerRepository;

/**
 * Fail-closed customer-mapping resolution (audit finding #1). Only the dependencies actually
 * touched by {@code resolveCustomerIdPrefix} are mocked; the rest are irrelevant to this path.
 */
class UserDataServiceTest {

    private UserDataService newService(CustomerRepository repo, SecurityProperties security) {
        return new UserDataService(null, null, null, null, repo, null, null, null, null, security);
    }

    @Test
    void strictMapping_unmappedCustomer_throws() {
        CustomerRepository repo = mock(CustomerRepository.class);
        SecurityProperties security = mock(SecurityProperties.class);
        when(security.isStrictCustomerMappingEnabled()).thenReturn(true);

        UserDataService service = newService(repo, security);

        // A null/blank token yields no customerId claim -> unmapped -> must fail closed.
        assertThrows(UnprovisionedCustomerException.class, () -> service.resolveCustomerIdPrefix(null));
    }

    @Test
    void lenientMapping_unmappedCustomer_fallsBackToBoi() {
        CustomerRepository repo = mock(CustomerRepository.class);
        SecurityProperties security = mock(SecurityProperties.class);
        when(security.isStrictCustomerMappingEnabled()).thenReturn(false);

        UserDataService service = newService(repo, security);

        assertEquals("BOI", service.resolveCustomerIdPrefix(null));
    }
}
