package com.team26.freelance.user.dto;

import java.util.List;

public class ActivityFeedResponseDTO {

    private List<AuthEventDTO> content;
    private int page;
    private int size;
    private long totalElements;

    public ActivityFeedResponseDTO() {
    }

    public ActivityFeedResponseDTO(List<AuthEventDTO> content, int page, int size, long totalElements) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
    }

    public List<AuthEventDTO> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }
}
