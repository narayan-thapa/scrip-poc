package np.com.thapanarayan.backend.ingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import np.com.thapanarayan.backend.ingestion.internal.RawFileArchive.ArchivedFile;

class LocalRawFileArchiveTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 3);

    private LocalRawFileArchive archiveUnder(Path root) {
        IngestionProperties props = new IngestionProperties(
                52_428_800L, 400, 1_073_741_824L, new BigDecimal("0.5"),
                500, root.toString(), false, "", 300L);
        return new LocalRawFileArchive(props);
    }

    @Test
    void archivesContentAddressedAndDedupsIdenticalBytes(@TempDir Path root) throws IOException {
        LocalRawFileArchive archive = archiveUnder(root);
        byte[] content = "Symbol,Buyer\nBHCL,41\n".getBytes(StandardCharsets.UTF_8);

        ArchivedFile first = archive.archive(DATE, new ByteArrayInputStream(content));
        ArchivedFile second = archive.archive(DATE, new ByteArrayInputStream(content));

        assertThat(first.sha256()).isEqualTo(second.sha256());
        assertThat(first.path()).isEqualTo(second.path());
        assertThat(first.path().getFileName().toString()).isEqualTo(first.sha256() + ".csv");

        // Only the single content-addressed file exists (the temp .part was moved/removed).
        try (var files = Files.list(root.resolve("raw").resolve(DATE.toString()))) {
            assertThat(files.count()).isEqualTo(1);
        }
    }

    @Test
    void differentContentGetsDifferentHash(@TempDir Path root) throws IOException {
        LocalRawFileArchive archive = archiveUnder(root);

        ArchivedFile a = archive.archive(DATE, new ByteArrayInputStream("aaa".getBytes(StandardCharsets.UTF_8)));
        ArchivedFile b = archive.archive(DATE, new ByteArrayInputStream("bbb".getBytes(StandardCharsets.UTF_8)));

        assertThat(a.sha256()).isNotEqualTo(b.sha256());
    }

    @Test
    void findLatestReturnsAnArchivedFile(@TempDir Path root) throws IOException {
        LocalRawFileArchive archive = archiveUnder(root);
        archive.archive(DATE, new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));

        assertThat(archive.findLatest(DATE)).isPresent();
        assertThat(archive.findLatest(LocalDate.of(2099, 1, 1))).isEmpty();
    }
}
