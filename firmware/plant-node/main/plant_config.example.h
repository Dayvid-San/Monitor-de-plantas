// Copie este arquivo para "plant_config.h" (gitignored) e ajuste para o seu
// hardware. Cada entrada de PLANT_CHANNELS corresponde a UMA planta cadastrada
// no dashboard com o mesmo device_id (Kconfig PLANT_DEVICE_ID) e o mesmo
// "channel" number.
#pragma once

#include "driver/gpio.h"
#include "esp_adc/adc_oneshot.h"

typedef struct {
    int channel;                 // precisa bater com o "channel" cadastrado no dashboard
    adc_channel_t soil_adc_channel;
    gpio_num_t pump_gpio;
    int moisture_min_pct;        // abaixo disso, liga a bomba (se auto_water estiver ativo)
    int moisture_max_pct;        // ao atingir isso (ou o tempo máximo), desliga a bomba
    int pump_max_seconds;        // trava de segurança: nunca rega mais que isso de uma vez
} plant_channel_t;

// 1 = relé aciona em nível alto, 0 = módulo relé ativo em nível baixo (comum em módulos baratos)
#define PUMP_ACTIVE_LEVEL 0

static const plant_channel_t PLANT_CHANNELS[] = {
    {
        .channel = 0,
        .soil_adc_channel = ADC_CHANNEL_0,   // GPIO36 (VP) no devkit ESP32 típico
        .pump_gpio = GPIO_NUM_25,
        .moisture_min_pct = 30,
        .moisture_max_pct = 60,
        .pump_max_seconds = 8,
    },
    {
        .channel = 1,
        .soil_adc_channel = ADC_CHANNEL_3,   // GPIO39 (VN)
        .pump_gpio = GPIO_NUM_26,
        .moisture_min_pct = 30,
        .moisture_max_pct = 60,
        .pump_max_seconds = 8,
    },
    // Adicione mais plantas aqui (até o número de canais ADC1/GPIOs livres na sua placa).
};
#define PLANT_CHANNEL_COUNT (sizeof(PLANT_CHANNELS) / sizeof(PLANT_CHANNELS[0]))

// Sensores compartilhados por todas as plantas deste nó.
#define DHT22_GPIO       GPIO_NUM_4
#define LDR_ADC_CHANNEL  ADC_CHANNEL_6        // GPIO34

// Calibração do sensor capacitivo de umidade (leitura bruta do ADC, 0-4095).
// Meça com o sensor seco ao ar (SOIL_RAW_DRY) e depois em água (SOIL_RAW_WET)
// usando o log serial e ajuste estes valores.
#define SOIL_RAW_DRY  2800
#define SOIL_RAW_WET  1200

// Intervalo mínimo entre regas de uma mesma planta, mesmo que a umidade
// volte a cair rápido — evita encharcar o vaso.
#define PUMP_COOLDOWN_SEC (15 * 60)
