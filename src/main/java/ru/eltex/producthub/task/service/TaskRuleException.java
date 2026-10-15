package ru.eltex.producthub.task.service;

/** Нарушено правило задач (например, закрыть задачу на вопрос без ответа). */
public class TaskRuleException extends RuntimeException {

    private final String messageKey;

    public TaskRuleException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
