package com.vulinh.data;

import module java.base;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record Answer(UUID questionId, UUID from, String text) {}
