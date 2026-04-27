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

CREATE TABLE IF NOT EXISTS fusion_key_synonym_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_username VARCHAR(128) NOT NULL,
  canonical_key VARCHAR(255) NOT NULL,
  aliases_json TEXT,
  built_in TINYINT(1) NOT NULL,
  enabled TINYINT(1) NOT NULL,
  remark VARCHAR(512),
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_fusion_key_synonym_owner_key (owner_username, canonical_key),
  INDEX idx_fusion_key_synonym_owner (owner_username)
);

CREATE TABLE IF NOT EXISTS fusion_key_synonym_history_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  synonym_id BIGINT NOT NULL,
  owner_username VARCHAR(128) NOT NULL,
  canonical_key VARCHAR(255) NOT NULL,
  version_no INT NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  before_json LONGTEXT,
  after_json LONGTEXT,
  actor_username VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_fusion_key_synonym_history_owner_syn (owner_username, synonym_id),
  INDEX idx_fusion_key_synonym_history_owner_key (owner_username, canonical_key)
);

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

CREATE TABLE IF NOT EXISTS nifi_flow_run_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(128),
  owner_username VARCHAR(128) NOT NULL,
  flow_type VARCHAR(64) NOT NULL,
  process_group_id VARCHAR(128) NOT NULL,
  dispatch_status VARCHAR(32) NOT NULL,
  external_run_id VARCHAR(128),
  request_json LONGTEXT,
  response_json LONGTEXT,
  error_message VARCHAR(1024),
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_nifi_flow_owner_created (owner_username, created_at),
  INDEX idx_nifi_flow_owner_status (owner_username, dispatch_status)
);

CREATE TABLE IF NOT EXISTS nifi_flow_template_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(128),
  owner_username VARCHAR(128) NOT NULL,
  flow_type VARCHAR(64) NOT NULL,
  process_group_id VARCHAR(128) NOT NULL,
  parameter_schema_json LONGTEXT,
  version_no INT NOT NULL,
  enabled TINYINT(1) NOT NULL,
  remark VARCHAR(512),
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_nifi_template_owner_flow (owner_username, flow_type),
  INDEX idx_nifi_template_owner_updated (owner_username, updated_at)
);

CREATE TABLE IF NOT EXISTS nifi_task_reconcile_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(128),
  owner_username VARCHAR(128) NOT NULL,
  trigger_type VARCHAR(32) NOT NULL,
  trigger_user VARCHAR(128) NOT NULL,
  reconcile_mode VARCHAR(32) NOT NULL,
  task_type VARCHAR(32),
  task_id BIGINT,
  result_json LONGTEXT,
  created_at DATETIME NOT NULL,
  INDEX idx_nifi_reconcile_owner_created (owner_username, created_at),
  INDEX idx_nifi_reconcile_owner_task (owner_username, task_type, task_id)
);

CREATE TABLE IF NOT EXISTS bronze_ingest_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(128),
  owner_username VARCHAR(128) NOT NULL,
  ingest_type VARCHAR(32) NOT NULL,
  source_task_type VARCHAR(32) NOT NULL,
  source_task_id BIGINT NOT NULL,
  source_table VARCHAR(255) NOT NULL,
  source_object VARCHAR(255),
  row_no INT,
  raw_payload_json LONGTEXT,
  created_at DATETIME NOT NULL,
  INDEX idx_bronze_owner_task (owner_username, source_task_type, source_task_id)
);

CREATE TABLE IF NOT EXISTS silver_standard_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(128),
  owner_username VARCHAR(128) NOT NULL,
  standard_table VARCHAR(255) NOT NULL,
  source_task_type VARCHAR(32) NOT NULL,
  source_task_id BIGINT NOT NULL,
  source_id BIGINT,
  source_object VARCHAR(255),
  row_no INT,
  normalized_payload_json LONGTEXT,
  quality_status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_silver_owner_task (owner_username, source_task_type, source_task_id),
  INDEX idx_silver_owner_table (owner_username, standard_table)
);

CREATE TABLE IF NOT EXISTS gold_fusion_wide_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(128),
  owner_username VARCHAR(128) NOT NULL,
  gold_table VARCHAR(255) NOT NULL,
  fusion_task_id BIGINT NOT NULL,
  entity_key VARCHAR(512),
  match_type VARCHAR(64) NOT NULL,
  confidence DECIMAL(6,4) NOT NULL,
  source_records_json LONGTEXT,
  merged_payload_json LONGTEXT,
  created_at DATETIME NOT NULL,
  INDEX idx_gold_owner_task (owner_username, fusion_task_id),
  INDEX idx_gold_owner_table (owner_username, gold_table)
);

CREATE TABLE IF NOT EXISTS rect_issue_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_key VARCHAR(128) NOT NULL,
  code VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  level VARCHAR(32) NOT NULL,
  unit VARCHAR(255) NOT NULL,
  description TEXT,
  evidence_json LONGTEXT,
  regulation_clause TEXT,
  status VARCHAR(32) NOT NULL,
  created_by VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_rect_issue_owner (owner_key),
  INDEX idx_rect_issue_owner_unit (owner_key, unit)
);

