package com.blockchain.worker.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/worker")
public class WorkerController {

    @Value("${worker.id:worker-1}")
    private String workerId;

    @Value("${worker.threads:8}")
    private int threads;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "workerId", workerId,
                "threads", threads,
                "status", "running"));
    }
}