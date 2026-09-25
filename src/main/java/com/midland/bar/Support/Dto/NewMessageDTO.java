package com.midland.bar.Support.Dto;

import lombok.Getter;
import lombok.Setter;

/** What a branch sends when it raises something. */
@Getter
@Setter
public class NewMessageDTO {
    private String category;
    private String subject;
    private String body;
}
