CREATE TABLE users (
 id VARCHAR(36) PRIMARY KEY, account VARCHAR(128) NOT NULL, normalized_account VARCHAR(128) NOT NULL UNIQUE,
 display_name VARCHAR(128) NOT NULL, password_hash VARCHAR(100) NOT NULL, status VARCHAR(16) NOT NULL,
 revision BIGINT NOT NULL, generation BIGINT NOT NULL, sync_position BIGINT NOT NULL,
 created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL
);
CREATE TABLE auth_sessions (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, family_id VARCHAR(36) NOT NULL,
 access_hash VARCHAR(64) NOT NULL UNIQUE, refresh_hash VARCHAR(64) NOT NULL UNIQUE,
 access_expires_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, revoked_at BIGINT NULL,
 consumed BOOLEAN NOT NULL, created_at BIGINT NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE INDEX idx_sessions_family ON auth_sessions(family_id);
CREATE TABLE auth_rate (
 bucket_id VARCHAR(64) PRIMARY KEY, attempts INT NOT NULL, window_start BIGINT NOT NULL
);
CREATE TABLE patients (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, revision BIGINT NOT NULL,
 deleted_at BIGINT NULL, payload LONGTEXT NOT NULL,
 FOREIGN KEY(owner_id) REFERENCES users(id)
);
CREATE INDEX idx_patients_owner ON patients(owner_id,deleted_at,id);
CREATE TABLE lesions (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, patient_id VARCHAR(36) NOT NULL,
 revision BIGINT NOT NULL, deleted_at BIGINT NULL, payload LONGTEXT NOT NULL,
 FOREIGN KEY(owner_id) REFERENCES users(id), FOREIGN KEY(patient_id) REFERENCES patients(id)
);
CREATE INDEX idx_lesions_parent ON lesions(owner_id,patient_id,deleted_at,id);
CREATE TABLE files (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, patient_id VARCHAR(36) NOT NULL,
 state VARCHAR(16) NOT NULL, payload LONGTEXT NOT NULL,
 FOREIGN KEY(owner_id) REFERENCES users(id), FOREIGN KEY(patient_id) REFERENCES patients(id)
);
CREATE TABLE file_uploads (
 id VARCHAR(36) PRIMARY KEY, file_id VARCHAR(36) NOT NULL UNIQUE, owner_id VARCHAR(36) NOT NULL,
 patient_id VARCHAR(36) NOT NULL, state VARCHAR(16) NOT NULL, expires_at BIGINT NOT NULL,
 created_at BIGINT NOT NULL, FOREIGN KEY(file_id) REFERENCES files(id), FOREIGN KEY(owner_id) REFERENCES users(id)
);
CREATE TABLE captures (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, patient_id VARCHAR(36) NOT NULL,
 lesion_id VARCHAR(36) NOT NULL, file_id VARCHAR(36) NOT NULL, captured_at BIGINT NOT NULL,
 revision BIGINT NOT NULL, deleted_at BIGINT NULL, payload LONGTEXT NOT NULL,
 FOREIGN KEY(owner_id) REFERENCES users(id), FOREIGN KEY(patient_id) REFERENCES patients(id),
 FOREIGN KEY(lesion_id) REFERENCES lesions(id), FOREIGN KEY(file_id) REFERENCES files(id)
);
CREATE INDEX idx_captures_parent ON captures(owner_id,lesion_id,deleted_at,captured_at,id);
CREATE TABLE capture_revisions (
 capture_id VARCHAR(36) NOT NULL, revision BIGINT NOT NULL, content_revision BIGINT NOT NULL,
 content_hash VARCHAR(64) NOT NULL, payload LONGTEXT NOT NULL, actor_id VARCHAR(36) NOT NULL,
 created_at BIGINT NOT NULL, PRIMARY KEY(capture_id,revision), FOREIGN KEY(capture_id) REFERENCES captures(id)
);
CREATE TABLE idempotency_records (
 owner_id VARCHAR(36) NOT NULL, request_method VARCHAR(8) NOT NULL, request_path VARCHAR(255) NOT NULL,
 idem_key VARCHAR(128) NOT NULL, request_hash VARCHAR(64) NOT NULL, response_status INT NOT NULL,
 response_body LONGTEXT NULL, etag VARCHAR(64) NULL, created_at BIGINT NOT NULL,
 PRIMARY KEY(owner_id,request_method,request_path,idem_key), FOREIGN KEY(owner_id) REFERENCES users(id)
);
CREATE TABLE sync_changes (
 owner_id VARCHAR(36) NOT NULL, position BIGINT NOT NULL, payload LONGTEXT NOT NULL,
 created_at BIGINT NOT NULL, PRIMARY KEY(owner_id,position), FOREIGN KEY(owner_id) REFERENCES users(id)
);
CREATE TABLE sync_snapshots (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, generation BIGINT NOT NULL,
 position BIGINT NOT NULL, expires_at BIGINT NOT NULL, FOREIGN KEY(owner_id) REFERENCES users(id)
);
CREATE TABLE sync_snapshot_items (
 snapshot_id VARCHAR(36) NOT NULL, ordinal BIGINT NOT NULL, payload LONGTEXT NOT NULL,
 PRIMARY KEY(snapshot_id,ordinal), FOREIGN KEY(snapshot_id) REFERENCES sync_snapshots(id)
);
CREATE TABLE offline_imports (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, dataset_id VARCHAR(36) NOT NULL,
 allowed_patients LONGTEXT NOT NULL, expires_at BIGINT NOT NULL, FOREIGN KEY(owner_id) REFERENCES users(id)
);
