package com.midland.bar.Uaa.Dto;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class LoginDTO {
    private String username;
    private String password;
    /** For a user of several branches: the one to work in. Asked for when missing (CHOOSE_BRANCH). */
    private String branchUID;
}
