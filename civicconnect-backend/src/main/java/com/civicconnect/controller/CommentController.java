package com.civicconnect.controller;

import com.civicconnect.dto.CommentResponse;
import com.civicconnect.dto.NewComment;
import com.civicconnect.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** API-10 comments on a request (FR-010). Append-only: no PATCH or DELETE. */
@RestController
@RequestMapping("/api/v1/requests/{id}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGEMENT')")
    public CommentResponse add(@PathVariable Long id, @Valid @RequestBody NewComment body) {
        return commentService.add(id, body);
    }

    /** Requesters get only comments marked visible to them. */
    @GetMapping
    public List<CommentResponse> list(@PathVariable Long id) {
        return commentService.list(id);
    }
}
