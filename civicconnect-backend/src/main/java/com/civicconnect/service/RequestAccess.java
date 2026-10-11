package com.civicconnect.service;

import com.civicconnect.model.ServiceRequest;
import com.civicconnect.repository.HandlerCategoryRepository;
import com.civicconnect.repository.RequestRepository;
import com.civicconnect.security.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * Object-level access checks for requests (ASR-001, FR-003, FR-005).
 * Requesters only see their own requests (others look like "not found"), staff only see requests
 * in their categories, and managers see everything.
 */
@Component
public class RequestAccess {

    private final RequestRepository requestRepository;
    private final HandlerCategoryRepository handlerCategoryRepository;

    public RequestAccess(RequestRepository requestRepository, HandlerCategoryRepository handlerCategoryRepository) {
        this.requestRepository = requestRepository;
        this.handlerCategoryRepository = handlerCategoryRepository;
    }

    public ServiceRequest load(Long id) {
        return requestRepository.findById(id).orElseThrow(() -> Errors.notFound("Request"));
    }

    /** Loads a request the user may view, or throws 404 / 403. */
    public ServiceRequest loadViewable(Long id, CurrentUser user) {
        ServiceRequest request = load(id);
        if (user.isRequester()) {
            if (!request.getRequester().getClientId().equals(user.id())) {
                throw Errors.notFound("Request");
            }
        } else {
            checkStaffScope(request, user);
        }
        return request;
    }

    /** Staff must cover the request's category; managers always pass; requesters never do. */
    public void checkStaffScope(ServiceRequest request, CurrentUser user) {
        if (user.isManagement()) {
            return;
        }
        if (!user.isStaff() || !inScope(user.id(), request.getCategory().getCatId())) {
            throw Errors.forbidden("This request is outside your categories.");
        }
    }

    public boolean inScope(Long empId, Long catId) {
        return handlerCategoryRepository.findCategoryIds(empId).contains(catId);
    }
}
