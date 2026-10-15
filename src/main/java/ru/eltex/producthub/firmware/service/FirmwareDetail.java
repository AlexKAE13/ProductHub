package ru.eltex.producthub.firmware.service;

import java.util.List;

import ru.eltex.producthub.common.AuditLog;
import ru.eltex.producthub.firmware.domain.Firmware;
import ru.eltex.producthub.sku.domain.Sku;

public record FirmwareDetail(Firmware firmware, List<Sku> skus, List<AuditLog> history, boolean canEdit) {
}
