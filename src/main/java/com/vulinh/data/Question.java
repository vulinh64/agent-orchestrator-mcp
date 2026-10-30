package com.vulinh.data;

import module java.base;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record Question(UUID id, UUID requestId, UUID from, UUID to, String text) {}
