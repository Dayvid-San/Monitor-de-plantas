package com.plantmonitor.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conexão única e síncrona ao SQLite (data/plants.db), no mesmo espírito do
 * better-sqlite3 usado na versão Node.js: sem pool, sem ORM, helpers finos
 * sobre JDBC puro.
 */
public class Database {
    public static final String DATA_DIR = "data";
    public static final String PHOTOS_DIR = DATA_DIR + File.separator + "photos";

    private static Connection connection;

    public static synchronized Connection get() {
        if (connection == null) {
            throw new IllegalStateException("Database.init() precisa ser chamado antes de Database.get()");
        }
        return connection;
    }

    public static synchronized void init() {
        new File(PHOTOS_DIR).mkdirs();
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + DATA_DIR + File.separator + "plants.db");
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
            }
            applySchema();
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao abrir o banco de dados", e);
        }
    }

    private static void applySchema() throws SQLException {
        String schema = readResource("/schema.sql");
        try (Statement st = connection.createStatement()) {
            for (String statement : schema.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    st.execute(trimmed);
                }
            }
        }
    }

    private static String readResource(String path) {
        try (InputStream is = Database.class.getResourceAsStream(path)) {
            if (is == null) throw new IOException("Recurso não encontrado: " + path);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Equivalente a db.prepare(sql).all(params) do better-sqlite3. */
    public static List<Map<String, Object>> queryAll(String sql, Object... params) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = get().prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rows.addAll(toMaps(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return rows;
    }

    /** Equivalente a db.prepare(sql).get(params) do better-sqlite3 (null se não achar). */
    public static Map<String, Object> queryOne(String sql, Object... params) {
        List<Map<String, Object>> rows = queryAll(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Equivalente a db.prepare(sql).run(params) — retorna o id gerado (ou -1 se não houver). */
    public static long update(String sql, Object... params) {
        try (PreparedStatement ps = get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            return -1;
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) {
                ps.setNull(i + 1, java.sql.Types.NULL);
            } else {
                ps.setObject(i + 1, params[i]);
            }
        }
    }

    private static List<Map<String, Object>> toMaps(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int columns = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columns; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    /** Envolve SQLException para expor o código de erro do SQLite (ex. constraint UNIQUE) sem checked exceptions. */
    public static class DbException extends RuntimeException {
        public final String sqliteErrorCode;

        DbException(SQLException cause) {
            super(cause);
            this.sqliteErrorCode = extractCode(cause);
        }

        private static String extractCode(SQLException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("UNIQUE constraint failed")) return "SQLITE_CONSTRAINT_UNIQUE";
            return "SQLITE_ERROR";
        }
    }
}
