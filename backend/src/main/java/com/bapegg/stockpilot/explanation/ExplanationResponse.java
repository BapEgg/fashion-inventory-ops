package com.bapegg.stockpilot.explanation;

public record ExplanationResponse(boolean available, String reason, String explanation) {

    public static ExplanationResponse unavailable(String reason) {
        return new ExplanationResponse(false, reason, null);
    }
}
