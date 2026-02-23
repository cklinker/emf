package com.emf.controlplane.action.handlers;

import com.emf.controlplane.action.CollectionActionHandler;
import com.emf.controlplane.service.ConnectedAppService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Action handler for rotating a connected app's client secret.
 */
@Component
public class RotateConnectedAppSecretHandler implements CollectionActionHandler {

    private final ConnectedAppService connectedAppService;

    public RotateConnectedAppSecretHandler(ConnectedAppService connectedAppService) {
        this.connectedAppService = connectedAppService;
    }

    @Override
    public String getCollectionName() {
        return "connected-apps";
    }

    @Override
    public String getActionName() {
        return "rotate-secret";
    }

    @Override
    public boolean isInstanceAction() {
        return true;
    }

    @Override
    public Object execute(String id, Map<String, Object> body, String tenantId, String userId) {
        ConnectedAppService.ConnectedAppCreateResult result = connectedAppService.rotateSecret(id);
        return Map.of(
                "id", id,
                "clientId", result.getApp().getClientId(),
                "clientSecret", result.getPlaintextSecret()
        );
    }
}
