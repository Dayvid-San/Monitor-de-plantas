package com.plantmonitor;

public class Main {
    public static void main(String[] args) throws Exception {
        String command = args.length > 0 ? args[0] : "start";
        switch (command) {
            case "generate-vapid" -> GenerateVapidKeys.run();
            case "simulate" -> Simulator.run();
            case "start" -> App.start();
            default -> {
                System.err.println("Comando desconhecido: " + command + " (use: start | simulate | generate-vapid)");
                System.exit(1);
            }
        }
    }
}
