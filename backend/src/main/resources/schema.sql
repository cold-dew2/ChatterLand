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
  token_version INT NOT NULL DEFAULT 0,
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
  session_id CHAR(36) NULL,
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
  target_phonemes VARCHAR(40) NULL,
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
  analysis_type VARCHAR(20) NULL,
  assessment_status VARCHAR(30) NULL,
  assessment_json TEXT NULL,
  analysis_version VARCHAR(40) NULL,
  request_key VARCHAR(64) NULL,
  request_hash CHAR(64) NULL,
  pronunciation_status VARCHAR(30) NULL,
  review_status VARCHAR(30) NULL,
  teacher_judgement VARCHAR(30) NULL,
  teacher_note VARCHAR(1000) NULL,
  teacher_confirmed_json TEXT NULL,
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
  version INT NOT NULL DEFAULT 0,
  request_key VARCHAR(64) NULL,
  request_hash CHAR(64) NULL,
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

-- ── 단어·문장 평가 구조(자동 분석 근거·판정 보류·교사 확정 결과 분리) ──────────────
-- 모두 NULL 허용 컬럼 추가이며 기존 데이터와 API 필드는 그대로 둔다.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='analysis_type')=0, 'ALTER TABLE speech_analyses ADD COLUMN analysis_type VARCHAR(20) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='assessment_status')=0, 'ALTER TABLE speech_analyses ADD COLUMN assessment_status VARCHAR(30) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='assessment_json')=0, 'ALTER TABLE speech_analyses ADD COLUMN assessment_json TEXT NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='analysis_version')=0, 'ALTER TABLE speech_analyses ADD COLUMN analysis_version VARCHAR(40) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='teacher_confirmed_json')=0, 'ALTER TABLE speech_analyses ADD COLUMN teacher_confirmed_json TEXT NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='exercises' AND COLUMN_NAME='target_phonemes')=0, 'ALTER TABLE exercises ADD COLUMN target_phonemes VARCHAR(40) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
-- 기본 연습 문제의 목표 음소(선생님이 지정하지 않은 경우에만 채운다)
UPDATE exercises SET target_phonemes='ㄹ' WHERE exercise_id=1 AND target_phonemes IS NULL;
UPDATE exercises SET target_phonemes='ㅂ,ㅍ' WHERE exercise_id=2 AND target_phonemes IS NULL;

-- ── 중복 음성 분석 방지(멱등성 키) ──────────────
-- 화면이 녹음마다 만든 Idempotency-Key를 학생별로 한 번만 처리한다. 실패한 분석은 키를 비워 다시 시도할 수 있다.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='request_key')=0, 'ALTER TABLE speech_analyses ADD COLUMN request_key VARCHAR(64) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND COLUMN_NAME='request_hash')=0, 'ALTER TABLE speech_analyses ADD COLUMN request_hash CHAR(64) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_analyses' AND INDEX_NAME='uq_analysis_request_key')=0, 'ALTER TABLE speech_analyses ADD UNIQUE KEY uq_analysis_request_key (student_id, request_key)', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;

-- ── 숙제 중복 생성 방지(멱등성 키)·동시 수정 충돌 방지(버전) ──────────────
-- 선생님별로 같은 Idempotency-Key의 숙제 생성은 한 번만 저장한다. version은 수정할 때마다 1씩 늘어난다(기존 행은 0).
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='homeworks' AND COLUMN_NAME='request_key')=0, 'ALTER TABLE homeworks ADD COLUMN request_key VARCHAR(64) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='homeworks' AND COLUMN_NAME='request_hash')=0, 'ALTER TABLE homeworks ADD COLUMN request_hash CHAR(64) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='homeworks' AND INDEX_NAME='uq_homework_request_key')=0, 'ALTER TABLE homeworks ADD UNIQUE KEY uq_homework_request_key (teacher_id, request_key)', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='homeworks' AND COLUMN_NAME='version')=0, 'ALTER TABLE homeworks ADD COLUMN version INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;

-- ── 비밀번호 재설정 후 기존 access token 무효화 ──────────────
-- access token에 발급 당시 token_version을 담고, 요청마다 현재 값과 비교한다. 비밀번호를 재설정하면 1 늘어난다(기존 행은 0).
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='token_version')=0, 'ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;

-- ── 로그아웃 시 access token 즉시 무효화(로그인 세션 ID) ──────────────
-- 로그인 1회(기기)마다 세션 ID를 만들고 refresh token 회전 시 이어받는다. access token의 sid와 비교해, 로그아웃한 세션의 토큰을 거절한다.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='refresh_tokens' AND COLUMN_NAME='session_id')=0, 'ALTER TABLE refresh_tokens ADD COLUMN session_id CHAR(36) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='refresh_tokens' AND INDEX_NAME='idx_refresh_session')=0, 'ALTER TABLE refresh_tokens ADD KEY idx_refresh_session (session_id)', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;

-- ── 로그인 상태 비밀번호 변경 시도 제한 ──────────────
-- 현재 비밀번호를 틀린 시각만 남긴다(비밀번호 값은 저장하지 않음). 성공하면 지우고, 오래된 기록은 정리 작업이 지운다.
CREATE TABLE IF NOT EXISTS password_change_failures (
  failure_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_pw_change_failure_user (user_id, created_at),
  CONSTRAINT fk_pw_change_failure_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 오래된 refresh token 정리용 인덱스 ──────────────
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='refresh_tokens' AND INDEX_NAME='idx_refresh_expires')=0, 'ALTER TABLE refresh_tokens ADD KEY idx_refresh_expires (expires_at)', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='refresh_tokens' AND INDEX_NAME='idx_refresh_revoked')=0, 'ALTER TABLE refresh_tokens ADD KEY idx_refresh_revoked (revoked_at)', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;


