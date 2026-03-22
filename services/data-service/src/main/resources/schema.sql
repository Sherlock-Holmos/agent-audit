CREATE SCHEMA IF NOT EXISTS agent_audit_staging;

CREATE TABLE IF NOT EXISTS data_source_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_username VARCHAR(128) NOT NULL,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(32) NOT NULL,
  db_type VARCHAR(64),
  host VARCHAR(255),
  port INT,
  database_name VARCHAR(255),
  username VARCHAR(255),
  db_password VARCHAR(512),
  file_name VARCHAR(512),
  file_size BIGINT,
  file_path VARCHAR(1024),
  preview_rows INT,
  status VARCHAR(32) NOT NULL,
  remark VARCHAR(512),
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_data_source_owner (owner_username)
);

CREATE TABLE IF NOT EXISTS clean_rule_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_username VARCHAR(128) NOT NULL,
  name VARCHAR(255) NOT NULL,
  category VARCHAR(32) NOT NULL,
  file_name VARCHAR(255),
  content TEXT,
  enabled TINYINT(1) NOT NULL,
  remark VARCHAR(512),
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_clean_rule_owner (owner_username)
);

ALTER TABLE data_source_record ADD COLUMN IF NOT EXISTS db_password VARCHAR(512);

CREATE TABLE IF NOT EXISTS clean_strategy_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_username VARCHAR(128) NOT NULL,
  name VARCHAR(255) NOT NULL,
  code VARCHAR(64) NOT NULL,
  content TEXT,
  remark VARCHAR(512),
  built_in TINYINT(1) NOT NULL,
  enabled TINYINT(1) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_clean_strategy_owner_code (owner_username, code),
  INDEX idx_clean_strategy_owner (owner_username)
);

ALTER TABLE clean_strategy_record ADD COLUMN IF NOT EXISTS content TEXT;
ALTER TABLE clean_strategy_record ADD COLUMN IF NOT EXISTS remark VARCHAR(512);

CREATE TABLE IF NOT EXISTS clean_task_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_username VARCHAR(128) NOT NULL,
  task_name VARCHAR(255) NOT NULL,
  clean_objects_json TEXT NOT NULL,
  clean_object_names_json TEXT NOT NULL,
  clean_rule_names_json TEXT,
  strategy_code VARCHAR(64) NOT NULL,
  strategy_name VARCHAR(255) NOT NULL,
  standard_table VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  cleaned_rows INT NOT NULL,
  remark VARCHAR(512),
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_clean_task_owner (owner_username)
);

CREATE TABLE IF NOT EXISTS fusion_task_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_username VARCHAR(128) NOT NULL,
  task_name VARCHAR(255) NOT NULL,
  target_table VARCHAR(255) NOT NULL,
  clean_task_ids_json TEXT NOT NULL,
  clean_task_names_json TEXT NOT NULL,
  standard_tables_json TEXT NOT NULL,
  strategy VARCHAR(64) NOT NULL,
  fusion_config_json TEXT,
  status VARCHAR(32) NOT NULL,
  fusion_rows INT NOT NULL,
  remark VARCHAR(512),
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_fusion_task_owner (owner_username)
);

ALTER TABLE fusion_task_record ADD COLUMN IF NOT EXISTS fusion_config_json TEXT;

CREATE INDEX idx_clean_task_owner_status_updated ON clean_task_record(owner_username, status, updated_at);
CREATE INDEX idx_fusion_task_owner_status_updated ON fusion_task_record(owner_username, status, updated_at);

CREATE TABLE IF NOT EXISTS process_job_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id VARCHAR(64) NOT NULL,
  owner_username VARCHAR(128) NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  task_ref_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  error_message VARCHAR(1024),
  result_json LONGTEXT,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_process_job_id (job_id),
  INDEX idx_process_job_owner (owner_username),
  INDEX idx_process_job_owner_status (owner_username, status)
);

ALTER TABLE process_job_record ADD COLUMN IF NOT EXISTS failure_category VARCHAR(64);
ALTER TABLE process_job_record ADD COLUMN IF NOT EXISTS alert_status VARCHAR(16);

CREATE TABLE IF NOT EXISTS task_idempotency_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_username VARCHAR(128) NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  task_ref_id BIGINT NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  job_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_task_idempotency (owner_username, task_type, task_ref_id, idempotency_key),
  INDEX idx_task_idempotency_job (job_id)
);

CREATE TABLE IF NOT EXISTS etl_field_lineage (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(128),
  owner_username VARCHAR(128) NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  task_id BIGINT NOT NULL,
  source_table VARCHAR(255) NOT NULL,
  source_field VARCHAR(255) NOT NULL,
  target_table VARCHAR(255) NOT NULL,
  target_field VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_lineage_owner_task (owner_username, task_type, task_id)
);

CREATE TABLE IF NOT EXISTS etl_quality_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(128),
  owner_username VARCHAR(128) NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  task_id BIGINT NOT NULL,
  table_name VARCHAR(255) NOT NULL,
  total_rows INT NOT NULL,
  unknown_rows INT NOT NULL,
  duplicate_rows INT NOT NULL,
  quality_score INT NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_quality_owner_task (owner_username, task_type, task_id)
);

CREATE TABLE IF NOT EXISTS etl_table_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(128),
  owner_username VARCHAR(128) NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  task_id BIGINT NOT NULL,
  table_name VARCHAR(255) NOT NULL,
  snapshot_version INT NOT NULL,
  row_count INT NOT NULL,
  schema_json LONGTEXT,
  created_at DATETIME NOT NULL,
  INDEX idx_snapshot_owner_task (owner_username, task_type, task_id)
);

CREATE TABLE IF NOT EXISTS audit_action_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(128),
  actor_username VARCHAR(128) NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(64) NOT NULL,
  result_status VARCHAR(16) NOT NULL,
  detail_json LONGTEXT,
  created_at DATETIME NOT NULL,
  INDEX idx_audit_actor_created (actor_username, created_at)
);

ALTER TABLE etl_field_lineage ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE etl_quality_report ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE etl_table_snapshot ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE audit_action_record ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);

CREATE TABLE IF NOT EXISTS etl_workflow_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(128) NOT NULL,
  owner_username VARCHAR(128) NOT NULL,
  workflow_name VARCHAR(255) NOT NULL,
  workflow_json LONGTEXT,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_workflow_owner (tenant_id, owner_username)
);

CREATE TABLE IF NOT EXISTS etl_workflow_run_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(128) NOT NULL,
  owner_username VARCHAR(128) NOT NULL,
  workflow_id BIGINT NOT NULL,
  run_status VARCHAR(32) NOT NULL,
  start_at DATETIME,
  end_at DATETIME,
  error_message VARCHAR(1024),
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_workflow_run_owner (tenant_id, owner_username, workflow_id)
);

CREATE TABLE IF NOT EXISTS etl_workflow_node_run_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id BIGINT NOT NULL,
  node_id VARCHAR(128) NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  task_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  error_message VARCHAR(1024),
  started_at DATETIME,
  ended_at DATETIME,
  INDEX idx_node_run_run (run_id)
);
