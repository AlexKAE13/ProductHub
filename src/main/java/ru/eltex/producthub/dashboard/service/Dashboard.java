package ru.eltex.producthub.dashboard.service;

import java.util.List;

/** Цифры дашборда (SPEC.md 5.12). ownerId пуст, если считаем по всем. */
public record Dashboard(Long ownerId,
                        long skuTotal, List<Count> skuByStatus, double avgCompleteness, List<Item> weakestSkus,
                        long staleArtifacts, List<Item> staleArtifactList, long staleSkus, List<Item> staleSkuList,
                        List<Count> byLanguage,
                        long openQuestions, long answeredQuestions, Double avgAnswerHours,
                        List<Count> tasksByStatus, List<Count> tasksByAssignee, long overdueTasks,
                        List<Phrase> topQueries, List<Phrase> zeroQueries, List<Item> topSkus, List<Item> topArtifacts) {

    /** Ключ (статус, язык, id пользователя), подпись и число. */
    public record Count(String key, String label, long n) {
    }

    /** Строка списка: заголовок, пояснение, адрес и число (заполненность, просмотры или дата в sub). */
    public record Item(String title, String sub, String url, long n) {
    }

    public record Phrase(String text, long n) {
    }
}
