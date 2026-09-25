package com.midland.bar.Utils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PageableParam {
    private String searchParam;
    private String filter;
    private Integer page;
    private Integer size;
    private String sortBy;
    private Sort.Direction direction;
    private Integer defaultSize = 10;
    private LocalDate date;

    public Pageable pageable(Boolean sorted){
        String sortedByField = sortBy != null ? sortBy : "createdAt";
        Sort.Direction sortDirection = direction != null ? direction : Sort.Direction.DESC;

        Sort sort = sorted ? Sort.by(sortDirection, sortedByField) : Sort.unsorted();

        int pageNumber = (page == null || page < 0) ? 0 : page;
        int pageSize = (size == null || size <= 0) ? defaultSize : size;

        return PageRequest.of(pageNumber, pageSize, sort);
    }


    public String key(){
        return searchParam !=null ? searchParam.toLowerCase(): "";
    }


}
