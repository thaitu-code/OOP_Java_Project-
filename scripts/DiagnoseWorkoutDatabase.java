import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only metadata diagnosis. Never prints connection credentials. */
public class DiagnoseWorkoutDatabase {
    public static void main(String[] args) throws Exception {
        Properties config = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("user", setting(config, "username"));
        connectionProperties.setProperty("password", setting(config, "password"));
        connectionProperties.setProperty("connectTimeout", "8");
        connectionProperties.setProperty("socketTimeout", "8");
        connectionProperties.setProperty("readOnly", "true");
        String url = setting(config, "url");
        DriverManager.setLoginTimeout(8);
        try (Connection connection = DriverManager.getConnection(url, connectionProperties)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(8);
                statement.execute("SET TRANSACTION READ ONLY");
                statement.execute("SET LOCAL statement_timeout = '5s'");
                if (args.length == 1 && "--latency".equals(args[0])) {
                    for (int sample = 1; sample <= 8; sample++) {
                        long started = System.nanoTime();
                        try (ResultSet rows = statement.executeQuery("SELECT 1")) { rows.next(); }
                        System.out.printf(java.util.Locale.ROOT, "SELECT 1 round-trip sample %d: %.1f ms%n",
                                sample, (System.nanoTime() - started) / 1_000_000.0);
                    }
                    try (ResultSet rows = statement.executeQuery("EXPLAIN (ANALYZE, TIMING OFF) SELECT 1")) {
                        while (rows.next()) {
                            String line = rows.getString(1);
                            if (line.startsWith("Planning Time:") || line.startsWith("Execution Time:")) System.out.println(line);
                        }
                    }
                    return;
                }
                String script = Files.readString(Path.of("scripts/sql/workout_tracking_diagnose.sql"), StandardCharsets.UTF_8)
                        .replaceAll("(?m)^\\s*--.*$", "");
                for (String sql : script.split(";")) {
                    if (sql.isBlank()) continue;
                    if (!sql.stripLeading().matches("(?is)SELECT\\s+.*")) {
                        throw new IllegalStateException("Diagnosis accepts SELECT statements only");
                    }
                    try (ResultSet rows = statement.executeQuery(sql)) {
                        ResultSetMetaData metadata = rows.getMetaData();
                        for (int column = 1; column <= metadata.getColumnCount(); column++) {
                            System.out.print((column == 1 ? "" : " | ") + metadata.getColumnLabel(column));
                        }
                        System.out.println();
                        int count = 0;
                        while (rows.next()) {
                            if (++count > 100) throw new IllegalStateException("Unexpectedly large diagnostic result");
                            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                                System.out.print((column == 1 ? "" : " | ") + String.valueOf(rows.getObject(column)));
                            }
                            System.out.println();
                        }
                        System.out.println("Rows: " + count);
                    }
                }
            } finally {
                connection.rollback();
            }
        } catch (SQLException exception) {
            System.err.println("Read-only diagnosis failed. SQLState=" + exception.getSQLState()
                    + "; type=" + exception.getClass().getSimpleName());
            System.exit(1);
        }
    }

    static String setting(Properties config, String name) {
        String environmentKey = "SPRING_DATASOURCE_" + name.toUpperCase(java.util.Locale.ROOT);
        String value = System.getenv(environmentKey);
        if (value == null) value = config.getProperty("spring.datasource." + name);
        if (value == null) throw new IllegalStateException("Missing datasource setting: " + name);
        Pattern placeholder = Pattern.compile("\\$\\{([^{}]+)}");
        for (int pass = 0; pass < 10 && value.contains("${"); pass++) {
            Matcher matcher = placeholder.matcher(value);
            StringBuffer resolved = new StringBuffer();
            while (matcher.find()) {
                String expression = matcher.group(1);
                int separator = expression.indexOf(':');
                String key = separator < 0 ? expression : expression.substring(0, separator);
                String replacement = System.getenv(key);
                if (replacement == null) replacement = config.getProperty(key);
                if (replacement == null && separator >= 0) replacement = expression.substring(separator + 1);
                if (replacement == null) throw new IllegalStateException("Unresolved datasource variable: " + key);
                matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(resolved);
            value = resolved.toString();
        }
        if (value.contains("${")) throw new IllegalStateException("Unresolved datasource setting: " + name);
        return value;
    }
}
