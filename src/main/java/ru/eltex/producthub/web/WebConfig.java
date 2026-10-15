package ru.eltex.producthub.web;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

@Configuration
public class WebConfig implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {

    /** Запоминает шаблон и маршрут страницы: комментарии к интерфейсу указывают, какой файл править. */
    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(new PageContextInterceptor());
    }


    /** Интерфейс только на русском (английский позже, SPEC.md раздел 9, этап 8). */
    @Bean
    LocaleResolver localeResolver() {
        return new FixedLocaleResolver(Locale.forLanguageTag("ru"));
    }

    /** Все тексты интерфейса лежат только в messages_ru.properties (CLAUDE.md). */
    @Bean
    MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(Locale.forLanguageTag("ru"));
        return source;
    }
}
