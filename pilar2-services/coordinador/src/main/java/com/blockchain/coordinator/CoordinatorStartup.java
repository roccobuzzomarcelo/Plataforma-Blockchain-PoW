package com.blockchain.coordinator;

import com.blockchain.coordinator.service.GenesisService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Al arrancar la aplicación, inicializa el bloque génesis si Redis está vacío.
 */
@Component
public class CoordinatorStartup implements ApplicationRunner {

    private final GenesisService genesisService;

    public CoordinatorStartup(GenesisService genesisService) {
        this.genesisService = genesisService;
    }

    @Override
    public void run(ApplicationArguments args) {
        genesisService.initializeIfNeeded();
    }
}