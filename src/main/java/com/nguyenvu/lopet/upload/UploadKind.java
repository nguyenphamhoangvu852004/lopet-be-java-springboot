package com.nguyenvu.lopet.upload;

public enum UploadKind {
    IMAGE("image"),
    VIDEO("video");

    private final String resourceType;

    UploadKind(String resourceType) {
        this.resourceType = resourceType;
    }

    public String resourceType() {
        return resourceType;
    }
}
