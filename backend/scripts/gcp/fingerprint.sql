-- 데이터 이전 검증용 지문(핵심 테이블 행 내용의 SHA-256). 원본·대상에서 같은 결과가 나와야 한다. 비밀값을 출력하지 않는다(해시만).
SET SESSION group_concat_max_len=1000000000;
SELECT 'users', SHA2(GROUP_CONCAT(CONCAT_WS('|',user_id,role,center_id,name,email,password_hash,IFNULL(age,''),status,token_version,created_at) ORDER BY user_id SEPARATOR '\n'),256) FROM users
UNION ALL SELECT 'student_profiles', SHA2(GROUP_CONCAT(CONCAT_WS('|',student_id,IFNULL(user_id,''),center_id,name,IFNULL(age,''),status,learner_type) ORDER BY student_id SEPARATOR '\n'),256) FROM student_profiles
UNION ALL SELECT 'teacher_students', SHA2(GROUP_CONCAT(CONCAT_WS('|',teacher_id,student_id,status) ORDER BY teacher_id,student_id SEPARATOR '\n'),256) FROM teacher_students
UNION ALL SELECT 'homeworks', SHA2(GROUP_CONCAT(CONCAT_WS('|',homework_id,teacher_id,student_id,title,status,due_date,IFNULL(exercise_id,''),version) ORDER BY homework_id SEPARATOR '\n'),256) FROM homeworks
UNION ALL SELECT 'homework_exercises', SHA2(GROUP_CONCAT(CONCAT_WS('|',homework_id,exercise_id,sort_order) ORDER BY homework_id,exercise_id SEPARATOR '\n'),256) FROM homework_exercises
UNION ALL SELECT 'practice_attempts', SHA2(GROUP_CONCAT(CONCAT_WS('|',attempt_id,student_id,exercise_id,item_id,IFNULL(analysis_id,''),IFNULL(match_rate,''),attempt_type,IFNULL(homework_id,''),created_at) ORDER BY attempt_id SEPARATOR '\n'),256) FROM practice_attempts
UNION ALL SELECT 'speech_analyses', SHA2(GROUP_CONCAT(CONCAT_WS('|',analysis_id,student_id,status,IFNULL(audio_path,''),IFNULL(transcript,''),created_at) ORDER BY analysis_id SEPARATOR '\n'),256) FROM speech_analyses
UNION ALL SELECT 'user_consents', SHA2(GROUP_CONCAT(CONCAT_WS('|',user_id,consent_type,agreed,policy_version,IFNULL(agreed_at,''),IFNULL(withdrawn_at,'')) ORDER BY user_id,consent_type SEPARATOR '\n'),256) FROM user_consents
UNION ALL SELECT 'conversations', SHA2(GROUP_CONCAT(CONCAT_WS('|',conversation_id,student_id,topic,status,IFNULL(summary,'')) ORDER BY conversation_id SEPARATOR '\n'),256) FROM conversations
UNION ALL SELECT 'exercises', SHA2(GROUP_CONCAT(CONCAT_WS('|',exercise_id,category_id,title,instruction,active) ORDER BY exercise_id SEPARATOR '\n'),256) FROM exercises
UNION ALL SELECT 'exercise_items', SHA2(GROUP_CONCAT(CONCAT_WS('|',exercise_id,sort_order,text_value,IFNULL(emoji,'')) ORDER BY exercise_id,sort_order SEPARATOR '\n'),256) FROM exercise_items
UNION ALL SELECT 'knowledge_chunks', SHA2(GROUP_CONCAT(CONCAT_WS('|',chunk_id,document_id,content) ORDER BY chunk_id SEPARATOR '\n'),256) FROM knowledge_chunks;
