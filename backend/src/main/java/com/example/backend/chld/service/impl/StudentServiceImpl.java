package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.request.AttemptRequest;
import com.example.backend.chld.dto.request.MessageRequest;
import com.example.backend.chld.dto.response.PageResponse;
import com.example.backend.chld.mapper.StudentMapper;
import com.example.backend.chld.mapper.UserMapper;
import com.example.backend.chld.service.ConsentPolicy;
import com.example.backend.chld.service.ConsentService;
import com.example.backend.chld.service.SpeechAnalysisService;
import com.example.backend.chld.service.SpeechRecognitionService;
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
    private final StudentMapper mapper;
    private final UserMapper users;
    private final ExternalProviderService providers;
    private final AudioStorageService audioStorage;
    private final ConversationPersistenceService conversationPersistence;
    private final SpeechAnalysisService speechAnalysis;
    private final SpeechRecognitionService recognizer;
    private final ConsentService consents;
    private final boolean localSpeech;

    public StudentServiceImpl(StudentMapper mapper, UserMapper users, ExternalProviderService providers, AudioStorageService audioStorage,
                              ConversationPersistenceService conversationPersistence, SpeechAnalysisService speechAnalysis,
                              SpeechRecognitionService recognizer, ConsentService consents, @Value("${app.speech.engine:local}") String engine) {
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
        return profile;
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
        return PageResponse.of(rows,mapper.countHomeworks(studentId),window);
    }

    @Override @Transactional public Map<String,Object> completeHomework(TokenPrincipal principal,long homeworkId) {
        long studentId=studentId(principal);
        Map<String,Object> homework=mapper.findHomework(studentId,homeworkId);
        if(homework==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"숙제를 찾을 수 없습니다.");
        mapper.completeHomework(studentId,homeworkId);
        Map<String,Object> updated=mapper.findHomework(studentId,homeworkId);
        normalizeHomework(updated);
        return updated;
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
        mapper.insertAttempt(studentId,exerciseId,Long.toString(itemId),analysisId,score,matchRate,"PRACTICE");
        Map<String,Object> attempt=mapper.findAttemptByAnalysis(studentId,analysisId);
        if(attempt==null) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"연습 기록을 저장하지 못했습니다.");
        Map<String,Object> saved=new LinkedHashMap<>();
        saved.put("saved",true); saved.put("attemptId",attempt.get("attemptId")); saved.put("studentId",studentId); saved.put("exerciseId",exerciseId);
        saved.put("itemId",String.valueOf(attempt.get("itemId"))); saved.put("score",attempt.get("score")); saved.put("matchRate",attempt.get("matchRate"));
        return saved;
    }

    @Override public Map<String,Object> analyzeSpeech(TokenPrincipal principal,MultipartFile audio,String exerciseId,String itemId) {
        requireStudent(principal);
        consents.requireConsent(principal.userId(),ConsentPolicy.VOICE);
        Map<String,Object> profile=profileFor(principal.userId());
        long studentId=((Number)profile.get("studentId")).longValue();
        long id=parseId(exerciseId);
        if(mapper.findExercise(id)==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"연습 문제를 찾을 수 없습니다.");
        long parsedItemId=parseId(itemId);
        Map<String,Object> item=mapper.findExerciseItem(id,parsedItemId);
        if(item==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"연습 문항이 해당 연습 문제에 포함되어 있지 않습니다.");
        return speechAnalysis.analyze(studentId,String.valueOf(profile.get("learnerType")),id,parsedItemId,String.valueOf(item.get("text")),audio);
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
        providers.requireAiProvider();
        List<Map<String,Object>> history=new ArrayList<>(mapper.findRecentMessages(id,20));
        history.add(Map.of("speaker","STUDENT","content",text));
        String reply=providers.generateReply(String.valueOf(conversation.get("topic")),history);
        conversationPersistence.saveTurn(id,text,reply,audioPath);
        return Map.of("text",text,"response",reply,"aiText",reply);
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
    private void attachItems(Map<String,Object> exercise) { long id=((Number)exercise.get("exerciseId")).longValue(); exercise.put("items",mapper.findExerciseItems(id)); }
    private void normalizeHomework(Map<String,Object> homework) {
        Object done=homework.get("done");
        homework.put("done",done instanceof Number number ? number.intValue()!=0 : Boolean.TRUE.equals(done));
    }
}