-- ── AI 학습 피드백(자동 분석·교사 확정 결과와 분리 보관) ──────────────
-- 점수가 아니라 AI가 분석 근거를 쉽게 풀어 쓴 설명만 저장한다. evidence_hash가 달라지면(교사 재검토 등) 이전 설명은 쓰지 않는다.
CREATE TABLE IF NOT EXISTS speech_ai_feedback (
  analysis_id CHAR(36) PRIMARY KEY,
  feedback_text VARCHAR(1000) NOT NULL,
  evidence_hash CHAR(64) NOT NULL,
  model_name VARCHAR(120) NULL,
  prompt_version VARCHAR(40) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_ai_feedback_analysis FOREIGN KEY (analysis_id) REFERENCES speech_analyses(analysis_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── RAG 지식베이스(한국어 발음 교육 자료) ──────────────
-- AI 설명의 근거로 쓰는 자료. review_status가 APPROVED인 자료만 검색한다(DRAFT: 등록만 됨·검수 전, RETIRED: 사용 중지).
-- 자료 내용을 바꾸면 version을 올린다(설명의 근거 해시에 포함되어 이전 설명을 다시 쓰지 않는다).
CREATE TABLE IF NOT EXISTS knowledge_documents (
  document_id VARCHAR(60) PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  source_citation VARCHAR(500) NOT NULL,
  source_url VARCHAR(500) NULL,
  publisher VARCHAR(120) NULL,
  author VARCHAR(120) NULL,
  published_year SMALLINT NULL,
  topic VARCHAR(120) NOT NULL,
  scope VARCHAR(300) NOT NULL,
  license_note VARCHAR(500) NOT NULL,
  review_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  version INT NOT NULL DEFAULT 1,
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_knowledge_status (review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 청크: location은 원문 위치(예: 제20항), tags는 검색용 태그(쉼표 구분: rule:20, phoneme:ㄹ, slot:CODA 등)
CREATE TABLE IF NOT EXISTS knowledge_chunks (
  chunk_id VARCHAR(80) PRIMARY KEY,
  document_id VARCHAR(60) NOT NULL,
  chunk_order INT NOT NULL,
  location VARCHAR(120) NOT NULL,
  content VARCHAR(2000) NOT NULL,
  tags VARCHAR(500) NOT NULL,
  KEY idx_chunk_document (document_id, chunk_order),
  CONSTRAINT fk_chunk_document FOREIGN KEY (document_id) REFERENCES knowledge_documents(document_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- AI 설명에 쓴 근거 출처(제목·원문 위치·청크 ID·버전)
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='speech_ai_feedback' AND COLUMN_NAME='sources_json')=0, 'ALTER TABLE speech_ai_feedback ADD COLUMN sources_json TEXT NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;

-- 초기 자료: 표준 발음법 조항 요약. 개발자가 작성한 요약이므로 DRAFT(검수 전)로 등록하며, 검수자가 원문과 대조해 APPROVED로 바꿔야 검색된다.
-- INSERT IGNORE: 검수 상태·수정 내용을 재시작 때 덮어쓰지 않는다.
INSERT IGNORE INTO knowledge_documents(document_id,title,source_citation,source_url,publisher,author,published_year,topic,scope,license_note,review_status,version) VALUES
('std-pronunciation-1988','표준 발음법(표준어 규정 제2부)',
 '문교부 고시 제88-2호(1988. 1. 19.) 「표준어 규정」 제2부 표준 발음법. 현행 원문은 국립국어원 한국어 어문 규범에서 확인.',
 'https://korean.go.kr/kornorms/','국립국어원(현행 원문 제공)',NULL,1988,'표준 발음·음운 규칙',
 '표준어를 말할 때의 표준 발음 규칙. 아동 조음 발달·조음 오류의 진단이나 치료 기준이 아니다.',
 '정부 고시(어문 규범). 저작권법 제7조(보호받지 못하는 저작물)에 해당할 것으로 보이나 법적 검토 필요. 이 저장소의 청크는 원문이 아니라 개발자가 작성한 요약이다.',
 'DRAFT',1);
INSERT IGNORE INTO knowledge_chunks(chunk_id,document_id,chunk_order,location,content,tags) VALUES
('std-pron-08','std-pronunciation-1988',8,'제4장 제8항','받침소리로는 ㄱ, ㄴ, ㄷ, ㄹ, ㅁ, ㅂ, ㅇ의 일곱 개 자음만 발음한다.','rule:8,slot:CODA,phoneme:ㄱ,phoneme:ㄴ,phoneme:ㄷ,phoneme:ㄹ,phoneme:ㅁ,phoneme:ㅂ,phoneme:ㅇ'),
('std-pron-09','std-pronunciation-1988',9,'제4장 제9항','받침 ㄲ, ㅋ과 ㅅ, ㅆ, ㅈ, ㅊ, ㅌ과 ㅍ은 낱말 끝이나 자음 앞에서 각각 대표음 ㄱ, ㄷ, ㅂ으로 발음한다. 예: 닦다[닥따], 옷[옫], 잎[입].','rule:9,slot:CODA,phoneme:ㄲ,phoneme:ㅋ,phoneme:ㅅ,phoneme:ㅆ,phoneme:ㅈ,phoneme:ㅊ,phoneme:ㅌ,phoneme:ㅍ,phoneme:ㄱ,phoneme:ㄷ,phoneme:ㅂ'),
('std-pron-13','std-pronunciation-1988',13,'제4장 제13항','홑받침이나 쌍받침이 모음으로 시작하는 조사·어미·접미사와 이어지면, 받침을 제 음가대로 뒤 음절의 첫소리로 옮겨 발음한다(연음). 예: 옷이[오시], 꽃을[꼬츨].','rule:13,slot:CODA,slot:ONSET'),
('std-pron-17','std-pronunciation-1988',17,'제5장 제17항','받침 ㄷ, ㅌ이 조사나 접미사의 모음 ㅣ와 이어지면 ㅈ, ㅊ으로 바꾸어 뒤 음절 첫소리로 옮겨 발음한다(구개음화). 예: 굳이[구지], 같이[가치].','rule:17,slot:CODA,phoneme:ㄷ,phoneme:ㅌ,phoneme:ㅈ,phoneme:ㅊ'),
('std-pron-18','std-pronunciation-1988',18,'제5장 제18항','받침 ㄱ, ㄷ, ㅂ 계열은 ㄴ, ㅁ 앞에서 각각 ㅇ, ㄴ, ㅁ으로 발음한다(비음화). 예: 국물[궁물], 닫는[단는], 밥물[밤물].','rule:18,slot:CODA,phoneme:ㄱ,phoneme:ㄷ,phoneme:ㅂ,phoneme:ㅇ,phoneme:ㄴ,phoneme:ㅁ'),
('std-pron-19','std-pronunciation-1988',19,'제5장 제19항','받침 ㅁ, ㅇ 뒤에 이어지는 ㄹ은 ㄴ으로 발음한다. 예: 담력[담녁], 강릉[강능].','rule:19,slot:ONSET,phoneme:ㄹ,phoneme:ㄴ,phoneme:ㅁ,phoneme:ㅇ'),
('std-pron-20','std-pronunciation-1988',20,'제5장 제20항','ㄴ은 ㄹ의 앞이나 뒤에서 ㄹ로 발음한다(유음화). 예: 난로[날로], 신라[실라], 칼날[칼랄].','rule:20,slot:CODA,slot:ONSET,phoneme:ㄴ,phoneme:ㄹ'),
('std-pron-23','std-pronunciation-1988',23,'제6장 제23항','받침 ㄱ, ㄷ, ㅂ 계열 뒤에 이어지는 ㄱ, ㄷ, ㅂ, ㅅ, ㅈ은 된소리로 발음한다(경음화). 예: 국밥[국빱], 깎다[깍따], 옆집[엽찝].','rule:23,slot:ONSET,phoneme:ㄱ,phoneme:ㄷ,phoneme:ㅂ,phoneme:ㅅ,phoneme:ㅈ,phoneme:ㄲ,phoneme:ㄸ,phoneme:ㅃ,phoneme:ㅆ,phoneme:ㅉ');

-- ── RAG 자료 출처·사용 권한 관리 ──────────────
-- category: ARTICULATION_PLACE(조음 위치)/ARTICULATION_MANNER(조음 방법)/CONSONANT/VOWEL/CODA(받침)/PRONUNCIATION_RULE(음운 규칙)/TEACHER_EXAMPLE(교사 승인 설명 예시)
-- verification_status: 출처·사용 권한 확인 상태. PENDING(확인 전·불명확)/VERIFIED(출처 URL·저작권 정책 확인)/REJECTED(사용 불가).
-- AI 검색 대상 = verification_status VERIFIED 이면서 review_status가 허용 상태(기본 APPROVED). TEACHER_EXAMPLE은 승인자(reviewed_by)까지 있어야 한다.
-- 승인자·승인 시각은 기존 reviewed_by/reviewed_at(= approved_by/approved_at), 사용 권한은 license_note, 원문 판본은 source_version에 둔다.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='knowledge_documents' AND COLUMN_NAME='category')=0, 'ALTER TABLE knowledge_documents ADD COLUMN category VARCHAR(30) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='knowledge_documents' AND COLUMN_NAME='source_version')=0, 'ALTER TABLE knowledge_documents ADD COLUMN source_version VARCHAR(200) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='knowledge_documents' AND COLUMN_NAME='verification_status')=0, 'ALTER TABLE knowledge_documents ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT ''PENDING''', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='knowledge_documents' AND COLUMN_NAME='verified_at')=0, 'ALTER TABLE knowledge_documents ADD COLUMN verified_at DATETIME NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='knowledge_documents' AND COLUMN_NAME='verification_note')=0, 'ALTER TABLE knowledge_documents ADD COLUMN verification_note VARCHAR(500) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
-- 기존 요약 자료(std-pronunciation-1988)는 분류만 채운다. 개발자 요약이고 사용 권한이 '법적 검토 필요'라 PENDING(검색 제외)으로 둔다.
UPDATE knowledge_documents SET category='PRONUNCIATION_RULE' WHERE document_id='std-pronunciation-1988' AND category IS NULL;

-- 원문 자료: 국립국어원 「한국어 어문 규범」 표준 발음법 조문·해설(출처·사용 권한 확인, 내용 검수 전).
-- 교사 승인 설명 예시(TEACHER_EXAMPLE)는 실제 교사가 작성·승인한 것만 넣는다. 임의 예시를 seed로 넣지 않는다(docs/design/ai-feedback.md 9.2 참고).
-- BEGIN knowledge-seed: scripts/knowledge-content/build_seed.py 로 다시 만든다(직접 고치지 않는다).
-- 출처: https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002 (2026-10-06 추출). 원문 조문·해설을 그대로 옮겼다.
-- 출처·사용 권한 확인 완료(VERIFIED), 내용 검수 전(DRAFT): 교사·운영자가 승인(review_status=APPROVED, reviewed_by/at)해야 AI 검색 대상이 된다.
INSERT IGNORE INTO knowledge_documents(document_id,title,source_citation,source_url,publisher,author,published_year,topic,scope,license_note,category,source_version,verification_status,verified_at,verification_note,review_status,version) VALUES
('nikl-pron-consonant','표준 발음법 제2장 자음과 모음(자음 조항)','문화체육관광부 고시 제2017-13호(2017. 3. 28.) 「표준어 규정」 제2부 표준 발음법. 국립국어원 「한국어 어문 규범」에서 제공하는 현행 원문.','https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002','국립국어원',NULL,2017,'자음','표준어를 말할 때의 표준 발음 규범(원문). 아동 조음 발달·조음 오류의 진단이나 치료 기준이 아니다.','국립국어원 누리집 저작권 정책: 누리집 제공 자료는 저작권법 제24조의2(공공저작물의 자유이용)에 따라 별도 이용허락 없이 이용 가능(해당 페이지에 별도 공공누리 유형 표시 없음, 2026-10-06 확인). 조문은 정부 고시(저작권법 제7조의 보호받지 못하는 저작물). 출처 표시: 국립국어원 「한국어 어문 규범」 표준 발음법.','CONSONANT','문화체육관광부 고시 제2017-13호(2017. 3. 28.) 「표준어 규정」 제2부 표준 발음법','VERIFIED','2026-10-06 00:00:00','2026-10-06 원문 페이지(https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002)에서 조문·해설을 그대로 추출하고 국립국어원 저작권 정책(https://www.korean.go.kr/front/nuri/pageView.do?page_id=P000189&mkn=3)을 확인함. 내용 검수(승인)는 별도.','DRAFT',1),
('nikl-pron-vowel','표준 발음법 제2장 자음과 모음(모음 조항)','문화체육관광부 고시 제2017-13호(2017. 3. 28.) 「표준어 규정」 제2부 표준 발음법. 국립국어원 「한국어 어문 규범」에서 제공하는 현행 원문.','https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002','국립국어원',NULL,2017,'모음','표준어를 말할 때의 표준 발음 규범(원문). 아동 조음 발달·조음 오류의 진단이나 치료 기준이 아니다.','국립국어원 누리집 저작권 정책: 누리집 제공 자료는 저작권법 제24조의2(공공저작물의 자유이용)에 따라 별도 이용허락 없이 이용 가능(해당 페이지에 별도 공공누리 유형 표시 없음, 2026-10-06 확인). 조문은 정부 고시(저작권법 제7조의 보호받지 못하는 저작물). 출처 표시: 국립국어원 「한국어 어문 규범」 표준 발음법.','VOWEL','문화체육관광부 고시 제2017-13호(2017. 3. 28.) 「표준어 규정」 제2부 표준 발음법','VERIFIED','2026-10-06 00:00:00','2026-10-06 원문 페이지(https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002)에서 조문·해설을 그대로 추출하고 국립국어원 저작권 정책(https://www.korean.go.kr/front/nuri/pageView.do?page_id=P000189&mkn=3)을 확인함. 내용 검수(승인)는 별도.','DRAFT',1),
('nikl-pron-coda','표준 발음법 제4장 받침의 발음','문화체육관광부 고시 제2017-13호(2017. 3. 28.) 「표준어 규정」 제2부 표준 발음법. 국립국어원 「한국어 어문 규범」에서 제공하는 현행 원문.','https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002','국립국어원',NULL,2017,'받침','표준어를 말할 때의 표준 발음 규범(원문). 아동 조음 발달·조음 오류의 진단이나 치료 기준이 아니다.','국립국어원 누리집 저작권 정책: 누리집 제공 자료는 저작권법 제24조의2(공공저작물의 자유이용)에 따라 별도 이용허락 없이 이용 가능(해당 페이지에 별도 공공누리 유형 표시 없음, 2026-10-06 확인). 조문은 정부 고시(저작권법 제7조의 보호받지 못하는 저작물). 출처 표시: 국립국어원 「한국어 어문 규범」 표준 발음법.','CODA','문화체육관광부 고시 제2017-13호(2017. 3. 28.) 「표준어 규정」 제2부 표준 발음법','VERIFIED','2026-10-06 00:00:00','2026-10-06 원문 페이지(https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002)에서 조문·해설을 그대로 추출하고 국립국어원 저작권 정책(https://www.korean.go.kr/front/nuri/pageView.do?page_id=P000189&mkn=3)을 확인함. 내용 검수(승인)는 별도.','DRAFT',1),
('nikl-pron-assimilation','표준 발음법 제5장 음의 동화·제6장 경음화(일부 조항)','문화체육관광부 고시 제2017-13호(2017. 3. 28.) 「표준어 규정」 제2부 표준 발음법. 국립국어원 「한국어 어문 규범」에서 제공하는 현행 원문.','https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002','국립국어원',NULL,2017,'표준 발음·음운 규칙','표준어를 말할 때의 표준 발음 규범(원문). 아동 조음 발달·조음 오류의 진단이나 치료 기준이 아니다.','국립국어원 누리집 저작권 정책: 누리집 제공 자료는 저작권법 제24조의2(공공저작물의 자유이용)에 따라 별도 이용허락 없이 이용 가능(해당 페이지에 별도 공공누리 유형 표시 없음, 2026-10-06 확인). 조문은 정부 고시(저작권법 제7조의 보호받지 못하는 저작물). 출처 표시: 국립국어원 「한국어 어문 규범」 표준 발음법.','PRONUNCIATION_RULE','문화체육관광부 고시 제2017-13호(2017. 3. 28.) 「표준어 규정」 제2부 표준 발음법','VERIFIED','2026-10-06 00:00:00','2026-10-06 원문 페이지(https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002)에서 조문·해설을 그대로 추출하고 국립국어원 저작권 정책(https://www.korean.go.kr/front/nuri/pageView.do?page_id=P000189&mkn=3)을 확인함. 내용 검수(승인)는 별도.','DRAFT',1),
('nikl-pron-place','표준 발음법 제2항(자음의 조음 위치) 해설','문화체육관광부 고시 제2017-13호(2017. 3. 28.) 「표준어 규정」 제2부 표준 발음법. 국립국어원 「한국어 어문 규범」에서 제공하는 현행 원문. 해설 부분.','https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002','국립국어원',NULL,2017,'자음의 조음 위치','표준 발음법 조항에 대한 국립국어원 해설(원문). 아동 조음 발달·조음 오류의 진단이나 치료 기준이 아니다.','국립국어원 누리집 저작권 정책: 누리집 제공 자료는 저작권법 제24조의2(공공저작물의 자유이용)에 따라 별도 이용허락 없이 이용 가능(해당 페이지에 별도 공공누리 유형 표시 없음, 2026-10-06 확인). 해설은 국립국어원이 작성한 공공저작물이며 출처를 표시한다: 국립국어원 「한국어 어문 규범」 표준 발음법 해설.','ARTICULATION_PLACE','문화체육관광부 고시 제2017-13호(2017. 3. 28.) 「표준어 규정」 제2부 표준 발음법','VERIFIED','2026-10-06 00:00:00','2026-10-06 원문 페이지(https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002)에서 조문·해설을 그대로 추출하고 국립국어원 저작권 정책(https://www.korean.go.kr/front/nuri/pageView.do?page_id=P000189&mkn=3)을 확인함. 내용 검수(승인)는 별도.','DRAFT',1),
('nikl-pron-manner','표준 발음법 제2항(자음의 조음 방법) 해설','문화체육관광부 고시 제2017-13호(2017. 3. 28.) 「표준어 규정」 제2부 표준 발음법. 국립국어원 「한국어 어문 규범」에서 제공하는 현행 원문. 해설 부분.','https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002','국립국어원',NULL,2017,'자음의 조음 방법','표준 발음법 조항에 대한 국립국어원 해설(원문). 아동 조음 발달·조음 오류의 진단이나 치료 기준이 아니다.','국립국어원 누리집 저작권 정책: 누리집 제공 자료는 저작권법 제24조의2(공공저작물의 자유이용)에 따라 별도 이용허락 없이 이용 가능(해당 페이지에 별도 공공누리 유형 표시 없음, 2026-10-06 확인). 해설은 국립국어원이 작성한 공공저작물이며 출처를 표시한다: 국립국어원 「한국어 어문 규범」 표준 발음법 해설.','ARTICULATION_MANNER','문화체육관광부 고시 제2017-13호(2017. 3. 28.) 「표준어 규정」 제2부 표준 발음법','VERIFIED','2026-10-06 00:00:00','2026-10-06 원문 페이지(https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002)에서 조문·해설을 그대로 추출하고 국립국어원 저작권 정책(https://www.korean.go.kr/front/nuri/pageView.do?page_id=P000189&mkn=3)을 확인함. 내용 검수(승인)는 별도.','DRAFT',1),
('nikl-pron-vowel-guide','표준 발음법 제4항·제5항(모음의 분류) 해설','문화체육관광부 고시 제2017-13호(2017. 3. 28.) 「표준어 규정」 제2부 표준 발음법. 국립국어원 「한국어 어문 규범」에서 제공하는 현행 원문. 해설 부분.','https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002','국립국어원',NULL,2017,'모음의 분류','표준 발음법 조항에 대한 국립국어원 해설(원문). 아동 조음 발달·조음 오류의 진단이나 치료 기준이 아니다.','국립국어원 누리집 저작권 정책: 누리집 제공 자료는 저작권법 제24조의2(공공저작물의 자유이용)에 따라 별도 이용허락 없이 이용 가능(해당 페이지에 별도 공공누리 유형 표시 없음, 2026-10-06 확인). 해설은 국립국어원이 작성한 공공저작물이며 출처를 표시한다: 국립국어원 「한국어 어문 규범」 표준 발음법 해설.','VOWEL','문화체육관광부 고시 제2017-13호(2017. 3. 28.) 「표준어 규정」 제2부 표준 발음법','VERIFIED','2026-10-06 00:00:00','2026-10-06 원문 페이지(https://korean.go.kr/kornorms/regltn/regltnView.do?regltn_code=0002)에서 조문·해설을 그대로 추출하고 국립국어원 저작권 정책(https://www.korean.go.kr/front/nuri/pageView.do?page_id=P000189&mkn=3)을 확인함. 내용 검수(승인)는 별도.','DRAFT',1);
INSERT IGNORE INTO knowledge_chunks(chunk_id,document_id,chunk_order,location,content,tags) VALUES
('nikl-pron-consonant-02','nikl-pron-consonant',2,'제2장 제2항','표준어의 자음은 다음 19개로 한다. ㄱ, ㄲ, ㄴ, ㄷ, ㄸ, ㄹ, ㅁ, ㅂ, ㅃ, ㅅ, ㅆ, ㅇ, ㅈ, ㅉ, ㅊ, ㅋ, ㅌ, ㅍ, ㅎ','slot:ONSET,phoneme:ㄱ,phoneme:ㄲ,phoneme:ㄴ,phoneme:ㄷ,phoneme:ㄸ,phoneme:ㄹ,phoneme:ㅁ,phoneme:ㅂ,phoneme:ㅃ,phoneme:ㅅ,phoneme:ㅆ,phoneme:ㅇ,phoneme:ㅈ,phoneme:ㅉ,phoneme:ㅊ,phoneme:ㅋ,phoneme:ㅌ,phoneme:ㅍ,phoneme:ㅎ'),
('nikl-pron-vowel-03','nikl-pron-vowel',3,'제2장 제3항','표준어의 모음은 다음 21개로 한다. ㅏ, ㅐ, ㅑ, ㅒ, ㅓ, ㅔ, ㅕ, ㅖ, ㅗ, ㅘ, ㅙ, ㅚ, ㅛ, ㅜ, ㅝ, ㅞ, ㅟ, ㅠ, ㅡ, ㅢ, ㅣ','slot:NUCLEUS,phoneme:ㅏ,phoneme:ㅐ,phoneme:ㅑ,phoneme:ㅒ,phoneme:ㅓ,phoneme:ㅔ,phoneme:ㅕ,phoneme:ㅖ,phoneme:ㅗ,phoneme:ㅘ,phoneme:ㅙ,phoneme:ㅚ,phoneme:ㅛ,phoneme:ㅜ,phoneme:ㅝ,phoneme:ㅞ,phoneme:ㅟ,phoneme:ㅠ,phoneme:ㅡ,phoneme:ㅢ,phoneme:ㅣ'),
('nikl-pron-vowel-04','nikl-pron-vowel',4,'제2장 제4항','‘ㅏ ㅐ ㅓ ㅔ ㅗ ㅚ ㅜ ㅟ ㅡ ㅣ’는 단모음(單母音)으로 발음한다. [붙임] ‘ㅚ, ㅟ’는 이중 모음으로 발음할 수 있다.','slot:NUCLEUS,phoneme:ㅏ,phoneme:ㅐ,phoneme:ㅓ,phoneme:ㅔ,phoneme:ㅗ,phoneme:ㅚ,phoneme:ㅜ,phoneme:ㅟ,phoneme:ㅡ,phoneme:ㅣ'),
('nikl-pron-vowel-05','nikl-pron-vowel',5,'제2장 제5항','‘ㅑ ㅒ ㅕ ㅖ ㅘ ㅙ ㅛ ㅝ ㅞ ㅠ ㅢ’는 이중 모음으로 발음한다. 다만 1. 용언의 활용형에 나타나는 ‘져, 쪄, 쳐’는 [저, 쩌, 처]로 발음한다. 가지어→가져[가저], 찌어→쪄[쩌], 다치어→다쳐[다처] 다만 2. ‘예, 례'' 이외의 ‘ㅖ’는 [ㅔ]로도 발음한다. 계집[계ː집/게ː집], 계시다[계ː시다/게ː시다], 시계[시계/시게](時計), 연계[연계/연게](連繫), 몌별[몌별/메별](袂別), 개폐[개폐/개페](開閉), 혜택[혜ː택/헤ː택](惠澤), 지혜[지혜/지헤](智慧) 다만 3. 자음을 첫소리로 가지고 있는 음절의 ‘ㅢ’는 [ㅣ]로 발음한다. 늴리리, 닁큼, 무늬, 띄어쓰기, 씌어, 틔어, 희어, 희떱다, 희망, 유희 다만 4. 단어의 첫음절 이외의 ‘의’는 [ㅣ]로, 조사 ‘의’는 [ㅔ]로 발음함도 허용한다. 주의[주의/주이], 협의[혀븨/혀비], 우리의[우리의/우리에], 강의의[강ː의의/강ː이에]','slot:NUCLEUS,phoneme:ㅑ,phoneme:ㅒ,phoneme:ㅕ,phoneme:ㅖ,phoneme:ㅘ,phoneme:ㅙ,phoneme:ㅛ,phoneme:ㅝ,phoneme:ㅞ,phoneme:ㅠ,phoneme:ㅢ'),
('nikl-pron-coda-08','nikl-pron-coda',8,'제4장 제8항','받침소리로는 ‘ㄱ, ㄴ, ㄷ, ㄹ, ㅁ, ㅂ, ㅇ’의 7개 자음만 발음한다.','rule:8,slot:CODA,phoneme:ㄱ,phoneme:ㄴ,phoneme:ㄷ,phoneme:ㄹ,phoneme:ㅁ,phoneme:ㅂ,phoneme:ㅇ'),
('nikl-pron-coda-09','nikl-pron-coda',9,'제4장 제9항','받침 ‘ㄲ, ㅋ’, ‘ㅅ, ㅆ, ㅈ, ㅊ, ㅌ’, ‘ㅍ’은 어말 또는 자음 앞에서 각각 대표음 [ㄱ, ㄷ, ㅂ]으로 발음한다. 닦다[닥따], 키읔[키윽], 키읔과[키윽꽈], 옷[옫], 웃다[욷ː따], 있다[읻따], 젖[젇], 빚다[빋따], 꽃[꼳], 쫓다[쫃따], 솥[솓], 뱉다[밷ː따], 앞[압], 덮다[덥따]','rule:9,slot:CODA,phoneme:ㄲ,phoneme:ㅋ,phoneme:ㅅ,phoneme:ㅆ,phoneme:ㅈ,phoneme:ㅊ,phoneme:ㅌ,phoneme:ㅍ,phoneme:ㄱ,phoneme:ㄷ,phoneme:ㅂ'),
('nikl-pron-coda-10','nikl-pron-coda',10,'제4장 제10항','겹받침 ‘ㄳ’, ‘ㄵ’, ‘ㄼ, ㄽ, ㄾ’, ‘ㅄ’은 어말 또는 자음 앞에서 각각 [ㄱ, ㄴ, ㄹ, ㅂ]으로 발음한다. 넋[넉], 넋과[넉꽈], 앉다[안따], 여덟[여덜], 넓다[널따], 외곬[외골], 핥다[할따], 값[갑], 없다[업ː따] 다만, ‘밟-’은 자음 앞에서 [밥]으로 발음하고, ‘넓-’은 다음과 같은 경우에 [넙]으로 발음한다. (1) 밟다[밥ː따], 밟소[밥ː쏘], 밟지[밥ː찌], 밟는[밥ː는→밤ː는], 밟게[밥ː께], 밟고[밥ː꼬] (2) 넓-죽하다[넙쭈카다], 넓-둥글다[넙뚱글다]','slot:CODA,phoneme:ㄳ,phoneme:ㄵ,phoneme:ㄼ,phoneme:ㄽ,phoneme:ㄾ,phoneme:ㅄ,phoneme:ㄱ,phoneme:ㄴ,phoneme:ㄹ,phoneme:ㅂ'),
('nikl-pron-coda-11','nikl-pron-coda',11,'제4장 제11항','겹받침 ‘ㄺ, ㄻ, ㄿ’은 어말 또는 자음 앞에서 각각 [ㄱ, ㅁ, ㅂ]으로 발음한다. 닭[닥], 흙과[흑꽈], 맑다[막따], 늙지[늑찌], 삶[삼ː], 젊다[점ː따], 읊고[읍꼬], 읊다[읍따] 다만, 용언의 어간 말음 ‘ㄺ’은 ‘ㄱ’ 앞에서 [ㄹ]로 발음한다. 맑게[말께], 묽고[물꼬], 얽거나[얼꺼나]','slot:CODA,phoneme:ㄺ,phoneme:ㄻ,phoneme:ㄿ,phoneme:ㄱ,phoneme:ㅁ,phoneme:ㅂ'),
('nikl-pron-coda-12','nikl-pron-coda',12,'제4장 제12항','받침 ‘ㅎ’의 발음은 다음과 같다. 1. ‘ㅎ(ㄶ, ㅀ)’ 뒤에 ‘ㄱ, ㄷ, ㅈ’이 결합되는 경우에는, 뒤 음절 첫소리와 합쳐서 [ㅋ, ㅌ, ㅊ]으로 발음한다. 놓고[노코], 좋던[조ː턴], 쌓지[싸치], 많고[만ː코], 않던[안턴], 닳지[달치] [붙임 1] 받침 ‘ㄱ(ㄺ), ㄷ, ㅂ(ㄼ), ㅈ(ㄵ)’이 뒤 음절 첫소리 ‘ㅎ’과 결합되는 경우에도, 역시 두 음을 합쳐서 [ㅋ, ㅌ, ㅍ, ㅊ]으로 발음한다. 각하[가카], 먹히다[머키다], 밝히다[발키다], 맏형[마텽], 좁히다[조피다], 넓히다[널피다], 꽂히다[꼬치다], 앉히다[안치다] [붙임 2] 규정에 따라 ''ㄷ''으로 발음되는 ‘ㅅ, ㅈ, ㅊ, ㅌ’의 경우에도 이에 준한다. 옷 한 벌[오탄벌], 낮 한때[나탄때], 꽃 한 송이[꼬탄송이], 숱하다[수타다] 2. ‘ㅎ(ㄶ, ㅀ)’ 뒤에 ‘ㅅ’이 결합되는 경우에는, ‘ㅅ’을 [ㅆ]으로 발음한다. 닿소[다ː쏘], 많소[만ː쏘], 싫소[실쏘] 3. ‘ㅎ’ 뒤에 ‘ㄴ’이 결합되는 경우에는, [ㄴ]으로 발음한다. 놓는[논는], 쌓네[싼네] [붙임] ‘ㄶ, ㅀ’ 뒤에 ‘ㄴ’이 결합되는 경우에는, ‘ㅎ’을 발음하지 않는다. 않네[안네], 않는[안는], 뚫네[뚤네→뚤레], 뚫는[뚤는→뚤른] * ‘뚫네[뚤네→뚤레], 뚫는[뚤는→뚤른]’에 대해서는 제20항 참조. 4. ‘ㅎ(ㄶ, ㅀ)’ 뒤에 모음으로 시작된 어미나 접미사가 결합되는 경우에는, ‘ㅎ’을 발음하지 않는다. 낳은[나은], 놓아[노아], 쌓이다[싸이다], 많아[마ː나], 않은[아는], 닳아[다라], 싫어도[시러도]','slot:CODA,phoneme:ㅎ'),
('nikl-pron-coda-13','nikl-pron-coda',13,'제4장 제13항','홑받침이나 쌍받침이 모음으로 시작된 조사나 어미, 접미사와 결합되는 경우에는, 제 음가대로 뒤 음절 첫소리로 옮겨 발음한다. 깎아[까까], 옷이[오시], 있어[이써], 낮이[나지], 꽂아[꼬자], 꽃을[꼬츨], 쫓아[쪼차], 밭에[바테], 앞으로[아프로], 덮이다[더피다]','rule:13,slot:CODA,slot:ONSET'),
('nikl-pron-coda-14','nikl-pron-coda',14,'제4장 제14항','겹받침이 모음으로 시작된 조사나 어미, 접미사와 결합되는 경우에는, 뒤엣것만을 뒤 음절 첫소리로 옮겨 발음한다.(이 경우, ‘ㅅ’은 된소리로 발음함.) 넋이[넉씨], 앉아[안자], 닭을[달글], 젊어[절머], 곬이[골씨], 핥아[할타], 읊어[을퍼], 값을[갑쓸], 없어[업ː써]','slot:CODA,phoneme:ㅅ'),
('nikl-pron-coda-15','nikl-pron-coda',15,'제4장 제15항','받침 뒤에 모음 ‘ㅏ, ㅓ, ㅗ, ㅜ, ㅟ’ 들로 시작되는 실질 형태소가 연결되는 경우에는, 대표음으로 바꾸어서 뒤 음절 첫소리로 옮겨 발음한다. 밭 아래[바다래], 늪 앞[느밥], 젖어미[저더미], 맛없다[마덥따], 겉옷[거돋], 헛웃음[허두슴], 꽃 위[꼬뒤] 다만, ‘맛있다, 멋있다’는 [마싣따], [머싣따]로도 발음할 수 있다. [붙임] 겹받침의 경우에는, 그중 하나만을 옮겨 발음한다. 넋 없다[너겁따], 닭 앞에[다가페], 값어치[가버치], 값있는[가빈는]','slot:CODA,phoneme:ㅏ,phoneme:ㅓ,phoneme:ㅗ,phoneme:ㅜ,phoneme:ㅟ'),
('nikl-pron-coda-16','nikl-pron-coda',16,'제4장 제16항','한글 자모의 이름은 그 받침소리를 연음하되, ‘ㄷ, ㅈ, ㅊ, ㅋ, ㅌ, ㅍ, ㅎ’의 경우에는 특별히 다음과 같이 발음한다. 디귿이[디그시], 디귿을[디그슬], 디귿에[디그세], 지읒이[지으시], 지읒을[지으슬], 지읒에[지으세], 치읓이[치으시], 치읓을[치으슬], 치읓에[치으세], 키읔이[키으기], 키읔을[키으글], 키읔에[키으게], 티읕이[티으시], 티읕을[티으슬], 티읕에[티으세], 피읖이[피으비], 피읖을[피으블], 피읖에[피으베], 히읗이[히으시], 히읗을[히으슬], 히읗에[히으세]','slot:CODA,phoneme:ㄷ,phoneme:ㅈ,phoneme:ㅊ,phoneme:ㅋ,phoneme:ㅌ,phoneme:ㅍ,phoneme:ㅎ'),
('nikl-pron-assimilation-17','nikl-pron-assimilation',17,'제5장 제17항','받침 ‘ㄷ, ㅌ(ㄾ)’이 조사나 접미사의 모음 ‘ㅣ’와 결합되는 경우에는, [ㅈ, ㅊ]으로 바꾸어서 뒤 음절 첫소리로 옮겨 발음한다. 곧이듣다[고지듣따], 굳이[구지], 미닫이[미ː다지], 땀받이[땀바지], 밭이[바치], 벼훑이[벼훌치] [붙임] ‘ㄷ’ 뒤에 접미사 ‘히’가 결합되어 ‘티’를 이루는 것은 [치]로 발음한다. 굳히다[구치다], 닫히다[다치다], 묻히다[무치다]','rule:17,slot:CODA,phoneme:ㄷ,phoneme:ㅌ,phoneme:ㄾ,phoneme:ㅣ,phoneme:ㅈ,phoneme:ㅊ'),
('nikl-pron-assimilation-18','nikl-pron-assimilation',18,'제5장 제18항','받침 ‘ㄱ(ㄲ, ㅋ, ㄳ, ㄺ), ㄷ(ㅅ, ㅆ, ㅈ, ㅊ, ㅌ, ㅎ), ㅂ(ㅍ, ㄼ, ㄿ, ㅄ)’은 ‘ㄴ, ㅁ’ 앞에서 [ㅇ, ㄴ, ㅁ]으로 발음한다. 먹는[멍는], 국물[궁물], 깎는[깡는], 키읔만[키응만], 몫몫이[몽목씨], 긁는[긍는], 흙만[흥만], 닫는[단는], 짓는[진ː는], 옷맵시[온맵씨], 있는[인는], 맞는[만는], 젖멍울[전멍울], 쫓는[쫀는], 꽃망울[꼰망울], 붙는[분는], 놓는[논는], 잡는[잠는], 밥물[밤물], 앞마당[암마당], 밟는[밤ː는], 읊는[음는], 없는[엄ː는] [붙임] 두 단어를 이어서 한 마디로 발음하는 경우에도 이와 같다. 책 넣는다[챙넌는다], 흙 말리다[흥말리다], 옷 맞추다[온맏추다], 밥 먹는다[밤멍는다], 값 매기다[감매기다]','rule:18,slot:CODA,phoneme:ㄱ,phoneme:ㄲ,phoneme:ㅋ,phoneme:ㄳ,phoneme:ㄺ,phoneme:ㄷ,phoneme:ㅅ,phoneme:ㅆ,phoneme:ㅈ,phoneme:ㅊ,phoneme:ㅌ,phoneme:ㅎ,phoneme:ㅂ,phoneme:ㅍ,phoneme:ㄼ,phoneme:ㄿ,phoneme:ㅄ,phoneme:ㄴ,phoneme:ㅁ,phoneme:ㅇ'),
('nikl-pron-assimilation-19','nikl-pron-assimilation',19,'제5장 제19항','받침 ‘ㅁ, ㅇ’ 뒤에 연결되는 ‘ㄹ’은 [ㄴ]으로 발음한다. 담력[담ː녁], 침략[침ː냑], 강릉[강능], 항로[항ː노], 대통령[대ː통녕] [붙임] 받침 ‘ㄱ, ㅂ’ 뒤에 연결되는 ‘ㄹ’도 [ㄴ]으로 발음한다. 막론[막논→망논], 석류[석뉴→성뉴], 협력[협녁→혐녁], 법리[법니→범니]','rule:19,slot:ONSET,phoneme:ㅁ,phoneme:ㅇ,phoneme:ㄹ,phoneme:ㄴ'),
('nikl-pron-assimilation-20','nikl-pron-assimilation',20,'제5장 제20항','‘ㄴ’은 ‘ㄹ’의 앞이나 뒤에서 [ㄹ]로 발음한다. (1) 난로[날ː로], 신라[실라], 천리[철리], 광한루[광ː할루], 대관령[대ː괄령] (2) 칼날[칼랄], 물난리[물랄리], 줄넘기[줄럼끼], 할는지[할른지] [붙임] 첫소리 ‘ㄴ’이 ‘ㅀ’, ‘ㄾ’ 뒤에 연결되는 경우에도 이에 준한다. 닳는[달른], 뚫는[뚤른], 핥네[할레] 다만, 다음과 같은 단어들은 ‘ㄹ’을 [ㄴ]으로 발음한다. 의견란[의ː견난], 임진란[임ː진난], 생산량[생산냥], 결단력[결딴녁], 공권력[공꿘녁], 동원령[동ː원녕], 상견례[상견녜], 횡단로[횡단노], 이원론[이ː원논], 입원료[이붠뇨], 구근류[구근뉴]','rule:20,slot:CODA,slot:ONSET,phoneme:ㄴ,phoneme:ㄹ'),
('nikl-pron-assimilation-23','nikl-pron-assimilation',23,'제6장 제23항','받침 ‘ㄱ(ㄲ, ㅋ, ㄳ, ㄺ), ㄷ(ㅅ, ㅆ, ㅈ, ㅊ, ㅌ), ㅂ(ㅍ, ㄼ, ㄿ, ㅄ)’ 뒤에 연결되는 ‘ㄱ, ㄷ, ㅂ, ㅅ, ㅈ’은 된소리로 발음한다. 국밥[국빱], 깎다[깍따], 넋받이[넉빠지], 삯돈[삭똔], 닭장[닥짱], 칡범[칙뻠], 뻗대다[뻗때다], 옷고름[옫꼬름], 있던[읻떤], 꽂고[꼳꼬], 꽃다발[꼳따발], 낯설다[낟썰다], 밭갈이[받까리], 솥전[솓쩐], 곱돌[곱똘], 덮개[덥깨], 옆집[엽찝], 넓죽하다[넙쭈카다], 읊조리다[읍쪼리다], 값지다[갑찌다]','rule:23,slot:ONSET,phoneme:ㄱ,phoneme:ㄲ,phoneme:ㅋ,phoneme:ㄳ,phoneme:ㄺ,phoneme:ㄷ,phoneme:ㅅ,phoneme:ㅆ,phoneme:ㅈ,phoneme:ㅊ,phoneme:ㅌ,phoneme:ㅂ,phoneme:ㅍ,phoneme:ㄼ,phoneme:ㄿ,phoneme:ㅄ'),
('nikl-pron-place-01','nikl-pron-place',1,'제2장 제2항 해설(자음 분류표)','국어의 자음은 5개의 조음 위치에서 발음된다. 두 입술에서 내는 양순음, 혀끝을 치조 부위에 대거나 접근하여 내는 치조음, 혀의 앞부분을 경구개 부위에 대어 내는 경구개음, 혀의 뒷부분을 연구개 부위에 대어 내는 연구개음, 성문에서 내는 후음이 있다. [분류표] 양순음: ㅂ(파열음 평음), ㅃ(파열음 경음), ㅍ(파열음 격음), ㅁ(비음)','phoneme:ㅂ,phoneme:ㅃ,phoneme:ㅍ,phoneme:ㅁ'),
('nikl-pron-place-02','nikl-pron-place',2,'제2장 제2항 해설(자음 분류표)','국어의 자음은 5개의 조음 위치에서 발음된다. 두 입술에서 내는 양순음, 혀끝을 치조 부위에 대거나 접근하여 내는 치조음, 혀의 앞부분을 경구개 부위에 대어 내는 경구개음, 혀의 뒷부분을 연구개 부위에 대어 내는 연구개음, 성문에서 내는 후음이 있다. [분류표] 치조음: ㄷ(파열음 평음), ㄸ(파열음 경음), ㅌ(파열음 격음), ㅅ(마찰음 평음), ㅆ(마찰음 경음), ㄴ(비음), ㄹ(유음)','phoneme:ㄷ,phoneme:ㄸ,phoneme:ㅌ,phoneme:ㅅ,phoneme:ㅆ,phoneme:ㄴ,phoneme:ㄹ'),
('nikl-pron-place-03','nikl-pron-place',3,'제2장 제2항 해설(자음 분류표)','국어의 자음은 5개의 조음 위치에서 발음된다. 두 입술에서 내는 양순음, 혀끝을 치조 부위에 대거나 접근하여 내는 치조음, 혀의 앞부분을 경구개 부위에 대어 내는 경구개음, 혀의 뒷부분을 연구개 부위에 대어 내는 연구개음, 성문에서 내는 후음이 있다. [분류표] 경구개음: ㅈ(파찰음 평음), ㅉ(파찰음 경음), ㅊ(파찰음 격음)','phoneme:ㅈ,phoneme:ㅉ,phoneme:ㅊ'),
('nikl-pron-place-04','nikl-pron-place',4,'제2장 제2항 해설(자음 분류표)','국어의 자음은 5개의 조음 위치에서 발음된다. 두 입술에서 내는 양순음, 혀끝을 치조 부위에 대거나 접근하여 내는 치조음, 혀의 앞부분을 경구개 부위에 대어 내는 경구개음, 혀의 뒷부분을 연구개 부위에 대어 내는 연구개음, 성문에서 내는 후음이 있다. [분류표] 연구개음: ㄱ(파열음 평음), ㄲ(파열음 경음), ㅋ(파열음 격음), ㅇ(비음)','phoneme:ㄱ,phoneme:ㄲ,phoneme:ㅋ,phoneme:ㅇ'),
('nikl-pron-place-05','nikl-pron-place',5,'제2장 제2항 해설(자음 분류표)','국어의 자음은 5개의 조음 위치에서 발음된다. 두 입술에서 내는 양순음, 혀끝을 치조 부위에 대거나 접근하여 내는 치조음, 혀의 앞부분을 경구개 부위에 대어 내는 경구개음, 혀의 뒷부분을 연구개 부위에 대어 내는 연구개음, 성문에서 내는 후음이 있다. [분류표] 후음: ㅎ(마찰음)','phoneme:ㅎ'),
('nikl-pron-manner-01','nikl-pron-manner',1,'제2장 제2항 해설(자음 분류표)','국어의 자음은 소리 나는 방식에 따라서 다양하게 분류할 수 있다. 공기를 막았다가 터뜨리는 파열음, 좁은 틈으로 공기를 마찰하여 내는 마찰음, 공기를 막았다가 마찰하여 내는 파찰음, 코로 공기를 보내어 내는 비음, 공기의 흐름을 거의 방해하지 않으며 내는 유음의 다섯 가지로 구분된다. 파열음, 마찰음, 파찰음은 다시 평음(예사소리), 경음(된소리), 격음(거센소리)의 세 부류로 구분된다. 이러한 자음의 구분 방식은 국어의 특징 중 하나이다. [분류표] 파열음: ㅂ(양순음, 평음), ㄷ(치조음, 평음), ㄱ(연구개음, 평음), ㅃ(양순음, 경음), ㄸ(치조음, 경음), ㄲ(연구개음, 경음), ㅍ(양순음, 격음), ㅌ(치조음, 격음), ㅋ(연구개음, 격음)','phoneme:ㅂ,phoneme:ㄷ,phoneme:ㄱ,phoneme:ㅃ,phoneme:ㄸ,phoneme:ㄲ,phoneme:ㅍ,phoneme:ㅌ,phoneme:ㅋ'),
('nikl-pron-manner-02','nikl-pron-manner',2,'제2장 제2항 해설(자음 분류표)','국어의 자음은 소리 나는 방식에 따라서 다양하게 분류할 수 있다. 공기를 막았다가 터뜨리는 파열음, 좁은 틈으로 공기를 마찰하여 내는 마찰음, 공기를 막았다가 마찰하여 내는 파찰음, 코로 공기를 보내어 내는 비음, 공기의 흐름을 거의 방해하지 않으며 내는 유음의 다섯 가지로 구분된다. 파열음, 마찰음, 파찰음은 다시 평음(예사소리), 경음(된소리), 격음(거센소리)의 세 부류로 구분된다. 이러한 자음의 구분 방식은 국어의 특징 중 하나이다. [분류표] 마찰음: ㅅ(치조음, 평음), ㅎ(후음), ㅆ(치조음, 경음)','phoneme:ㅅ,phoneme:ㅎ,phoneme:ㅆ'),
('nikl-pron-manner-03','nikl-pron-manner',3,'제2장 제2항 해설(자음 분류표)','국어의 자음은 소리 나는 방식에 따라서 다양하게 분류할 수 있다. 공기를 막았다가 터뜨리는 파열음, 좁은 틈으로 공기를 마찰하여 내는 마찰음, 공기를 막았다가 마찰하여 내는 파찰음, 코로 공기를 보내어 내는 비음, 공기의 흐름을 거의 방해하지 않으며 내는 유음의 다섯 가지로 구분된다. 파열음, 마찰음, 파찰음은 다시 평음(예사소리), 경음(된소리), 격음(거센소리)의 세 부류로 구분된다. 이러한 자음의 구분 방식은 국어의 특징 중 하나이다. [분류표] 파찰음: ㅈ(경구개음, 평음), ㅉ(경구개음, 경음), ㅊ(경구개음, 격음)','phoneme:ㅈ,phoneme:ㅉ,phoneme:ㅊ'),
('nikl-pron-manner-04','nikl-pron-manner',4,'제2장 제2항 해설(자음 분류표)','국어의 자음은 소리 나는 방식에 따라서 다양하게 분류할 수 있다. 공기를 막았다가 터뜨리는 파열음, 좁은 틈으로 공기를 마찰하여 내는 마찰음, 공기를 막았다가 마찰하여 내는 파찰음, 코로 공기를 보내어 내는 비음, 공기의 흐름을 거의 방해하지 않으며 내는 유음의 다섯 가지로 구분된다. 파열음, 마찰음, 파찰음은 다시 평음(예사소리), 경음(된소리), 격음(거센소리)의 세 부류로 구분된다. 이러한 자음의 구분 방식은 국어의 특징 중 하나이다. [분류표] 비음: ㅁ(양순음), ㄴ(치조음), ㅇ(연구개음)','phoneme:ㅁ,phoneme:ㄴ,phoneme:ㅇ'),
('nikl-pron-manner-05','nikl-pron-manner',5,'제2장 제2항 해설(자음 분류표)','국어의 자음은 소리 나는 방식에 따라서 다양하게 분류할 수 있다. 공기를 막았다가 터뜨리는 파열음, 좁은 틈으로 공기를 마찰하여 내는 마찰음, 공기를 막았다가 마찰하여 내는 파찰음, 코로 공기를 보내어 내는 비음, 공기의 흐름을 거의 방해하지 않으며 내는 유음의 다섯 가지로 구분된다. 파열음, 마찰음, 파찰음은 다시 평음(예사소리), 경음(된소리), 격음(거센소리)의 세 부류로 구분된다. 이러한 자음의 구분 방식은 국어의 특징 중 하나이다. [분류표] 유음: ㄹ(치조음)','phoneme:ㄹ'),
('nikl-pron-manner-06','nikl-pron-manner',6,'제2장 제2항 해설','후음인 ‘ㅎ’은 격음이나 평음 등으로 분류하지 않고 제시하였다. 평음, 경음, 격음으로 구분하는 것은 파열음처럼 3항 대립이 있을 때 의미가 있는데, 후음은 ‘ㅎ’ 하나로, 대립하는 다른 자음이 없다. 따라서 비음이나 유음과 마찬가지로 구분하지 않고 제시하였다.','phoneme:ㅎ'),
('nikl-pron-vowel-guide-01','nikl-pron-vowel-guide',1,'제2장 제4항 해설(단모음 분류표)','단모음의 분류는 크게 혀의 위치, 입술 모양에 따라 이루어진다. 이 중 혀의 위치는 다시 전후 위치와 높이로 구별하기 때문에 실제로는 세 가지 기준에 따라 분류된다. 혀의 전후 위치에 따라 전설 모음과 후설 모음이, 혀의 높이에 따라 고모음, 중모음, 저모음이, 입술 모양에 따라 원순 모음과 평순 모음이 구분된다. [분류표] ㅣ: 전설 모음, 평순 모음, 고모음','phoneme:ㅣ'),
('nikl-pron-vowel-guide-02','nikl-pron-vowel-guide',2,'제2장 제4항 해설(단모음 분류표)','단모음의 분류는 크게 혀의 위치, 입술 모양에 따라 이루어진다. 이 중 혀의 위치는 다시 전후 위치와 높이로 구별하기 때문에 실제로는 세 가지 기준에 따라 분류된다. 혀의 전후 위치에 따라 전설 모음과 후설 모음이, 혀의 높이에 따라 고모음, 중모음, 저모음이, 입술 모양에 따라 원순 모음과 평순 모음이 구분된다. [분류표] ㅟ: 전설 모음, 원순 모음, 고모음','phoneme:ㅟ'),
('nikl-pron-vowel-guide-03','nikl-pron-vowel-guide',3,'제2장 제4항 해설(단모음 분류표)','단모음의 분류는 크게 혀의 위치, 입술 모양에 따라 이루어진다. 이 중 혀의 위치는 다시 전후 위치와 높이로 구별하기 때문에 실제로는 세 가지 기준에 따라 분류된다. 혀의 전후 위치에 따라 전설 모음과 후설 모음이, 혀의 높이에 따라 고모음, 중모음, 저모음이, 입술 모양에 따라 원순 모음과 평순 모음이 구분된다. [분류표] ㅡ: 후설 모음, 평순 모음, 고모음','phoneme:ㅡ'),
('nikl-pron-vowel-guide-04','nikl-pron-vowel-guide',4,'제2장 제4항 해설(단모음 분류표)','단모음의 분류는 크게 혀의 위치, 입술 모양에 따라 이루어진다. 이 중 혀의 위치는 다시 전후 위치와 높이로 구별하기 때문에 실제로는 세 가지 기준에 따라 분류된다. 혀의 전후 위치에 따라 전설 모음과 후설 모음이, 혀의 높이에 따라 고모음, 중모음, 저모음이, 입술 모양에 따라 원순 모음과 평순 모음이 구분된다. [분류표] ㅜ: 후설 모음, 원순 모음, 고모음','phoneme:ㅜ'),
('nikl-pron-vowel-guide-05','nikl-pron-vowel-guide',5,'제2장 제4항 해설(단모음 분류표)','단모음의 분류는 크게 혀의 위치, 입술 모양에 따라 이루어진다. 이 중 혀의 위치는 다시 전후 위치와 높이로 구별하기 때문에 실제로는 세 가지 기준에 따라 분류된다. 혀의 전후 위치에 따라 전설 모음과 후설 모음이, 혀의 높이에 따라 고모음, 중모음, 저모음이, 입술 모양에 따라 원순 모음과 평순 모음이 구분된다. [분류표] ㅔ: 전설 모음, 평순 모음, 중모음','phoneme:ㅔ'),
('nikl-pron-vowel-guide-06','nikl-pron-vowel-guide',6,'제2장 제4항 해설(단모음 분류표)','단모음의 분류는 크게 혀의 위치, 입술 모양에 따라 이루어진다. 이 중 혀의 위치는 다시 전후 위치와 높이로 구별하기 때문에 실제로는 세 가지 기준에 따라 분류된다. 혀의 전후 위치에 따라 전설 모음과 후설 모음이, 혀의 높이에 따라 고모음, 중모음, 저모음이, 입술 모양에 따라 원순 모음과 평순 모음이 구분된다. [분류표] ㅚ: 전설 모음, 원순 모음, 중모음','phoneme:ㅚ'),
('nikl-pron-vowel-guide-07','nikl-pron-vowel-guide',7,'제2장 제4항 해설(단모음 분류표)','단모음의 분류는 크게 혀의 위치, 입술 모양에 따라 이루어진다. 이 중 혀의 위치는 다시 전후 위치와 높이로 구별하기 때문에 실제로는 세 가지 기준에 따라 분류된다. 혀의 전후 위치에 따라 전설 모음과 후설 모음이, 혀의 높이에 따라 고모음, 중모음, 저모음이, 입술 모양에 따라 원순 모음과 평순 모음이 구분된다. [분류표] ㅓ: 후설 모음, 평순 모음, 중모음','phoneme:ㅓ'),
('nikl-pron-vowel-guide-08','nikl-pron-vowel-guide',8,'제2장 제4항 해설(단모음 분류표)','단모음의 분류는 크게 혀의 위치, 입술 모양에 따라 이루어진다. 이 중 혀의 위치는 다시 전후 위치와 높이로 구별하기 때문에 실제로는 세 가지 기준에 따라 분류된다. 혀의 전후 위치에 따라 전설 모음과 후설 모음이, 혀의 높이에 따라 고모음, 중모음, 저모음이, 입술 모양에 따라 원순 모음과 평순 모음이 구분된다. [분류표] ㅗ: 후설 모음, 원순 모음, 중모음','phoneme:ㅗ'),
('nikl-pron-vowel-guide-09','nikl-pron-vowel-guide',9,'제2장 제4항 해설(단모음 분류표)','단모음의 분류는 크게 혀의 위치, 입술 모양에 따라 이루어진다. 이 중 혀의 위치는 다시 전후 위치와 높이로 구별하기 때문에 실제로는 세 가지 기준에 따라 분류된다. 혀의 전후 위치에 따라 전설 모음과 후설 모음이, 혀의 높이에 따라 고모음, 중모음, 저모음이, 입술 모양에 따라 원순 모음과 평순 모음이 구분된다. [분류표] ㅐ: 전설 모음, 평순 모음, 저모음','phoneme:ㅐ'),
('nikl-pron-vowel-guide-10','nikl-pron-vowel-guide',10,'제2장 제4항 해설(단모음 분류표)','단모음의 분류는 크게 혀의 위치, 입술 모양에 따라 이루어진다. 이 중 혀의 위치는 다시 전후 위치와 높이로 구별하기 때문에 실제로는 세 가지 기준에 따라 분류된다. 혀의 전후 위치에 따라 전설 모음과 후설 모음이, 혀의 높이에 따라 고모음, 중모음, 저모음이, 입술 모양에 따라 원순 모음과 평순 모음이 구분된다. [분류표] ㅏ: 후설 모음, 평순 모음, 저모음','phoneme:ㅏ'),
('nikl-pron-vowel-guide-11','nikl-pron-vowel-guide',11,'제2장 제5항 해설','‘ㅑ, ㅒ, ㅕ, ㅖ, ㅛ, ㅠ’는 각각 반모음 ‘ㅣ[j]’와 단모음 ‘ㅏ, ㅐ, ㅓ, ㅔ, ㅗ, ㅜ’의 결합으로 이루어진다. ‘ㅢ’는 단모음 ‘ㅡ’와 반모음 ‘ㅣ[j]’의 결합으로 이루어진다. ‘ㅘ, ㅙ, ㅝ, ㅞ’는 각각 반모음 ‘ㅗ/ㅜ[w]’와 단모음 ‘ㅏ, ㅐ, ㅓ, ㅔ’의 결합으로 이루어진다.','phoneme:ㅑ,phoneme:ㅒ,phoneme:ㅕ,phoneme:ㅖ,phoneme:ㅘ,phoneme:ㅙ,phoneme:ㅛ,phoneme:ㅝ,phoneme:ㅞ,phoneme:ㅠ,phoneme:ㅢ');
-- END knowledge-seed

-- ── 학습 서비스 확장: 연습 콘텐츠 분류, 숙제↔콘텐츠, 연습 기록↔숙제 ──────────────
-- 모두 NULL 허용 추가 컬럼이다. 기존 연습 세트·숙제·기록은 그대로 동작한다(분류 없음 = 기존 콘텐츠, 숙제 없음 = 자율 연습).
-- difficulty: BEGINNER/INTERMEDIATE/ADVANCED, content_type: WORD/SHORT_SENTENCE/LONG_SENTENCE,
-- pronunciation_rule: BASIC_CONSONANT/BASIC_VOWEL/CODA/LIAISON/NASALIZATION/TENSIFICATION/PALATALIZATION/ASPIRATION/CONSONANT_ASSIMILATION/COMPREHENSIVE
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='exercises' AND COLUMN_NAME='difficulty')=0, 'ALTER TABLE exercises ADD COLUMN difficulty VARCHAR(20) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='exercises' AND COLUMN_NAME='content_type')=0, 'ALTER TABLE exercises ADD COLUMN content_type VARCHAR(20) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='exercises' AND COLUMN_NAME='pronunciation_rule')=0, 'ALTER TABLE exercises ADD COLUMN pronunciation_rule VARCHAR(40) NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='exercises' AND INDEX_NAME='idx_exercise_filters')=0, 'ALTER TABLE exercises ADD KEY idx_exercise_filters (active, pronunciation_rule, difficulty, content_type)', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
-- 숙제가 참조하는 연습 세트(없으면 기존처럼 자유 숙제)
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='homeworks' AND COLUMN_NAME='exercise_id')=0, 'ALTER TABLE homeworks ADD COLUMN exercise_id BIGINT NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='homeworks' AND CONSTRAINT_NAME='fk_homework_exercise')=0, 'ALTER TABLE homeworks ADD CONSTRAINT fk_homework_exercise FOREIGN KEY (exercise_id) REFERENCES exercises(exercise_id)', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
-- 연습 기록이 어떤 숙제로 수행되었는지(없으면 자율 연습). 숙제를 지워도 기록은 남는다(SET NULL).
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='practice_attempts' AND COLUMN_NAME='homework_id')=0, 'ALTER TABLE practice_attempts ADD COLUMN homework_id BIGINT NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='practice_attempts' AND CONSTRAINT_NAME='fk_attempt_homework')=0, 'ALTER TABLE practice_attempts ADD CONSTRAINT fk_attempt_homework FOREIGN KEY (homework_id) REFERENCES homeworks(homework_id) ON DELETE SET NULL', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;

-- ── 연습 기록 유형 구분: SELF(학생이 고른 자율 연습)·LESSON(수업 연습) 추가 ──────────────
-- ENUM에 값만 추가한다. 기존 PRACTICE 행은 자율/수업 출처를 알 수 없으므로 바꾸지 않고 '구분 전 기록'으로 남긴다.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='practice_attempts' AND COLUMN_NAME='attempt_type' AND COLUMN_TYPE LIKE '%''LESSON''%')=0,
  'ALTER TABLE practice_attempts MODIFY attempt_type ENUM(''PRACTICE'',''HOMEWORK'',''AI_CHAT'',''SELF'',''LESSON'') NOT NULL DEFAULT ''PRACTICE''', 'SELECT 1');
PREPARE schema_stmt FROM @ddl; EXECUTE schema_stmt; DEALLOCATE PREPARE schema_stmt;
