package com.xperience.hero.web;

/**
 * Minimal, safe error payload for REST responses. Deliberately carries only a
 * human-readable message — never a stack trace or internal exception detail.
 */
public record ErrorResponse(String error) {
}
