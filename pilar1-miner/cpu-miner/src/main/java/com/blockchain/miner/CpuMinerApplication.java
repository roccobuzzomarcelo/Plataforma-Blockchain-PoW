package com.blockchain.miner;

import com.blockchain.miner.miner.CpuMiner;
import com.blockchain.miner.miner.MinerResult;

public class CpuMinerApplication {

    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        String cadena = args[0];
        String prefix = args[1];

        CpuMiner miner = new CpuMiner();
        MinerResult result;

        if (args.length == 4) {
            // Con rango: equivalente al Hit #7
            long rangeMin = Long.parseLong(args[2]);
            long rangeMax = Long.parseLong(args[3]);
            result = miner.mine(cadena, prefix, rangeMin, rangeMax);
        } else {
            // Sin rango: equivalente al Hit #5
            result = miner.mine(cadena, prefix);
        }

        System.out.println(result);
    }

    private static void printUsage() {
        System.out.println("Uso:");
        System.out.println("  Sin rango:  CpuMinerApplication <cadena> <prefijo>");
        System.out.println("  Con rango:  CpuMinerApplication <cadena> <prefijo> <min> <max>");
        System.out.println();
        System.out.println("Ejemplos:");
        System.out.println("  CpuMinerApplication blockchain 00");
        System.out.println("  CpuMinerApplication blockchain 00 0 1000");
    }
}