package com.civicconnect.dto;

import com.civicconnect.model.Comment;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        Long requestId,
        Long authorEmpId,
        String content,
        boolean visibleToRequester,
        LocalDateTime createdAt
) {
    public static CommentResponse from(Comment c) {
        return new CommentResponse(c.getComId(), c.getRequest().getReqId(), c.getEmployee().getEmpId(),
                c.getContent(), c.isVisibleToRequester(), c.getCreatedAt());
    }
}
