package np.com.thapanarayan.backend.ingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import np.com.thapanarayan.backend.platform.api.DomainException;

class FilenameValidatorTest {

    @Test
    void acceptsConformingNameAndExtractsDate() {
        assertThat(FilenameValidator.requireValidAndExtractDate("2026-01-13.csv"))
                .isEqualTo(LocalDate.of(2026, 1, 13));
    }

    @Test
    void rejectsWrongExtensionOrShape() {
        assertThatThrownBy(() -> FilenameValidator.requireValidAndExtractDate("2026-01-13.txt"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> FilenameValidator.requireValidAndExtractDate("floorsheet.csv"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsPathTraversalAttempts() {
        assertThatThrownBy(() -> FilenameValidator.requireValidAndExtractDate("../../etc/passwd"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> FilenameValidator.requireValidAndExtractDate("../2026-01-13.csv"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsImpossibleDate() {
        assertThatThrownBy(() -> FilenameValidator.requireValidAndExtractDate("2026-13-40.csv"))
                .isInstanceOf(DomainException.class);
    }
}
