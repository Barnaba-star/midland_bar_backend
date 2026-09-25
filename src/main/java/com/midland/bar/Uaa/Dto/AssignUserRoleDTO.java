package com.midland.bar.Uaa.Dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AssignUserRoleDTO {
    private String userUID;
    private List<String> roleUIDS;
}
