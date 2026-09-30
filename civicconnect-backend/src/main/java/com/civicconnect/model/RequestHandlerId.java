package com.civicconnect.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RequestHandlerId implements Serializable {

    private Long reqId;
    private Long empId;

    public RequestHandlerId() {}

    public RequestHandlerId(Long reqId, Long empId) {
        this.reqId = reqId;
        this.empId = empId;
    }

    public Long getReqId() { return reqId; }
    public void setReqId(Long reqId) { this.reqId = reqId; }

    public Long getEmpId() { return empId; }
    public void setEmpId(Long empId) { this.empId = empId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RequestHandlerId)) return false;
        RequestHandlerId that = (RequestHandlerId) o;
        return Objects.equals(reqId, that.reqId) && Objects.equals(empId, that.empId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reqId, empId);
    }
}