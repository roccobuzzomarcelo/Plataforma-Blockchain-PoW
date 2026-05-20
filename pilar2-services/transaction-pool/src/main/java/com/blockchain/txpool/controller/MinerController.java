package com.blockchain.txpool.controller;

import com.blockchain.txpool.service.MinerMonitorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pool/miners")
public class MinerController {

    private final MinerMonitorService minerMonitor;

    public MinerController(MinerMonitorService minerMonitor) {
        this.minerMonitor = minerMonitor;
    }

    // POST /api/pool/miners/keepalive
    @PostMapping("/keepalive")
    public ResponseEntity<String> keepAlive(@RequestBody KeepaliveRequest request) {
        minerMonitor.registerKeepAlive(request.workerId());
        return ResponseEntity.ok("Keep-alive registrado: " + request.workerId());
    }

    // GET /api/pool/miners/status
    @GetMapping("/status")
    public ResponseEntity<MinerStatus> getMinerStatus() {
        return ResponseEntity.ok(new MinerStatus(
                minerMonitor.hasActiveGpuMiners(),
                minerMonitor.getActiveGpuMinerCount()));
    }

    public record KeepaliveRequest(String workerId) {
    }

    public record MinerStatus(boolean gpuAvailable, long activeGpuMiners) {
    }
}