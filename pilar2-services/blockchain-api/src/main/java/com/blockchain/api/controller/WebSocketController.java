package com.blockchain.api.controller;

import com.blockchain.shared.event.BlockMinedEvent;

import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // Notifica a todos los clientes cuando se mina un bloque
    public void notifyBlockMined(@NonNull BlockMinedEvent event) {
        messagingTemplate.convertAndSend("/topic/blocks", event);
    }

    // Notifica el estado actual de los workers
    public void notifyWorkerStatus(@NonNull Object status) {
        messagingTemplate.convertAndSend("/topic/workers", status);
    }
}