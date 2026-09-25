package com.midland.bar.Payment.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One person owed a share of the month, and what they are owed. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShareRecipientDTO {
    private String uid;
    private String name;
    private String username;

    /** What the month earned them. */
    private long amount;

    /** Only staff payouts are tracked today; the rest report zero paid. */
    private long paid;
    private long outstanding;
}
