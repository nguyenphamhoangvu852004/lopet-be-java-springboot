package com.nguyenvu.lopet.upload;

import org.springframework.web.multipart.MultipartFile;

public record Videos(MultipartFile... files) {
}
