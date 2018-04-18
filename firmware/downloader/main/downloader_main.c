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

#define DOWNLOADER_QUEUE_BUF_LEN (512)

typedef struct {
  uint16_t len;
  unsigned char buf[DOWNLOADER_QUEUE_BUF_LEN];
} downloader_queue_buf;

typedef struct {
  QueueHandle_t command_queue;
  QueueHandle_t event_queue;
} downloader_queues;

typedef struct {
  QueueHandle_t queue;
  netclient_context *netclient;
} task_netclient_subtask_ctx;

void task_netclient_write(void *parameters) {
  task_netclient_subtask_ctx ctx = *(task_netclient_subtask_ctx *)parameters;
  free(parameters);

  downloader_queue_buf next_item;
  while (1) {
    next_item.len = netclient_read(ctx.netclient, next_item.buf, DOWNLOADER_QUEUE_BUF_LEN);
    xQueueSend(ctx.queue, &next_item, portMAX_DELAY);
  }

  vTaskDelete(NULL);
}

void task_netclient_read(void *parameters) {
  task_netclient_subtask_ctx ctx = *(task_netclient_subtask_ctx *)parameters;
  free(parameters);

  downloader_queue_buf next_item;
  while (1) {
    xQueueReceive(ctx.queue, &next_item, portMAX_DELAY);
    netclient_write(ctx.netclient, next_item.buf, next_item.len);
  }

  vTaskDelete(NULL);
}

void task_netclient(void *parameters) {
  downloader_queues *queues = parameters;
  netclient_context *netclient = malloc(sizeof(netclient_context));
  netclient_init(netclient);
  printf("setupping\n");
  if (netclient_setup(netclient) != 0) {
    printf("setupping failed\n");
    vTaskDelete(NULL);
    return;
  }
  printf("setupped, waiting for IP\n");
  printf("connectifying\n");
  if (netclient_connect(netclient) != 0) {
    printf("connectifying failed\n");
    vTaskDelete(NULL);
    return;
  }
  printf("Ready to roll!\n");

  task_netclient_subtask_ctx *writer_ctx = malloc(sizeof(task_netclient_subtask_ctx));
  writer_ctx->queue = queues->event_queue;
  writer_ctx->netclient = netclient;

  task_netclient_subtask_ctx *reader_ctx = malloc(sizeof(task_netclient_subtask_ctx));
  reader_ctx->queue = queues->command_queue;
  reader_ctx->netclient = netclient;

  xTaskCreate(&task_netclient_write, "task_netclient_write", 16384, writer_ctx, 1, NULL);
  xTaskCreate(&task_netclient_read, "task_netclient_read", 16384, reader_ctx, 1, NULL);

  vTaskDelete(NULL);
}

esp_err_t event_handler(void *ctx, system_event_t *event) {
  downloader_queues *queues = ctx;
  switch(event->event_id) {
  case SYSTEM_EVENT_STA_GOT_IP:
    printf("Got IP!\n");
    xTaskCreate(&task_netclient, "task_netclient", 16384, queues, 1, NULL);
    break;
  default:
    break;
  }
  return ESP_OK;
}

void app_main() {
    printf("Hello world!\n");
    ESP_ERROR_CHECK(nvs_flash_init());
    tcpip_adapter_init();

    downloader_queues *queues = malloc(sizeof(downloader_queues));
    queues->command_queue = xQueueCreate(1, sizeof(downloader_queue_buf));
    queues->event_queue = queues->command_queue;
    /* queues->event_queue = xQueueCreate(1, sizeof(downloader_queue_buf)); */

    ESP_ERROR_CHECK(esp_event_loop_init(event_handler, queues));

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
