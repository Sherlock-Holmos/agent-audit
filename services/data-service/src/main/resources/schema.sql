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

CREATE TABLE IF NOT EXISTS clean_strategy_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_username VARCHAR(128) NOT NULL,
  name VARCHAR(255) NOT NULL,
  code VARCHAR(64) NOT NULL,
  built_in TINYINT(1) NOT NULL,
  enabled TINYINT(1) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_clean_strategy_owner_code (owner_username, code),
  INDEX idx_clean_strategy_owner (owner_username)
);

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
  status VARCHAR(32) NOT NULL,
  fusion_rows INT NOT NULL,
  remark VARCHAR(512),
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_fusion_task_owner (owner_username)
);
