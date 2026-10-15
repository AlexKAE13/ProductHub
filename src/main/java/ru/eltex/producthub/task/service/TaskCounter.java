package ru.eltex.producthub.task.service;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ru.eltex.producthub.security.HubUserDetails;

/** Счетчик новых задач в шапке: {@code ${@taskCounter.count()}}. Считается один раз за запрос. */
@Component("taskCounter")
public class TaskCounter {

    private static final String ATTR = TaskCounter.class.getName() + ".COUNT";
    private final TaskService tasks;

    public TaskCounter(TaskService tasks) {
        this.tasks = tasks;
    }

    public long count() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof HubUserDetails user)) {
            return 0;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes instanceof ServletRequestAttributes s ? s.getRequest() : null;
        if (request != null && request.getAttribute(ATTR) instanceof Long cached) {
            return cached;
        }
        long value = tasks.newCount(user.getId());
        if (request != null) {
            request.setAttribute(ATTR, value);
        }
        return value;
    }
}
