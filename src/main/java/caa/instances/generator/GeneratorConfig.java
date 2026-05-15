package caa.instances.generator;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.List;
import java.util.Map;

public class GeneratorConfig {

    private final HttpRequest baseRequest;
    private final List<String> endpoints;
    private final String endpointType;
    private final List<String> params;
    private final String paramType; // "Param" 或 "Value"
    private final String valueType;
    private final String valueInput;
    private final int valueLength;
    private final Map<String, ParamLocation> selectedMethods;

    public GeneratorConfig(
            HttpRequest baseRequest,
            List<String> endpoints,
            String endpointType,
            List<String> params,
            String paramType,
            String valueType,
            String valueInput,
            int valueLength,
            Map<String, ParamLocation> selectedMethods
    ) {
        this.baseRequest = baseRequest;
        this.endpoints = endpoints != null ? List.copyOf(endpoints) : List.of();
        this.endpointType = endpointType;
        this.params = params != null ? List.copyOf(params) : List.of();
        this.paramType = paramType;
        this.valueType = valueType;
        this.valueInput = valueInput;
        this.valueLength = valueLength;
        this.selectedMethods =
                selectedMethods != null ? Map.copyOf(selectedMethods) : Map.of();
    }

    public HttpRequest getBaseRequest() {
        return baseRequest;
    }

    public List<String> getEndpoints() {
        return endpoints;
    }

    public String getEndpointType() {
        return endpointType;
    }

    public List<String> getParams() {
        return params;
    }

    public String getParamType() {
        return paramType;
    }

    public String getValueType() {
        return valueType;
    }

    public String getValueInput() {
        return valueInput;
    }

    public int getValueLength() {
        return valueLength;
    }

    public Map<String, ParamLocation> getSelectedMethods() {
        return selectedMethods;
    }

    public boolean hasEndpoints() {
        return !endpoints.isEmpty();
    }

    public boolean hasParams() {
        return !params.isEmpty();
    }

    public boolean isValueMode() {
        return "Value".equals(paramType);
    }
}
