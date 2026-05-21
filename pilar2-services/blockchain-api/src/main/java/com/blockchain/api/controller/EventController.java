package com.blockchain.api.controller;

import com.blockchain.shared.event.BlockMinedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final SimpMessagingTemplate ws;

    public EventController(SimpMessagingTemplate ws) {
        this.ws = ws;
    }

    @PostMapping("/block-mined")
    public ResponseEntity<Void> blockMined(@RequestBody BlockMinedEvent event) {
        ws.convertAndSend("/topic/blocks", event);
        return ResponseEntity.ok().build();
    }
}