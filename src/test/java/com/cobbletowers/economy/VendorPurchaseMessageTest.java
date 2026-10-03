package com.cobbletowers.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.economy.VendorPurchaseService.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A refused purchase must say why, and every outcome must have a sentence (P19). */
class VendorPurchaseMessageTest {

    @Test
    @DisplayName("every outcome has a non-empty message, so a new Result cannot ship silent")
    void everyResultHasAMessage() {
        for (Result result : Result.values()) {
            String message = VendorPurchaseService.describe(result, "Ash", false);
            assertFalse(message.isBlank(), result + " has no message");
        }
    }

    @Test
    @DisplayName("a purchase for a teammate names them; one for yourself does not")
    void namesTheTarget() {
        assertEquals("Bought for Ash.", VendorPurchaseService.describe(Result.SUCCESS, "Ash", false));
        assertEquals("Bought for yourself.", VendorPurchaseService.describe(Result.SUCCESS, "Ash", true));
    }

    @Test
    @DisplayName("an offline or departed teammate is named in the refusal")
    void refusalsNameTheTeammate() {
        assertTrue(VendorPurchaseService.describe(Result.TARGET_OFFLINE, "Ash", false).contains("Ash"));
        assertTrue(VendorPurchaseService.describe(Result.TARGET_NOT_IN_RUN, "Ash", false).contains("Ash"));
    }
}
