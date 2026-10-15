package com.civicconnect.access.repository;

import com.civicconnect.access.domain.Employee;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Sets one employee's password hash without loading the row (staff may not read other columns).
     * Clears the persistence context afterwards, so no Employee loaded earlier keeps the old hash.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Employee e set e.password = :hash where e.empId = :id")
    int updatePassword(@Param("id") Long id, @Param("hash") String hash);

    /** Active flag and role only, for the per-request account check (B1). */
    @Query("select e.active as active, e.role as role from Employee e where e.empId = :id")
    Optional<EmployeeStatus> findStatus(@Param("id") Long id);

    /**
     * Names only. Staff may read these columns but not the rest of the employee row (V3 grants),
     * so names are always read through this projection, never by loading the Employee entity.
     */
    @Query("select e.empId as id, e.firstName as firstName, e.surname as surname from Employee e where e.empId in :ids")
    List<EmployeeName> findNames(@Param("ids") Collection<Long> ids);

    /** Active staff who cover a category, for the manager's "assign to" list. */
    @Query("""
            select e.empId as id, e.firstName as firstName, e.surname as surname
            from HandlerCategory h join h.employee e
            where h.category.catId = :catId and e.active = true and e.role = com.civicconnect.access.domain.Employee.Role.STAFF
            order by e.surname, e.firstName""")
    List<EmployeeName> findActiveHandlersFor(@Param("catId") Long catId);

    /** Locks the active managers' rows, so two managers cannot deactivate each other at the same moment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.role = com.civicconnect.access.domain.Employee.Role.MANAGEMENT and e.active = true")
    List<Employee> lockActiveManagers();

    interface EmployeeStatus {
        boolean getActive();
        Employee.Role getRole();
    }

    interface EmployeeName {
        Long getId();
        String getFirstName();
        String getSurname();

        default String fullName() { return getFirstName() + " " + getSurname(); }
    }
}
