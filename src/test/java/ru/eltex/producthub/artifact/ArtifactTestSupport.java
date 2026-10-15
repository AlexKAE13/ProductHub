package ru.eltex.producthub.artifact;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import ru.eltex.producthub.artifact.domain.FileRole;
import ru.eltex.producthub.artifact.service.IncomingFile;

/** Файлы с правильными сигнатурами (pdf, pptx, png) и уникальным содержимым для тестов. */
public final class ArtifactTestSupport {

    private ArtifactTestSupport() {
    }

    public static byte[] pdf() {
        return ("%PDF-1.4\n% " + UUID.randomUUID() + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] pptx() {
        return ("PK\u0003\u0004 " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] png() {
        byte[] head = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] tail = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[head.length + tail.length];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(tail, 0, all, head.length, tail.length);
        return all;
    }

    public static IncomingFile file(String name, byte[] content) {
        return new IncomingFile(name, FileRole.MAIN, () -> new ByteArrayInputStream(content));
    }

    public static IncomingFile file(String name, byte[] content, FileRole role) {
        return new IncomingFile(name, role, () -> new ByteArrayInputStream(content));
    }

    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
