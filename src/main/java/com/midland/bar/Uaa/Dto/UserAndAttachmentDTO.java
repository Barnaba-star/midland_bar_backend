package com.midland.bar.Uaa.Dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserAndAttachmentDTO {
    private String uid;
    private ProfilePicDTO profilePicDTO;
    private UserDTO userDTO;
}
