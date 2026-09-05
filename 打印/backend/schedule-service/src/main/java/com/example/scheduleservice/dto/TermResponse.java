package com.example.scheduleservice.dto;

import com.example.scheduleservice.model.Term;

public record TermResponse(String xnm, String xqm) {
    public static TermResponse from(Term term) {
        return new TermResponse(term.xnm(), term.xqm());
    }
}
