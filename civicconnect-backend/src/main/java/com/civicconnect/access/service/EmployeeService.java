package com.civicconnect.access.service;

import com.civicconnect.access.domain.Employee;
import com.civicconnect.access.domain.HandlerCategory;
import com.civicconnect.access.dto.EmployeePatch;
import com.civicconnect.access.dto.EmployeeResponse;
import com.civicconnect.access.dto.HandlerCategoriesUpdate;
import com.civicconnect.access.dto.HandlerOption;
import com.civicconnect.access.dto.NewEmployee;
import com.civicconnect.access.event.StaffMemberRemovedEvent;
import com.civicconnect.access.repository.ClientRepository;
import com.civicconnect.access.repository.EmployeeRepository;
import com.civicconnect.access.repository.HandlerCategoryRepository;
import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.common.error.Errors;
import com.civicconnect.common.security.CurrentUser;
import com.civicconnect.common.security.CurrentUserService;
import com.civicconnect.common.security.DbSession;
import com.civicconnect.requests.domain.Category;
import com.civicconnect.requests.dto.CategoryResponse;
import com.civicconnect.requests.repository.CategoryRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final ClientRepository clientRepository;
    private final CategoryRepository categoryRepository;
    private final HandlerCategoryRepository handlerCategoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUser;
    private final DbSession db;
    private final StaffDirectory staffDirectory;
    private final ApplicationEventPublisher events;

    public EmployeeService(EmployeeRepository employeeRepository, ClientRepository clientRepository,
                           CategoryRepository categoryRepository, HandlerCategoryRepository handlerCategoryRepository,
                           PasswordEncoder passwordEncoder, CurrentUserService currentUser, DbSession db,
                           StaffDirectory staffDirectory, ApplicationEventPublisher events) {
        this.employeeRepository = employeeRepository;
        this.clientRepository = clientRepository;
        this.categoryRepository = categoryRepository;
        this.handlerCategoryRepository = handlerCategoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.db = db;
        this.staffDirectory = staffDirectory;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> list(Pageable pageable) {
        db.begin(currentUser.get());
        return employeeRepository.findAll(pageable).map(EmployeeResponse::from);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long id) {
        db.begin(currentUser.get());
        return EmployeeResponse.from(load(id));
    }

    @Transactional
    public EmployeeResponse create(NewEmployee body) {
        db.begin(currentUser.get());
        String email = body.email().trim().toLowerCase(Locale.ROOT);
        checkEmailFree(email);

        Employee e = new Employee();
        e.setFirstName(body.firstName().trim());
        e.setSurname(body.surname().trim());
        e.setEmail(email);
        e.setPassword(passwordEncoder.encode(body.password()));
        e.setPhone(body.phone());
        e.setRole(body.role());
        e.setDateOfBirth(body.dateOfBirth());
        e.setStreet(body.street());
        e.setArea(body.area());
        e.setCity(body.city());
        e.setPostalCode(body.postalCode());
        return EmployeeResponse.from(employeeRepository.save(e));
    }

    /**
     * Management edits an employee. Two rules apply when someone stops being a handler or a manager:
     *  - a staff member who is deactivated or moved to Management is taken off their open requests
     *    (StaffMemberRemovedEvent, handled by the Requests module in this same transaction);
     *  - the last active manager cannot be deactivated or moved to Staff - another employee must be
     *    given the Management role first, so the system is never left without a manager.
     */
    @Transactional
    public EmployeeResponse update(Long id, EmployeePatch body) {
        CurrentUser actor = currentUser.get();
        db.begin(actor);
        Employee e = load(id);

        boolean wasActiveStaff = e.isActive() && e.getRole() == Employee.Role.STAFF;
        boolean wasActiveManager = e.isActive() && e.getRole() == Employee.Role.MANAGEMENT;
        boolean willBeActive = body.active() != null ? body.active() : e.isActive();
        Employee.Role willHaveRole = body.role() != null ? body.role() : e.getRole();

        if (wasActiveManager && (!willBeActive || willHaveRole != Employee.Role.MANAGEMENT)) {
            ensureAnotherActiveManager(e.getEmpId());
        }

        if (body.email() != null) {
            String email = body.email().trim().toLowerCase(Locale.ROOT);
            if (!email.equalsIgnoreCase(e.getEmail())) {
                checkEmailFree(email);
                e.setEmail(email);
            }
        }
        if (body.firstName() != null) e.setFirstName(body.firstName().trim());
        if (body.surname() != null) e.setSurname(body.surname().trim());
        if (body.password() != null) e.setPassword(passwordEncoder.encode(body.password()));
        if (body.phone() != null) e.setPhone(body.phone());
        if (body.role() != null) e.setRole(body.role());
        if (body.active() != null) e.setActive(body.active());
        if (body.dateOfBirth() != null) e.setDateOfBirth(body.dateOfBirth());
        if (body.street() != null) e.setStreet(body.street());
        if (body.area() != null) e.setArea(body.area());
        if (body.city() != null) e.setCity(body.city());
        if (body.postalCode() != null) e.setPostalCode(body.postalCode());

        if (wasActiveStaff && (!willBeActive || willHaveRole != Employee.Role.STAFF)) {
            employeeRepository.flush();
            String reason = !willBeActive ? "deactivated" : "moved to Management";
            events.publishEvent(new StaffMemberRemovedEvent(e.getEmpId(), actor.id(), reason));
        }
        return EmployeeResponse.from(e);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories(Long empId) {
        db.begin(currentUser.get());
        load(empId);
        return handlerCategoryRepository.findByEmployee(empId).stream()
                .map(hc -> CategoryResponse.from(hc.getCategory())).toList();
    }

    @Transactional(readOnly = true)
    public List<HandlerOption> handlersFor(Long catId) {
        db.begin(currentUser.get());
        if (!categoryRepository.existsById(catId)) {
            throw Errors.notFound("Category");
        }
        return staffDirectory.activeHandlersFor(catId);
    }

    @Transactional
    public List<CategoryResponse> replaceCategories(Long empId, HandlerCategoriesUpdate body) {
        db.begin(currentUser.get());
        Employee employee = load(empId);
        if (employee.getRole() != Employee.Role.STAFF) {
            throw Errors.invalidField("id", "Only staff members have handler categories");
        }

        Set<Long> wanted = new HashSet<>(body.categoryIds());
        List<Category> categories = categoryRepository.findAllById(wanted);
        if (categories.size() != wanted.size()) {
            throw Errors.invalidField("categoryIds", "One or more categories do not exist");
        }

        List<HandlerCategory> current = handlerCategoryRepository.findByEmployee(empId);
        Set<Long> have = new HashSet<>();
        for (HandlerCategory hc : current) {
            Long catId = hc.getCategory().getCatId();
            if (wanted.contains(catId)) {
                have.add(catId);
            } else {
                handlerCategoryRepository.delete(hc);
            }
        }
        for (Category c : categories) {
            if (!have.contains(c.getCatId())) {
                handlerCategoryRepository.save(new HandlerCategory(employee, c));
            }
        }
        return categories.stream().map(CategoryResponse::from).toList();
    }

    private void ensureAnotherActiveManager(Long empId) {
        boolean another = employeeRepository.lockActiveManagers().stream()
                .anyMatch(m -> !m.getEmpId().equals(empId));
        if (!another) {
            throw Errors.rule(ErrorCode.LAST_MANAGER, "This is the only active manager. Give another employee the "
                    + "Management role before deactivating this account or moving it to Staff.");
        }
    }

    private Employee load(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> Errors.notFound("Employee"));
    }

    private void checkEmailFree(String email) {
        if (employeeRepository.existsByEmailIgnoreCase(email) || clientRepository.existsByEmailIgnoreCase(email)) {
            throw Errors.conflict(ErrorCode.CONFLICT, "This email address is already in use.");
        }
    }
}
