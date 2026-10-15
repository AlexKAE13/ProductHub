package ru.eltex.producthub.qa.domain;

public enum QuestionStatus {
    NEW, IN_PROGRESS, ANSWERED, ACCEPTED, REJECTED, CLOSED;

    public boolean isOpen() {
        return this == NEW || this == IN_PROGRESS;
    }
}
