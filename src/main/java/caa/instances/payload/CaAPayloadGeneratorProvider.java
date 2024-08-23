package caa.instances.payload;

import burp.api.montoya.intruder.AttackConfiguration;
import burp.api.montoya.intruder.PayloadGenerator;
import burp.api.montoya.intruder.PayloadGeneratorProvider;

public class CaAPayloadGeneratorProvider implements PayloadGeneratorProvider {
    @Override
    public String displayName() {
        return "CaA - Payload Generator";
    }

    @Override
    public PayloadGenerator providePayloadGenerator(AttackConfiguration attackConfiguration) {
        return new CaAPayloadGenerator();
    }
}
