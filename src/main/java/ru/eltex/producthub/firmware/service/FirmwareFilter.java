package ru.eltex.producthub.firmware.service;

public record FirmwareFilter(String sku, Long line, String status, String q, int page) {

    public static final int PAGE_SIZE = 50;
}
