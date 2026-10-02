CREATE TABLE IF NOT EXISTS centers (
  center_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
  user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role ENUM('STUDENT','TEACHER') NOT NULL,
  center_id BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  email VARCHAR(254) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  age TINYINT UNSIGNED NULL,
  terms_agreed BOOLEAN NOT NULL DEFAULT FALSE,
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_users_email (email),
  KEY idx_users_center_role (center_id, role),
  CONSTRAINT fk_users_center FOREIGN KEY (center_id) REFERENCES centers(center_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS student_profiles (
  student_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL UNIQUE,
  center_id BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  age TINYINT UNSIGNED NULL,
  sessions_limit SMALLINT UNSIGNED NOT NULL DEFAULT 20,
  parent_phone VARCHAR(30) NULL,
  focus_areas VARCHAR(600) NULL,
  status ENUM('ACTIVE','PAUSED','COMPLETED') NOT NULL DEFAULT 'ACTIVE',
  learner_type VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
  memo VARCHAR(1000) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_student_profile_center (center_id, status, name),
  CONSTRAINT fk_student_profile_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
  CONSTRAINT fk_student_profile_center FOREIGN KEY (center_id) REFERENCES centers(center_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS teacher_students (
  teacher_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  memo VARCHAR(1000) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (teacher_id, student_id),
  CONSTRAINT fk_teacher_students_teacher FOREIGN KEY (teacher_id) REFERENCES users(user_id),
  CONSTRAINT fk_teacher_students_student FOREIGN KEY (student_id) REFERENCES student_profiles(student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refresh_tokens (
  token_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_refresh_hash (token_hash),
  KEY idx_refresh_user (user_id),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS practice_categories (
  category_id VARCHAR(40) PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  color VARCHAR(20) NOT NULL,
  description VARCHAR(300) NOT NULL DEFAULT '',
  sort_order SMALLINT NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exercises (
  exercise_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_id VARCHAR(40) NOT NULL,
  title VARCHAR(120) NOT NULL,
  instruction VARCHAR(500) NOT NULL,
  input_type ENUM('mic','read','speak') NOT NULL DEFAULT 'mic',
  sort_order SMALLINT NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  KEY idx_exercise_category (category_id, active, sort_order),
  CONSTRAINT fk_exercise_category FOREIGN KEY (category_id) REFERENCES practice_categories(category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exercise_items (
  item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  exercise_id BIGINT NOT NULL,
  text_value VARCHAR(500) NOT NULL,
  image_url VARCHAR(1000) NULL,
  emoji VARCHAR(16) NULL,
  sort_order SMALLINT NOT NULL DEFAULT 0,
  UNIQUE KEY uq_items_exercise_order (exercise_id, sort_order),
  KEY idx_items_exercise (exercise_id, sort_order),
  CONSTRAINT fk_items_exercise FOREIGN KEY (exercise_id) REFERENCES exercises(exercise_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS therapy_sessions (
  session_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  scheduled_at DATETIME NULL,
  status ENUM('UPCOMING','IN_PROGRESS','COMPLETED','CANCELLED') NOT NULL DEFAULT 'UPCOMING',
  summary VARCHAR(1000) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_session_student_date (student_id, scheduled_at DESC),
  CONSTRAINT fk_session_student FOREIGN KEY (student_id) REFERENCES student_profiles(student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS session_exercises (
  session_id BIGINT NOT NULL,
  exercise_id BIGINT NOT NULL,
  sort_order SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (session_id, exercise_id),
  CONSTRAINT fk_session_exercise_session FOREIGN KEY (session_id) REFERENCES therapy_sessions(session_id) ON DELETE CASCADE,
  CONSTRAINT fk_session_exercise_exercise FOREIGN KEY (exercise_id) REFERENCES exercises(exercise_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS speech_analyses (
  analysis_id CHAR(36) PRIMARY KEY,
  student_id BIGINT NOT NULL,
  exercise_id BIGINT NOT NULL,
  item_id VARCHAR(80) NOT NULL,
  audio_path VARCHAR(1000) NULL,
  audio_mime VARCHAR(100) NOT NULL,
  status ENUM('PENDING','PROCESSING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
  pronunciation_score DECIMAL(5,2) NULL,
  speech_rate_score DECIMAL(5,2) NULL,
  fluency_score DECIMAL(5,2) NULL,
  overall_score DECIMAL(5,2) NULL,
  transcript TEXT NULL,
  feedback TEXT NULL,
  error_code VARCHAR(80) NULL,
  evaluation_mode VARCHAR(30) NULL,
  target_text VARCHAR(500) NULL,
  match_rate DECIMAL(5,2) NULL,
  comparison_json TEXT NULL,
  recognition_confidence DECIMAL(5,4) NULL,
  engine_name VARCHAR(80) NULL,
  model_name VARCHAR(160) NULL,
  pronunciation_status VARCHAR(30) NULL,
  review_status VARCHAR(30) NULL,
  teacher_judgement VARCHAR(30) NULL,
  teacher_note VARCHAR(1000) NULL,
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  audio_deleted_at DATETIME NULL,
  audio_expires_at DATETIME NULL,
  audio_delete_attempts SMALLINT NOT NULL DEFAULT 0,
  audio_delete_error VARCHAR(200) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME NULL,
  KEY idx_analysis_student (student_id, created_at DESC),
  CONSTRAINT fk_analysis_student FOREIGN KEY (student_id) REFERENCES student_profiles(student_id),
  CONSTRAINT fk_analysis_exercise FOREIGN KEY (exercise_id) REFERENCES exercises(exercise_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS practice_attempts (
  attempt_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  exercise_id BIGINT NOT NULL,
  item_id VARCHAR(80) NOT NULL,
  analysis_id CHAR(36) NULL,
  score DECIMAL(5,2) NULL,
  match_rate DECIMAL(5,2) NULL,
  attempt_type ENUM('PRACTICE','HOMEWORK','AI_CHAT') NOT NULL DEFAULT 'PRACTICE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_attempt_analysis (analysis_id),
  KEY idx_attempt_student_date (student_id, created_at DESC),
  CONSTRAINT fk_attempt_student FOREIGN KEY (student_id) REFERENCES student_profiles(student_id),
  CONSTRAINT fk_attempt_exercise FOREIGN KEY (exercise_id) REFERENCES exercises(exercise_id),
  CONSTRAINT fk_attempt_analysis FOREIGN KEY (analysis_id) REFERENCES speech_analyses(analysis_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversations (
  conversation_id CHAR(36) PRIMARY KEY,
  student_id BIGINT NOT NULL,
  topic VARCHAR(120) NOT NULL,
  status ENUM('ACTIVE','CLOSED') NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_conversation_student (student_id, updated_at DESC),
  CONSTRAINT fk_conversation_student FOREIGN KEY (student_id) REFERENCES student_profiles(student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversation_messages (
  message_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id CHAR(36) NOT NULL,
  speaker ENUM('STUDENT','ASSISTANT') NOT NULL,
  content TEXT NOT NULL,
  audio_path VARCHAR(1000) NULL,
  audio_deleted_at DATETIME NULL,
  feedback TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_message_conversation (conversation_id, created_at),
  CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(conversation_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS homeworks (
  homework_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  teacher_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  type VARCHAR(60) NOT NULL,
  description VARCHAR(2000) NULL,
  target_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 10,
  due_date DATE NOT NULL,
  status ENUM('PENDING','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_homework_teacher_due (teacher_id, due_date),
  KEY idx_homework_student (student_id, status),
  CONSTRAINT fk_homework_teacher FOREIGN KEY (teacher_id) REFERENCES users(user_id),
  CONSTRAINT fk_homework_student FOREIGN KEY (student_id) REFERENCES student_profiles(student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS password_reset_requests (
  request_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  code_hash CHAR(64) NOT NULL,
  code_expires_at DATETIME NOT NULL,
  attempts TINYINT UNSIGNED NOT NULL DEFAULT 0,
  verified_at DATETIME NULL,
  invalidated_at DATETIME NULL,
  reset_token_hash CHAR(64) NULL,
  reset_token_expires_at DATETIME NULL,
  used_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_reset_token (reset_token_hash),
  KEY idx_reset_user_created (user_id, created_at),
  CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_consents (
  consent_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  consent_type VARCHAR(40) NOT NULL,
  agreed BOOLEAN NOT NULL,
  policy_version VARCHAR(20) NOT NULL,
  agreed_at DATETIME NULL,
  withdrawn_at DATETIME NULL,
  guardian_name VARCHAR(80) NULL,
  guardian_relation VARCHAR(20) NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_consent (user_id, consent_type),
  CONSTRAINT fk_consent_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 기존 DB 호환 마이그레이션 (로컬 음성 인식·문장 일치도·선생님 검토) ──────────────
-- CREATE TABLE IF NOT EXISTS는 기존 테이블에 새 컬럼을 추가하지 않으므로, 컬럼이 없을 때만 ALTER를 실행한다.
-- 모두 NULL 허용(또는 기본값 보유) 컬럼 추가이며 기존 데이터와 API 응답 필드는 변경하지 않는다.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='student_profiles' AND COLUMN_NAME='learner_type')=0, 'ALTER TABLE student_profiles ADD COLUMN learner_type VARCHAR(20) NOT NULL DEFAULT ''GENERAL'' AFTER status', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='evaluation_mode')=0, 'ALTER TABLE speech_analyses ADD COLUMN evaluation_mode VARCHAR(30) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='target_text')=0, 'ALTER TABLE speech_analyses ADD COLUMN target_text VARCHAR(500) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='match_rate')=0, 'ALTER TABLE speech_analyses ADD COLUMN match_rate DECIMAL(5,2) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='comparison_json')=0, 'ALTER TABLE speech_analyses ADD COLUMN comparison_json TEXT NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='recognition_confidence')=0, 'ALTER TABLE speech_analyses ADD COLUMN recognition_confidence DECIMAL(5,4) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='engine_name')=0, 'ALTER TABLE speech_analyses ADD COLUMN engine_name VARCHAR(80) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='model_name')=0, 'ALTER TABLE speech_analyses ADD COLUMN model_name VARCHAR(160) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='pronunciation_status')=0, 'ALTER TABLE speech_analyses ADD COLUMN pronunciation_status VARCHAR(30) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='review_status')=0, 'ALTER TABLE speech_analyses ADD COLUMN review_status VARCHAR(30) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='teacher_judgement')=0, 'ALTER TABLE speech_analyses ADD COLUMN teacher_judgement VARCHAR(30) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='teacher_note')=0, 'ALTER TABLE speech_analyses ADD COLUMN teacher_note VARCHAR(1000) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='reviewed_by')=0, 'ALTER TABLE speech_analyses ADD COLUMN reviewed_by BIGINT NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='reviewed_at')=0, 'ALTER TABLE speech_analyses ADD COLUMN reviewed_at DATETIME NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='audio_deleted_at')=0, 'ALTER TABLE speech_analyses ADD COLUMN audio_deleted_at DATETIME NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='practice_attempts' AND COLUMN_NAME='match_rate')=0, 'ALTER TABLE practice_attempts ADD COLUMN match_rate DECIMAL(5,2) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='centers' AND COLUMN_NAME='active')=0, 'ALTER TABLE centers ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='audio_expires_at')=0, 'ALTER TABLE speech_analyses ADD COLUMN audio_expires_at DATETIME NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='audio_delete_attempts')=0, 'ALTER TABLE speech_analyses ADD COLUMN audio_delete_attempts SMALLINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='audio_delete_error')=0, 'ALTER TABLE speech_analyses ADD COLUMN audio_delete_error VARCHAR(200) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='conversation_messages' AND COLUMN_NAME='audio_deleted_at')=0, 'ALTER TABLE conversation_messages ADD COLUMN audio_deleted_at DATETIME NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
-- 보관 기간(기본 6개월) 도입 이전에 저장된 녹음은 생성 시각 기준으로 만료일을 채운다.
UPDATE speech_analyses SET audio_expires_at=DATE_ADD(created_at, INTERVAL 6 MONTH) WHERE audio_expires_at IS NULL AND audio_path IS NOT NULL;
-- 실제 측정값이 없는 연습(로컬 음성 인식)은 점수를 NULL로 저장한다. 음성 파일 삭제 후 경로를 비운다.
ALTER TABLE practice_attempts MODIFY score DECIMAL(5,2) NULL;
ALTER TABLE speech_analyses MODIFY audio_path VARCHAR(1000) NULL;

-- 연습 문항 시드 중복 정리: 과거에는 고유 키가 없어 서버를 시작할 때마다 같은 문항이 다시 추가되었다.
-- 중복 문항을 가리키는 기록을 남길 문항(가장 작은 item_id)으로 옮긴 뒤 중복을 지우고 고유 키를 추가한다.
UPDATE practice_attempts pa JOIN exercise_items d ON d.item_id=CAST(pa.item_id AS UNSIGNED)
  JOIN (SELECT exercise_id,sort_order,MIN(item_id) AS keep_id FROM exercise_items GROUP BY exercise_id,sort_order) k
    ON k.exercise_id=d.exercise_id AND k.sort_order=d.sort_order
  SET pa.item_id=CAST(k.keep_id AS CHAR) WHERE d.item_id<>k.keep_id;
UPDATE speech_analyses sa JOIN exercise_items d ON d.item_id=CAST(sa.item_id AS UNSIGNED)
  JOIN (SELECT exercise_id,sort_order,MIN(item_id) AS keep_id FROM exercise_items GROUP BY exercise_id,sort_order) k
    ON k.exercise_id=d.exercise_id AND k.sort_order=d.sort_order
  SET sa.item_id=CAST(k.keep_id AS CHAR) WHERE d.item_id<>k.keep_id;
DELETE d FROM exercise_items d
  JOIN (SELECT exercise_id,sort_order,MIN(item_id) AS keep_id FROM exercise_items GROUP BY exercise_id,sort_order) k
    ON k.exercise_id=d.exercise_id AND k.sort_order=d.sort_order
  WHERE d.item_id>k.keep_id;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='exercise_items' AND INDEX_NAME='uq_items_exercise_order')=0, 'ALTER TABLE exercise_items ADD UNIQUE KEY uq_items_exercise_order (exercise_id, sort_order)', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;

INSERT IGNORE INTO centers (center_id, name) VALUES (1, '평택언어이재활센터'), (2, '체터랜드 강남점'), (3, '체터랜드 서초점');
INSERT IGNORE INTO practice_categories (category_id, name, color, description, sort_order) VALUES
('articulation', '발음', '#5BA08E', '정확한 소리와 조음 위치를 연습해요.', 1),
('vocabulary', '어휘', '#5B8BC8', '낱말의 의미와 사용을 연습해요.', 2),
('fluency', '유창성', '#7B72C8', '호흡과 자연스러운 말의 흐름을 연습해요.', 3),
('expression', '표현력', '#E8875A', '문장과 이야기로 생각을 표현해요.', 4),
('comprehension', '이해력', '#E5A55D', '듣고 의미를 이해하는 연습을 해요.', 5);
INSERT IGNORE INTO exercises (exercise_id, category_id, title, instruction, input_type, sort_order) VALUES
(1, 'articulation', 'ㄹ 발음', 'ㄹ 발음을 크고 또렷하게 말해보세요.', 'mic', 1),
(2, 'articulation', 'ㅂ / ㅍ 구별', 'ㅂ과 ㅍ 발음을 구별해 말해보세요.', 'mic', 2),
(3, 'vocabulary', '과일 어휘', '그림을 보고 이름을 말해보세요.', 'speak', 1),
(4, 'fluency', '천천히 말하기', '호흡을 가다듬고 문장을 말해보세요.', 'mic', 1),
(5, 'expression', '이야기 만들기', '그림을 보고 이야기를 만들어 보세요.', 'speak', 1);
INSERT IGNORE INTO exercise_items (exercise_id, text_value, emoji, sort_order) VALUES
(1,'라디오',NULL,1),(1,'리본',NULL,2),(1,'로봇',NULL,3),(1,'레몬',NULL,4),
(2,'바나나',NULL,1),(2,'파나마',NULL,2),(2,'불',NULL,3),(2,'풀',NULL,4),
(3,'사과','🍎',1),(3,'바나나','🍌',2),(3,'포도','🍇',3),
(4,'오늘은 날씨가 좋아요.',NULL,1),(4,'천천히 또박또박 말해요.',NULL,2),
(5,'토끼가 숲속을 걸어가요.',NULL,1),(5,'거북이가 친구를 만났어요.',NULL,2);
