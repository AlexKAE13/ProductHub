package ru.eltex.producthub.common;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Время с начала текущего запроса, для подвала страницы: {@code ${@requestTiming.elapsedMs()}}. */
@Component("requestTiming")
public class RequestTiming {

    public String elapsedMs() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            Object start = request.getAttribute(RequestTimingFilter.START_ATTRIBUTE);
            if (start instanceof Long nanos) {
                return RequestTimingFilter.format(System.nanoTime() - nanos);
            }
        }
        return "0";
    }
}
