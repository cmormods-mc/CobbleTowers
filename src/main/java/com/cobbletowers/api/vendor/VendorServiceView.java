package com.cobbletowers.api.vendor;

import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;

/** One thing the Tower Supply Vendor sells, as it stands for one particular run (TDS #16, #19). */
public interface VendorServiceView {

    ResourceLocation id();

    String displayName();

    int priceCobbleDollars();

    /** Empty when unlimited; otherwise how many more times this run may still buy it. */
    OptionalInt remainingPurchases();
}
