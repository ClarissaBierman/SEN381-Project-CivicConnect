package com.civicconnect.service;

import com.civicconnect.error.ErrorCode;
import com.civicconnect.dto.*;
import com.civicconnect.model.Category;
import com.civicconnect.model.Employee;
import com.civicconnect.model.HandlerCategory;
import com.civicconnect.repository.CategoryRepository;
import com.civicconnect.repository.ClientRepository;
import com.civicconnect.repository.EmployeeRepository;
import com.civicconnect.repository.HandlerCategoryRepository;
import com.civicconnect.security.CurrentUserService;
import com.civicconnect.security.DbSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
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

    public EmployeeService(EmployeeRepository employeeRepository, ClientRepository clientRepository,
                           CategoryRepository categoryRepository, HandlerCategoryRepository handlerCategoryRepository,
                           PasswordEncoder passwordEncoder, CurrentUserService currentUser, DbSession db) {
        this.employeeRepository = employeeRepository;
        this.clientRepository = clientRepository;
        this.categoryRepository = categoryRepository;
        this.handlerCategoryRepository = handlerCategoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.db = db;
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
        String email = body.email().trim().toLowerCase();
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

    @Transactional
    public EmployeeResponse update(Long id, EmployeePatch body) {
        db.begin(currentUser.get());
        Employee e = load(id);
        if (body.email() != null) {
            String email = body.email().trim().toLowerCase();
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
        return EmployeeResponse.from(e);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories(Long empId) {
        db.begin(currentUser.get());
        load(empId);
        return handlerCategoryRepository.findByEmployee(empId).stream()
                .map(hc -> CategoryResponse.from(hc.getCategory())).toList();
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

    private Employee load(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> Errors.notFound("Employee"));
    }

    private void checkEmailFree(String email) {
        if (employeeRepository.existsByEmailIgnoreCase(email) || clientRepository.existsByEmailIgnoreCase(email)) {
            throw Errors.conflict(ErrorCode.CONFLICT, "This email address is already in use.");
        }
    }
}
