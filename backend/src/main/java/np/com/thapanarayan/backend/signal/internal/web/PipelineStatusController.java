package np.com.thapanarayan.backend.signal.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import np.com.thapanarayan.backend.signal.internal.PipelineStatusTracker;
import np.com.thapanarayan.backend.signal.internal.PipelineStatusTracker.StageStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Last-run state per pipeline stage (ingest → aggregate → indicators → signals). */
@RestController
@RequestMapping("/api/v1/system/pipeline")
@Tag(name = "Pipeline", description = "Daily pipeline status")
class PipelineStatusController {

    private final PipelineStatusTracker tracker;

    PipelineStatusController(PipelineStatusTracker tracker) {
        this.tracker = tracker;
    }

    @GetMapping("/status")
    List<StageStatus> status() {
        return tracker.snapshot();
    }
}
