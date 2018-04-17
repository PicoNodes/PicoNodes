#include <stdio.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_system.h"
#include "esp_spi_flash.h"
#include "nvs_flash.h"
#include "esp_wifi.h"
#include "esp_event.h"
#include "esp_event_loop.h"
#include "esp_spiffs.h"
#include "netclient.h"

#define WIFI_SSID "TestAP"
#define WIFI_PASSWORD "verysecret"

esp_err_t event_handler(void *ctx, system_event_t *event) {
  switch(event->event_id) {
  case SYSTEM_EVENT_STA_GOT_IP:
    printf("Got IP!\n");
    netclient_context *netclient = malloc(sizeof(netclient_context));
    netclient_init(netclient);
    printf("setupping\n");
    if (netclient_setup(netclient) != 0) {
      return ESP_OK;
    }
    printf("connectifying\n");
    if (netclient_connect(netclient) != 0) {
      return ESP_OK;
    }
    printf("Ready to roll!\n");
    break;
  default:
    break;
  }
  return ESP_OK;
}

void app_main()
{
    printf("Hello world!\n");
    ESP_ERROR_CHECK(nvs_flash_init());
    tcpip_adapter_init();
    ESP_ERROR_CHECK(esp_event_loop_init(event_handler, NULL));

    esp_vfs_spiffs_conf_t spiffs = {
      .base_path = "/config",
      .partition_label = "config",
      .max_files = 10,
      .format_if_mount_failed = false
    };
    ESP_ERROR_CHECK(esp_vfs_spiffs_register(&spiffs));

    wifi_init_config_t config = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&config));
    ESP_ERROR_CHECK(esp_wifi_set_storage(WIFI_STORAGE_RAM));
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
