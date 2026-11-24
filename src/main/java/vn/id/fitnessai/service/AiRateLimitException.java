package vn.id.fitnessai.service;

public class AiRateLimitException extends IllegalStateException {
    public AiRateLimitException(String message) { super(message); }
}
