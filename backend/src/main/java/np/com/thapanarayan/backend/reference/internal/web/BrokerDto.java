package np.com.thapanarayan.backend.reference.internal.web;

import np.com.thapanarayan.backend.reference.internal.domain.Broker;

public record BrokerDto(Integer brokerId, String name, String status) {

    static BrokerDto from(Broker b) {
        return new BrokerDto(b.getBrokerId(), b.getName(), b.getStatus().name());
    }
}
