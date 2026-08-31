package io.kelta.runtime.module.service;

/**
 * A second stand-in platform port, so {@code RuntimeModuleManagerServiceTest} can exercise a module
 * that publishes more than one service and has the later one refused.
 */
public interface CountingPort {

    int count();
}
