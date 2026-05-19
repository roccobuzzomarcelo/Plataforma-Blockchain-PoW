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
│   ├── cpu-miner/                          # Minero CPU en Java 21
│   │   ├── src/
│   │   │   └── main/java/com/blockchain/miner/
│   │   │       ├── CpuMinerApplication.java
│   │   │       ├── miner/
│   │   │       │   ├── CpuMiner.java       # Lógica con ExecutorService
│   │   │       │   └── MinerResult.java    # Modelo resultado (record)
│   │   │       └── hash/
│   │   │           └── HashUtils.java      # MD5 / SHA-256 utils
│   │   └── pom.xml
│   │
│   ├── gpu-miner/                          # Minero GPU en CUDA
│   │   ├── src/
│   │   │   ├── main.cu                     # Entry point CUDA
│   │   │   ├── miner.cu                    # Lógica de minería GPU
│   │   │   └── hash_utils.cuh             # Header utils de hash
│   │   ├── CMakeLists.txt
│   │   └── Dockerfile                      # Basado en GPGPU-Sim
│   │
│   ├── hits/                               # Un directorio por Hit del enunciado
│   │   ├── hit1-setup/
│   │   │   └── README.md                   # Documentación del entorno
│   │   ├── hit2-hello-world/
│   │   │   └── hello.cu
│   │   ├── hit3-libs/
│   │   │   ├── thrust_example.cu
│   │   │   └── README.md                   # Análisis cccl vs CUDA puro
│   │   ├── hit4-hash/
│   │   │   └── md5_gpu.cu
│   │   ├── hit5-brute-force/
│   │   │   └── brute_force.cu
│   │   ├── hit6-prefix-length/
│   │   │   ├── prefix_test.cu
│   │   │   └── README.md                   # Análisis longitud vs tiempo
│   │   └── hit7-range-limits/
│   │       └── range_miner.cu
│   │
│   ├── benchmarks/
│   │   ├── run_benchmarks.sh               # Script comparativa GPU vs CPU
│   │   └── results/                        # CSVs y gráficos de resultados
│   │
│   └── tests/
│       ├── test_cases.json                 # Batería de inputs de prueba
│       └── validate.sh                     # Valida outputs GPU vs CPU
│
├── pilar2-services/
│   │
│   ├── coordinator/                        # Nodo Coordinador de Tareas (NCT)
│   │   ├── src/main/java/com/blockchain/coordinator/
│   │   │   ├── CoordinatorApplication.java
│   │   │   ├── controller/
│   │   │   │   └── BlockController.java    # REST API del coordinador
│   │   │   ├── service/
│   │   │   │   ├── BlockService.java       # Lógica de formación de bloques
│   │   │   │   └── ConsensusService.java   # Algoritmo de consenso
│   │   │   ├── messaging/
│   │   │   │   └── TaskPublisher.java      # Publica tareas en RabbitMQ
│   │   │   └── model/
│   │   │       ├── Block.java              # Record: bloque de la chain
│   │   │       └── MiningTask.java         # Record: tarea enviada a workers
│   │   ├── src/main/resources/
│   │   │   └── application.yml
│   │   ├── Dockerfile
│   │   └── pom.xml
│   │
│   ├── worker/                             # Nodo Worker (minero CPU Java)
│   │   ├── src/main/java/com/blockchain/worker/
│   │   │   ├── WorkerApplication.java
│   │   │   ├── messaging/
│   │   │   │   ├── TaskConsumer.java       # Consume tareas de RabbitMQ
│   │   │   │   └── ResultPublisher.java    # Publica resultado al coordinator
│   │   │   ├── miner/
│   │   │   │   ├── PoWMiner.java           # ExecutorService + ThreadPool
│   │   │   │   └── HashUtils.java
│   │   │   └── model/
│   │   │       └── MiningResult.java       # Record: resultado del PoW
│   │   ├── src/main/resources/
│   │   │   └── application.yml
│   │   ├── Dockerfile
│   │   └── pom.xml
│   │
│   ├── transaction-pool/                   # Pool de Transacciones (TrP)
│   │   ├── src/main/java/com/blockchain/txpool/
│   │   │   ├── TransactionPoolApplication.java
│   │   │   ├── controller/
│   │   │   │   └── TransactionController.java  # Recibe txs nuevas
│   │   │   ├── service/
│   │   │   │   ├── PoolService.java            # Gestiona pool de txs
│   │   │   │   └── SplitService.java           # Fragmenta rangos de nonce
│   │   │   └── model/
│   │   │       └── Transaction.java            # Record: transferencia A→B
│   │   ├── src/main/resources/
│   │   │   └── application.yml
│   │   ├── Dockerfile
│   │   └── pom.xml
│   │
│   ├── blockchain-api/                     # Backend REST (consulta de la chain)
│   │   ├── src/main/java/com/blockchain/api/
│   │   │   ├── BlockchainApiApplication.java
│   │   │   ├── controller/
│   │   │   │   ├── ChainController.java    # GET bloques, estado de la chain
│   │   │   │   └── TransactionController.java
│   │   │   ├── service/
│   │   │   │   └── ChainService.java       # Lee desde Redis
│   │   │   └── model/
│   │   │       └── BlockDTO.java           # Record: respuesta al frontend
│   │   ├── src/main/resources/
│   │   │   └── application.yml
│   │   ├── Dockerfile
│   │   └── pom.xml
│   │
│   ├── frontend/                           # React
│   │   ├── src/
│   │   │   ├── App.jsx
│   │   │   ├── components/
│   │   │   │   ├── BlockList.jsx           # Visualiza la cadena de bloques
│   │   │   │   ├── TransactionForm.jsx     # Envía nuevas transacciones
│   │   │   │   └── MinerStatus.jsx         # Estado de los workers
│   │   │   └── services/
│   │   │       └── api.js                  # Llamadas a blockchain-api
│   │   ├── package.json
│   │   ├── Dockerfile
│   │   └── nginx.conf
│   │
│   ├── shared/                             # Modelos y utils compartidos entre servicios
│   │   └── src/main/java/com/blockchain/shared/
│   │       ├── model/
│   │       │   ├── Block.java
│   │       │   └── Transaction.java
│   │       └── util/
│   │           └── HashUtils.java
│   │   └── pom.xml
│   │
│   └── docker-compose.yml                  # Levanta todo el stack localmente
│
├── pilar3-infra/
│   │
│   ├── opentofu/                           # Infraestructura como código (GKE)
│   │   ├── main.tf                         # Cluster GKE principal
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   └── modules/
│   │       ├── gke-cluster/                # Módulo del cluster
│   │       ├── node-groups/                # Nodegroups infra y apps
│   │       └── external-vms/              # VMs para GPU workers
│   │
│   ├── k8s/                               # Manifests de Kubernetes
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
│   ├── pipelines/                          # GitHub Actions
│   │   ├── pipeline1-infra.yml             # Construye entorno K8s
│   │   ├── pipeline2-services.yml          # Despliega Redis + RabbitMQ
│   │   ├── pipeline3-apps.yml             # Despliega microservicios
│   │   └── pipeline4-workers.yml          # Despliega VMs workers dinámicas
│   │
│   └── tests-load/                         # Pruebas de carga
│       ├── load_test.sh                    # Script principal
│       └── scenarios/
│           ├── bulk_transactions.json      # 1 a 100.000 transacciones
│           ├── prefix_difficulty.json      # Prefijo 1 a 8 caracteres
│           └── pool_fragmentation.json     # Fragmentación 1% a 50%
│
└── docs/
    ├── informe/
    │   ├── pilar1.md
    │   ├── pilar2.md
    │   └── pilar3.md
    ├── diagramas/
    │   └── arquitectura.png
    └── resultados/
        ├── graficos/
        └── analisis.md
```