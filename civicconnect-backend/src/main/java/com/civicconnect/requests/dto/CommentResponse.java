package com.civicconnect.requests.dto;

import com.civicconnect.common.time.Time;
import com.civicconnect.requests.domain.Comment;

import java.time.Instant;

/**
 * A comment. Staff and managers see who wrote it; requesters see "CivicConnect" and no employee ID,
 * because staff names are not shown to the public (ASR-005).
 */
public record CommentResponse(
        Long id,
        Long requestId,
        Long authorEmpId,
        String authorName,
        String content,
        boolean visibleToRequester,
        Instant createdAt
) {
    public static final String PUBLIC_AUTHOR = "CivicConnect";

    public static CommentResponse forStaff(Comment c, String authorName) {
        return new CommentResponse(c.getComId(), c.getRequest().getReqId(), c.getEmployee().getEmpId(),
                authorName, c.getContent(), c.isVisibleToRequester(), Time.toInstant(c.getCreatedAt()));
    }

    public static CommentResponse forRequester(Comment c) {
        return new CommentResponse(c.getComId(), c.getRequest().getReqId(), null, PUBLIC_AUTHOR,
                c.getContent(), true, Time.toInstant(c.getCreatedAt()));
    }
}
