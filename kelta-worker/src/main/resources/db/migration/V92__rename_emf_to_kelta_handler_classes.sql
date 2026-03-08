-- V92: Update workflow action handler class references from com.emf to io.kelta
-- Part of EMF -> Kelta rebranding

UPDATE workflow_action_type
SET handler_class = REPLACE(handler_class, 'com.emf.controlplane.service.workflow.handlers.', 'io.kelta.controlplane.service.workflow.handlers.'),
    updated_at = NOW()
WHERE handler_class LIKE 'com.emf.controlplane.service.workflow.handlers.%';
