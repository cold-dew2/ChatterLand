package com.example.backend.chld.service.impl;

import com.example.backend.chld.dto.request.HomeworkCreateRequest;
import com.example.backend.chld.dto.request.HomeworkUpdateRequest;
import com.example.backend.chld.dto.request.SpeechReviewRequest;
import com.example.backend.chld.dto.request.StudentCreateRequest;
import com.example.backend.chld.dto.response.PageResponse;
import com.example.backend.chld.mapper.StudentMapper;
import com.example.backend.chld.mapper.StudentProfileCommand;
import com.example.backend.chld.mapper.TeacherMapper;
import com.example.backend.chld.mapper.UserMapper;
import com.example.backend.chld.service.SpeechAnalysisService;
import com.example.backend.chld.service.TeacherService;
import com.example.backend.global.jwt.TokenPrincipal;
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
    public TeacherServiceImpl(TeacherMapper teachers, StudentMapper students, UserMapper users,
                              SpeechAnalysisService speechAnalysis, AudioStorageService audioStorage, ReportPdfGenerator pdfGenerator) {
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

    @Override @Transactional public Map<String,Object> addHomework(TokenPrincipal principal,HomeworkCreateRequest request) {
        long teacherId=requireTeacher(principal); assignedStudent(teacherId,request.studentId());
        teachers.insertHomework(teacherId,request.studentId(),request.title().trim(),request.type(),blankToNull(request.description()),request.targetMinutes(),request.dueDate());
        Map<String,Object> latest=teachers.findLatestHomework(teacherId,request.studentId(),request.title().trim());
        if(latest==null) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"숙제를 저장하지 못했습니다.");
        return latest;
    }

    @Override @Transactional public Map<String,Object> updateHomework(TokenPrincipal principal,long homeworkId,HomeworkUpdateRequest request) {
        long teacherId=requireTeacher(principal); Map<String,Object> existing=teachers.findHomework(teacherId,homeworkId);
        if(existing==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"숙제를 찾을 수 없습니다.");
        if(request.title()==null&&request.type()==null&&request.description()==null&&request.targetMinutes()==null&&request.dueDate()==null&&request.status()==null&&request.done()==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"변경할 내용이 없습니다.");
        String status=request.status()==null?null:normalizeHomeworkStatus(request.status());
        if(request.done()!=null) status=null;
        teachers.updateHomework(teacherId,homeworkId,request.title(),request.type(),request.description(),request.targetMinutes(),request.dueDate(),status,request.done());
        return teachers.findHomework(teacherId,homeworkId);
    }

    @Override @Transactional public void deleteHomework(TokenPrincipal principal,long homeworkId) {
        long teacherId=requireTeacher(principal); if(teachers.findHomework(teacherId,homeworkId)==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"숙제를 찾을 수 없습니다.");
        teachers.deleteHomework(teacherId,homeworkId);
    }

    @Override public Map<String,Object> analytics(TokenPrincipal principal,long studentId,LocalDate startDate,LocalDate endDate) {
        long teacherId=requireTeacher(principal); assignedStudent(teacherId,studentId); validateRange(startDate,endDate);
        Map<String,Object> aggregate=teachers.analytics(studentId,startDate,endDate);
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
                        "totalSessions",previous.get("totalSessions"),"completedSessions",previous.get("completedSessions")));
    }

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
        if(teachers.reviewAnalysis(teacherId,analysisId,request.judgement(),blankToNull(request.note()))!=1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"검토 결과를 저장하지 못했습니다.");
        Map<String,Object> updated=new LinkedHashMap<>(speechAnalysis.toResponse(teacherAnalysis(teacherId,analysisId)));
        updated.remove("audioPath");
        return updated;
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
                List.of("평균 문장 일치도",percent(analytics.get("averageMatchRate"),"기록 없음")),
                List.of("이전 기간 평균 문장 일치도",percent(previous.get("averageMatchRate"),"기록 없음")),
                List.of("발음 평가 점수",analytics.get("overallScore")==null?"미평가":analytics.get("overallScore")+"점 (외부 분석 제공자)"),
                List.of("선생님 검토 대기",analytics.get("pendingReviews")+"건"),
                List.of("숙제 수행 (마감일 기준)",analytics.get("homeworkCompleted")+" / "+analytics.get("homeworkTotal")+"건 완료"),
                List.of("완료 세션",analytics.get("completedSessions")+"회"),
                List.of("강점 영역 / 연습 영역",report.get("strength")+" / "+report.get("practiceFocus"))),new float[]{2,3},List.of()));
        sections.add(ReportPdfGenerator.Section.table("영역별 결과",List.of("영역","연습 수","평균 문장 일치도","외부 분석 점수"),
                areas.stream().map(a->List.of(String.valueOf(a.get("label")),a.get("attempts")+"회",percent(a.get("matchRate"),"-"),a.get("score")==null?"미평가":a.get("score")+"점")).toList(),
                new float[]{2,1,2,2},List.of("영역별 기록이 없습니다.")));
        sections.add(ReportPdfGenerator.Section.table("일자별 추이",List.of("날짜","연습 수","평균 문장 일치도"),
                trend.stream().map(t->List.of(String.valueOf(t.get("date")),t.get("attempts")+"회",percent(t.get("matchRate"),"-"))).toList(),
                new float[]{2,1,2},List.of("선택 기간에 연습 기록이 없습니다.")));
        sections.add(ReportPdfGenerator.Section.table("음성 연습 및 선생님 검토 기록",List.of("일시","활동 / 목표 문장","AI 인식 결과","문장 일치도","선생님 판단 / 메모"),
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
                "문장 일치도는 음성 인식(로컬 Whisper 모델) 결과와 목표 문장의 음절 일치 정도이며 발음 정확도 점수가 아닙니다.",
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
