package ru.eltex.producthub.common;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Замер времени ответа (SPEC.md 8.1): пишет в лог, ставит заголовок Server-Timing и сохраняет момент старта
 * для подвала страницы (см. {@link RequestTiming}). Заголовок ставится, когда приложение начинает писать тело
 * ответа, то есть он учитывает время контроллера и запросов к БД, но не саму отрисовку шаблона.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTimingFilter extends OncePerRequestFilter {

    static final String START_ATTRIBUTE = RequestTimingFilter.class.getName() + ".START";
    private static final Logger log = LoggerFactory.getLogger(RequestTimingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/") || path.startsWith("/vendor/") || path.startsWith("/js/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        request.setAttribute(START_ATTRIBUTE, start);
        try {
            chain.doFilter(request, new TimingResponse(response, start));
        } finally {
            log.info("{} {} -> {} за {} мс", request.getMethod(), request.getRequestURI(), response.getStatus(),
                    format(System.nanoTime() - start));
        }
    }

    static String format(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1_000_000.0);
    }

    private static final class TimingResponse extends HttpServletResponseWrapper {

        private final long start;
        private boolean headerSet;

        TimingResponse(HttpServletResponse response, long start) {
            super(response);
            this.start = start;
        }

        private void setTimingHeader() {
            if (!headerSet && !isCommitted()) {
                headerSet = true;
                setHeader("Server-Timing", "app;dur=" + format(System.nanoTime() - start));
            }
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            setTimingHeader();
            return super.getOutputStream();
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            setTimingHeader();
            return super.getWriter();
        }

        @Override
        public void sendError(int sc) throws IOException {
            setTimingHeader();
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            setTimingHeader();
            super.sendError(sc, msg);
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            setTimingHeader();
            super.sendRedirect(location);
        }
    }
}
