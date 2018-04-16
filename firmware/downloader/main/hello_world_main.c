#include <stdio.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_system.h"
#include "esp_spi_flash.h"
#include "esp_wifi.h"
#include "esp_event.h"
#include "esp_event_loop.h"

#define WIFI_SSID "TestAP"
#define WIFI_PASSWORD "sosecret"

esp_err_t event_handler(void *ctx, system_event_t *event) {
  switch(event->event_id) {
  case SYSTEM_EVENT_STA_GOT_IP:
    printf("Got IP!\n");
    break;
  default:
    break;
  }
  return ESP_OK;
}

void app_main()
{
    printf("Hello world!\n");
    tcpip_adapter_init();
    ESP_ERROR_CHECK(esp_event_loop_init(event_handler, NULL));

    wifi_init_config_t config = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_set_storage(WIFI_STORAGE_RAM));
    ESP_ERROR_CHECK(esp_wifi_init(&config));
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));

    wifi_config_t sta_config = {
      .sta = {
        .ssid = WIFI_SSID,
        .password = WIFI_PASSWORD,
        .bssid_set = false
      }
    };
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &sta_config));
    ESP_ERROR_CHECK(esp_wifi_start());
    printf("Connecting...\n");
    ESP_ERROR_CHECK(esp_wifi_connect());
}
