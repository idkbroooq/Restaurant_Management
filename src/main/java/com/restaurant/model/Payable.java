package com.restaurant.model;

import java.math.BigDecimal;

/**
 * // OOP: Interface, Abstraction
 * Marks an entity (e.g. DiningSession) whose total amount is payable.
 * Implemented by service layer objects that can produce a payable total.
 */
public interface Payable {

    /** Returns the gross amount due, including tax and discounts. */
    BigDecimal getAmountDue();

    /** Returns an identifier for the payable entity (e.g. session ID). */
    Long getPayableId();
}
