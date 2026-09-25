package com.midland.bar.Utils.Responses;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ResponsePage<T> {

    private List<T> data;
    private String message;
    private ResponseStatus status;
    private long totalElements;
    private int totalPages;
    private int size;
    private int currentPage;
    private boolean last;
    private boolean first;


    public ResponsePage(Page<T> page) {
        this.data = page.getContent();
        this.status = ResponseStatus.SUCCESS;
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.size = page.getSize();
        this.currentPage = page.getNumber();
        this.first = page.isFirst();
        this.last = page.isLast();
    }

    public ResponsePage(Page<T> page, String message) {
        this(page);
        this.message = message;
    }

    public ResponsePage(Page<T> page, String message, ResponseStatus status) {
        this(page);
        this.message = message;
        this.status = status;
    }

    public ResponsePage(String message) {
        this.message = message;
    }
}

