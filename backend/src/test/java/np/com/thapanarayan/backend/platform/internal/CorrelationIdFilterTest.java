package np.com.thapanarayan.backend.platform.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** The correlation-id filter: generate vs. honor inbound, echo on the response, and always clear the MDC. */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesAnIdWhenAbsentEchoesItAndClearsTheMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenInsideChain = new String[1];

        filter.doFilter(request, response, (req, res) -> seenInsideChain[0] = MDC.get(CorrelationIdFilter.MDC_KEY));

        assertThat(seenInsideChain[0]).isNotBlank();
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(seenInsideChain[0]);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull(); // cleared after the request
    }

    @Test
    void honorsAnInboundCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenInsideChain = new String[1];

        filter.doFilter(request, response, (req, res) -> seenInsideChain[0] = MDC.get(CorrelationIdFilter.MDC_KEY));

        assertThat(seenInsideChain[0]).isEqualTo("trace-123");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("trace-123");
    }
}
