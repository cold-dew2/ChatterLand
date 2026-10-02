package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversationRequest(@NotBlank @Size(max=120) String topic) { }
