package com.seple.ThingsBoard_Bot.exception;

/**
 * Thrown when a request's ThingsBoard customer cannot be resolved to an internal customer
 * prefix (no {@code customers} mapping row, or no customerId claim on the token) while strict
 * customer-mapping is enabled.
 *
 * <p>This is the fail-closed signal that replaces the previous silent fallback to the "BOI"
 * tenant — serving a default tenant's data to an unmapped user is a cross-tenant data leak, so
 * the request is rejected (HTTP 403) instead.
 */
public class UnprovisionedCustomerException extends RuntimeException {

    public UnprovisionedCustomerException(String tbCustomerId) {
        super("No customer mapping is provisioned for ThingsBoard customer '" + tbCustomerId
                + "'. Refusing to serve a default tenant's data.");
    }
}
