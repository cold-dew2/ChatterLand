package com.example.backend.chld.dto.request;

import jakarta.validation.constraints.Size;

public record MessageRequest(@Size(max=4000) String text) { }
