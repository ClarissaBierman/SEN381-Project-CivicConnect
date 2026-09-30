CREATE TABLE client (
                        client_id BIGSERIAL PRIMARY KEY,
                        first_name VARCHAR(100) NOT NULL,
                        surname VARCHAR(100) NOT NULL,
                        email VARCHAR(255) NOT NULL UNIQUE,
                        password VARCHAR(255) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT now(),
                        date_of_birth DATE,
                        phone VARCHAR(30)
);

CREATE TABLE employee (
                          emp_id BIGSERIAL PRIMARY KEY,
                          first_name VARCHAR(100) NOT NULL,
                          surname VARCHAR(100) NOT NULL,
                          email VARCHAR(255) NOT NULL UNIQUE,
                          password VARCHAR(255) NOT NULL,
                          created_at TIMESTAMP NOT NULL DEFAULT now(),
                          date_of_birth DATE,
                          street VARCHAR(255),
                          area VARCHAR(100),
                          city VARCHAR(100),
                          postal_code VARCHAR(20),
                          role VARCHAR(20) NOT NULL CHECK (role IN ('STAFF', 'MANAGEMENT')),
                          phone VARCHAR(30)
);

CREATE TABLE category (
                          cat_id BIGSERIAL PRIMARY KEY,
                          cat_name VARCHAR(100) NOT NULL UNIQUE,
                          active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE request (
                         req_id BIGSERIAL PRIMARY KEY,
                         ref_num VARCHAR(50) NOT NULL UNIQUE,
                         cat_id BIGINT NOT NULL REFERENCES category(cat_id),
                         description TEXT NOT NULL,
                         location VARCHAR(255),
                         attachment_url VARCHAR(500),
                         status VARCHAR(20) NOT NULL DEFAULT 'NEW'
                             CHECK (status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED')),
                         requester_id BIGINT NOT NULL REFERENCES client(client_id),
                         created_at TIMESTAMP NOT NULL DEFAULT now(),
                         closed_at TIMESTAMP,
                         updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE request_handler (
                                 req_id BIGINT NOT NULL REFERENCES request(req_id),
                                 emp_id BIGINT NOT NULL REFERENCES employee(emp_id),
                                 assigned_at TIMESTAMP NOT NULL DEFAULT now(),
                                 unassigned_at TIMESTAMP,
                                 PRIMARY KEY (req_id, emp_id)
);

CREATE TABLE audit_entry (
                             com_id BIGSERIAL PRIMARY KEY,
                             req_id BIGINT NOT NULL REFERENCES request(req_id),
                             entry_type VARCHAR(50) NOT NULL,
                             emp_id BIGINT NOT NULL REFERENCES employee(emp_id),
                             content TEXT,
                             created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_request_status ON request(status);
CREATE INDEX idx_request_requester ON request(requester_id);
CREATE INDEX idx_request_handler_emp ON request_handler(emp_id);
CREATE INDEX idx_audit_entry_request ON audit_entry(req_id);