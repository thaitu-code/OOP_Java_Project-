package vn.id.fitnessai.controller;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import vn.id.fitnessai.service.WorkoutService;
import vn.id.fitnessai.service.WorkoutTrackingService;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(OutputCaptureExtension.class)
class WorkoutDatabaseErrorHandlerTest {
    private final WorkoutController controller = new WorkoutController(
            mock(WorkoutService.class), mock(WorkoutTrackingService.class));

    @Test
    void logsConstraintAndSqlStateWithoutRowValuesOrSql(CapturedOutput output) {
        SQLException sql = new SQLException("PRIVATE_ROW_CONTENT", "23514");
        ConstraintViolationException cause = new ConstraintViolationException(
                "PRIVATE_SQL_CONTENT", sql, "workout_details_status_check");

        var response = controller.handleDataConflict(new DataIntegrityViolationException("PRIVATE_WRAPPER", cause));

        assertEquals(409, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().toString().contains("workout_details_status_check"));
        assertFalse(response.getBody().toString().contains("PRIVATE"));
        assertTrue(output.getAll().contains("constraint=workout_details_status_check"));
        assertTrue(output.getAll().contains("SQLState=23514"));
        assertFalse(output.getAll().contains("PRIVATE"));
    }

    @Test
    void missingJdbcCauseStillReturnsConflictAndLogsSafeMetadata(CapturedOutput output) {
        var response = controller.handleDataConflict(new DataIntegrityViolationException("PRIVATE_DETAIL"));

        assertEquals(409, response.getStatusCode().value());
        assertTrue(output.getAll().contains("constraint=unknown"));
        assertTrue(output.getAll().contains("SQLState=unknown"));
        assertFalse(output.getAll().contains("PRIVATE_DETAIL"));
    }
}
