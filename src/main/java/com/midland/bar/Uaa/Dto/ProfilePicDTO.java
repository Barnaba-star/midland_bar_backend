package com.midland.bar.Uaa.Dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfilePicDTO {
    @NotNull(message = "Provide Image Name")
    private String imageName;
    @NotNull(message = "Provide Path")
    private String path;
    @NotNull(message = "Provide Type")
    private String type;
}
