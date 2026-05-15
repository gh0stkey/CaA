package caa.instances.menu;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import caa.component.generator.Generator;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GeneratorContextMenuProvider implements ContextMenuItemsProvider {

    private final Generator generator;

    public GeneratorContextMenuProvider(Generator generator) {
        this.generator = generator;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        if (generator == null) {
            return List.of();
        }

        List<HttpRequest> requests = new ArrayList<>();

        List<HttpRequestResponse> requestResponses =
                event.selectedRequestResponses();
        for (HttpRequestResponse rr : requestResponses) {
            requests.add(rr.request());
        }

        Optional<MessageEditorHttpRequestResponse> editorRequestResponse =
                event.messageEditorRequestResponse();
        editorRequestResponse.ifPresent(messageEditorHttpRequestResponse -> requests.add(
                messageEditorHttpRequestResponse.requestResponse().request()
        ));

        if (requests.isEmpty()) {
            return List.of();
        }

        List<Component> menuItems = new ArrayList<>();

        JMenuItem sendToGeneratorItem = new JMenuItem("Send to CaA Generator");
        sendToGeneratorItem.addActionListener(e -> {
            for (HttpRequest request : requests) {
                generator.insertNewTab(request, "Param", "");
            }
        });

        menuItems.add(sendToGeneratorItem);
        return menuItems;
    }
}
