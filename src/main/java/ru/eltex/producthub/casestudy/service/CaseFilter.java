package ru.eltex.producthub.casestudy.service;

public record CaseFilter(String country, Integer year, Long tag, String sku, String q, int page) {

    public static final int PAGE_SIZE = 50;
}
