package com.midland.bar.Bar.Projection;

/** A bill code and whether an unpaid bill is holding it right now. */
public interface BillCodeProjection {
    String getUid();
    String getCode();
    Boolean getInUse();
}
