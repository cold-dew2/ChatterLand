package com.example.backend.chld.service.impl;

import com.example.backend.chld.mapper.StudentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationPersistenceService {
    private final StudentMapper mapper;

    public ConversationPersistenceService(StudentMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public void saveTurn(String conversationId, String studentText, String assistantText, String audioPath) {
        mapper.insertMessage(conversationId, "STUDENT", studentText, null, audioPath);
        mapper.insertMessage(conversationId, "ASSISTANT", assistantText, null, null);
        mapper.touchConversation(conversationId);
    }
}
