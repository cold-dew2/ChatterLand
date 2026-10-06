package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.request.HomeworkCreateRequest;
import com.example.backend.chld.dto.request.HomeworkUpdateRequest;
import com.example.backend.chld.dto.request.SpeechReviewRequest;
import com.example.backend.chld.dto.request.StudentCreateRequest;
import com.example.backend.chld.dto.response.PageResponse;
import com.example.backend.chld.exception.ConflictException;
import com.example.backend.chld.mapper.PracticeContentMapper;
import com.example.backend.chld.mapper.StudentMapper;
import com.example.backend.chld.mapper.StudentProfileCommand;
import com.example.backend.chld.mapper.TeacherMapper;
import com.example.backend.chld.mapper.UserMapper;
import com.example.backend.chld.service.SpeechAnalysisService;
import com.example.backend.chld.service.SpeechFeedbackService;
import com.example.backend.chld.service.TeacherService;
import com.example.backend.global.jwt.TokenPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class TeacherServiceImpl implements TeacherService {
    private final TeacherMapper teachers;
    private final StudentMapper students;
    private final UserMapper users;
    private final SpeechAnalysisService speechAnalysis;
    private final AudioStorageService audioStorage;
    private final ReportPdfGenerator pdfGenerator;
    private final SpeechFeedbackService speechFeedback;
    private final PracticeContentMapper contents;
    public TeacherServiceImpl(TeacherMapper teachers, StudentMapper students, UserMapper users,
                              SpeechAnalysisService speechAnalysis, AudioStorageService audioStorage, ReportPdfGenerator pdfGenerator,
                              SpeechFeedbackService speechFeedback, PracticeContentMapper contents) {
        this.speechFeedback=speechFeedback; this.contents=contents;
        this.teachers=teachers; this.students=students; this.users=users; this.speechAnalysis=speechAnalysis; this.audioStorage=audioStorage; this.pdfGenerator=pdfGenerator;
    }

    @Override public PageResponse<Map<String,Object>> students(TokenPrincipal principal,String keyword,String status,int page,int size) {
        long teacherId=requireTeacher(principal); PageResponse.Window p=PageResponse.window(page,size);
        List<Map<String,Object>> rows=teachers.findStudents(teacherId,blankToNull(keyword),blankToNull(status),p.size(),p.offset());
        rows.forEach(this::normalizeTags);
        return PageResponse.of(rows,teachers.countStudents(teacherId,blankToNull(keyword),blankToNull(status)),p);
    }

    @Override public List<Map<String,Object>> availableStudents(TokenPrincipal principal) {
        long teacherId=requireTeacher(principal);
        Map<String,Object> teacher=users.findById(teacherId);
        return teachers.findAvailableStudents(teacherId,num(teacher,"centerId"));
    }

    @Override @Transactional public Map<String,Object> addStudent(TokenPrincipal principal,StudentCreateRequest request) {
        long teacherId=requireTeacher(principal); Map<String,Object> teacher=users.findById(teacherId);
        long centerId=num(teacher,"centerId"); long studentId;
        if(request.studentId()!=null) {
            Map<String,Object> profile=teachers.findProfileById(request.studentId());
            if(profile==null||num(profile,"centerId")!=centerId) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"같은 센터의 학생 프로필을 찾을 수 없습니다.");
            studentId=request.studentId();
            if(teachers.findStudent(teacherId,studentId)!=null) throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 담당 학생으로 등록되어 있습니다.");
        } else {
            StudentProfileCommand command=new StudentProfileCommand(); command.setCenterId(centerId); command.setName(request.name().trim());
            command.setAge(request.age()); command.setPhone(blankToNull(request.parentPhone())); command.setMemo(blankToNull(request.memo()));
            command.setFocusAreas(request.tags()==null?null:String.join(",",request.tags())); command.setSessionsLimit(request.sessionsTotal()==null?20:request.sessionsTotal());
            command.setStatus(normalizeStudentStatus(request.status()));
            command.setLearnerType(request.learnerType()==null?"GENERAL":request.learnerType());
            teachers.insertStudentProfile(command); studentId=command.getStudentId();
        }
        teachers.linkStudent(teacherId,studentId,request.memo());
        Map<String,Object> saved=teachers.findStudent(teacherId,studentId); normalizeTags(saved); return saved;
    }

    @Override public Map<String,Object> student(TokenPrincipal principal,long studentId) {
        Map<String,Object> result=assignedStudent(requireTeacher(principal),studentId); normalizeTags(result); return result;
    }

    @Override @Transactional public Map<String,Object> updateStudent(TokenPrincipal principal,long studentId,StudentCreateRequest request) {
        long teacherId=requireTeacher(principal);
        assignedStudent(teacherId,studentId);
        if(request.name()==null||request.name().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"학생 이름을 입력해 주세요.");
        teachers.updateStudent(teacherId,studentId,request.name().trim(),request.age(),blankToNull(request.parentPhone()),
                blankToNull(request.memo()),request.tags()==null?null:String.join(",",request.tags()),request.sessionsTotal(),normalizeStudentStatus(request.status()),request.learnerType());
        Map<String,Object> updated=teachers.findStudent(teacherId,studentId); normalizeTags(updated); return updated;
    }

    @Override @Transactional public void deleteStudent(TokenPrincipal principal,long studentId) {
        long teacherId=requireTeacher(principal);
        assignedStudent(teacherId,studentId);
        teachers.unlinkStudent(teacherId,studentId);
    }

    @Override public PageResponse<Map<String,Object>> sessions(TokenPrincipal principal,long studentId,int page,int size) {
        long teacherId=requireTeacher(principal); assignedStudent(teacherId,studentId); PageResponse.Window p=PageResponse.window(page,size);
        List<Map<String,Object>> rows=teachers.findStudentSessions(studentId,p.size(),p.offset());
        rows.forEach(row->{Object sid=row.get("sessionId"); if(sid instanceof Number n){List<Map<String,Object>> exercises=students.findSessionExercises(n.longValue()); exercises.forEach(e->e.put("items",students.findExerciseItems(((Number)e.get("exerciseId")).longValue()))); row.put("exercises",exercises);}});
        return PageResponse.of(rows,teachers.countStudentSessions(studentId),p);
    }

    @Override public PageResponse<Map<String,Object>> homeworks(TokenPrincipal principal,Long studentId,String status,int page,int size) {
        long teacherId=requireTeacher(principal); if(studentId!=null) assignedStudent(teacherId,studentId);
        String normalized=normalizeHomeworkStatus(status); PageResponse.Window p=PageResponse.window(page,size);
        return PageResponse.of(teachers.findHomeworks(teacherId,studentId,normalized,p.size(),p.offset()),teachers.countHomeworks(teacherId,studentId,normalized),p);
    }

    /**
     * 숙제 등록. Idempotency-Key가 있으면 (선생님, 키) 유니크 제약으로 한 번만 저장한다.
     * - 같은 키·같은 내용: 새로 만들지 않고 처음 만든 숙제를 돌려준다(reused=true).
     * - 같은 키·다른 내용: 409(IDEMPOTENCY_KEY_REUSED).
     * - 저장에 실패한 요청은 키가 남지 않으므로 같은 키로 다시 시도할 수 있다.
     * 트랜잭션으로 묶지 않는다: 동시 요청에서 유니크 제약에 걸린 뒤 다른 요청이 커밋한 행을 바로 다시 읽어야 하는데,
     * REPEATABLE READ 트랜잭션 안에서는 먼저 잡힌 스냅샷 때문에 그 행이 보이지 않는다. 쓰기는 INSERT 한 문장이다.
     */
    @Override public Map<String,Object> addHomework(TokenPrincipal principal,HomeworkCreateRequest request,String idempotencyKey) {
        long teacherId=requireTeacher(principal); assignedStudent(teacherId,request.studentId());
        String title=request.title().trim(), description=blankToNull(request.description());
        // 숙제로 낼 연습 세트: 운영 중인(active) 콘텐츠만. 담당 학생 확인은 위 assignedStudent가 서버에서 한다.
        Long exerciseId=request.exerciseId();
        if(exerciseId!=null&&contents.findActiveExercise(exerciseId)==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"숙제로 낼 연습 콘텐츠를 찾을 수 없습니다.");
        String key=IdempotencyKeys.normalize(idempotencyKey);
        if(key==null) {
            teachers.insertHomework(teacherId,request.studentId(),title,request.type(),description,request.targetMinutes(),request.dueDate(),null,null,exerciseId);
            Map<String,Object> latest=teachers.findLatestHomework(teacherId,request.studentId(),title);
            if(latest==null) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"숙제를 저장하지 못했습니다.");
            return latest;
        }
        // 연습 세트를 지정한 경우에만 지문에 넣는다(기존 키의 지문은 그대로 유지).
        String hash=exerciseId==null?IdempotencyKeys.fingerprint(request.studentId(),title,request.type(),description,request.targetMinutes(),request.dueDate())
                :IdempotencyKeys.fingerprint(request.studentId(),title,request.type(),description,request.targetMinutes(),request.dueDate(),exerciseId);
        Map<String,Object> existing=teachers.findHomeworkByRequestKey(teacherId,key);
        if(existing!=null) return replayHomework(existing,hash);
        try {
            teachers.insertHomework(teacherId,request.studentId(),title,request.type(),description,request.targetMinutes(),request.dueDate(),key,hash,exerciseId);
        } catch(DuplicateKeyException concurrent) {
            // 같은 키의 요청이 동시에 도착해 다른 요청이 먼저 저장했다.
            existing=teachers.findHomeworkByRequestKey(teacherId,key);
            if(existing==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"같은 숙제 등록 요청을 처리하고 있어요. 목록을 새로 불러와 확인해 주세요.");
            return replayHomework(existing,hash);
        }
        Map<String,Object> saved=teachers.findHomeworkByRequestKey(teacherId,key);
        if(saved==null) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"숙제를 저장하지 못했습니다.");
        saved.remove("requestHash");
        return saved;
    }

    private Map<String,Object> replayHomework(Map<String,Object> existing,String hash) {
        if(!Objects.equals(existing.get("requestHash"),hash))
            throw new ConflictException(ConflictException.IDEMPOTENCY_KEY_REUSED,"같은 요청 키로 다른 숙제 내용이 전송되었습니다. 숙제 등록을 다시 시도해 주세요.");
        Map<String,Object> response=new LinkedHashMap<>(existing);
        response.remove("requestHash");
        response.put("reused",true);
        return response;
    }

    /**
     * 숙제 수정. 쓰기는 조건부 UPDATE 한 문장(version 비교 포함)이라 트랜잭션으로 묶지 않는다.
     * REPEATABLE READ 트랜잭션에서 먼저 SELECT한 뒤 다른 요청이 바꾼 행을 UPDATE하면 MariaDB(innodb_snapshot_isolation)는
     * 0건이 아니라 오류 1020을 내 500이 되므로, 동시 수정이 409(VERSION_CONFLICT)로 끝나도록 각 문장을 따로 실행한다.
     */
    @Override public Map<String,Object> updateHomework(TokenPrincipal principal,long homeworkId,HomeworkUpdateRequest request) {
        long teacherId=requireTeacher(principal); Map<String,Object> existing=teachers.findHomework(teacherId,homeworkId);
        if(existing==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"숙제를 찾을 수 없습니다.");
        if(request.title()==null&&request.type()==null&&request.description()==null&&request.targetMinutes()==null&&request.dueDate()==null&&request.status()==null&&request.done()==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"변경할 내용이 없습니다.");
        String status=request.status()==null?null:normalizeHomeworkStatus(request.status());
        if(request.done()!=null) status=null;
        int updated=teachers.updateHomework(teacherId,homeworkId,request.title(),request.type(),request.description(),request.targetMinutes(),request.dueDate(),status,request.done(),request.version());
        if(updated!=1) {
            if(teachers.findHomework(teacherId,homeworkId)==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"숙제를 찾을 수 없습니다.");
            // 화면이 조회한 뒤 다른 곳(다른 탭·학생 완료 처리 등)에서 먼저 바뀌었다. 덮어쓰지 않는다.
            throw new ConflictException(ConflictException.VERSION_CONFLICT,"다른 곳에서 먼저 바뀐 숙제예요. 최신 내용을 불러왔으니 확인한 뒤 다시 저장해 주세요.");
        }
        return teachers.findHomework(teacherId,homeworkId);
    }

    /** 조건부 DELETE 한 문장(version 비교)으로 지운다. 수정과 같은 이유(MariaDB 스냅샷 충돌)로 트랜잭션으로 묶지 않는다. */
    @Override public void deleteHomework(TokenPrincipal principal,long homeworkId,int version) {
        long teacherId=requireTeacher(principal);
        if(teachers.deleteHomework(teacherId,homeworkId,version)==1) return;
        if(teachers.findHomework(teacherId,homeworkId)==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"숙제를 찾을 수 없습니다.");
        throw new ConflictException(ConflictException.VERSION_CONFLICT,"다른 곳에서 먼저 바뀐 숙제예요. 최신 내용을 불러왔으니 확인한 뒤 다시 삭제해 주세요.");
    }

    @Override public Map<String,Object> analytics(TokenPrincipal principal,long studentId,LocalDate startDate,LocalDate endDate) {
        long teacherId=requireTeacher(principal); assignedStudent(teacherId,studentId); validateRange(startDate,endDate);
        Map<String,Object> aggregate=teachers.analytics(studentId,startDate,endDate);
        // 이전 기간: 조회 기간과 같은 일수, 조회 시작일 바로 전날에 끝나는 기간(겹치거나 비는 날 없음). 같은 집계 쿼리로 계산한다.
        long periodDays=ChronoUnit.DAYS.between(startDate,endDate)+1;
        LocalDate previousEnd=startDate.minusDays(1);
        LocalDate previousStart=previousEnd.minusDays(periodDays-1);
        Map<String,Object> previous=teachers.analytics(studentId,previousStart,previousEnd);
        List<Map<String,Object>> areas=teachers.areaAnalytics(studentId,startDate,endDate);
        List<Map<String,Object>> trend=teachers.scoreTrend(studentId,startDate,endDate);
        return map("studentId",studentId,"startDate",startDate,"endDate",endDate,
                "overallScore",aggregate.get("overallScore"),"averageMatchRate",aggregate.get("averageMatchRate"),
                "totalAttempts",aggregate.get("totalAttempts"),"scoredAttempts",aggregate.get("scoredAttempts"),
                "matchedAttempts",aggregate.get("matchedAttempts"),"pendingReviews",aggregate.get("pendingReviews"),
                "homeworkTotal",aggregate.get("homeworkTotal"),"homeworkCompleted",aggregate.get("homeworkCompleted"),
                "totalSessions",aggregate.get("totalSessions"),"completedSessions",aggregate.get("completedSessions"),
                "areaScores",areas,"scoreTrend",trend,
                "previousPeriod",map("startDate",previousStart,"endDate",previousEnd,
                        "overallScore",previous.get("overallScore"),"averageMatchRate",previous.get("averageMatchRate"),
                        "totalAttempts",previous.get("totalAttempts"),
                        "homeworkTotal",previous.get("homeworkTotal"),"homeworkCompleted",previous.get("homeworkCompleted"),
                        "totalSessions",previous.get("totalSessions"),"completedSessions",previous.get("completedSessions")),
                "comparison",comparePeriods(aggregate,previous));
    }

    /**
     * 현재·이전 기간 비교값. 어느 한쪽에 기록이 없으면 변화량은 null(0으로 간주하지 않음), 숙제가 0건인 기간의 완료율은 null(0으로 나누지 않음).
     * 평균 텍스트 일치율 변화는 화면에 보이는 반올림 값끼리의 차이(%p)다.
     */
    static Map<String,Object> comparePeriods(Map<String,Object> current,Map<String,Object> previous) {
        Integer currentRate=intOrNull(current.get("averageMatchRate")), previousRate=intOrNull(previous.get("averageMatchRate"));
        Integer currentHomework=completionRate(current), previousHomework=completionRate(previous);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("averageMatchRateChange",currentRate!=null&&previousRate!=null?currentRate-previousRate:null);
        result.put("totalAttemptsChange",longOrZero(current.get("totalAttempts"))-longOrZero(previous.get("totalAttempts")));
        result.put("homeworkCompletionRate",currentHomework);
        result.put("previousHomeworkCompletionRate",previousHomework);
        result.put("homeworkCompletionRateChange",currentHomework!=null&&previousHomework!=null?currentHomework-previousHomework:null);
        return result;
    }

    private static Integer completionRate(Map<String,Object> period) {
        long total=longOrZero(period.get("homeworkTotal"));
        return total==0?null:(int)Math.round(longOrZero(period.get("homeworkCompleted"))*100.0/total);
    }

    private static Integer intOrNull(Object value) { return value instanceof Number number?(int)Math.round(number.doubleValue()):null; }
    private static long longOrZero(Object value) { return value instanceof Number number?number.longValue():0L; }

    @Override public Map<String,Object> report(TokenPrincipal principal,long studentId,LocalDate startDate,LocalDate endDate) {
        Map<String,Object> student=student(principal,studentId); Map<String,Object> analytics=analytics(principal,studentId,startDate,endDate);
        List<Map<String,Object>> areas=(List<Map<String,Object>>)analytics.get("areaScores");
        List<Map<String,Object>> scored=areas.stream().filter(x->x.get("score")!=null).toList();
        String strongest=scored.stream().max(Comparator.comparingDouble(x->((Number)x.get("score")).doubleValue())).map(x->String.valueOf(x.get("label"))).orElse("분석 데이터 없음");
        String focus=scored.stream().min(Comparator.comparingDouble(x->((Number)x.get("score")).doubleValue())).map(x->String.valueOf(x.get("label"))).orElse("분석 데이터 없음");
        return map("student",student,"analytics",analytics,"startDate",startDate,"endDate",endDate,
                "strength",strongest,"practiceFocus",focus,"generatedAt",java.time.Instant.now().toString());
    }

    @Override public PageResponse<Map<String,Object>> speechAnalyses(TokenPrincipal principal,long studentId,String reviewStatus,int page,int size) {
        long teacherId=requireTeacher(principal); assignedStudent(teacherId,studentId);
        String status=blankToNull(reviewStatus);
        if(status!=null&&!Set.of("PENDING","REVIEWED","NOT_REQUIRED").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"검토 상태를 확인해 주세요.");
        PageResponse.Window p=PageResponse.window(page,size);
        List<Map<String,Object>> rows=teachers.findStudentAnalyses(studentId,status,p.size(),p.offset()).stream().map(speechAnalysis::toResponse).toList();
        return PageResponse.of(rows,teachers.countStudentAnalyses(studentId,status),p);
    }

    @Override public SpeechAudio speechAnalysisAudio(TokenPrincipal principal,String analysisId) {
        Map<String,Object> analysis=teacherAnalysis(requireTeacher(principal),analysisId);
        return new SpeechAudio(audioStorage.read((String)analysis.get("audioPath")),String.valueOf(analysis.get("audioMime")));
    }

    @Override @Transactional public Map<String,Object> reviewSpeechAnalysis(TokenPrincipal principal,String analysisId,SpeechReviewRequest request) {
        long teacherId=requireTeacher(principal); Map<String,Object> analysis=teacherAnalysis(teacherId,analysisId);
        if(!"COMPLETED".equals(String.valueOf(analysis.get("status")))) throw new ResponseStatusException(HttpStatus.CONFLICT,"완료된 분석만 검토할 수 있습니다.");
        // 교사 확정 오류는 자동 분석의 오류 후보(assessment_json)와 다른 컬럼에 저장한다.
        String confirmed=request.confirmedErrors()==null||request.confirmedErrors().isEmpty()?null:speechAnalysis.toJson(request.confirmedErrors());
        if(teachers.reviewAnalysis(teacherId,analysisId,request.judgement(),blankToNull(request.note()),confirmed)!=1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"검토 결과를 저장하지 못했습니다.");
        Map<String,Object> updated=new LinkedHashMap<>(speechAnalysis.toResponse(teacherAnalysis(teacherId,analysisId)));
        updated.remove("audioPath");
        return updated;
    }

    /**
     * 담당 학생의 연습 기록(자율 연습·숙제 연습). 담당 여부는 서버에서 확인한다(담당이 아니면 403/404, 기존 학생 상세와 같은 검사).
     * textMatchRate는 음성 인식 글자 일치율이며 발음 정확도가 아니다.
     */
    @Override public PageResponse<Map<String,Object>> studentAttempts(TokenPrincipal principal,long studentId,String type,int page,int size) {
        long teacherId=requireTeacher(principal); assignedStudent(teacherId,studentId);
        String t=type==null||type.isBlank()?null:type.trim().toUpperCase(Locale.ROOT);
        if(t!=null&&!Set.of("PRACTICE","SELF","LESSON","HOMEWORK").contains(t)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"연습 종류는 SELF·LESSON·HOMEWORK·PRACTICE(구분 전 기록)입니다.");
        PageResponse.Window p=PageResponse.window(page,size);
        return PageResponse.of(teachers.findStudentAttempts(studentId,t,p.size(),p.offset()),teachers.countStudentAttempts(studentId,t),p);
    }

    @Override public Map<String,Object> studentAttemptSummary(TokenPrincipal principal,long studentId) {
        long teacherId=requireTeacher(principal); assignedStudent(teacherId,studentId);
        return teachers.attemptSummary(studentId);
    }

    @Override public Map<String,Object> speechFeedback(TokenPrincipal principal,String analysisId) {
        // 담당 여부는 서버에서 분석 ID로 확인한다(클라이언트가 보낸 학생 ID를 믿지 않음). 담당이 아니면 404.
        return speechFeedback.teacherView(speechAnalysis.toResponse(teacherAnalysis(requireTeacher(principal),analysisId)));
    }

    private Map<String,Object> teacherAnalysis(long teacherId,String analysisId) {
        Map<String,Object> analysis=teachers.findAnalysisForTeacher(teacherId,analysisId);
        if(analysis==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"담당 학생의 분석 결과를 찾을 수 없습니다.");
        return analysis;
    }

    @Override public byte[] reportPdf(TokenPrincipal principal,long studentId,LocalDate startDate,LocalDate endDate) {
        Map<String,Object> report=report(principal,studentId,startDate,endDate);
        Map<?,?> student=(Map<?,?>)report.get("student"); Map<?,?> analytics=(Map<?,?>)report.get("analytics");
        Map<?,?> previous=(Map<?,?>)analytics.get("previousPeriod");
        List<Map<String,Object>> analyses=teachers.findAnalysesForReport(studentId,startDate,endDate,500);
        List<Map<String,Object>> areas=(List<Map<String,Object>>)analytics.get("areaScores");
        List<Map<String,Object>> trend=(List<Map<String,Object>>)analytics.get("scoreTrend");
        String learner="THERAPY".equals(student.get("learnerType"))?"언어재활 아동 (발음 평가는 선생님 검토)":"일반 아동 (문장 연습)";

        List<ReportPdfGenerator.Section> sections=new ArrayList<>();
        sections.add(ReportPdfGenerator.Section.table("요약",List.of("항목","값"),List.of(
                List.of("연습 기록",analytics.get("totalAttempts")+"건"),
                List.of("평균 텍스트 일치율",percent(analytics.get("averageMatchRate"),"기록 없음")),
                List.of("이전 기간 평균 텍스트 일치율",percent(previous.get("averageMatchRate"),"기록 없음")),
                List.of("발음 평가 점수",analytics.get("overallScore")==null?"미평가":analytics.get("overallScore")+"점 (외부 분석 제공자)"),
                List.of("선생님 검토 대기",analytics.get("pendingReviews")+"건"),
                List.of("숙제 수행 (마감일 기준)",analytics.get("homeworkCompleted")+" / "+analytics.get("homeworkTotal")+"건 완료"),
                List.of("완료 세션",analytics.get("completedSessions")+"회"),
                List.of("강점 영역 / 연습 영역",report.get("strength")+" / "+report.get("practiceFocus"))),new float[]{2,3},List.of()));
        sections.add(ReportPdfGenerator.Section.table("영역별 결과",List.of("영역","연습 수","평균 텍스트 일치율","외부 분석 점수"),
                areas.stream().map(a->List.of(String.valueOf(a.get("label")),a.get("attempts")+"회",percent(a.get("matchRate"),"-"),a.get("score")==null?"미평가":a.get("score")+"점")).toList(),
                new float[]{2,1,2,2},List.of("영역별 기록이 없습니다.")));
        sections.add(ReportPdfGenerator.Section.table("일자별 추이",List.of("날짜","연습 수","평균 텍스트 일치율"),
                trend.stream().map(t->List.of(String.valueOf(t.get("date")),t.get("attempts")+"회",percent(t.get("matchRate"),"-"))).toList(),
                new float[]{2,1,2},List.of("선택 기간에 연습 기록이 없습니다.")));
        sections.add(ReportPdfGenerator.Section.table("음성 연습 및 선생님 검토 기록",List.of("일시","활동 / 목표 문장","AI 인식 결과","텍스트 일치율","선생님 판단 / 메모"),
                analyses.stream().map(a->List.of(String.valueOf(a.get("createdAt")),
                        a.get("exerciseTitle")+" / "+nullToDash(a.get("targetText")),nullToDash(a.get("transcript")),
                        percent(a.get("matchRate"),"PRONUNCIATION_REVIEW".equals(a.get("evaluationMode"))?"계산 안 함":"-"),
                        judgement(a))).toList(),
                new float[]{1.6f,2.4f,2f,1.2f,2.6f},List.of("선택 기간에 저장된 음성 연습 기록이 없습니다.")));
        ReportPdfGenerator.ReportDocument document=new ReportPdfGenerator.ReportDocument("채터랜드 학습 리포트",List.of(
                new String[]{"학생",String.valueOf(student.get("name"))+" ("+student.get("age")+"세)"},
                new String[]{"언어재활센터",String.valueOf(student.get("centerName"))},
                new String[]{"학습자 유형",learner},
                new String[]{"조회 기간",startDate+" ~ "+endDate},
                new String[]{"생성 시각",java.time.LocalDateTime.now().withNano(0).toString().replace('T',' ')}),
                sections,List.of(
                "텍스트 일치율은 음성 인식(로컬 Whisper 모델) 결과와 목표 문장의 음절 일치 정도이며 발음 정확도 점수가 아닙니다.",
                "발음·속도·유창성은 검증된 평가 방법이 없어 측정하지 않으며 '미평가'로 표시합니다.",
                "AI 인식 결과는 진단이나 치료 효과 판정이 아니며, 발음 판단은 선생님 검토 결과를 기준으로 합니다.",
                "조회 기간에 저장된 실제 기록만 사용했습니다."));
        try { return pdfGenerator.generate(document); }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"리포트 PDF를 만들지 못했습니다. 잠시 뒤 다시 시도해 주세요."); }
    }

    private String percent(Object value,String empty){return value==null?empty:Math.round(((Number)value).doubleValue())+"%";}
    private String nullToDash(Object value){return value==null||String.valueOf(value).isBlank()?"-":String.valueOf(value);}
    private String judgement(Map<String,Object> analysis){
        String review=String.valueOf(analysis.get("reviewStatus"));
        if(!"REVIEWED".equals(review)) return "PENDING".equals(review)?"검토 대기":"검토 대상 아님";
        String label=switch(String.valueOf(analysis.get("teacherJudgement"))){case "ACCEPTABLE"->"목표 발음에 가까움";case "NEEDS_PRACTICE"->"연습 필요";case "UNCLEAR"->"판단 어려움";default->"검토 완료";};
        Object note=analysis.get("teacherNote");
        return note==null?label:label+" / "+note;
    }

    private Map<String,Object> assignedStudent(long teacherId,long studentId) {
        Map<String,Object> result=teachers.findStudent(teacherId,studentId);
        if(result==null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"담당 학생 정보에 접근할 수 없습니다.");
        return result;
    }
    private long requireTeacher(TokenPrincipal principal) { if(principal==null||!"TEACHER".equals(principal.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"선생님 권한이 필요합니다."); return principal.userId(); }
    private void validateRange(LocalDate start,LocalDate end) { if(start==null||end==null||end.isBefore(start)||ChronoUnit.DAYS.between(start,end)>366) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"조회 기간은 1년 이내로 설정해 주세요."); }
    private String normalizeHomeworkStatus(String status) { if(status==null||status.isBlank()||status.equals("전체")) return null; return switch(status.toLowerCase(Locale.ROOT)){case "완료","completed","done"->"COMPLETED";case "진행중","in_progress"->"IN_PROGRESS";case "예정","pending"->"PENDING";default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"숙제 상태를 확인해 주세요.");}; }
    private String normalizeStudentStatus(String status){if(status==null||status.isBlank())return "ACTIVE";return switch(status){case "예정","PAUSED"->"PAUSED";case "완료","COMPLETED"->"COMPLETED";case "진행중","ACTIVE"->"ACTIVE";default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"학생 상태를 확인해 주세요.");};}
    private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
    private long num(Map<String,Object> map,String key){return ((Number)map.get(key)).longValue();}
    private void normalizeTags(Map<String,Object> student){if(student==null)return; Object value=student.get("focusAreas"); student.put("tags",value==null||String.valueOf(value).isBlank()?List.of():Arrays.stream(String.valueOf(value).split(",")).toList());}
    private Map<String,Object> map(Object...pairs){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put(String.valueOf(pairs[i]),pairs[i+1]);return result;}
}
