package com.civicconnect.requests.repository;

import com.civicconnect.requests.domain.Comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("select c from Comment c where c.request.reqId = :reqId order by c.createdAt, c.comId")
    List<Comment> findAllForRequest(@Param("reqId") Long reqId);

    @Query("select c from Comment c where c.request.reqId = :reqId and c.visibleToRequester = true order by c.createdAt, c.comId")
    List<Comment> findVisibleForRequest(@Param("reqId") Long reqId);
}
