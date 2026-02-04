package caa.component.datatable;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import caa.component.generator.Generator;
import caa.instances.Database;
import caa.utils.ConfigLoader;

public record DatatableContext(MontoyaApi api, Database db, ConfigLoader configLoader, Generator generator, HttpRequest httpRequest) {}
