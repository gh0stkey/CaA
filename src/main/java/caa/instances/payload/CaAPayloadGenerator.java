package caa.instances.payload;

import burp.api.montoya.intruder.GeneratedPayload;
import burp.api.montoya.intruder.IntruderInsertionPoint;
import burp.api.montoya.intruder.PayloadGenerator;
import caa.Config;

import java.util.List;

public class CaAPayloadGenerator implements PayloadGenerator {
    private int payloadIndex = 0;

    @Override
    public GeneratedPayload generatePayloadFor(IntruderInsertionPoint insertionPoint) {
        List<String> payload = Config.globalPayload;

        if (payloadIndex >= payload.size()) {
            return GeneratedPayload.end();
        }

        String payloadValue = payload.get(payloadIndex);

        payloadIndex++;

        return GeneratedPayload.payload(payloadValue);
    }
}
