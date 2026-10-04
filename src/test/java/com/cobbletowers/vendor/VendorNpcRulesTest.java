package com.cobbletowers.vendor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VendorNpcRulesTest {

    private static final UUID RUN = UUID.randomUUID();

    @Test
    void aTagRoundTripsItsRun() {
        assertEquals(Optional.of(RUN), VendorNpcRules.runOf(VendorNpcRules.runTag(RUN)));
    }

    @Test
    void otherTagsAndMalformedIdsNameNoRun() {
        assertTrue(VendorNpcRules.runOf("cobbletowers_vendor").isEmpty());
        assertTrue(VendorNpcRules.runOf("something_else").isEmpty());
        assertTrue(VendorNpcRules.runOf("cobbletowers_vendor_run:not-a-uuid").isEmpty());
    }

    @Test
    void aParticipantMayUseTheVendorAtAnIntermission() {
        assertEquals(VendorNpcRules.Access.ALLOWED, VendorNpcRules.access(RUN, Optional.of(RUN), true));
    }

    @Test
    void anOutsiderOrAnotherTeamIsRefused() {
        assertEquals(VendorNpcRules.Access.NOT_IN_THIS_RUN, VendorNpcRules.access(RUN, Optional.empty(), true));
        assertEquals(VendorNpcRules.Access.NOT_IN_THIS_RUN,
                VendorNpcRules.access(RUN, Optional.of(UUID.randomUUID()), true));
    }

    @Test
    void theVendorIsClosedOutsideAnIntermission() {
        assertEquals(VendorNpcRules.Access.CLOSED, VendorNpcRules.access(RUN, Optional.of(RUN), false));
    }

    @Test
    void everyRefusalSaysSomething() {
        assertEquals("", VendorNpcRules.describe(VendorNpcRules.Access.ALLOWED));
        assertTrue(!VendorNpcRules.describe(VendorNpcRules.Access.CLOSED).isEmpty());
        assertTrue(!VendorNpcRules.describe(VendorNpcRules.Access.NOT_IN_THIS_RUN).isEmpty());
    }

    @Test
    void yawFacesTheTarget() {
        assertEquals(0f, VendorNpcRules.yawToward(0, 0, 0, 5), 0.01f);     // south
        assertEquals(180f, Math.abs(VendorNpcRules.yawToward(0, 0, 0, -5)), 0.01f); // north
        assertEquals(-90f, VendorNpcRules.yawToward(0, 0, 5, 0), 0.01f);   // east
        assertEquals(90f, VendorNpcRules.yawToward(0, 0, -5, 0), 0.01f);   // west
    }
}
