package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.request.AttemptRequest;
import com.example.backend.chld.dto.request.MessageRequest;
import com.example.backend.chld.dto.response.PageResponse;
import com.example.backend.chld.mapper.RecommendationMapper;
import com.example.backend.chld.mapper.StudentMapper;
import com.example.backend.chld.mapper.UserMapper;
import com.example.backend.chld.service.ConsentPolicy;
import com.example.backend.chld.service.ConsentService;
import com.example.backend.chld.service.LearningReportService;
import com.example.backend.chld.service.RecommendationService;
import com.example.backend.chld.service.SpeechAnalysisService;
import com.example.backend.chld.service.SpeechRecognitionService;
import com.example.backend.chld.service.SpeechFeedbackService;
import com.example.backend.chld.service.StudentService;
import com.example.backend.global.jwt.TokenPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class StudentServiceImpl implements StudentService {
    private final SpeechFeedbackService speechFeedback;
    private final StudentMapper mapper;
    private final UserMapper users;
    private final ExternalProviderService providers;
    private final AudioStorageService audioStorage;
    private final ConversationPersistenceService conversationPersistence;
    private final SpeechAnalysisService speechAnalysis;
    private final SpeechRecognitionService recognizer;
    private final ConsentService consents;
    private final boolean localSpeech;
    private final LearningReportService learningReports;
    private final RecommendationService recommendations;
    private final RecommendationMapper contentLookup;

    public StudentServiceImpl(StudentMapper mapper, UserMapper users, ExternalProviderService providers, AudioStorageService audioStorage,
                              ConversationPersistenceService conversationPersistence, SpeechAnalysisService speechAnalysis,
                              SpeechRecognitionService recognizer, ConsentService consents, SpeechFeedbackService speechFeedback,
                              LearningReportService learningReports, RecommendationService recommendations, RecommendationMapper contentLookup,
                              @Value("${app.speech.engine:local}") String engine) {
        this.learningReports=learningReports; this.recommendations=recommendations; this.contentLookup=contentLookup;
        this.speechFeedback=speechFeedback;
        this.mapper=mapper; this.users=users; this.providers=providers; this.audioStorage=audioStorage; this.conversationPersistence=conversationPersistence;
        this.speechAnalysis=speechAnalysis; this.recognizer=recognizer; this.consents=consents;
        this.localSpeech=!"external".equalsIgnoreCase(engine==null?"":engine.trim());
    }

    @Override public Map<String,Object> profile(TokenPrincipal principal) {
        requireStudent(principal);
        Map<String,Object> profile=profileFor(principal.userId());
        profile.put("streak", activityStreak(mapper.findActivityDates(((Number) profile.get("studentId")).longValue())));
        profile.put("totalSessions", profile.getOrDefault("totalSessions", 0));
        profile.put("completedSessions", profile.getOrDefault("completedSessions", 0));
        profile.put("level", "학습 중");
        profile.put("totalAttempts", profile.getOrDefault("totalAttempts", 0));
        // 담당 선생님: 실제 담당 연결이 있는 경우만(없으면 빈 목록 = 개인 학습)
        profile.put("teachers", mapper.findAssignedTeachers(((Number) profile.get("studentId")).longValue()));
        return profile;
    }

    /**
     * 학습 현황 = 누적 학습 리포트(선생님·보호자 화면과 같은 집계). period 없이 부르면 전체 기간(기존 호출과 같은 범위).
     * 기존 응답 키(trend: 최근 14일, recent: 최근 학습 기록 5개)는 그대로 둔다.
     */
    @Override public Map<String,Object> learningStatus(TokenPrincipal principal,String period) {
        long studentId=studentId(principal);
        Map<String,Object> status=new LinkedHashMap<>(learningReports.report(studentId,period));
        status.put("trend",mapper.findLearningTrend(studentId,13));
        status.put("recent",mapper.findHistory(studentId,"word",5,0));
        return status;
    }

    /** 본인 기록 기반 규칙형 추천(학생 ID는 로그인 정보로만 정한다) */
    @Override public Map<String,Object> recommendations(TokenPrincipal principal) {
        return recommendations.recommend(studentId(principal));
    }

    static int activityStreak(List<LocalDate> activityDates) {
        if (activityDates.isEmpty()) return 0;
        Set<LocalDate> dates = new HashSet<>(activityDates);
        LocalDate cursor = LocalDate.now();
        if (!dates.contains(cursor)) cursor = cursor.minusDays(1);
        int streak = 0;
        while (dates.contains(cursor)) { streak++; cursor = cursor.minusDays(1); }
        return streak;
    }

    @Override public PageResponse<Map<String,Object>> sessions(TokenPrincipal principal,int page,int size,String status) {
        long studentId=studentId(principal);
        String normalized=status == null || status.isBlank() ? null : status.toUpperCase(Locale.ROOT);
        PageResponse.Window p=PageResponse.window(page,size); List<Map<String,Object>> rows=mapper.findSessions(studentId,normalized,p.size(),p.offset());
        rows.forEach(this::attachExercises);
        return PageResponse.of(rows,mapper.countSessions(studentId,normalized),p);
    }

    @Override public PageResponse<Map<String,Object>> homeworks(TokenPrincipal principal,int page,int size) {
        long studentId=studentId(principal);
        PageResponse.Window window=PageResponse.window(page,size);
        List<Map<String,Object>> rows=mapper.findHomeworks(studentId,window.size(),window.offset());
        rows.forEach(this::normalizeHomework);
        attachHomeworkExercises(rows);
        return PageResponse.of(rows,mapper.countHomeworks(studentId),window);
    }

    /** 쓰기는 조건부 UPDATE 한 문장이다. 선생님의 동시 수정과 겹쳐도 MariaDB 스냅샷 충돌(오류 1020)이 나지 않도록 트랜잭션으로 묶지 않는다. */
    @Override public Map<String,Object> completeHomework(TokenPrincipal principal,long homeworkId) {
        long studentId=studentId(principal);
        Map<String,Object> homework=mapper.findHomework(studentId,homeworkId);
        if(homework==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"숙제를 찾을 수 없습니다.");
        mapper.completeHomework(studentId,homeworkId);
        Map<String,Object> updated=mapper.findHomework(studentId,homeworkId);
        normalizeHomework(updated);
        return updated;
    }

    /**
     * 숙제 자동 완료: 숙제가 연습 세트를 지정했고 실제 수행한 세트와 같으며, 음성 분석이 정상 완료(COMPLETED)되고 판정 보류(HOLD)가 아닐 때만.
     * (PROCESSING·FAILED·분석 없음은 위 검사에서 이미 저장되지 않는다.) 이미 완료된 숙제는 조건부 UPDATE가 0건이라 상태가 다시 바뀌지 않는다.
     * 숙제 상태 변경이 실패해도(동시 수정 등) 연습 기록 저장은 유지하고, 학생은 기존처럼 직접 완료할 수 있다.
     */
    /**
     * 숙제가 아닌 연습 기록의 유형. SELF: 학생이 직접 고른 자율 연습, LESSON: 수업(세션) 연습 — 본인 수업이고 그 수업에 든 연습 세트인지 서버에서 확인한다.
     * 유형을 보내지 않는 기존 클라이언트는 PRACTICE(구분 없음)로 저장한다.
     */
    private String practiceType(long studentId,long exerciseId,AttemptRequest request) {
        String type=request.practiceType()==null||request.practiceType().isBlank()?null:request.practiceType().trim().toUpperCase(Locale.ROOT);
        if(type==null) return "PRACTICE";
        if(type.equals("SELF")) return "SELF";
        if(!type.equals("LESSON")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"연습 유형은 SELF 또는 LESSON입니다.");
        if(request.sessionId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"수업 연습에는 수업 ID가 필요합니다.");
        if(mapper.findSession(studentId,request.sessionId())==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"수업을 찾을 수 없습니다.");
        boolean inLesson=mapper.findSessionExercises(request.sessionId()).stream().anyMatch(e->((Number)e.get("exerciseId")).longValue()==exerciseId);
        if(!inLesson) throw new ResponseStatusException(HttpStatus.CONFLICT,"이 수업에 포함된 연습이 아니에요.");
        return "LESSON";
    }

    private boolean autoCompleteHomework(long studentId,long homeworkId,Map<String,Object> homework,long exerciseId,Map<String,Object> analysis) {
        List<Long> assigned=assignedExerciseIds(homeworkId,homework);
        if(!assigned.contains(exerciseId)) return false;
        if(!"COMPLETED".equals(String.valueOf(analysis.get("status")))||"HOLD".equals(analysis.get("assessmentStatus"))) return false;
        if("COMPLETED".equals(String.valueOf(homework.get("status")))) return true;
        // 콘텐츠가 여러 개면 모든 콘텐츠를 정상 완료해야 숙제를 완료한다(하나뿐이면 기존과 같다).
        if(mapper.countHomeworkExercisesNotDone(homeworkId)>0) return false;
        try {
            mapper.completeHomework(studentId,homeworkId);
            return true;
        } catch(org.springframework.dao.DataAccessException e) {
            return false;
        }
    }

    @Override public Map<String,Object> session(TokenPrincipal principal,long sessionId) {
        long studentId=studentId(principal); Map<String,Object> session=mapper.findSession(studentId,sessionId);
        if(session==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"세션을 찾을 수 없습니다.");
        attachExercises(session); return session;
    }

    @Override public Object categories(TokenPrincipal principal) { studentId(principal); return mapper.findCategories(); }

    @Override public PageResponse<Map<String,Object>> exercises(TokenPrincipal principal,String categoryId,int page,int size) {
        studentId(principal); PageResponse.Window p=PageResponse.window(page,size); List<Map<String,Object>> rows=mapper.findExercises(categoryId,p.size(),p.offset());
        for(Map<String,Object> exercise:rows) attachItems(exercise);
        return PageResponse.of(rows,mapper.countExercises(categoryId),p);
    }

    @Override public PageResponse<Map<String,Object>> practicedExercises(TokenPrincipal principal,String categoryId,int page,int size) {
        long studentId=studentId(principal); PageResponse.Window p=PageResponse.window(page,size);
        List<Map<String,Object>> rows=mapper.findPracticedExercises(studentId,categoryId,p.size(),p.offset());
        for(Map<String,Object> exercise:rows) attachItems(exercise);
        return PageResponse.of(rows,mapper.countPracticedExercises(studentId,categoryId),p);
    }

    @Override @Transactional public Map<String,Object> saveAttempt(TokenPrincipal principal,AttemptRequest request) {
        long studentId=studentId(principal); long exerciseId=parseId(request.exerciseId());
        if(mapper.findExercise(exerciseId)==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"연습 문제를 찾을 수 없습니다.");
        long itemId=parseId(request.itemId());
        if(mapper.findExerciseItem(exerciseId,itemId)==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"연습 문항이 해당 연습 문제에 포함되어 있지 않습니다.");
        String analysisId=request.audioId();
        Map<String,Object> analysis=mapper.findAnalysis(studentId,analysisId);
        if(analysis==null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"본인의 음성 분석 결과만 연결할 수 있습니다.");
        if(!"COMPLETED".equals(String.valueOf(analysis.get("status")))
                || ((Number)analysis.get("exerciseId")).longValue()!=exerciseId
                || !String.valueOf(analysis.get("itemId")).equals(Long.toString(itemId)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"완료된 음성 분석과 연습 문항 정보가 일치하지 않습니다.");
        // 점수는 서버에 저장된 실제 측정값만 사용한다. 로컬 음성 인식은 발음 점수가 없으므로 NULL로 저장하고 문장 일치도만 연결한다.
        BigDecimal score=(BigDecimal)analysis.get("overallScore");
        BigDecimal matchRate=(BigDecimal)analysis.get("matchRate");
        if(request.score()!=null&&(score==null||Math.round(score.doubleValue())!=Math.round(request.score())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"요청 점수가 저장된 음성 분석 결과와 일치하지 않습니다.");
        // 숙제로 수행한 연습: 본인 숙제인지(다른 학생 숙제 ID면 404), 숙제가 연습 세트를 지정했으면 같은 세트인지 확인한다.
        Long homeworkId=request.homeworkId();
        Map<String,Object> homework=null;
        if(homeworkId!=null){
            homework=mapper.findHomework(studentId,homeworkId);
            if(homework==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"숙제를 찾을 수 없습니다.");
            List<Long> assigned=assignedExerciseIds(homeworkId,homework);
            if(!assigned.isEmpty()&&!assigned.contains(exerciseId))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"숙제에 지정된 연습이 아니에요.");
        }
        mapper.insertAttempt(studentId,exerciseId,Long.toString(itemId),analysisId,score,matchRate,
                homeworkId==null?practiceType(studentId,exerciseId,request):"HOMEWORK",homeworkId);
        Map<String,Object> attempt=mapper.findAttemptByAnalysis(studentId,analysisId);
        if(attempt==null) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"연습 기록을 저장하지 못했습니다.");
        Map<String,Object> saved=new LinkedHashMap<>();
        saved.put("saved",true); saved.put("attemptId",attempt.get("attemptId")); saved.put("studentId",studentId); saved.put("exerciseId",exerciseId);
        saved.put("itemId",String.valueOf(attempt.get("itemId"))); saved.put("score",attempt.get("score")); saved.put("matchRate",attempt.get("matchRate"));
        saved.put("attemptType",attempt.get("attemptType")); saved.put("homeworkId",attempt.get("homeworkId"));
        saved.put("homeworkCompleted",homework!=null&&autoCompleteHomework(studentId,homeworkId,homework,exerciseId,analysis));
        return saved;
    }

    @Override public Map<String,Object> analyzeSpeech(TokenPrincipal principal,MultipartFile audio,String exerciseId,String itemId,String requestKey) {
        requireStudent(principal);
        consents.requireConsent(principal.userId(),ConsentPolicy.VOICE);
        Map<String,Object> profile=profileFor(principal.userId());
        long studentId=((Number)profile.get("studentId")).longValue();
        long id=parseId(exerciseId);
        Map<String,Object> exercise=mapper.findExercise(id);
        if(exercise==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"연습 문제를 찾을 수 없습니다.");
        long parsedItemId=parseId(itemId);
        Map<String,Object> item=mapper.findExerciseItem(id,parsedItemId);
        if(item==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"연습 문항이 해당 연습 문제에 포함되어 있지 않습니다.");
        return speechAnalysis.analyze(studentId,String.valueOf(profile.get("learnerType")),id,parsedItemId,String.valueOf(item.get("text")),
                (String)exercise.get("targetPhonemes"),audio,requestKey);
    }

    @Override public Map<String,Object> speechFeedback(TokenPrincipal principal,String analysisId) {
        return speechFeedback.feedback(principal.userId(),speechAnalysis.findAnalysis(studentId(principal),analysisId));
    }

    @Override public Map<String,Object> generateSpeechFeedback(TokenPrincipal principal,String analysisId) {
        return speechFeedback.generate(principal.userId(),speechAnalysis.findAnalysis(studentId(principal),analysisId));
    }

    @Override public Map<String,Object> speechAnalysis(TokenPrincipal principal,String analysisId) {
        return speechAnalysis.findAnalysis(studentId(principal),analysisId);
    }

    @Override public Map<String,Object> createConversation(TokenPrincipal principal,String topic) {
        long studentId=studentId(principal);
        String normalized=topic == null ? "" : topic.trim();
        if (!Set.of("학교 이야기", "좋아하는 음식", "동물 이야기", "오늘의 기분").contains(normalized))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"지원하지 않는 대화 주제입니다.");
        consents.requireConsent(principal.userId(),ConsentPolicy.AI_CHAT);
        // AI 제공자 설정이 없으면 대화방을 만들기 전에 알려 준다(가짜 응답을 만들지 않는다).
        providers.requireAiProvider();
        String id=UUID.randomUUID().toString(); mapper.createConversation(id,studentId,normalized);
        return Map.of("conversationId",id,"topic",normalized);
    }

    @Override public Map<String,Object> sendMessage(TokenPrincipal principal,String conversationId,MessageRequest request) {
        long studentId=studentId(principal); requireConversation(studentId,conversationId);
        consents.requireConsent(principal.userId(),ConsentPolicy.AI_CHAT);
        if(request.text()==null||request.text().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"메시지를 입력해 주세요.");
        return persistAndReply(conversationId,studentId,request.text().trim(),null);
    }

    @Override public Map<String,Object> sendAudio(TokenPrincipal principal,String conversationId,MultipartFile audio) {
        long studentId=studentId(principal); requireConversation(studentId,conversationId);
        consents.requireConsent(principal.userId(),ConsentPolicy.VOICE);
        consents.requireConsent(principal.userId(),ConsentPolicy.AI_CHAT);
        providers.requireAiProvider();
        if(localSpeech) recognizer.requireAvailable(); else providers.requireSpeechProvider();
        AudioStorageService.StoredAudio stored=audioStorage.store(audio);
        String transcript;
        try {
            // 로컬 엔진: 서버에서 음성을 텍스트로 바꾸고, 외부 AI에는 텍스트만 보낸다.
            transcript=localSpeech ? recognizer.recognize(java.nio.file.Path.of(stored.path())).transcript() : providers.transcribe(stored.path(),stored.mimeType());
        } finally {
            // 대화 음성은 텍스트로 바꾼 뒤 보관하지 않는다.
            audioStorage.discard(stored);
        }
        if(transcript==null||transcript.isBlank()||SpeechAnalysisServiceImpl.isHallucination(transcript,""))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,"목소리를 알아듣지 못했어요. 다시 말해 주세요.");
        return persistAndReply(conversationId,studentId,transcript.trim(),null);
    }

    private Map<String,Object> persistAndReply(String id,long studentId,String text,String audioPath) {
        Map<String,Object> conversation=requireConversation(studentId,id);
        if("CLOSED".equals(String.valueOf(conversation.get("status")))) throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 끝난 대화예요. 새 대화를 시작해 주세요.");
        providers.requireAiProvider();
        List<Map<String,Object>> history=new ArrayList<>(mapper.findRecentMessages(id,20));
        history.add(Map.of("speaker","STUDENT","content",text));
        String reply=providers.generateReply(String.valueOf(conversation.get("topic")),history);
        conversationPersistence.saveTurn(id,text,reply,audioPath);
        return Map.of("text",text,"response",reply,"aiText",reply);
    }

    /** 대화에 나온 낱말·주제와 연결되는 실제 활성 콘텐츠(외부 AI를 쓰지 않는다). */
    @Override public Map<String,Object> conversationContents(TokenPrincipal principal,String conversationId) {
        long studentId=studentId(principal); Map<String,Object> conversation=requireConversation(studentId,conversationId);
        return Map.of("items",relatedContents(studentId,conversationId,String.valueOf(conversation.get("topic"))));
    }

    /**
     * 대화 끝내기: 실제 대화 내용으로 AI 요약을 만든 뒤에만 종료·저장한다. 요약이 실패하면 아무것도 저장하지 않고 대화는 그대로 열려 있다(다시 시도 가능).
     * 학생이 한 말이 없으면 AI를 부르지 않는다. 이미 요약한 대화는 저장된 요약을 다시 준다.
     */
    @Override public Map<String,Object> endConversation(TokenPrincipal principal,String conversationId) {
        long studentId=studentId(principal); Map<String,Object> conversation=requireConversation(studentId,conversationId);
        String topic=String.valueOf(conversation.get("topic"));
        boolean closed="CLOSED".equals(String.valueOf(conversation.get("status")));
        if(closed&&conversation.get("summary")!=null) return conversationSummary(studentId,conversationId,topic,String.valueOf(conversation.get("summary")));
        if(closed) throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 끝난 대화예요.");
        consents.requireConsent(principal.userId(),ConsentPolicy.AI_CHAT);
        if(mapper.countStudentMessages(conversationId)==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"아직 나눈 이야기가 없어 요약할 수 없어요. 먼저 한 마디 해 볼까요?");
        providers.requireAiProvider();
        String summary=providers.summarizeConversation(topic,mapper.findRecentMessages(conversationId,60));
        if(mapper.endConversation(conversationId,studentId,summary)!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 끝난 대화예요.");
        return conversationSummary(studentId,conversationId,topic,summary);
    }

    private Map<String,Object> conversationSummary(long studentId,String conversationId,String topic,String summary) {
        List<Map<String,Object>> contents=relatedContents(studentId,conversationId,topic);
        List<Map<String,Object>> next=new ArrayList<>();
        if(!contents.isEmpty()) next.add(Map.of("type","PRACTICE","label","대화와 관련된 연습 해 보기"));
        next.add(Map.of("type","NEW_CONVERSATION","label","다른 주제로 이야기하기"));
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("conversationId",conversationId); result.put("topic",topic); result.put("status","CLOSED"); result.put("summary",summary);
        result.put("studentTurns",mapper.countStudentMessages(conversationId));
        result.put("relatedContents",contents); result.put("nextActivities",next);
        return result;
    }

    /** 학생이 말한 낱말(최근 것부터) → 주제 기본 낱말 순으로 콘텐츠를 찾는다. 같은 콘텐츠는 한 번만, 최대 3개 */
    private List<Map<String,Object>> relatedContents(long studentId,String conversationId,String topic) {
        List<String> said=mapper.findRecentMessages(conversationId,60).stream()
                .filter(message->"STUDENT".equals(String.valueOf(message.get("speaker")))).map(message->String.valueOf(message.get("content"))).toList();
        List<Map<String,Object>> result=new ArrayList<>(); Set<Long> used=new HashSet<>();
        for(String keyword:ConversationKeywords.keywords(topic,said)) {
            if(result.size()>=3) break;
            for(Map<String,Object> row:contentLookup.findContentsMentioning(studentId,keyword,used,1)) {
                Map<String,Object> item=new LinkedHashMap<>(row);
                used.add(((Number)row.get("exerciseId")).longValue());
                item.put("keyword",keyword);
                item.put("reason",ConversationKeywords.fromTopic(topic,keyword)&&!said.stream().anyMatch(text->text.contains(keyword))
                        ? "'"+topic+"' 주제와 관련된 연습이에요" : "대화에서 나온 '"+keyword+"' 낱말이 들어 있는 연습이에요");
                result.add(item);
            }
        }
        return result;
    }

    @Override public PageResponse<Map<String,Object>> conversations(TokenPrincipal principal,int page,int size) {
        long studentId=studentId(principal); PageResponse.Window p=PageResponse.window(page,size);
        return PageResponse.of(mapper.findConversations(studentId,p.size(),p.offset()),mapper.countConversations(studentId),p);
    }

    @Override public PageResponse<Map<String,Object>> history(TokenPrincipal principal,String type,int page,int size) {
        long studentId=studentId(principal);
        if(type!=null&&!type.isBlank()&&!Set.of("ai","word","hw").contains(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"지원하지 않는 기록 유형입니다.");
        PageResponse.Window p=PageResponse.window(page,size); return PageResponse.of(mapper.findHistory(studentId,type,p.size(),p.offset()),mapper.countHistory(studentId,type),p);
    }

    private Map<String,Object> profileFor(long userId) {
        Map<String,Object> result=mapper.findStudentByUserId(userId);
        if(result==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"학생 프로필을 찾을 수 없습니다.");
        return result;
    }
    private long studentId(TokenPrincipal principal) { requireStudent(principal); Object id=profileFor(principal.userId()).get("studentId"); return ((Number)id).longValue(); }
    private void requireStudent(TokenPrincipal principal) { if(principal==null||!"STUDENT".equals(principal.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"학생 권한이 필요합니다."); }
    private Map<String,Object> requireConversation(long studentId,String id) { Map<String,Object> value=mapper.findConversation(id,studentId); if(value==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"대화를 찾을 수 없습니다."); return value; }
    private long parseId(String value) { try { return Long.parseLong(value); } catch(NumberFormatException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"연습 ID 형식이 올바르지 않습니다."); } }
    private void attachExercises(Map<String,Object> session) { long id=((Number)session.get("sessionId")).longValue(); List<Map<String,Object>> exercises=mapper.findSessionExercises(id); exercises.forEach(this::attachItems); session.put("exercises",exercises); }
    /** 숙제에 지정된 연습 콘텐츠 ID(연결 표 우선, 없으면 기존 단일 exercise_id) */
    private List<Long> assignedExerciseIds(long homeworkId,Map<String,Object> homework) {
        List<Long> ids=new ArrayList<>();
        for(Map<String,Object> row:mapper.findHomeworkExercises(List.of(homeworkId))) ids.add(((Number)row.get("exerciseId")).longValue());
        if(ids.isEmpty()&&homework.get("exerciseId") instanceof Number single) ids.add(single.longValue());
        return ids;
    }
    /** 숙제 응답에 연결된 연습 콘텐츠 목록(exercises)을 붙인다(기존 exerciseId·exerciseTitle 필드는 그대로) */
    private void attachHomeworkExercises(List<Map<String,Object>> homeworks) {
        if(homeworks.isEmpty()) return;
        List<Long> ids=homeworks.stream().map(row->((Number)row.get("homeworkId")).longValue()).toList();
        Map<Long,List<Map<String,Object>>> byHomework=new LinkedHashMap<>();
        // 조회 결과(세션 캐시에서 같은 객체가 다시 올 수 있음)는 바꾸지 않고 복사본에서 숙제 ID를 뺀다.
        for(Map<String,Object> row:mapper.findHomeworkExercises(ids)) {
            Map<String,Object> exercise=new LinkedHashMap<>(row);
            byHomework.computeIfAbsent(((Number)exercise.remove("homeworkId")).longValue(),k->new ArrayList<>()).add(exercise);
        }
        for(Map<String,Object> homework:homeworks) homework.put("exercises",byHomework.getOrDefault(((Number)homework.get("homeworkId")).longValue(),List.of()));
    }
    private void attachItems(Map<String,Object> exercise) { long id=((Number)exercise.get("exerciseId")).longValue(); exercise.put("items",mapper.findExerciseItems(id)); }
    private void normalizeHomework(Map<String,Object> homework) {
        Object done=homework.get("done");
        homework.put("done",done instanceof Number number ? number.intValue()!=0 : Boolean.TRUE.equals(done));
    }
}
