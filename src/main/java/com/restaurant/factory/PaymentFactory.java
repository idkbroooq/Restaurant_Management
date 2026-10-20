package com.restaurant.factory;

import com.restaurant.enums.PaymentMethodType;
import com.restaurant.strategy.CardPayment;
import com.restaurant.strategy.CashPayment;
import com.restaurant.strategy.PaymentMethod;
import com.restaurant.strategy.UpiPayment;
import org.springframework.stereotype.Component;

/**
 * // OOP: Factory Pattern, Abstraction, Polymorphism
 * Creates the correct PaymentMethod strategy at runtime based on the
 * PaymentMethodType enum chosen by the customer.
 *
 * Encapsulates object creation so callers only know about the PaymentMethod interface.
 */
@Component // Spring-managed so it can be injected
public class PaymentFactory {

    /**
     * Produces a PaymentMethod implementation for the given type.
     *
     * @param type the chosen payment channel
     * @return concrete PaymentMethod strategy
     * @throws IllegalArgumentException for unknown types
     */
    public PaymentMethod create(PaymentMethodType type) {
        // OOP: Polymorphism – caller receives PaymentMethod interface; impl is hidden
        return switch (type) {
            case CASH -> new CashPayment();
            case UPI  -> new UpiPayment();
            case CARD -> new CardPayment();
        };
    }
}
