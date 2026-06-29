package np.com.thapanarayan.backend.ingestion.internal;

import np.com.thapanarayan.backend.ingestion.api.IngestionBatchView;
import np.com.thapanarayan.backend.ingestion.api.IngestionJobView;
import np.com.thapanarayan.backend.ingestion.api.RejectionView;

/** Entity → published-view mapping. */
final class IngestionMapper {

    private IngestionMapper() {
    }

    static IngestionJobView toView(IngestionJobEntity e) {
        return new IngestionJobView(
                e.getId(), e.getBatchId(), e.getTradeDate(), e.getSourceFilename(), e.getFileHash(),
                e.getRowsRead(), e.getRowsAccepted(), e.getRowsRejected(), e.getRowsDuplicate(),
                e.getStatus(), e.getStartedAt(), e.getFinishedAt());
    }

    static IngestionBatchView toView(IngestionBatchEntity e) {
        return new IngestionBatchView(
                e.getId(), e.getFileCount(), e.getDateFrom(), e.getDateTo(),
                e.getStatus(), e.getSubmittedBy(), e.getSubmittedAt(), e.getFinishedAt());
    }

    static RejectionView toView(IngestionRejectionEntity e) {
        return new RejectionView(e.getId(), e.getJobId(), e.getRawLine(), e.getReasonCode(), e.getDetail());
    }
}
