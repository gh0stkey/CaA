package caa.instances.payload;

import burp.api.montoya.intruder.GeneratedPayload;
import burp.api.montoya.intruder.IntruderInsertionPoint;
import burp.api.montoya.intruder.PayloadGenerator;
import caa.Config;

public class CaAPayloadGenerator implements PayloadGenerator {
    private int payloadIndex = 0;

    @Override
    public GeneratedPayload generatePayloadFor(IntruderInsertionPoint insertionPoint) {
        if (payloadIndex > Config.globalPayload.size()) {
            return GeneratedPayload.end();
        }

        String payload = Config.globalPayload.get(payloadIndex);

        payloadIndex++;

        return GeneratedPayload.payload(payload);
    }
}