CREATE TABLE IF NOT EXISTS rect_task_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_key VARCHAR(128) NOT NULL,
  issue_id BIGINT NOT NULL,
  parent_id BIGINT,
  title VARCHAR(255) NOT NULL,
  unit VARCHAR(255) NOT NULL,
  assignee VARCHAR(128) NOT NULL,
  claimed_by VARCHAR(128),
  created_by VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  progress INT NOT NULL,
  deadline VARCHAR(32),
  review_status VARCHAR(32) NOT NULL,
  review_comment VARCHAR(512),
  measure TEXT,
  attachments_json LONGTEXT,
  feedback TEXT,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_rect_task_owner_issue (owner_key, issue_id),
  INDEX idx_rect_task_owner_assignee (owner_key, assignee)
);

CREATE TABLE IF NOT EXISTS rect_supervision_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_key VARCHAR(128) NOT NULL,
  issue_id BIGINT NOT NULL,
  note TEXT,
  supervisor VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_rect_supervision_owner_issue (owner_key, issue_id)
);

CREATE TABLE IF NOT EXISTS rect_issue_share_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_key VARCHAR(128) NOT NULL,
  issue_id BIGINT NOT NULL,
  from_department VARCHAR(255) NOT NULL,
  to_department VARCHAR(255) NOT NULL,
  purpose TEXT,
  status VARCHAR(32) NOT NULL,
  created_by VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_rect_issue_share_owner_issue (owner_key, issue_id),
  INDEX idx_rect_issue_share_owner_to_dept (owner_key, to_department)
);

CREATE TABLE IF NOT EXISTS rect_issue_share_feedback_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_key VARCHAR(128) NOT NULL,
  share_id BIGINT NOT NULL,
  feedback_text TEXT,
  attachments_json LONGTEXT,
  created_by VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_rect_share_feedback_owner_share (owner_key, share_id)
);

CREATE TABLE IF NOT EXISTS rect_rule_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_key VARCHAR(128) NOT NULL,
  name VARCHAR(255) NOT NULL,
  enabled TINYINT(1) NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_rect_rule_owner (owner_key)
);

CREATE TABLE IF NOT EXISTS rect_reminder_rule_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_key VARCHAR(128) NOT NULL,
  name VARCHAR(255) NOT NULL,
  trigger_type VARCHAR(64) NOT NULL,
  trigger_value INT NOT NULL,
  enabled TINYINT(1) NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_rect_reminder_rule_owner (owner_key)
);

CREATE TABLE IF NOT EXISTS rect_reminder_dispatch_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_key VARCHAR(128) NOT NULL,
  rule_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL,
  reminder_date VARCHAR(16) NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_rect_reminder_dispatch_once (owner_key, rule_id, task_id, reminder_date),
  INDEX idx_rect_reminder_dispatch_owner (owner_key)
);

CREATE TABLE IF NOT EXISTS rect_user_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_key VARCHAR(128) NOT NULL,
  username VARCHAR(128) NOT NULL,
  nickname VARCHAR(255) NOT NULL,
  role VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  unit VARCHAR(255),
  department VARCHAR(255),
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_rect_user_owner_username (owner_key, username),
  INDEX idx_rect_user_owner_role (owner_key, role)
);

CREATE TABLE IF NOT EXISTS rect_department_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_key VARCHAR(128) NOT NULL,
  name VARCHAR(255) NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_rect_department_owner_name (owner_key, name),
  INDEX idx_rect_department_owner (owner_key)
);

CREATE TABLE IF NOT EXISTS rect_report_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_key VARCHAR(128) NOT NULL,
  unit VARCHAR(255) NOT NULL,
  title VARCHAR(255) NOT NULL,
  summary TEXT,
  submitter VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_rect_report_owner_unit (owner_key, unit)
);

ALTER TABLE rect_user_record ADD COLUMN IF NOT EXISTS unit VARCHAR(255);

CREATE TABLE IF NOT EXISTS rect_notification_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_key VARCHAR(128) NOT NULL,
  type VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  content TEXT,
  from_user VARCHAR(128) NOT NULL,
  related_task_id BIGINT,
  related_issue_id BIGINT,
  created_at DATETIME NOT NULL,
  INDEX idx_rect_notification_owner_created (owner_key, created_at)
);

CREATE TABLE IF NOT EXISTS rect_notification_receiver_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_key VARCHAR(128) NOT NULL,
  notification_id BIGINT NOT NULL,
  receiver_username VARCHAR(128) NOT NULL,
  UNIQUE KEY uk_rect_notification_receiver (owner_key, notification_id, receiver_username),
  INDEX idx_rect_notification_receiver_user (owner_key, receiver_username)
);

CREATE TABLE IF NOT EXISTS rect_notification_read_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_key VARCHAR(128) NOT NULL,
  notification_id BIGINT NOT NULL,
  username VARCHAR(128) NOT NULL,
  read_at DATETIME NOT NULL,
  UNIQUE KEY uk_rect_notification_read (owner_key, notification_id, username)
);

CREATE TABLE IF NOT EXISTS rect_notification_interaction_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_key VARCHAR(128) NOT NULL,
  notification_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL,
  actor VARCHAR(128) NOT NULL,
  message TEXT,
  created_at DATETIME NOT NULL,
  INDEX idx_rect_notification_interaction (owner_key, notification_id)
);

CREATE TABLE IF NOT EXISTS rect_org_department_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_key VARCHAR(128) NOT NULL,
  unit VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  parent_id BIGINT,
  leader_username VARCHAR(128),
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_rect_org_dept_owner_unit_name (owner_key, unit, name),
  INDEX idx_rect_org_dept_owner_unit (owner_key, unit)
);
