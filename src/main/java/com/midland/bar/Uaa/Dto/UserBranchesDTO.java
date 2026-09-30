package com.midland.bar.Uaa.Dto;

import lombok.Data;

import java.util.List;

/** The branches besides their home branch that a user may work in. */
@Data
public class UserBranchesDTO {
    private String userUID;
    private List<String> branchUIDs;
}
