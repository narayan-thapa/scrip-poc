package np.com.thapanarayan.backend.ingestion.internal.archive;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@link RawArchive} that writes to MinIO/S3 when configured, otherwise to a local directory so
 * dev works without an object store. Keys are {@code raw/{date}/{hash}.csv} — derived only from the
 * trade date and content hash, never the untrusted filename.
 */
@Component
class RawArchiveImpl implements RawArchive {

    private final ObjectProvider<MinioClient> minioProvider;
    private final String bucket;
    private final Path localRoot;

    RawArchiveImpl(ObjectProvider<MinioClient> minioProvider,
                   @Value("${platform.object-store.bucket:nepse-raw}") String bucket,
                   @Value("${platform.object-store.local-dir:${java.io.tmpdir}/nepse-raw}") String localDir) {
        this.minioProvider = minioProvider;
        this.bucket = bucket;
        this.localRoot = Path.of(localDir);
    }

    @Override
    public String store(LocalDate tradeDate, String contentHash, byte[] content) {
        String key = "raw/" + tradeDate + "/" + contentHash + ".csv";
        MinioClient minio = minioProvider.getIfAvailable();
        if (minio != null) {
            storeToObjectStore(minio, key, content);
        } else {
            storeToLocal(key, content);
        }
        return key;
    }

    @Override
    public InputStream open(String key) {
        MinioClient minio = minioProvider.getIfAvailable();
        try {
            if (minio != null) {
                return minio.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
            }
            return Files.newInputStream(localRoot.resolve(key));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot open archived object: " + key, e);
        }
    }

    private void storeToObjectStore(MinioClient minio, String key, byte[] content) {
        try {
            boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                    .contentType("text/csv")
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to archive to object store: " + key, e);
        }
    }

    private void storeToLocal(String key, byte[] content) {
        try {
            Path target = localRoot.resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to archive locally: " + key, e);
        }
    }
}
