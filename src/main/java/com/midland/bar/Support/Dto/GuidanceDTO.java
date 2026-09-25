package com.midland.bar.Support.Dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A note as it is read or written.
 *
 * The file's path on disk is deliberately absent: the client asks for the
 * file by this record's uid, so where it is kept stays the server's
 * business.
 */
@Getter
@Setter
@NoArgsConstructor
public class GuidanceDTO {

    private String uid;
    private String title;
    private String body;
    private String videoUrl;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Boolean published;
    private Integer position;
    private LocalDate createdAt;
}
