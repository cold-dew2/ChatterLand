package com.example.backend.chld.mapper;

public class StudentProfileCommand {
    private Long studentId;
    private long centerId;
    private String name;
    private Integer age;
    private String phone;
    private String memo;
    private String focusAreas;
    private int sessionsLimit = 20;
    private String status = "ACTIVE";
    private String learnerType = "GENERAL";

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public long getCenterId() { return centerId; }
    public void setCenterId(long centerId) { this.centerId = centerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public String getFocusAreas() { return focusAreas; }
    public void setFocusAreas(String focusAreas) { this.focusAreas = focusAreas; }
    public int getSessionsLimit() { return sessionsLimit; }
    public void setSessionsLimit(int sessionsLimit) { this.sessionsLimit = sessionsLimit; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLearnerType() { return learnerType; }
    public void setLearnerType(String learnerType) { this.learnerType = learnerType; }
}
