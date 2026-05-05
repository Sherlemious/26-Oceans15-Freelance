package com.team26.freelance.user.dto;

import java.util.List;

public class UserActivityFeedDTO {
    private List<UserActivityDTO> content;
    private int page;
    private int size;
    private long totalElements;

    public UserActivityFeedDTO(List<UserActivityDTO> content, int page, int size, long totalElements) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<UserActivityDTO> getContent() { return content; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }

    public static class Builder {
        private List<UserActivityDTO> content;
        private int page;
        private int size;
        private long totalElements;

        public Builder content(List<UserActivityDTO> content) {
            this.content = content;
            return this;
        }

        public Builder page(int page) {
            this.page = page;
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public Builder totalElements(long totalElements) {
            this.totalElements = totalElements;
            return this;
        }

        public UserActivityFeedDTO build() {
            return new UserActivityFeedDTO(content, page, size, totalElements);
        }
    }
}
