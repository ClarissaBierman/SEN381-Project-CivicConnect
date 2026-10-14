package com.civicconnect.requests.service;

import com.civicconnect.access.service.StaffDirectory;
import com.civicconnect.common.error.Errors;
import com.civicconnect.common.security.CurrentUser;
import com.civicconnect.requests.domain.ServiceRequest;
import com.civicconnect.requests.repository.RequestRepository;
import org.springframework.stereotype.Component;

/** Who may see or change a request (FR-003, FR-005, ASR-001). */
@Component
public class RequestAccess {

    private final RequestRepository requestRepository;
    private final StaffDirectory staff;

    public RequestAccess(RequestRepository requestRepository, StaffDirectory staff) {
        this.requestRepository = requestRepository;
        this.staff = staff;
    }

    public ServiceRequest load(Long id) {
        return requestRepository.findById(id).orElseThrow(() -> Errors.notFound("Request"));
    }

    /** For changes: loads and row-locks the request until the transaction ends (see RequestRepository). */
    public ServiceRequest loadForUpdate(Long id) {
        return requestRepository.findByIdForUpdate(id).orElseThrow(() -> Errors.notFound("Request"));
    }

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

    public void checkStaffScope(ServiceRequest request, CurrentUser user) {
        if (user.isManagement()) {
            return;
        }
        if (!user.isStaff() || !inScope(user.id(), request.getCategory().getCatId())) {
            throw Errors.forbidden("This request is outside your categories.");
        }
    }

    public boolean inScope(Long empId, Long catId) {
        return staff.covers(empId, catId);
    }
}
