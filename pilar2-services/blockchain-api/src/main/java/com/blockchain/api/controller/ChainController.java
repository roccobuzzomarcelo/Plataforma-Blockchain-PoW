package com.blockchain.api.controller;

import com.blockchain.api.service.BlockchainService;
import com.blockchain.shared.model.Block;
import com.blockchain.shared.model.Transaction;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chain")
public class ChainController {

    private final BlockchainService blockchainService;

    public ChainController(BlockchainService blockchainService) {
        this.blockchainService = blockchainService;
    }

    // GET /api/chain/blocks
    @GetMapping("/blocks")
    public ResponseEntity<List<Block>> getAllBlocks() {
        return ResponseEntity.ok(blockchainService.getAllBlocks());
    }

    // GET /api/chain/blocks/{index}
    @GetMapping("/blocks/{index}")
    public ResponseEntity<Block> getBlock(@PathVariable int index) {
        Block block = blockchainService.getBlock(index);
        return block != null
                ? ResponseEntity.ok(block)
                : ResponseEntity.notFound().build();
    }

    // GET /api/chain/blocks/latest
    @GetMapping("/blocks/latest")
    public ResponseEntity<Block> getLatestBlock() {
        Block block = blockchainService.getLatestBlock();
        return block != null
                ? ResponseEntity.ok(block)
                : ResponseEntity.notFound().build();
    }

    // GET /api/chain/transactions/pending
    @GetMapping("/transactions/pending")
    public ResponseEntity<List<Transaction>> getPendingTransactions() {
        return ResponseEntity.ok(blockchainService.getPendingTransactions());
    }

    // GET /api/chain/stats
    @GetMapping("/stats")
    public ResponseEntity<BlockchainService.BlockchainStats> getStats() {
        return ResponseEntity.ok(blockchainService.getStats());
    }
}