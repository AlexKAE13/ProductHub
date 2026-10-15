package ru.eltex.producthub.transfer;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Перенос данных из файловой базы H2 (пилот на рабочем компьютере) в PostgreSQL (сервер компании). Схема в обеих базах
 * создается одними миграциями, поэтому таблицы совпадают. Перед переносом таблицы PostgreSQL очищаются (кроме истории миграций),
 * затем строки копируются в порядке зависимостей. Связи таблицы на саму себя и циклы (SKU - преемник, комментарий - ответ,
 * материал - текущая версия) заполняются вторым проходом.
 */
public class H2DataTransfer {

    private static final String HISTORY = "flyway_schema_history";
    private static final int BATCH = 500;

    private record Fk(String table, String column, String parent) {
    }

    /** Копирует данные, возвращает число строк по таблицам. Транзакция одна: при ошибке в PostgreSQL ничего не меняется. */
    public Map<String, Integer> copy(Connection source, Connection target) throws SQLException {
        requireSameSchemaVersion(source, target);
        target.setAutoCommit(false);
        try {
            List<String> tables = tables(target);
            List<Fk> fks = foreignKeys(target, tables);
            Set<Fk> deferred = new LinkedHashSet<>();
            List<String> order = order(tables, fks, deferred);

            try (Statement st = target.createStatement()) {
                st.execute("TRUNCATE TABLE " + String.join(", ", tables.stream().map(H2DataTransfer::q).toList())
                        + " RESTART IDENTITY CASCADE");
            }
            Map<String, Integer> counts = new LinkedHashMap<>();
            List<Object[]> later = new ArrayList<>();
            for (String table : order) {
                counts.put(table, copyTable(source, target, table, deferred, later));
            }
            for (Object[] u : later) {
                try (PreparedStatement ps = target.prepareStatement(
                        "UPDATE " + q((String) u[0]) + " SET " + q((String) u[1]) + " = ? WHERE id = ?")) {
                    ps.setObject(1, u[3]);
                    ps.setObject(2, u[2]);
                    ps.executeUpdate();
                }
            }
            fixSequences(target, tables);
            target.commit();
            return counts;
        } catch (SQLException | RuntimeException e) {
            target.rollback();
            throw e;
        }
    }

    private static void requireSameSchemaVersion(Connection source, Connection target) throws SQLException {
        String sql = "SELECT MAX(CAST(\"version\" AS INT)) FROM " + HISTORY + " WHERE \"version\" IS NOT NULL";
        int a = scalar(source, sql);
        int b = scalar(target, sql);
        if (a != b) {
            throw new IllegalStateException("Версии схемы различаются: в H2 " + a + ", в PostgreSQL " + b
                    + ". Запустите на H2-базе ту же версию приложения, что и на сервере.");
        }
    }

    private static int scalar(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static List<String> tables(Connection target) throws SQLException {
        List<String> out = new ArrayList<>();
        DatabaseMetaData md = target.getMetaData();
        try (ResultSet rs = md.getTables(null, "public", "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (!name.equalsIgnoreCase(HISTORY)) {
                    out.add(name);
                }
            }
        }
        return out;
    }

    private static List<Fk> foreignKeys(Connection target, List<String> tables) throws SQLException {
        List<Fk> out = new ArrayList<>();
        DatabaseMetaData md = target.getMetaData();
        for (String t : tables) {
            try (ResultSet rs = md.getImportedKeys(null, "public", t)) {
                while (rs.next()) {
                    out.add(new Fk(t, rs.getString("FKCOLUMN_NAME"), rs.getString("PKTABLE_NAME")));
                }
            }
        }
        return out;
    }

