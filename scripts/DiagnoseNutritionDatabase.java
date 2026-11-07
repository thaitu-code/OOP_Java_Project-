import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Properties;

/** Metadata only. No application startup, DDL, user records, or credential output. */
public class DiagnoseNutritionDatabase {
    public static void main(String[] args) throws Exception {
        Properties config = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("user", DiagnoseWorkoutDatabase.setting(config, "username"));
        connectionProperties.setProperty("password", DiagnoseWorkoutDatabase.setting(config, "password"));
        connectionProperties.setProperty("connectTimeout", "8");
        connectionProperties.setProperty("socketTimeout", "8");
        connectionProperties.setProperty("readOnly", "true");
        DriverManager.setLoginTimeout(8);
        try (Connection connection = DriverManager.getConnection(
                DiagnoseWorkoutDatabase.setting(config, "url"), connectionProperties)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(8);
                statement.execute("SET TRANSACTION READ ONLY");
                statement.execute("SET LOCAL statement_timeout = '5s'");
                print(statement, """
                    SELECT table_name, column_name, data_type, is_nullable,
                           character_maximum_length, numeric_precision, numeric_scale
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND (table_name IN ('foods','food_categories','daily_meal_logs','meal_details')
                           OR (table_name = 'users' AND column_name = 'id'))
                    ORDER BY table_name, ordinal_position
                    """);
                print(statement, """
                    SELECT c.relname AS table_name, con.conname,
                           pg_get_constraintdef(con.oid) AS definition
                    FROM pg_constraint con
                    JOIN pg_class c ON c.oid = con.conrelid
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'public'
                      AND c.relname IN ('foods','food_categories','daily_meal_logs','meal_details')
                    ORDER BY c.relname, con.conname
                    """);
                print(statement, """
                    SELECT tablename, indexname, indexdef FROM pg_indexes
                    WHERE schemaname = 'public'
                      AND tablename IN ('foods','food_categories','daily_meal_logs','meal_details')
                    ORDER BY tablename, indexname
                    """);
            } finally {
                connection.rollback();
            }
        } catch (SQLException failure) {
            System.err.println("Nutrition metadata read failed; SQLState=" + failure.getSQLState()
                    + "; type=" + failure.getClass().getSimpleName());
            System.exit(1);
        }
    }

    private static void print(Statement statement, String sql) throws SQLException {
        try (ResultSet rows = statement.executeQuery(sql)) {
            ResultSetMetaData metadata = rows.getMetaData();
            int count = 0;
            while (rows.next()) {
                if (++count > 100) throw new IllegalStateException("Unexpected metadata volume");
                for (int column = 1; column <= metadata.getColumnCount(); column++) {
                    System.out.print((column == 1 ? "" : " | ") + metadata.getColumnLabel(column)
                            + "=" + rows.getObject(column));
                }
                System.out.println();
            }
            System.out.println("Metadata rows: " + count);
        }
    }
}
