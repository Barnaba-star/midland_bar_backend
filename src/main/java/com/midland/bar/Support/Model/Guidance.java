package com.midland.bar.Support.Model;

import com.midland.bar.Utils.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A note the platform publishes for its branches to read in POS.
 *
 * Three ways of carrying the same instruction, because one size does not
 * fit the country: words for the thing itself, a video link for anything
 * easier shown than described, and a file for what somebody needs on their
 * phone when the network is not there.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "guidance", indexes = {
        // POS only ever reads the published ones, in order.
        @Index(name = "idx_guidance_published", columnList = "published, position")
})
public class Guidance extends BaseEntity {

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", length = 8000)
    private String body;

    /**
     * A link, not an upload. Uploads are capped at 10MB here, which is a
     * short clip at best - and a branch on a phone should decide for itself
     * when to spend the data on watching it.
     */
    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "file_name", length = 255)
    private String fileName;

    /** Where it landed on disk. Never sent to the client, which asks for the
     *  file by this record's uid instead. */
    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_type", length = 100)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    /**
     * Unpublished is a draft. Something half-written should not be sitting
     * in front of a thousand branches while it is being thought about.
     */
    @Column(name = "published")
    private Boolean published = false;

    /** The order they read in, which is not the order they were written. */
    @Column(name = "position")
    private Integer position = 0;
}
