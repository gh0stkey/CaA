package caa.instances.intruder;

import burp.api.montoya.intruder.GeneratedPayload;
import burp.api.montoya.intruder.IntruderInsertionPoint;
import burp.api.montoya.intruder.PayloadGenerator;
import caa.Config;
import caa.instances.generator.GeneratorConfig;
import caa.instances.generator.PayloadIterator;

public class CaAPayloadGenerator implements PayloadGenerator {

    private final PayloadIterator iterator;

    public CaAPayloadGenerator() {
        GeneratorConfig config = Config.generatorConfig;
        if (config != null) {
            this.iterator = new PayloadIterator(config);
        } else {
            this.iterator = null;
        }
    }

    @Override
    public GeneratedPayload generatePayloadFor(
            IntruderInsertionPoint insertionPoint
    ) {
        if (iterator == null || !iterator.hasNext()) {
            return GeneratedPayload.end();
        }

        String payload = iterator.next();
        return GeneratedPayload.payload(payload);
    }
}
