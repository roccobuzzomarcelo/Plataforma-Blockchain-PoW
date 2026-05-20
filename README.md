# Blockchain PoW

```bash
blockchain-pow/
│
├── README.md
├── .gitignore
├── .env.example
│
├── pilar1-miner/
│   │
│   ├── cpu-miner/
│   │   ├── src/main/java/com/blockchain/miner/
│   │   │   ├── CpuMinerApplication.java
│   │   │   ├── miner/
│   │   │   │   ├── CpuMiner.java
│   │   │   │   └── MinerResult.java
│   │   │   └── hash/
│   │   │       └── HashUtils.java
│   │   └── pom.xml
│   │
│   ├── gpu-miner/                          # Se completa en Pilar 2
│   │   ├── src/
│   │   │   └── range_miner.cu             # Basado en Hit #7
│   │   └── Dockerfile                      # Basado en GPGPU-Sim
│   │
│   ├── hits/
│   │   ├── hit1-setup/
│   │   │   └── README.md
│   │   ├── hit2-hello-world/
│   │   │   └── hello.cu
│   │   ├── hit3-libs/
│   │   │   ├── thrust_host_only.cu
│   │   │   ├── thrust_vectors.cu
│   │   │   └── README.md
│   │   ├── hit4-hash/
│   │   │   ├── md5_gpu.cu
│   │   │   └── README.md
│   │   ├── hit5-brute-force/
│   │   │   ├── brute_force.cu
│   │   │   └── README.md
│   │   ├── hit6-prefix-length/
│   │   │   ├── prefix_test.cu
│   │   │   └── README.md
│   │   └── hit7-range-limits/
│   │       ├── range_miner.cu
│   │       └── README.md
│   │
│   ├── benchmarks/
│   │   ├── run_benchmarks.sh
│   │   └── results/
│   │
│   └── tests/
│       ├── test_cases.json
│       └── validate.sh
│
├── pilar2-services/
│   │
│   ├── shared/                             # Módulo Maven compartido
│   │   ├── src/main/java/com/blockchain/shared/
│   │   │   ├── model/
│   │   │   │   ├── Transaction.java
│   │   │   │   ├── Block.java
│   │   │   │   └── MiningTask.java
│   │   │   ├── event/
│   │   │   │   ├── MiningResultEvent.java
│   │   │   │   └── BlockMinedEvent.java
│   │   │   └── util/
│   │   │       └── HashUtils.java
│   │   └── pom.xml
│   │
│   ├── blockchain-api/                     # Backend REST + WebSocket
│   │   ├── src/main/java/com/blockchain/api/
│   │   │   ├── BlockchainApiApplication.java
│   │   │   ├── config/
│   │   │   │   ├── RedisConfig.java
│   │   │   │   ├── WebSocketConfig.java
│   │   │   │   └── CorsConfig.java
│   │   │   ├── controller/
│   │   │   │   ├── ChainController.java
│   │   │   │   └── WebSocketController.java
│   │   │   └── service/
│   │   │       └── BlockchainService.java
│   │   ├── src/main/resources/
│   │   │   └── application.yml
│   │   ├── Dockerfile
│   │   └── pom.xml
│   │
│   ├── coordinator/                        # Nodo Coordinador de Tareas (NCT)
│   │   ├── src/main/java/com/blockchain/coordinator/
│   │   │   ├── CoordinatorApplication.java
│   │   │   ├── config/
│   │   │   │   ├── RabbitMQConfig.java
│   │   │   │   └── RedisConfig.java
│   │   │   ├── controller/
│   │   │   │   └── CoordinatorController.java
│   │   │   ├── service/
│   │   │   │   ├── BlockService.java
│   │   │   │   └── ConsensusService.java
│   │   │   └── messaging/
│   │   │       ├── TaskPublisher.java
│   │   │       └── ResultConsumer.java
│   │   ├── src/main/resources/
│   │   │   └── application.yml
│   │   ├── Dockerfile
│   │   └── pom.xml
│   │
│   ├── transaction-pool/                   # Pool de Transacciones (TrP)
│   │   ├── src/main/java/com/blockchain/txpool/
│   │   │   ├── TransactionPoolApplication.java
│   │   │   ├── config/
│   │   │   │   └── RabbitMQConfig.java
│   │   │   ├── controller/
│   │   │   │   └── TransactionController.java
│   │   │   └── service/
│   │   │       ├── PoolService.java
│   │   │       └── SplitService.java
│   │   ├── src/main/resources/
│   │   │   └── application.yml
│   │   ├── Dockerfile
│   │   └── pom.xml
│   │
│   ├── worker/                             # Nodo Worker (minero CPU Java)
│   │   ├── src/main/java/com/blockchain/worker/
│   │   │   ├── WorkerApplication.java
│   │   │   ├── config/
│   │   │   │   └── RabbitMQConfig.java
│   │   │   ├── messaging/
│   │   │   │   ├── TaskConsumer.java
│   │   │   │   └── ResultPublisher.java
│   │   │   └── miner/
│   │   │       └── PoWMiner.java
│   │   ├── src/main/resources/
│   │   │   └── application.yml
│   │   ├── Dockerfile
│   │   └── pom.xml
│   │
│   ├── frontend/                           # React
│   │   ├── src/
│   │   │   ├── App.jsx
│   │   │   ├── components/
│   │   │   │   ├── BlockList.jsx
│   │   │   │   ├── TransactionForm.jsx
│   │   │   │   └── MinerStatus.jsx
│   │   │   └── services/
│   │   │       └── api.js
│   │   ├── package.json
│   │   ├── Dockerfile
│   │   └── nginx.conf
│   │
│   └── docker-compose.yml
│
├── pilar3-infra/
│   │
│   ├── opentofu/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   └── modules/
│   │       ├── gke-cluster/
│   │       ├── node-groups/
│   │       └── external-vms/
│   │
│   ├── k8s/
│   │   ├── namespace.yaml
│   │   ├── infra/
│   │   │   ├── rabbitmq.yaml
│   │   │   └── redis.yaml
│   │   └── apps/
│   │       ├── coordinator.yaml
│   │       ├── worker.yaml
│   │       ├── transaction-pool.yaml
│   │       ├── blockchain-api.yaml
│   │       └── frontend.yaml
│   │
│   ├── pipelines/
│   │   ├── pipeline1-infra.yml
│   │   ├── pipeline2-services.yml
│   │   ├── pipeline3-apps.yml
│   │   └── pipeline4-workers.yml
│   │
│   └── tests-load/
│       ├── load_test.sh
│       └── scenarios/
│           ├── bulk_transactions.json
│           ├── prefix_difficulty.json
│           └── pool_fragmentation.json
│
└── docs/
    ├── informe/
    │   ├── assets/
    │   │   ├── pilar1/
    │   │   └── pilar2/
    │   ├── pilar1.md
    │   ├── pilar2.md
    │   └── pilar3.md
    ├── diagramas/
    │   └── arquitectura.png
    └── resultados/
        ├── graficos/
        └── analisis.md
```