    /** Порядок копирования: родители раньше детей. Связи, которые мешают порядку (цикл или ссылка на себя), откладываются. */
    private static List<String> order(List<String> tables, List<Fk> fks, Set<Fk> deferred) {
        Map<String, Set<String>> parents = new HashMap<>();
        for (String t : tables) {
            parents.put(t, new LinkedHashSet<>());
        }
        for (Fk fk : fks) {
            if (fk.table().equals(fk.parent())) {
                deferred.add(fk);
            } else {
                parents.get(fk.table()).add(fk.parent());
            }
        }
        List<String> order = new ArrayList<>();
        Set<String> left = new LinkedHashSet<>(tables);
        while (!left.isEmpty()) {
            String next = null;
            for (String t : left) {
                if (parents.get(t).stream().noneMatch(left::contains)) {
                    next = t;
                    break;
                }
            }
            if (next == null) {
                // цикл: берем таблицу с наименьшим числом неготовых родителей и откладываем ее связи на оставшиеся таблицы
                String cut = left.stream().min((x, y) -> Long.compare(pending(parents, x, left), pending(parents, y, left))).orElseThrow();
                for (Fk fk : fks) {
                    if (fk.table().equals(cut) && left.contains(fk.parent())) {
                        deferred.add(fk);
                    }
                }
                parents.get(cut).removeIf(left::contains);
                next = cut;
            }
            order.add(next);
            left.remove(next);
        }
        return order;
    }

    private static long pending(Map<String, Set<String>> parents, String table, Set<String> left) {
        return parents.get(table).stream().filter(left::contains).count();
    }

    private static int copyTable(Connection source, Connection target, String table, Set<Fk> deferred, List<Object[]> later)
            throws SQLException {
        Set<String> deferredColumns = new LinkedHashSet<>();
        for (Fk fk : deferred) {
            if (fk.table().equals(table)) {
                deferredColumns.add(fk.column().toLowerCase());
            }
        }
        int rows = 0;
        try (Statement st = source.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM " + q(table))) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= n; i++) {
                columns.add(md.getColumnLabel(i).toLowerCase());
            }
            String sql = "INSERT INTO " + q(table) + " (" + String.join(", ", columns.stream().map(H2DataTransfer::q).toList())
                    + ") VALUES (" + "?, ".repeat(n - 1) + "?)";
            int idIndex = columns.indexOf("id");
            try (PreparedStatement ps = target.prepareStatement(sql)) {
                int batch = 0;
                while (rs.next()) {
                    for (int i = 1; i <= n; i++) {
                        Object value = rs.getObject(i);
                        String col = columns.get(i - 1);
                        if (value != null && deferredColumns.contains(col)) {
                            if (idIndex < 0) {
                                throw new IllegalStateException("В таблице " + table + " нет столбца id, связь " + col + " не перенести");
                            }
                            later.add(new Object[] {table, col, rs.getObject(idIndex + 1), value});
                            value = null;
                        }
                        ps.setObject(i, value);
                    }
                    ps.addBatch();
                    rows++;
                    if (++batch == BATCH) {
                        ps.executeBatch();
                        batch = 0;
                    }
                }
                ps.executeBatch();
            }
        }
        return rows;
    }

    /** После копирования со своими id счетчики идентификаторов ставятся на максимум, иначе следующая запись получила бы занятый id. */
    private static void fixSequences(Connection target, List<String> tables) throws SQLException {
        for (String t : tables) {
            boolean hasId;
            try (ResultSet cols = target.getMetaData().getColumns(null, "public", t, "id")) {
                hasId = cols.next();
            }
            if (!hasId) {
                continue;
            }
            String seq = null;
            try (PreparedStatement ps = target.prepareStatement("SELECT pg_get_serial_sequence(?, 'id')")) {
                ps.setString(1, q(t));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        seq = rs.getString(1);
                    }
                }
            } catch (SQLException e) {
                target.rollback();
                throw e;
            }
            if (seq != null) {
                try (Statement st = target.createStatement()) {
                    st.execute("SELECT setval('" + seq + "', COALESCE((SELECT MAX(id) FROM " + q(t) + "), 1), (SELECT COUNT(*) > 0 FROM "
                            + q(t) + "))");
                }
            }
        }
    }

    private static String q(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
