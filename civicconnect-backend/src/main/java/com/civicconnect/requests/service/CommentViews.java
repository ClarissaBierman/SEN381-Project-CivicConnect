package com.civicconnect.requests.service;

import com.civicconnect.access.service.StaffDirectory;
import com.civicconnect.common.security.CurrentUser;
import com.civicconnect.requests.domain.Comment;
import com.civicconnect.requests.dto.CommentResponse;
import com.civicconnect.requests.repository.CommentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** The comments a user may see on a request, in the shape for their role (FR-002, FR-010, ASR-005). */
@Component
public class CommentViews {

    private final CommentRepository comments;
    private final StaffDirectory staff;

    public CommentViews(CommentRepository comments, StaffDirectory staff) {
        this.comments = comments;
        this.staff = staff;
    }

    public List<CommentResponse> forRequest(Long reqId, CurrentUser user) {
        if (user.isRequester()) {
            return comments.findVisibleForRequest(reqId).stream().map(CommentResponse::forRequester).toList();
        }
        List<Comment> all = comments.findAllForRequest(reqId);
        return withNames(all);
    }

    public List<CommentResponse> withNames(List<Comment> list) {
        Map<Long, String> names = staff.names(list.stream().map(c -> c.getEmployee().getEmpId()).distinct().toList());
        return list.stream()
                .map(c -> CommentResponse.forStaff(c, names.get(c.getEmployee().getEmpId())))
                .toList();
    }
}
